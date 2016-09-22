package com.mesosphere.dcos.kafka.config;

import com.mesosphere.dcos.kafka.commons.state.KafkaState;
import com.mesosphere.dcos.kafka.config.ConfigStateValidator.ValidationException;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.config.ConfigStoreException;

import java.util.UUID;

/**
 * Retrieves and stores configurations in the state store.
 */
public class ConfigStateUpdater {
  private static final Log log = LogFactory.getLog(ConfigStateUpdater.class);

  private final KafkaConfigState kafkaConfigState;
  private final ConfigStateValidator validator;
  private final KafkaSchedulerConfiguration newTargetConfig;
  private final FrameworkState frameworkState;
  private final KafkaState kafkaState;

  public ConfigStateUpdater(KafkaSchedulerConfiguration newTargetConfig) {
    this.newTargetConfig = newTargetConfig;

    // We must bootstrap ZK settings from the new config:
    ZookeeperConfiguration zkConfig = newTargetConfig.getZookeeperConfig();
    this.kafkaConfigState = new KafkaConfigState(zkConfig);
    this.frameworkState = new FrameworkState(zkConfig);
    this.kafkaState = new KafkaState(zkConfig);
    this.validator = new ConfigStateValidator(frameworkState);
  }

  /**
   * Validates, stores, and returns the current target config based off the scheduler system environment.
   *
   * @throws ConfigStoreException if the new config fails to be written to persistent storage
   * @throws ValidationException if the new config is invalid or has invalid changes compared to the active config
   */
  public KafkaSchedulerConfiguration getTargetConfig() throws ConfigStoreException, ValidationException {
    if (!kafkaConfigState.hasTarget()) {
      log.info("Initializing config properties storage with new target.");
      setTargetConfig(newTargetConfig);
    } else {
      KafkaSchedulerConfiguration currTargetConfig = kafkaConfigState.getTargetConfig();
      log.info("Old config: " + currTargetConfig);
      log.info("New config: " + newTargetConfig);

      /* Validator needs to examine values like BROKER_COUNT which ConfigurationChangeDetector is told to ignore.
       * Therefore, always run validation, and run it against the raw list of properties.
       * See also {@link KafkaEnvConfiguratior.ignoredKeys()} */
      validator.validateConfigChange(currTargetConfig, newTargetConfig);

      if (!currTargetConfig.equals(newTargetConfig)) {
        log.info("Config change detected!");
        setTargetConfig(newTargetConfig);
        kafkaConfigState.syncConfigs(frameworkState);
        kafkaConfigState.cleanConfigs(frameworkState);
      } else {
        log.info("No config property changes detected, leaving brokers as-is.");
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
   * Returns the underlying Framework state storage to be used elsewhere.
   */
  public FrameworkState getFrameworkState() {
    return frameworkState;
  }

  /**
   * Returns the underlying Kafka state storage to be used elsewhere.
   */
  public KafkaState getKafkaState() {
    return kafkaState;
  }

  private void setTargetConfig(KafkaSchedulerConfiguration newTargetConfig) throws ConfigStoreException {
    UUID targetConfigName = kafkaConfigState.store(newTargetConfig);
    kafkaConfigState.setTargetName(targetConfigName);
    log.info("Set new target config: " + targetConfigName);
  }
}
