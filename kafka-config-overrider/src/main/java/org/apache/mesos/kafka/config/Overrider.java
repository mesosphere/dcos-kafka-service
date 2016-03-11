package org.apache.mesos.kafka.config;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigProperty;

/**
 * Overrides Kafka properties files.
 * Fetches values it will override from the configuration indicated stored in ZK.
 * Produces a non-zero exit code if it fails to fetch.
 */
public final class Overrider {
  private static final Log log = LogFactory.getLog(Overrider.class);
  private static KafkaConfigService envConfig = KafkaConfigService.getEnvConfig();
  private static String overridePrefix = envConfig.getOverridePrefix();

  private static KafkaConfigState configState = new KafkaConfigState(
      envConfig.getFrameworkName(), envConfig.getZookeeperAddress(), envConfig.getZkRootPrefix());

  public static void main(String[] args) {
    if (args.length != 1) {
      log.fatal("Expected a single argument, received: " + Arrays.toString(args));
      System.exit(1);
    }

    KafkaConfigService config = fetchConfig(args[0]);
    Map<String, String> overrides = getOverrides(config);
    UpdateProperties(overrides);
  }

  private static void UpdateProperties(Map<String, String> overrides) {
    String serverPropertiesFileName =
        envConfig.getKafkaSandboxPath() + "/config/server.properties";

    log.info("Updating config file: " + serverPropertiesFileName);

    try {
      FileInputStream in = new FileInputStream(serverPropertiesFileName);
      Properties props = new Properties();
      props.load(in);
      in.close();

      log.info("Opened properties file: " + props);

      FileOutputStream out = new FileOutputStream(serverPropertiesFileName);
      for (Map.Entry<String, String> override : overrides.entrySet()) {
        String key = override.getKey();
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

  private static KafkaConfigService fetchConfig(String configName) {
    log.info("Fetching configuration: " + configName);
    return configState.fetch(configName);
  }

  private static Map<String, String> getOverrides(KafkaConfigService config) {
    Map<String, String> overrides = new HashMap<>();
    for (Map<String, ConfigProperty> configNamespace : config.getNsPropertyMap().values()) {
      for (ConfigProperty configProperty : configNamespace.values()) {
        String key = configProperty.getName();
        if (key.startsWith(overridePrefix)) {
          key = convertKey(key);
          String value = configProperty.getValue();
          overrides.put(key, value);
        }
      }
    }

    Map<String, String> env = System.getenv();
    for (String key : env.keySet()) {
      if (key.startsWith(overridePrefix)) {
        String value = env.get(key);
        key = convertKey(key);
        overrides.put(key, value);
      }
    }

    if (config.advertisedHost()) {
      String ip = getIp();
      overrides.put("advertised.host.name", ip);
    } else {
      log.info("Advertise host ip is not enabled.");
    }

    return overrides;
  }

  private static String convertKey(String key) {
    key = key.substring(key.lastIndexOf(overridePrefix) + overridePrefix.length());
    key = key.toLowerCase();
    key = key.replace('_', '.');
    return key;
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
