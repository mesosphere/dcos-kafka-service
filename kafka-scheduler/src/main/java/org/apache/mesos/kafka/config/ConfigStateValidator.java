package org.apache.mesos.kafka.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.config.ChangedProperty;
import org.apache.mesos.config.ConfigurationChangeDetector;

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

  /**
   * Checks that the provided configuration change is valid.
   *
   * @throws ValidationException if the configuration change isn't allowed
   */
  public void validateConfigChange(ConfigurationChangeDetector changeDetector) throws ValidationException {

    List<ValidationError> errors = new ArrayList<>();
    for (ChangedProperty change : changeDetector.getChangedProperties()) {
      log.info("Validate change: " + change.toString());
      if (INT_VALUES_THAT_CANNOT_DECREASE.contains(change.getName())
          && Integer.parseInt(change.getNewValue()) < Integer.parseInt(change.getOldValue())) {
        errors.add(new ValidationError(change.getName(), "Decreasing this value is not supported."));
      }

      // ... any other in-framework change validation goes here ...
    }

    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }
}
