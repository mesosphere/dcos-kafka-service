package org.apache.mesos.kafka.config;

import java.util.Arrays;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.config.ConfigurationChangeDetector;
import org.apache.mesos.config.ConfigurationChangeNamespaces;
import org.apache.mesos.kafka.config.ConfigStateValidator.ValidationException;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.state.StateStoreException;

/**
 * Retrieves and stores configurations in the state store.
 */
public class ConfigStateUpdater {

  private static final Log log = LogFactory.getLog(ConfigStateUpdater.class);
  private static final ConfigurationChangeNamespaces namespaces = new ConfigurationChangeNamespaces("*", "*");

  private final KafkaConfigState kafkaConfigState;
  private final ConfigStateValidator validator;
  private final KafkaConfigService newTargetConfig;
  private final KafkaStateService kafkaStateService;

  public ConfigStateUpdater() {
    this.newTargetConfig = KafkaConfigService.getEnvConfig();
    this.validator = new ConfigStateValidator(namespaces);
    // We must bootstrap config management with some values from the new config:
    this.kafkaConfigState = new KafkaConfigState(
        newTargetConfig.getFrameworkName(),
        newTargetConfig.getZookeeperAddress(),
        newTargetConfig.getZkRootPrefix());
    this.kafkaStateService = new KafkaStateService(
        newTargetConfig.getZookeeperAddress(),
        newTargetConfig.getZkRoot());
  }

  /**
   * Validates, stores, and returns the current target config based off the scheduler system environment.
   *
   * @throws StateStoreException if the new config fails to be written to persistent storage
   * @throws ValidationException if the new config is invalid or has invalid changes compared to the active config
   */
  public KafkaConfigService getTargetConfig() throws StateStoreException, ValidationException {
    if (!kafkaConfigState.hasTarget()) {
      log.info("Initializing config properties storage with new target.");
      setTargetConfig(newTargetConfig);
    } else {
      KafkaConfigService currTargetConfig = kafkaConfigState.getTargetConfig();

      /* Validator needs to examine values like BROKER_COUNT which ConfigurationChangeDetector is told to ignore.
       * Therefore, always run validation, and run it against the raw list of properties.
       * See also {@link KafkaEnvConfiguratior.ignoredKeys()} */
      validator.validateConfigChange(currTargetConfig.getNsPropertyMap(), newTargetConfig.getNsPropertyMap());

      ConfigurationChangeDetector changeDetector = new ConfigurationChangeDetector(
          currTargetConfig.getNsPropertyMap(),
          newTargetConfig.getNsPropertyMap(),
          namespaces);
      if (changeDetector.isChangeDetected()) {
        log.info("Extra config properties detected: " + Arrays.toString(changeDetector.getExtraConfigs().toArray()));
        log.info("Missing config properties detected: " + Arrays.toString(changeDetector.getMissingConfigs().toArray()));
        log.info("Changed config properties detected: " + Arrays.toString(changeDetector.getChangedProperties().toArray()));
        setTargetConfig(newTargetConfig);
        kafkaConfigState.syncConfigs(kafkaStateService);
        kafkaConfigState.cleanConfigs(kafkaStateService);
      } else {
        log.info("No config properties changes detected.");
      }
    }
    return newTargetConfig;
  }

  /**
   * Returns the underlying config state storage to be used elsewhere.
   * This will only contain pre-validated target configs.
   */
  public KafkaConfigState getConfigState() {
    return kafkaConfigState;
  }

  /**
   * Returns the underlying kafka state storage to be used elsewhere.
   */
  public KafkaStateService getKafkaState() {
    return kafkaStateService;
  }

  private void setTargetConfig(KafkaConfigService newTargetConfig) throws StateStoreException {
    String targetConfigName = UUID.randomUUID().toString();
    kafkaConfigState.store(newTargetConfig, targetConfigName);
    kafkaConfigState.setTargetName(targetConfigName);
    log.info("Set new target config: " + targetConfigName);
  }
}
