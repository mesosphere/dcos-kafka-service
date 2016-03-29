package org.apache.mesos.kafka.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

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
  static {
    INT_VALUES_THAT_CANNOT_DECREASE.add("BROKER_COUNT");
  }

  public ConfigStateValidator() {
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

    final int oldBrokerCount = oldConfig.getBrokerConfiguration().getCount();
    final int newBrokerCount = newConfig.getBrokerConfiguration().getCount();

    if (newBrokerCount < oldBrokerCount) {
      errors.add(new ValidationError("BROKER_COUNT",
              "Decreasing this value (from " + oldBrokerCount + " to " + newBrokerCount + ") is not supported."));
    }

    // ... any other in-framework change validation goes here ...


    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }
}
