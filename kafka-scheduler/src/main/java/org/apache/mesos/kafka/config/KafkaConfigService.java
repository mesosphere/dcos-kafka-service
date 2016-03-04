package org.apache.mesos.kafka.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.mesos.config.ConfigProperty;
import org.apache.mesos.config.FrameworkConfigurationService;

import com.google.common.collect.Lists;

public class KafkaConfigService {

  public static class BrokerResources {
    private BrokerResources(
        String cpus, String mem, String disk) {
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
   * Returns a new Kafka Config containing the system environment.
   */
  static KafkaConfigService getEnvConfig() {
    if (null == envConfig) {
      envConfig = new KafkaConfigService();
      new KafkaEnvConfigurator().configure(envConfig.configService);
    }
    return envConfig;
  }

  static KafkaConfigService getHydratedConfig(
      Map<String, Map<String, ConfigProperty>> nsMap) {
    KafkaConfigService configService = new KafkaConfigService();
    new ZkHydratorConfigurator(nsMap).configure(configService.configService);
    return configService;
  }

  private final FrameworkConfigurationService configService;

  private KafkaConfigService() {
    configService = new FrameworkConfigurationService();
  }

  public String getZkRoot() {
    return "/" + configService.get("FRAMEWORK_NAME");
  }

  public String getKafkaZkUri() {
    return getZookeeperAddress() + getZkRoot();
  }

  public String getKafkaBinPath() {
    return configService.get("MESOS_SANDBOX") + "/" + getKafkaVersionName() + "/bin/";
  }

  public String getZookeeperAddress() {
    return "master.mesos:2181";
  }

  public int getBrokerCount() {
    return Integer.parseInt(configService.get("BROKER_COUNT"));
  }

  public BrokerResources getBrokerResources() {
    return new BrokerResources(
        configService.get("BROKER_CPUS"),
        configService.get("BROKER_MEM"),
        configService.get("BROKER_DISK"));
  }

  public String getFrameworkName() {
    return configService.get("FRAMEWORK_NAME");
  }

  public URI getApiUri() throws URISyntaxException {
    return new URI("http://" + configService.get("LIBPROCESS_IP") + ":" + configService.get("PORT0"));
  }

  public String getUser() {
    return configService.get("USER");
  }

  public String getRole() {
    return getFrameworkName() + "-role";
  }

  public String getPrincipal() {
    return getFrameworkName() + "-principal";
  }

  public String getKafkaVersionName() {
    return configService.get("KAFKA_VER_NAME");
  }

  public String getPlanStrategy() {
    return configService.get("PLAN_STRATEGY");
  }

  public String getPlacementStrategy() {
    return configService.get("PLACEMENT_STRATEGY");
  }

  public boolean persistentVolumes() {
    return Boolean.parseBoolean(configService.get("BROKER_PV"));
  }

  public boolean advertisedHost() {
    return Boolean.parseBoolean(configService.get("KAFKA_ADVERTISE_HOST_IP"));
  }

  public String getOverridePrefix() {
    return "KAFKA_OVERRIDE_";
  }

  public List<String> getMesosResourceUris() {
    return Lists.newArrayList(
        configService.get("KAFKA_URI"),
        configService.get("CONTAINER_HOOK_URI"),
        configService.get("JAVA_URI"),
        configService.get("OVERRIDER_URI"));
  }

  /**
   * For package-internal use only.
   */
  FrameworkConfigurationService getConfigService() {
    return configService;
  }

  public Map<String, Map<String, ConfigProperty>> getNsPropertyMap() {
    return configService.getNsPropertyMap();
  }
}
