package com.mesosphere.dcos.kafka.config;

import com.mesosphere.dcos.kafka.offer.OfferUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.RetryPolicy;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigStoreException;
import com.mesosphere.dcos.kafka.offer.PersistentOfferRequirementProvider;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.curator.CuratorConfigStore;

import java.util.*;

/**
 * Stores and manages multiple Kafka framework configurations in persistent storage.
 * Each configuration is in the form of a {@link KafkaSchedulerConfiguration}.
 */
public class KafkaConfigState {
  private static final Log log = LogFactory.getLog(KafkaConfigState.class);

  private final ConfigStore<KafkaSchedulerConfiguration> configStore;

  /**
   * Creates a new Kafka config state manager based on the provided bootstrap information.
   *
   * @see CuratorConfigStore
   */
  public KafkaConfigState(ZookeeperConfiguration zkConfig) {
    this.configStore = new CuratorConfigStore<>(
            zkConfig.getFrameworkName(), zkConfig.getMesosZkUri());
  }

  /**
   * Creates a new Kafka config state manager based on the provided bootstrap information, with a
   * custom {@link RetryPolicy}.
   *
   * @see CuratorConfigStore
   */
  public KafkaConfigState(String zkRoot, String zkHost, RetryPolicy retryPolicy) {
    this.configStore = new CuratorConfigStore<>(
            zkRoot, zkHost, retryPolicy);
  }

  /**
   * Returns the underlying {@link ConfigStore} instance.
   */
  public ConfigStore<KafkaSchedulerConfiguration> getConfigStore() {
      return configStore;
  }

  public KafkaSchedulerConfiguration fetch(UUID version) throws ConfigStoreException {
    try {
      return configStore.fetch(version, KafkaSchedulerConfiguration.getFactoryInstance());
    } catch (ConfigStoreException e) {
      log.error("Unable to fetch version: " + version, e);
      throw new ConfigStoreException(e);
    }
  }

  /**
   * Returns whether a current target configuration exists.
   */
  public boolean hasTarget() {
    try {
      configStore.getTargetConfig();
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  /**
   * Returns the name of the current target configuration.
   */
  public UUID getTargetName() throws ConfigStoreException {
    try {
      return configStore.getTargetConfig();
    } catch (Exception ex) {
      log.error("Failed to retrieve config target name", ex);
      throw ex;
    }
  }

  /**
   * Returns the content of the current target configuration.
   *
   * @throws ConfigStoreException if the underlying storage failed to read
   */
  public KafkaSchedulerConfiguration getTargetConfig() throws ConfigStoreException {
    return fetch(getTargetName());
  }

  /**
   * Returns a list of all available configuration names.
   *
   * @throws ConfigStoreException if the underlying storage failed to read
   */
  public Collection<UUID> getConfigNames() throws ConfigStoreException {
    return configStore.list();
  }

  /**
   * Stores the provided configuration against the provided version label.
   *
   * @throws ConfigStoreException if the underlying storage failed to write
   */
  public UUID store(KafkaSchedulerConfiguration configuration) throws ConfigStoreException {
    try {
      return configStore.store(configuration);
    } catch (Exception e) {
      String msg = "Failure to store configurations.";
      log.error(msg, e);
      throw new ConfigStoreException(msg, e);
    }
  }

  /**
   * Sets the name of the target configuration to be used in the future.
   */
  public void setTargetName(UUID targetConfigName) throws ConfigStoreException {
    try {
      configStore.setTargetConfig(targetConfigName);
    } catch (Exception ex) {
      String msg = "Failed to set target config with exception";
      log.error(msg, ex);
      throw new ConfigStoreException(msg, ex);
    }
  }

  public void syncConfigs(FrameworkState state) throws ConfigStoreException {
    try {
      UUID targetName = getTargetName();
      List<String> duplicateConfigs = getDuplicateConfigs();

      List<TaskInfo> taskInfos = state.getTaskInfos();
      for (TaskInfo taskInfo : taskInfos) {
        replaceDuplicateConfig(state, taskInfo, duplicateConfigs, targetName);
      }
    } catch (Exception ex) {
      log.error("Failed to synchronized configurations", ex);
      throw new ConfigStoreException(ex);
    }
  }

  public void cleanConfigs(FrameworkState state) throws ConfigStoreException {
    Set<UUID> activeConfigs = new HashSet<>();
    activeConfigs.add(getTargetName());
    activeConfigs.addAll(getTaskConfigs(state));

    log.info("Cleaning all configs which are NOT in the active list: " + activeConfigs);

    for (UUID configName : getConfigNames()) {
      if (!activeConfigs.contains(configName)) {
        try {
          log.info("Removing config: " + configName);
          configStore.clear(configName);
        } catch (ConfigStoreException e) {
          log.error("Unable to clear config: " + configName, e);
        }
      }
    }
  }

  private Set<UUID> getTaskConfigs(FrameworkState state) {
    Set<UUID> activeConfigs = new HashSet<>();

    try {
      for (TaskInfo taskInfo : state.getTaskInfos()) {
        Labels labels = taskInfo.getLabels();
        for (Label label : labels.getLabelsList()) {
          if (label.getKey().equals(PersistentOfferRequirementProvider.CONFIG_TARGET_KEY)) {
            activeConfigs.add(UUID.fromString(label.getValue()));
          }
        }
      }
    } catch (Exception ex) {
      log.error("Failed to fetch configurations from TaskInfos", ex);
    }

    return activeConfigs;
  }

  private void replaceDuplicateConfig(FrameworkState state, TaskInfo taskInfo, List<String> duplicateConfigs, UUID targetName)
          throws ConfigStoreException {
    try {
      String taskConfig = OfferUtils.getConfigName(taskInfo);

      for (String duplicateConfig : duplicateConfigs) {
        if (taskConfig.equals(duplicateConfig)) {
          Labels labels = Labels.newBuilder()
                  .addLabels(Label.newBuilder()
                          .setKey(PersistentOfferRequirementProvider.CONFIG_TARGET_KEY)
                          .setValue(targetName.toString()))
                  .build();

          TaskInfo newTaskInfo = TaskInfo.newBuilder(taskInfo).setLabels(labels).build();
          state.recordTaskInfo(newTaskInfo);
          return;
        }
      }
    } catch (Exception ex) {
      log.error("Failed to replace duplicate configuration for taskInfo: " + taskInfo, ex);
      throw new ConfigStoreException(ex);
    }
  }

  /**
   * Returns the list of configs which are duplicates of the current Target config.
   */
  private List<String> getDuplicateConfigs() throws ConfigStoreException {
    KafkaSchedulerConfiguration newTargetConfig = getTargetConfig();

    List<String> duplicateConfigs = new ArrayList<String>();
    final Collection<UUID> configNames = getConfigNames();
    for (UUID configName : configNames) {
      KafkaSchedulerConfiguration currTargetConfig = fetch(configName);

      final BrokerConfiguration currBrokerConfig = currTargetConfig.getBrokerConfiguration();
      final BrokerConfiguration newBrokerConfig = newTargetConfig.getBrokerConfiguration();

      final KafkaConfiguration currKafkaConfig = currTargetConfig.getKafkaConfiguration();
      final KafkaConfiguration newKafkaConfig = newTargetConfig.getKafkaConfiguration();

      if (currBrokerConfig.equals(newBrokerConfig) &&
          currKafkaConfig.equals(newKafkaConfig)) {
        log.info("Duplicate config detected: " + configName);
        duplicateConfigs.add(configName.toString());
      }
    }

    return duplicateConfigs;
  }
}
