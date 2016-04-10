package org.apache.mesos.kafka.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.state.KafkaStateService;

import java.util.*;

/**
 * Provides validation of configrations.
 */
public class ConfigStateValidator {

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

  private static final Log log = LogFactory.getLog(ConfigStateValidator.class);
  private static final Set<String> INT_VALUES_THAT_CANNOT_DECREASE = new HashSet<>();
  private final KafkaStateService state;

  static {
    INT_VALUES_THAT_CANNOT_DECREASE.add("BROKER_COUNT");
  }

  public ConfigStateValidator(KafkaStateService state) {
      this.state = state;
  }

  /**
   * Checks that the provided configuration change is valid.
   *
   * @throws ValidationException if the configuration change isn't allowed
   */
  public void validateConfigChange(
      KafkaSchedulerConfiguration oldConfig,
      KafkaSchedulerConfiguration newConfig)
          throws ValidationException {
    List<ValidationError> errors = new ArrayList<>();

    int currBrokerCount = Integer.MAX_VALUE;
    try {
      currBrokerCount = state.getTaskInfos().size();
    } catch (Exception ex) {
      log.error("Failed to retrieve Broker count with exception: " + ex);
    }

    final int newBrokerCount = newConfig.getServiceConfiguration().getCount();

    if (newBrokerCount < currBrokerCount) {
      errors.add(new ValidationError("BROKER_COUNT",
              "Decreasing this value (from " + currBrokerCount + " to " + newBrokerCount + ") is not supported."));
    }

    // ... any other in-framework change validation goes here ...


    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }
}