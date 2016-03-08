package org.apache.mesos.kafka.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.curator.framework.CuratorFramework;

import org.apache.mesos.config.ConfigurationChangeDetector;
import org.apache.mesos.config.ConfigurationChangeNamespaces;
import org.apache.mesos.config.state.ConfigState;
import org.apache.mesos.kafka.offer.OfferUtils;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.kafka.state.KafkaStateUtils;
import org.apache.mesos.protobuf.LabelBuilder;
import org.apache.mesos.state.StateStoreException;

import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.TaskInfo;

/**
 * Stores and manages multiple Kafka framework configurations in persistent storage.
 * Each configuration is in the form of a {@link KafkaConfigService}.
 */
public class KafkaConfigState {
  private final Log log = LogFactory.getLog(KafkaConfigState.class);

  private final CuratorFramework zkClient;
  private final ConfigState configState;
  private final String configTargetPath;

  /**
   * Creates a new Kafka config state manager based on the provided bootstrap information.
   */
  public KafkaConfigState(String frameworkName, String zkHost, String zkPathPrefix) {
    configTargetPath = zkPathPrefix + frameworkName + "/config_target";
    zkClient = KafkaStateUtils.createZkClient(zkHost);
    //TODO(nick): Just pass full ZK path (ie KafkaConfigService.getZkRoot()) to ConfigState?
    configState = new ConfigState(frameworkName, zkPathPrefix, zkClient);
  }

  public KafkaConfigService fetch(String version) throws StateStoreException {
    return KafkaConfigService.getHydratedConfig(configState.fetch(version));
  }

  public boolean hasTarget() {
    try {
      return null != zkClient.checkExists().forPath(configTargetPath);
    } catch (Exception ex) {
      log.error("Failed to determine existence of target config with exception: " + ex);
      return false;
    }
  }

  /**
   * Returns the name of the current target configuration.
   */
  public String getTargetName() {
    try {
      byte[] bytes = zkClient.getData().forPath(configTargetPath);
      return new String(bytes, "UTF-8");
    } catch (Exception ex) {
      log.error("Failed to retrieve config target name with exception: " + ex);
      return null;
    }
  }

  public KafkaConfigService getTargetConfig() {
    return fetch(getTargetName());
  }

  /**
   * Returns a list of all available configuration names.
   */
  public List<String> getConfigNames() {
    return configState.getVersions();
  }

  /**
   * Stores the provided configuration against the provided version label.
   * The configuration may then be marked as the target configuration with {@link #setTargetName(String)}.
   *
   * @throws StateStoreException if the underlying storage failed to write
   */
  void store(KafkaConfigService configurationService, String version) throws StateStoreException {
    configState.store(configurationService.getConfigService(), version);
  }

  /**
   * Sets the name of the target configuration to be used in the future.
   * The name must match a 'version' previously passed to {@link #store(KafkaConfigService, String)}.
   */
  void setTargetName(String targetConfigName) {
    try {
      byte[] bytes = targetConfigName.getBytes("UTF-8");

      if (!hasTarget()) {
        zkClient.create().creatingParentsIfNeeded().forPath(configTargetPath, bytes);
      } else {
        zkClient.setData().forPath(configTargetPath, bytes);
      }
    } catch (Exception ex) {
      log.error("Failed to set target config with exception: " + ex);
    }
  }

  void syncConfigs(KafkaStateService state) {
    try {
      String targetName = getTargetName();
      List<String> duplicateConfigs = getDuplicateConfigs();

      List<TaskInfo> taskInfos = state.getTaskInfos();
      for (TaskInfo taskInfo : taskInfos) {
        replaceDuplicateConfig(state, taskInfo, duplicateConfigs, targetName);
      }
    } catch (Exception ex) {
      log.error("Failed to synchronized configurations with exception: " + ex);
    }
  }

  private void replaceDuplicateConfig(KafkaStateService state, TaskInfo taskInfo, List<String> duplicateConfigs, String targetName) {
    try {
      String taskConfig = OfferUtils.getConfigName(taskInfo);

      for (String duplicateConfig : duplicateConfigs) {
        if (taskConfig.equals(duplicateConfig)) {
          Labels labels = new LabelBuilder()
            .addLabel("config_target", targetName)
            .build();

          TaskInfo newTaskInfo = TaskInfo.newBuilder(taskInfo).setLabels(labels).build();
          state.recordTaskInfo(newTaskInfo);
          return;
        }
      }
    } catch (Exception ex) {
      log.error("Failed to replace duplicate configuration for taskInfo: " + taskInfo + " with exception: " + ex);
    }
  }

  /**
   * Returns the list of configs which are duplicates of the current Target config.
   */
  private List<String> getDuplicateConfigs() {
    KafkaConfigService targetConfig = getTargetConfig();

    List<String> duplicateConfigs = new ArrayList<String>();
    for (String configName : configState.getVersions()) {
      KafkaConfigService currConfig = fetch(configName);

      ConfigurationChangeDetector changeDetector = new ConfigurationChangeDetector(
          currConfig.getNsPropertyMap(),
          targetConfig.getNsPropertyMap(),
          new ConfigurationChangeNamespaces("*", "*"));

      if (!changeDetector.isChangeDetected()) {
        log.info("Duplicate config detected: " + configName);
        duplicateConfigs.add(configName);
      }
    }

    return duplicateConfigs;
  }
}
