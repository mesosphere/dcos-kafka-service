package org.apache.mesos.kafka.config;

import com.google.common.base.CaseFormat;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.java8.Java8Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.config.ConfigStoreException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

/**
 * Overrides Kafka properties files.
 * Fetches values it will override from the configuration indicated stored in ZK.
 * Produces a non-zero exit code if it fails to fetch.
 */
public final class Overrider extends Application<DropwizardConfiguration> {
  private static final Log log = LogFactory.getLog(Overrider.class);

  private KafkaConfigState configState;

  private String configId;
  private KafkaSchedulerConfiguration configuration;

  public static void main(String[] args) throws Exception {
    new Overrider().run(args);
  }

  protected Overrider() {
    super();
  }

  @Override
  public String getName() {
    return "DCOS Kafka Service";
  }

  @Override
  public void initialize(Bootstrap<DropwizardConfiguration> bootstrap) {
    super.initialize(bootstrap);

    final EnvironmentVariableSubstitutor environmentVariableSubstitutor
            = new EnvironmentVariableSubstitutor(false, true);

    bootstrap.addBundle(new Java8Bundle());
    bootstrap.setConfigurationSourceProvider(
            new SubstitutingSourceProvider(
                    bootstrap.getConfigurationSourceProvider(),
                    environmentVariableSubstitutor));
  }

  @Override
  public void run(DropwizardConfiguration configEnv, Environment environment) throws Exception {
    this.configuration = configEnv.getSchedulerConfiguration();
    configId = System.getenv("CONFIG_ID");

    configState = new KafkaConfigState(configuration.getZookeeperConfig());

    if (StringUtils.isBlank(configId)) {
      log.error("Require configId. Please set CONFIG_ID env var correctly.");
      System.exit(1);
    }

    log.info("Default config: " + configEnv);

    KafkaSchedulerConfiguration configZk = fetchConfig(configId);

    log.info("Fetched config: " + configZk);

    Map<String, String> overrides = getOverrides(configZk, configuration);
    Map<String, String> statsdConfig = getStatsdConfig(configuration);
    updateProperties(overrides, statsdConfig);
    System.exit(0);
  }

  private void updateProperties(
      Map<String, String> overrides, Map<String, String> statsdConfig) {
    String serverPropertiesFileName =
        configuration.getKafkaConfiguration().getKafkaSandboxPath() + "/config/server.properties";

    log.info("Updating config file: " + serverPropertiesFileName);

    try {
      FileInputStream in = new FileInputStream(serverPropertiesFileName);
      Properties props = new Properties();
      props.load(in);
      in.close();

      log.info("Opened properties file: " + props);

      FileOutputStream out = new FileOutputStream(serverPropertiesFileName);
      for (Map.Entry<String, String> override : overrides.entrySet()) {
        String key = convertKey(override.getKey());
        String value = override.getValue();
        log.info("Overriding key: " + key + " value: " + value);
        props.setProperty(key, value);
      }
      for (Map.Entry<String, String> entry : statsdConfig.entrySet()) {
        log.info("Overriding metrics key: " + entry.getKey() + " value: " + entry.getValue());
        props.setProperty(entry.getKey(), entry.getValue());
      }

      log.info("Saving properties file: " + props);
      props.store(out, null);
      out.close();
    } catch (Exception ex) {
      log.error("Failed update properties with exception: " + ex);
    }
  }

  private static String convertKey(String key) {
    key = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, key);
    key = key.replace('_', '.');
    return key;
  }

  private KafkaSchedulerConfiguration fetchConfig(String configName) throws ConfigStoreException {
    log.info("Fetching configuration: " + configName);
    return configState.fetch(UUID.fromString(configName));
  }

  private static Map<String, String> getOverrides(
          KafkaSchedulerConfiguration fromZk,
          KafkaSchedulerConfiguration fromEnv) {
    Map<String, String> overrides = new TreeMap<>();

    final Map<String, String> overridesFromZk = fromZk.getKafkaConfiguration().getOverrides();
    log.info("Overrides from ZK: " + overridesFromZk);
    overrides.putAll(overridesFromZk);
    final Map<String, String> overridesFromEnv = fromEnv.getKafkaConfiguration().getOverrides();
    log.info("Overrides from ENV: " + overridesFromEnv);
    overrides.putAll(overridesFromEnv);

    if (fromZk.getKafkaConfiguration().isKafkaAdvertiseHostIp()) {
      String ip = getIp();
      overrides.put("advertised.host.name", ip);
    } else {
      log.info("Advertise host ip is not enabled.");
    }

    return overrides;
  }

  private static Map<String, String> getStatsdConfig(
      KafkaSchedulerConfiguration configuration) {
    String statsdHost = System.getenv("STATSD_UDP_HOST");
    String statsdPort = System.getenv("STATSD_UDP_PORT");
    if (StringUtils.isBlank(statsdHost) || StringUtils.isBlank(statsdPort)) {
      log.info("No metrics env found, skipping metrics setup");
      return new TreeMap<>();
    }
    log.info("Found env-provided metrics endpoint, configuring Kafka output: " + statsdHost + ":" + statsdPort);

    try {
      copyMetricsLibsIntoClasspath(configuration);
    } catch (Exception e) {
      log.warn("Failed to copy metrics libraries to classpath, skipping metrics config.", e);
      return new TreeMap<>();
    }

    Map<String, String> config = new TreeMap<>();
    config.put("kafka.metrics.reporters", "com.airbnb.kafka.KafkaStatsdMetricsReporter");
    config.put("external.kafka.statsd.reporter.enabled", "true");
    config.put("external.kafka.statsd.host", statsdHost);
    config.put("external.kafka.statsd.port", statsdPort);
    config.put("external.kafka.statsd.tag.enabled", "true");
    config.put("external.kafka.statsd.metrics.exclude_regex", "");
    return config;
  }

  /**
   * Copies files in SANDBOX/overrider/metrics-libs/* into SANDBOX/kafka_<any>/libs/ classpath.
   *
   * @throws Exception if any unexpected state is hit along the way, or if copies fail
   */
  private static void copyMetricsLibsIntoClasspath(KafkaSchedulerConfiguration configuration) throws Exception {
    String sandboxPath = System.getenv("MESOS_SANDBOX");
    if (StringUtils.isBlank(sandboxPath)) {
      throw new IllegalStateException("Missing 'MESOS_SANDBOX' env");
    }
    File mesosSandboxDir = new File(sandboxPath);
    if (!mesosSandboxDir.isDirectory()) {
      throw new IllegalStateException("'MESOS_SANDBOX' is not a directory: " + sandboxPath);
    }

    // find src dir: SANDBOX/overrider/metrics-libs/*.jar
    File libSrcDir = new File(new File(sandboxPath, "overrider"), "metrics-libs");
    if (!libSrcDir.isDirectory()) {
      throw new IllegalStateException(
          "Library source path is not a directory: " + libSrcDir.getAbsolutePath());
    }
    List<File> srcFiles = new ArrayList<>();
    for (File file : libSrcDir.listFiles()) {
      if (file.isFile() && file.getName().endsWith(".jar")) {
        srcFiles.add(file);
      }
    }
    if (srcFiles.isEmpty()) {
      throw new IllegalStateException(
          "Didn't find any jar files to be copied from directory: " + libSrcDir.getAbsolutePath());
    }

    // find dest dir(s)
    File kafkaLibsDir = new File(configuration.getKafkaConfiguration().getKafkaSandboxPath(), "libs");
    if (!kafkaLibsDir.isDirectory()) {
      throw new IllegalStateException("Library destination path is not a directory: " + kafkaLibsDir.getAbsolutePath());
    }
    log.info("Copying " + String.valueOf(srcFiles.size()) + " files to Kafka libs directory: " + kafkaLibsDir.getAbsolutePath());
    for (File srcFile : srcFiles) {
      log.info("Copying " + srcFile.getName());
      FileUtils.copyFile(srcFile, new File(kafkaLibsDir, srcFile.getName()));
    }
  }

  private static String getIp() {
    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      CommandLine commandline = CommandLine.parse("/opt/mesosphere/bin/detect_ip");
      DefaultExecutor exec = new DefaultExecutor();
      PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
      exec.setStreamHandler(streamHandler);

      log.info("Getting ip with command: " + commandline);
      if (exec.isFailure(exec.execute(commandline))) {
        log.error("Got error code when executing: " + commandline.toString());
        return null;
      } else {
        String ip = outputStream.toString().trim();
        log.info("Got ip: " + ip);
        return ip;
      }
    } catch (Exception ex) {
      log.error("Failed to detect ip address with exception: " + ex);
      return null;
    }
  }
}
