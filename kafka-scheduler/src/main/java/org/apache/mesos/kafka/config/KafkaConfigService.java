package org.apache.mesos.kafka.config;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.config.ConfigProperty;
import org.apache.mesos.config.FrameworkConfigurationService;

public class KafkaConfigService extends FrameworkConfigurationService {
  private final Log log = LogFactory.getLog(KafkaConfigService.class);

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
    ZkHydratorConfigurator zkConfigurator = new ZkHydratorConfigurator(nsMap);
    zkConfigurator.configure(configService);

    return configService;
  }

  public String getZkRoot() {
    return "/" + get("FRAMEWORK_NAME");
  }

  public String getKafkaZkUri() {
    return getZookeeperAddress() + getZkRoot();
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

  public String getOverridePrefix() {
    return "KAFKA_OVERRIDE_";
  }
}
