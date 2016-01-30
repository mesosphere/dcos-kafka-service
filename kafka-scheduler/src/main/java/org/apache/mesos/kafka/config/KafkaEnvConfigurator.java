package org.apache.mesos.kafka.config;

import org.apache.mesos.config.ConfigUtil;
import org.apache.mesos.config.FrameworkConfigurationService;
import org.apache.mesos.config.configurator.Configurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Takes system env variables adds them to the configuration.
 */
public class KafkaEnvConfigurator implements Configurator {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public void configure(FrameworkConfigurationService frameworkConfigurationService) {
    logger.info("Configuring System env into configuration service.");

    Map<String, String> map = System.getenv();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      frameworkConfigurationService.setValue((entry.getKey()), entry.getValue());
    }
  }
}
