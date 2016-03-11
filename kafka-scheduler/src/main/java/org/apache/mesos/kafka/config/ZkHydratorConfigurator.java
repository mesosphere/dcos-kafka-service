package org.apache.mesos.kafka.config;

import java.util.Map;

import org.apache.mesos.config.configurator.Configurator;
import org.apache.mesos.config.ConfigProperty;
import org.apache.mesos.config.FrameworkConfigurationService;

public class ZkHydratorConfigurator implements Configurator {
  Map<String, Map<String, ConfigProperty>> zkConfig = null;

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
