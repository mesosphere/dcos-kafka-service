package com.mesosphere.dcos.kafka.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.mesosphere.dcos.kafka.state.FrameworkState;

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
  private final FrameworkState state;

  static {
    INT_VALUES_THAT_CANNOT_DECREASE.add("BROKER_COUNT");
  }

  public ConfigStateValidator(FrameworkState state) {
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

    errors.addAll(validateServiceConfigChange(oldConfig.getServiceConfiguration(), newConfig.getServiceConfiguration()));
    errors.addAll(validateBrokerConfigChange(oldConfig.getBrokerConfiguration(), newConfig.getBrokerConfiguration()));
    errors.addAll(validateKafkaConfigChange(oldConfig.getKafkaConfiguration(), newConfig.getKafkaConfiguration()));

    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }

  Collection<ValidationError> validateKafkaConfigChange(KafkaConfiguration oldKafkaConfiguration, KafkaConfiguration newKafkaConfiguration) {
    List<ValidationError> errors = new ArrayList<>();
    String oldKafkaZkUri = oldKafkaConfiguration.getKafkaZkUri();
    String newKafkaZkUri = newKafkaConfiguration.getKafkaZkUri();
    if (!oldKafkaZkUri.equals(newKafkaZkUri)) {
      errors.add(new ValidationError("KAFKA_ZOOKEEPER_URI",
              "Changing this value (from " + oldKafkaZkUri + " to " + newKafkaZkUri + ") is not supported."));
    }

    return errors;
  }

  List<ValidationError> validateServiceConfigChange(ServiceConfiguration oldConfig, ServiceConfiguration newConfig) throws ValidationException {

    List<ValidationError> errors = new ArrayList<>();

    errors.addAll(validateBrokerCount(newConfig));
    errors.addAll(validateFrameworkName(oldConfig, newConfig));

    return errors;
  }

  private Collection<ValidationError> validateFrameworkName(ServiceConfiguration oldConfig, ServiceConfiguration newConfig) {
    List<ValidationError> errors = new ArrayList<>();

    String oldFrameworkName = oldConfig.getName();
    String newFrameworkName = newConfig.getName();

    if (!oldFrameworkName.equals(newFrameworkName)) {
      errors.add(new ValidationError("FRAMEWORK_NAME",
              "Changing this value (from " + oldFrameworkName + " to " + oldFrameworkName + ") is not supported."));
    }

    return errors;
  }

  private List<ValidationError> validateBrokerCount(ServiceConfiguration newConfig) {
    List<ValidationError> errors = new ArrayList<>();
    int currBrokerCount = Integer.MAX_VALUE;

    try {
      currBrokerCount = state.getTaskInfos().size();
    } catch (Exception ex) {
      log.error("Failed to retrieve Broker count with exception: " + ex);
    }

    final int newBrokerCount = newConfig.getCount();

    if (newBrokerCount < currBrokerCount) {
      errors.add(new ValidationError("BROKER_COUNT",
              "Decreasing this value (from " + currBrokerCount + " to " + newBrokerCount + ") is not supported."));
    }

    return errors;
  }

  private List<ValidationError> validateBrokerConfigChange(
      BrokerConfiguration oldConfig,
      BrokerConfiguration newConfig)
          throws ValidationException {

    List<ValidationError> errors = new ArrayList<>();
    errors.addAll(validateBrokerDisk(oldConfig, newConfig));
    errors.addAll(validateBrokerHeap(newConfig));

    return errors;
  }

  private List<ValidationError> validateBrokerDisk(BrokerConfiguration oldConfig, BrokerConfiguration newConfig) {
    List<ValidationError> errors = new ArrayList<>();

    double oldDisk = oldConfig.getDisk();
    double newDisk = newConfig.getDisk();

    if (oldDisk != newDisk) {
      errors.add(new ValidationError("disk",
              "Changing this value (from " + oldDisk + " to " + newDisk + ") is not supported."));
    }

    final String oldDiskType = oldConfig.getDiskType();
    final String newDiskType = newConfig.getDiskType();

    if (!StringUtils.equals(oldDiskType, newDiskType)) {
      errors.add(new ValidationError("diskType",
              "Changing this value (from " + oldDiskType + " to " + newDiskType + ") is not supported."));
    }

    return errors;
  }

  private List<ValidationError> validateBrokerHeap(BrokerConfiguration newConfig) {
    List<ValidationError> errors = new ArrayList<>();

    // Validate whether BROKER_HEAP_MB <= BROKER_MEM
    final double mem = newConfig.getMem();
    final int brokerHeapSize = newConfig.getHeap().getSizeMb();

    if (mem < brokerHeapSize) {
      errors.add(new ValidationError("mem",
              "BROKER_MEM should be greater than or equal to BROKER_HEAP_MB"));
    }

    return errors;
  }

}
