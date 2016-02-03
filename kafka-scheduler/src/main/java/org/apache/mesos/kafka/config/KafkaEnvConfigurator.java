package org.apache.mesos.kafka.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.mesos.config.ConfigUtil;
import org.apache.mesos.config.FrameworkConfigurationService;
import org.apache.mesos.config.configurator.Configurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      frameworkConfigurationService.setValue(entry.getKey(), entry.getValue());
    }

    for (String ignoredKey : ignoredKeys()) {
      frameworkConfigurationService.setValue(
          FrameworkConfigurationService.IGNORED_NAMESPACE,
          ignoredKey,
          "");
    }
  }

  private List<String> ignoredKeys() {
    return Arrays.asList(
        "LIBPROCESS_IP",
        "PORT0",
        "_",
        "HOST",
        "MESOS_SLAVE_ID",
        "MESOS_TASK_ID",
        "MESOS_SLAVE_PID",
        "MARATHON_APP_VERSION",
        "PATH",
        "MESOS_EXECUTOR_ID",
        "PORT",
        "MESOS_SANDBOX",
        "PORT_10000",
        "MESOS_DIRECTORY",
        "PWD",
        "PORTS");
  }
}
