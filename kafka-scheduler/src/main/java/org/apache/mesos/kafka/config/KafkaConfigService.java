package org.apache.mesos.kafka.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.mesos.config.ConfigProperty;
import org.apache.mesos.config.FrameworkConfigurationService;

import com.google.common.collect.Lists;

/**
 * Read-only retrieval service for a single configuration of the Kafka framework.
 * All access is via helper functions which retrieve the requested values from the underlying data.
 */
public class KafkaConfigService {

  /**
   * Simple structure for returning the values of per-broker resources to be reserved.
   */
  public static class BrokerResources {
    private BrokerResources(String cpus, String mem, String disk) {
      this.cpus = Double.parseDouble(cpus);
      this.mem = Double.parseDouble(mem);
      this.disk = Double.parseDouble(disk);
    }

    public final double cpus;
    public final double mem;
    public final double disk;
  }

  private static KafkaConfigService envConfig = null;

  /**
   * Returns a new unverified Kafka Config containing the system environment.
   */
  static KafkaConfigService getEnvConfig() {
    if (null == envConfig) {
      FrameworkConfigurationService configService = new FrameworkConfigurationService();
      new KafkaEnvConfigurator().configure(configService);
      envConfig = new KafkaConfigService(configService);
    }
    return envConfig;
  }

  /**
   * Returns a new Kafka Config containing the provided data from a previous config.
   */
  static KafkaConfigService getHydratedConfig(
      Map<String, Map<String, ConfigProperty>> nsMap) {
    FrameworkConfigurationService configService = new FrameworkConfigurationService();
    new ZkHydratorConfigurator(nsMap).configure(configService);
    return new KafkaConfigService(configService);
  }

  private final FrameworkConfigurationService configService;

  private KafkaConfigService(FrameworkConfigurationService configService) {
    this.configService = configService;
  }

  /**
   * Returns the prefix for the Kafka ZK node path.
   * Eg "/prefix/"
   */
  public String getZkRootPrefix() {
    return "/";
  }

  /**
   * Returns the path for the Kafka ZK node.
   * Eg "/prefix/path"
   */
  public String getZkRoot() {
    return getZkRootPrefix() + getFrameworkName();
  }

  /**
   * Returns the host+path for the Kafka ZK node in the form Kafka prefers.
   * Eg "host:port/path"
   */
  public String getKafkaZkUri() {
    return getZookeeperAddress() + getZkRoot();
  }

  /**
   * Returns to the on-disk path to the unzipped Kafka runtime.
   * e.g.: "/path/to/sandbox/kafka-0.1.2.3"
   */
  public String getKafkaSandboxPath() {
    return configService.get("MESOS_SANDBOX") + "/" + getKafkaVersionName();
  }

  /**
   * Returns the host for the Kafka ZK instance.
   * e.g. "host:port"
   */
  public String getZookeeperAddress() {
    return "master.mesos:2181";
  }

  /**
   * Returns the desired number of Kafka brokers to run.
   */
  public int getBrokerCount() {
    return Integer.parseInt(configService.get("BROKER_COUNT"));
  }

  /**
   * Returns the desired resources to allocate for each Kafka broker.
   */
  public BrokerResources getBrokerResources() {
    return new BrokerResources(
        configService.get("BROKER_CPUS"),
        configService.get("BROKER_MEM"),
        configService.get("BROKER_DISK"));
  }

  /**
   * Returns the list of mesos resource URLs to be downloaded/unpacked before starting Kafka brokers.
   */
  public List<String> getBrokerResourceUris() {
    return Lists.newArrayList(
        configService.get("KAFKA_URI"),
        configService.get("CONTAINER_HOOK_URI"),
        configService.get("JAVA_URI"),
        configService.get("OVERRIDER_URI"));
  }

  /**
   * Returns the user-visible name of the framework.
   * e.g. "kafka0"
   */
  public String getFrameworkName() {
    return configService.get("FRAMEWORK_NAME");
  }

  /**
   * Returns the HTTP url for reaching the scheduler's REST API.
   */
  public URI getApiUri() throws URISyntaxException {
    return new URI("http://" + System.getenv("LIBPROCESS_IP") + ":" + System.getenv("PORT0"));
  }

  /**
   * Returns the configured mesos user used by the framework.
   */
  public String getUser() {
    return configService.get("USER");
  }

  /**
   * Returns the configured mesos role used by the framework.
   */
  public String getRole() {
    return getFrameworkName() + "-role";
  }

  /**
   * Returns the configured mesos principal used by the framework.
   */
  public String getPrincipal() {
    return getFrameworkName() + "-principal";
  }

  /**
   * Returns the version of Kafka being managed by the framework.
   */
  public String getKafkaVersionName() {
    return configService.get("KAFKA_VER_NAME");
  }

  /**
   * Returns the name of the configured Plan strategy, e.g. "INSTALL".
   */
  public String getPlanStrategy() {
    return configService.get("PLAN_STRATEGY");
  }

  /**
   * Returns the name of the configured placement strategy, e.g. "NODE".
   */
  public String getPlacementStrategy() {
    return configService.get("PLACEMENT_STRATEGY");
  }

  /**
   * Returns whether mesos persistent volumes are enabled.
   */
  public boolean persistentVolumes() {
    return Boolean.parseBoolean(configService.get("BROKER_PV"));
  }

  /**
   * Returns whether host ip advertisement is enabled.
   * TODO: more info about what the behavior is between enabled vs disabled
   */
  public boolean advertisedHost() {
    return Boolean.parseBoolean(configService.get("KAFKA_ADVERTISE_HOST_IP"));
  }

  /**
   * Returns the prefix for environment variables which should be passed directly to the Kafka broker runtime, overriding any preexisting values.
   */
  public String getOverridePrefix() {
    return "KAFKA_OVERRIDE_";
  }

  /**
   * For package-internal use only.
   */
  FrameworkConfigurationService getConfigService() {
    return configService;
  }

  /**
   * Returns a copy of the underlying data in this form: map[namespace][name] = value
   */
  public Map<String, Map<String, ConfigProperty>> getNsPropertyMap() {
    return configService.getNsPropertyMap();
  }
}
