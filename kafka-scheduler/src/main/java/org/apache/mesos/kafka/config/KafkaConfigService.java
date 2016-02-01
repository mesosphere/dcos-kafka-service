package org.apache.mesos.kafka.config;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.config.FrameworkConfigurationService;

public class KafkaConfigService extends FrameworkConfigurationService {
  private final Log log = LogFactory.getLog(KafkaConfigService.class);

  private static KafkaConfigService targetConfig = null;
  private static KafkaConfigService persistedConfig = null;

  public static KafkaConfigService getTargetConfig() {
    if (null == targetConfig) {
      targetConfig = new KafkaConfigService();
      KafkaEnvConfigurator envConfigurator = new KafkaEnvConfigurator();
      envConfigurator.configure(targetConfig);
    }

    return targetConfig;
  }

  public static KafkaConfigService getPersistedConfig() {
    return getTargetConfig();
  }

  public String getZkRoot() {
    return "/" + get("FRAMEWORK_NAME");
  }

  public String getKafkaZkUri() {
    return "master.mesos:2181" + getZkRoot();
  }
}
