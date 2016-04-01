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

public class ConfigStateUpdater {
  private static final Log log = LogFactory.getLog(ConfigStateUpdater.class);

  private final KafkaConfigState kafkaConfigState;
  private final ConfigStateValidator validator;
  private final KafkaSchedulerConfiguration newTargetConfig;
  private final KafkaStateService kafkaStateService;

  public ConfigStateUpdater(KafkaSchedulerConfiguration newTargetConfig) {
    this.newTargetConfig = newTargetConfig;
    this.validator = new ConfigStateValidator();
    final KafkaConfiguration kafkaConfiguration = newTargetConfig.getKafkaConfiguration();
    final String frameworkName = newTargetConfig.getServiceConfiguration().getName();

    // We must bootstrap config management with some values from the new config:
    this.kafkaConfigState = new KafkaConfigState(
            frameworkName,
        kafkaConfiguration.getZkAddress(),
        "/");
    this.kafkaStateService = new KafkaStateService(
        kafkaConfiguration.getZkAddress(),
        "/" + frameworkName);
  }

  /**
   * Validates, stores, and returns the current target config based off the scheduler system environment.
   *
   * @throws StateStoreException if the new config fails to be written to persistent storage
   * @throws ValidationException if the new config is invalid or has invalid changes compared to the active config
   */
  public KafkaSchedulerConfiguration getTargetConfig() throws StateStoreException, ValidationException {
    if (!kafkaConfigState.hasTarget()) {
      log.info("Initializing config properties storage with new target.");
      setTargetConfig(newTargetConfig);
    } else {
      KafkaSchedulerConfiguration currTargetConfig = kafkaConfigState.getTargetConfig();

      /* Validator needs to examine values like BROKER_COUNT which ConfigurationChangeDetector is told to ignore.
       * Therefore, always run validation, and run it against the raw list of properties.
       * See also {@link KafkaEnvConfiguratior.ignoredKeys()} */
      validator.validateConfigChange(currTargetConfig, newTargetConfig);

      final BrokerConfiguration currTargetConfigBrokerConfiguration = currTargetConfig.getBrokerConfiguration();
      final BrokerConfiguration newTargetConfigBrokerConfiguration = newTargetConfig.getBrokerConfiguration();

      final KafkaConfiguration currTargetConfigKafkaConfiguration = currTargetConfig.getKafkaConfiguration();
      final KafkaConfiguration newTargetConfigKafkaConfiguration = newTargetConfig.getKafkaConfiguration();

      if (!currTargetConfigBrokerConfiguration.equals(newTargetConfigBrokerConfiguration) ||
              !currTargetConfigKafkaConfiguration.equals(newTargetConfigKafkaConfiguration)) {
        log.info("Config change detected!");
        log.info("Old config: " + currTargetConfig);
        log.info("New config: " + newTargetConfig);
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

  private void setTargetConfig(KafkaSchedulerConfiguration newTargetConfig) throws StateStoreException {
    String targetConfigName = UUID.randomUUID().toString();
    kafkaConfigState.store(newTargetConfig, targetConfigName);
    kafkaConfigState.setTargetName(targetConfigName);
    log.info("Set new target config: " + targetConfigName);
  }
}
