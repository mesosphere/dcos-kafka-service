package org.apache.mesos.kafka.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.mesos.config.ConfigProperty;
import org.apache.mesos.config.FrameworkConfigurationService;

/**
 * Represents a single Kafka configuration instance.
 */
public class KafkaConfigService extends FrameworkConfigurationService {

  private static KafkaConfigService envConfig = null;

  public static KafkaConfigService getEnvConfig() {
    if (null == envConfig) {
      envConfig = new KafkaConfigService();
      KafkaEnvConfigurator envConfigurator = new KafkaEnvConfigurator();
      envConfigurator.configure(envConfig);
    }
    return envConfig;
  }

  public static KafkaConfigService getHydratedConfig(
      Map<String, Map<String, ConfigProperty>> nsMap) {
    KafkaConfigService configService = new KafkaConfigService();
    // Translate nsMap to configService
    new ZkHydratorConfigurator(nsMap).configure(configService);
    return configService;
  }

  public String getZkRoot() {
    return "/" + get("FRAMEWORK_NAME");
  }

  public String getKafkaZkUri() {
    return getZookeeperAddress() + getZkRoot();
  }

  /**
   * Returns to the on-disk path to the unzipped Kafka runtime.
   * Eg: "/path/to/sandbox/kafka-0.1.2.3"
   */
  public String getKafkaSandboxPath() {
    return get("MESOS_SANDBOX") + "/" + getKafkaVersionName();
  }

  public String getZookeeperAddress() {
    return "master.mesos:2181";
  }

  public int getBrokerCount() {
    return Integer.parseInt(get("BROKER_COUNT"));
  }

  public String getFrameworkName() {
    return get("FRAMEWORK_NAME");
  }

  /**
   * Returns the HTTP url for reaching the scheduler's REST API.
   */
  public URI getApiUri() throws URISyntaxException {
    return new URI("http://" + get("LIBPROCESS_IP") + ":" + get("PORT0"));
  }

  public String getRole() {
    return getFrameworkName() + "-role";
  }

  public String getPrincipal() {
    return getFrameworkName() + "-principal";
  }

  public String getKafkaVersionName() {
    return get("KAFKA_VER_NAME");
  }

  public String getStrategy() {
    return get("PLAN_STRATEGY");
  }

  public boolean advertisedHost() {
    String overrideStr = get("KAFKA_ADVERTISE_HOST_IP");
    return Boolean.parseBoolean(overrideStr);
  }

  public String getOverridePrefix() {
    return "KAFKA_OVERRIDE_";
  }
}
