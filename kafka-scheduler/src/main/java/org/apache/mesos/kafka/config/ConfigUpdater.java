package org.apache.mesos.kafka.config;

import java.util.Arrays;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.config.ConfigurationChangeDetector;
import org.apache.mesos.config.ConfigurationChangeNamespaces;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.state.StateStoreException;

public class ConfigUpdater {
  private final Log log = LogFactory.getLog(KafkaScheduler.class);
  private final KafkaConfigState kafkaConfigState;
  private final KafkaConfigService newTargetConfig;
  private final KafkaStateService kafkaStateService;

  public ConfigUpdater() {
    this.newTargetConfig = KafkaConfigService.getEnvConfig();
    this.kafkaConfigState = new KafkaConfigState(
        newTargetConfig.getFrameworkName(), newTargetConfig.getZookeeperAddress(), newTargetConfig.getZkRootPrefix());
    this.kafkaStateService = KafkaStateService.getStateService(newTargetConfig);
  }

  /**
   * Validates, stores, and returns the current target config based off the scheduler system environment.
   *
   * @throws StateStoreException if the new configuration is invalid, either on its own or relative to a previous config
   */
  public KafkaConfigService getTargetConfig() throws StateStoreException {
    if (!kafkaConfigState.hasTarget()) {
      validateAndSetTargetConfig(newTargetConfig);
    } else {
      KafkaConfigService currTarget = kafkaConfigState.getTargetConfig();

      ConfigurationChangeDetector changeDetector = new ConfigurationChangeDetector(
          currTarget.getNsPropertyMap(),
          newTargetConfig.getNsPropertyMap(),
          new ConfigurationChangeNamespaces("*", "*"));

      if (changeDetector.isChangeDetected()) {
        log.info("Detected changed properties.");
        logConfigChange(changeDetector);
        validateConfigChange(changeDetector);
        validateAndSetTargetConfig(newTargetConfig);
        kafkaConfigState.syncConfigs(kafkaStateService);
      } else {
        log.info("No change detected.");
      }
    }
    return newTargetConfig;
  }

  /**
   * Returns the underlying config state storage.
   */
  public KafkaConfigState getConfigState() {
    //TODO return a read-only interface that just lists configs
    return kafkaConfigState;
  }

  public KafkaStateService getKafkaState() {
    return kafkaStateService;
  }

  private void logConfigChange(ConfigurationChangeDetector changeDetector) {
    log.info("Extra config properties detected: " + Arrays.toString(changeDetector.getExtraConfigs().toArray()));
    log.info("Missing config properties detected: " + Arrays.toString(changeDetector.getMissingConfigs().toArray()));
    log.info("Changed config properties detected: " + Arrays.toString(changeDetector.getChangedProperties().toArray()));
  }

  private void validateAndSetTargetConfig(KafkaConfigService newTargetConfig) throws StateStoreException {
    validateConfig(newTargetConfig);
    String targetConfigName = UUID.randomUUID().toString();
    kafkaConfigState.store(newTargetConfig, targetConfigName);
    kafkaConfigState.setTargetName(targetConfigName);
    log.info("Set new target config: " + targetConfigName);
  }

  private static void validateConfig(KafkaConfigService newTargetConfig) throws StateStoreException {
    //TODO validate values, eg block missing required values
  }

  private static void validateConfigChange(ConfigurationChangeDetector changeDetector) throws StateStoreException {
    //TODO validate changes, eg block broker count decreasing
  }
}
