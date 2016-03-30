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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Overrides Kafka properties files.
 * Fetches values it will override from the configuration indicated stored in ZK.
 * Produces a non-zero exit code if it fails to fetch.
 */
public final class Overrider extends Application<KafkaSchedulerConfiguration> {
  private static final Log log = LogFactory.getLog(Overrider.class);

  private static KafkaConfigState configState;

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
  public void initialize(Bootstrap<KafkaSchedulerConfiguration> bootstrap) {
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
  public void run(KafkaSchedulerConfiguration configEnv, Environment environment) throws Exception {
    this.configuration = configEnv;
    configId = System.getenv().get("CONFIG_ID");

    configState = new KafkaConfigState(
            configEnv.getServiceConfiguration().getName(), configEnv.getKafkaConfiguration().getZkAddress(), "/");

    if (StringUtils.isBlank(configId)) {
      log.error("Require configId. Please set CONFIG_ID env var correctly.");
      System.exit(1);
    }

    log.info("" + configEnv);

    KafkaSchedulerConfiguration configZk = fetchConfig(configId);
    Map<String, String> overrides = getOverrides(configZk, configEnv);
    UpdateProperties(overrides);
    System.exit(0);
  }

  private void UpdateProperties(Map<String, String> overrides) {
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

  private static KafkaSchedulerConfiguration fetchConfig(String configName) {
    log.info("Fetching configuration: " + configName);
    return configState.fetch(configName);
  }

  private static Map<String, String> getOverrides(
          KafkaSchedulerConfiguration fromZk,
          KafkaSchedulerConfiguration fromEnv) {
    Map<String, String> overrides = new HashMap<>();

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
