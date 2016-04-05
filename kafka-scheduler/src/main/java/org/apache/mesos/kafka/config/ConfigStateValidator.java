package org.apache.mesos.kafka.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.config.ConfigProperty;
import org.apache.mesos.config.ConfigurationChangeNamespaces;

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

  private final ConfigurationChangeNamespaces namespaces;

  public ConfigStateValidator(ConfigurationChangeNamespaces namespaces) {
    this.namespaces = namespaces;
  }

  /**
   * Checks that the provided configuration change is valid.
   * Requires the raw underlying configs, because some values to be checked may be filtered out by
   * {@link ConfigurationChangeDetector}.
   *
   * @throws ValidationException if the configuration change isn't allowed
   */
  public void validateConfigChange(
      Map<String, Map<String, ConfigProperty>> oldConfig,
      Map<String, Map<String, ConfigProperty>> newConfig)
          throws ValidationException {
    List<ValidationError> errors = new ArrayList<>();
    final Map<String, ConfigProperty> oldRoot = oldConfig.get(namespaces.getRoot()),
        newRoot = newConfig.get(namespaces.getRoot());
    for (String checkKey : INT_VALUES_THAT_CANNOT_DECREASE) {
      final ConfigProperty oldProperty = oldRoot.get(checkKey),
          newProperty = newRoot.get(checkKey);
      log.info("Compare config value " + checkKey + ": old=[" + oldProperty + "] new=[" + newProperty + "]");
      if (oldProperty == null || newProperty == null) {
        log.info("One version of the config is missing " + checkKey + ", skipping check");
        continue;
      }
      if (Integer.parseInt(newProperty.getValue()) >= Integer.parseInt(oldProperty.getValue())) {
        log.info("Value is same or increasing, OK!");
      } else {
        log.error("Refusing to decrease value from " + oldProperty.getValue() + " to " + newProperty.getValue() + ": " + checkKey);
        errors.add(new ValidationError(checkKey,
            "Decreasing this value (from " + oldProperty.getValue() + " to " + newProperty.getValue() + ") is not supported."));
      }
    }

    // ... any other in-framework change validation goes here ...


    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }
}
