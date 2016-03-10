package org.apache.mesos.kafka.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.config.ChangedProperty;
import org.apache.mesos.config.ConfigurationChangeDetector;
import org.apache.mesos.config.ConfigurationChangeNamespaces;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.state.StateStoreException;

public class ConfigStateUpdater {

  /**
   * A single validation error encountered when processing a modified scheduler config.
   */
  public static class ValidationError {
    private ValidationError(String fieldName, String msg) {
      this.fieldName = fieldName;
      this.msg = msg;
    }

    /**
     * Returns a user-visible explanation of the validation error.
     */
    public String toString() {
      return String.format("Validation error on field \"%s\": %s", fieldName, msg);
    }

    private final String fieldName;
    private final String msg;
  }

  /**
   * A set of one or more validation errors.
   */
  public static class ValidationException extends Exception {
    private final Collection<ValidationError> validationErrors;

    public ValidationException(Collection<ValidationError> validationErrors) {
      super(String.format("%d validation errors: %s",
          validationErrors.size(), validationErrors.toString()));
      this.validationErrors = validationErrors;
    }

    /**
     * Returns the list of validation errors encountered when processing the config.
     */
    public Collection<ValidationError> getValidationErrors() {
      return validationErrors;
    }
  }

  private static final Log log = LogFactory.getLog(KafkaScheduler.class);

  private static final Set<String> INT_VALUES_THAT_CANNOT_DECREASE = new HashSet<>();
  static {
    INT_VALUES_THAT_CANNOT_DECREASE.add("BROKER_COUNT");
  }

  private final KafkaConfigState kafkaConfigState;
  private final KafkaConfigService newTargetConfig;
  private final KafkaStateService kafkaStateService;

  public ConfigStateUpdater() {
    this.newTargetConfig = KafkaConfigService.getEnvConfig();
    // Bootstrap with some values from the new config:
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
      setTargetConfig(newTargetConfig);
    } else {
      KafkaConfigService currTarget = kafkaConfigState.getTargetConfig();

      ConfigurationChangeDetector changeDetector = new ConfigurationChangeDetector(
          currTarget.getNsPropertyMap(),
          newTargetConfig.getNsPropertyMap(),
          new ConfigurationChangeNamespaces("*", "*"));

      if (changeDetector.isChangeDetected()) {
        log.info("Detected changed properties.");
        logAndValidateConfigChange(changeDetector);
        setTargetConfig(newTargetConfig);
        kafkaConfigState.syncConfigs(kafkaStateService);
      } else {
        log.info("No change detected.");
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

  private static void logAndValidateConfigChange(ConfigurationChangeDetector changeDetector) throws ValidationException {
    log.info("Extra config properties detected: " + Arrays.toString(changeDetector.getExtraConfigs().toArray()));
    log.info("Missing config properties detected: " + Arrays.toString(changeDetector.getMissingConfigs().toArray()));
    log.info("Changed config properties detected: " + Arrays.toString(changeDetector.getChangedProperties().toArray()));

    List<ValidationError> errors = new ArrayList<>();
    for (ChangedProperty change : changeDetector.getChangedProperties()) {
      if (INT_VALUES_THAT_CANNOT_DECREASE.contains(change.getName())
          && Integer.parseInt(change.getNewValue()) < Integer.parseInt(change.getOldValue())) {
        errors.add(new ValidationError(change.getName(), "Decreasing this value is not supported."));
      }
    }

    // ... any other in-framework change validation goes here ...

    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }
}
