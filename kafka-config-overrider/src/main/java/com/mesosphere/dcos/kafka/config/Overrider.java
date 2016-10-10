package com.mesosphere.dcos.kafka.config;

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
  private OverriderEnvironment overriderEnvironment;

  public enum OverriderExitCode {
    SUCCESS,
    MISSING_CONFIG_ID,
    FAILED_TO_UPDATE_PROPERTIES_FILE
  }

  public static void main(String[] args) throws Exception {
    new Overrider().run(args);
  }

  protected Overrider() {
    super();
    overriderEnvironment = new OverriderEnvironment();
  }

  protected Overrider(OverriderEnvironment overriderEnvironment) {
    super();
    this.overriderEnvironment = overriderEnvironment;
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
  public void run(DropwizardConfiguration rootConfigEnv, Environment environment) throws Exception {
    // Initialize config objects based on container env. This contains ephemeral container-specific
    // settings, either provided by Mesos or by the Scheduler's PersistentOfferRequirementProvider.
    final KafkaSchedulerConfiguration configEnv = rootConfigEnv.getSchedulerConfiguration();
    final KafkaConfiguration kafkaConfigEnv = configEnv.getKafkaConfiguration();
    KafkaConfigState configState = new KafkaConfigState(configEnv.getZookeeperConfig());
    log.info("Environment config: " + configEnv);

    // Retrieve config objects for target configuration (from ZK). This contains persistent settings
    // which would have been configured at the service level.
    final String configId = overriderEnvironment.getConfigurationId();
    if (StringUtils.isBlank(configId)) {
      log.error("Configuration ID is required. Please set CONFIG_ID env var correctly.");
      System.exit(OverriderExitCode.MISSING_CONFIG_ID.ordinal());
    }
    log.info("Fetching ZK config: " + configId);
    KafkaSchedulerConfiguration configZk = configState.fetch(UUID.fromString(configId));
    KafkaConfiguration kafkaConfigZk = configZk.getKafkaConfiguration();
    log.info("Fetched ZK config '" + configId + "': " + kafkaConfigZk);

    Map<String, String> kafkaOverrides = getKafkaOverrides(kafkaConfigZk, kafkaConfigEnv);
    kafkaOverrides.putAll(getStatsdConfig(kafkaConfigEnv.getKafkaSandboxPath()));

    String kafkaPropertiesFileName =
        kafkaConfigEnv.getKafkaSandboxPath() + "/config/server.properties";
    updateProperties(kafkaPropertiesFileName, kafkaOverrides);

    System.exit(OverriderExitCode.SUCCESS.ordinal());
  }

  private static void updateProperties(
      String kafkaPropertiesFileName, Map<String, String> overrides) {

    log.info("Updating config file: " + kafkaPropertiesFileName);

    try {
      FileInputStream in = new FileInputStream(kafkaPropertiesFileName);
      Properties props = new Properties();
      props.load(in);
      in.close();

      log.info("Opened properties file: " + props);

      FileOutputStream out = new FileOutputStream(kafkaPropertiesFileName);
      for (Map.Entry<String, String> entry : overrides.entrySet()) {
        Object lastValue = props.setProperty(entry.getKey(), entry.getValue());
        if (lastValue != null) {
          log.info(String.format("Overriding Kafka property %s = %s (previously %s)",
              entry.getKey(), entry.getValue(), lastValue.toString()));
        } else {
          log.info(String.format("Overriding Kafka property %s = %s (previously unset)",
              entry.getKey(), entry.getValue()));
        }
      }

      log.info("Saving properties file: " + props);
      props.store(out, null);
      out.close();
    } catch (Exception ex) {
      log.error("Failed to update properties with exception: ",ex);
      System.exit(OverriderExitCode.FAILED_TO_UPDATE_PROPERTIES_FILE.ordinal());
    }
  }

  private static Map<String, String> getKafkaOverrides(
          KafkaConfiguration kafkaConfigZk,
          KafkaConfiguration kafkaConfigEnv) {
    // Use treemap for ordered printing in logs:
    Map<String, String> kafkaProperties = new TreeMap<>();

    // Copy any available overrides from the zk config. These come from the user-visible
    // ZOOKEEPER_OVERRIDE_* settings, and are pre-translated to "x.y.z" format.
    log.info("Overrides from ZK Config: " + kafkaConfigZk.getOverrides());
    kafkaProperties.putAll(kafkaConfigZk.getOverrides());

    // Fetch any available overrides from explicitly-set envvars. These settings are manually
    // embedded into the container environment at launch-time by PersistentOfferRequirementProvider.
    log.info("Overrides from System Environment: " + kafkaConfigEnv.getOverrides());
    kafkaProperties.putAll(kafkaConfigEnv.getOverrides());
    kafkaProperties.put("listeners", "PLAINTEXT://:" + kafkaConfigEnv.getOverrides().get("port"));

    // One additional override which is only determined *after* the container has started:
    if (kafkaConfigZk.isKafkaAdvertiseHostIp()) {
      String ip = getIp();
      log.info("Using advertised host ip: " + ip);
      kafkaProperties.put("advertised.host.name", ip);
    } else {
      log.info("Advertise host ip is not enabled.");
    }

    return kafkaProperties;
  }

  private Map<String, String> getStatsdConfig(String kafkaSandboxPath) {
    String statsdHost = overriderEnvironment.getStatsdUdpHost();
    String statsdPort = overriderEnvironment.getStatsdUdpPort();
    if (StringUtils.isBlank(statsdHost) || StringUtils.isBlank(statsdPort)) {
      log.info("No metrics env found, skipping metrics setup");
      return new TreeMap<>();
    }
    log.info("Found env-provided metrics endpoint, configuring Kafka output: " + statsdHost + ":" + statsdPort);

    try {
      copyMetricsLibsIntoClasspath(kafkaSandboxPath);
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
  private void copyMetricsLibsIntoClasspath(String kafkaSandboxPath) throws Exception {
    String sandboxPath = overriderEnvironment.getMesosSandbox();
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
    File kafkaLibsDir = new File(kafkaSandboxPath, "libs");
    if (!kafkaLibsDir.isDirectory()) {
      throw new IllegalStateException(
          "Library destination path is not a directory: " + kafkaLibsDir.getAbsolutePath());
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
