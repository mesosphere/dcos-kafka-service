package org.apache.mesos.kafka.config;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.config.FrameworkConfigurationService;

public class KafkaConfigService extends FrameworkConfigurationService {
  private final Log log = LogFactory.getLog(KafkaConfigService.class);

  private static KafkaConfigService configService = null;

  public static KafkaConfigService getConfigService() {
    if (null == configService) {
      configService = new KafkaConfigService();
      KafkaEnvConfigurator envConfigurator = new KafkaEnvConfigurator();
      envConfigurator.configure(configService);
    }

    return configService;
  }

  public String getZkRoot() {
    return "/" + get("FRAMEWORK_NAME");
  }

  public String getKafkaZkUri() {
    return "master.mesos:2181" + getZkRoot();
  }
}
