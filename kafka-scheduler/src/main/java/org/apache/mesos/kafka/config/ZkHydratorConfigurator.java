package org.apache.mesos.kafka.config;

import org.apache.mesos.config.ConfigProperty;
import org.apache.mesos.config.FrameworkConfigurationService;
import org.apache.mesos.config.configurator.Configurator;

import java.util.Map;

/**
 * Retrieves Configuration from Zookeeper.
 */
public class ZkHydratorConfigurator implements Configurator {
  private Map<String, Map<String, ConfigProperty>> zkConfig = null;

  public ZkHydratorConfigurator(Map<String, Map<String, ConfigProperty>> zkConfig) {
    this.zkConfig = zkConfig;
  }

  public void configure(FrameworkConfigurationService fwkConfigService) {
    for (Map.Entry<String, Map<String, ConfigProperty>> namespace : zkConfig.entrySet()) {
      String ns = namespace.getKey();

      for (Map.Entry<String, ConfigProperty> configProperty : namespace.getValue().entrySet()) {
        fwkConfigService.setValue(ns, configProperty.getValue().getName(), configProperty.getValue().getValue());
      }
    }
  }
}
