package org.apache.mesos.kafka.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.state.ConfigState;
import org.apache.mesos.kafka.offer.OfferUtils;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.kafka.state.KafkaStateUtils;
import org.apache.mesos.protobuf.LabelBuilder;
import org.apache.mesos.state.StateStoreException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores and manages multiple Kafka framework configurations in persistent storage.
 * Each configuration is in the form of a {@link KafkaConfigService}.
 */
public class KafkaConfigState {
  private final Log log = LogFactory.getLog(KafkaConfigState.class);

  private final CuratorFramework zkClient;
  private final String configTargetPath;
  private final String CONFIG_KEY = "config_target";
  private String zkConfigRootPath;
  private String zkVersionPath;
  private ConfigState configState;

  /**
   * Creates a new Kafka config state manager based on the provided bootstrap information.
   */
  public KafkaConfigState(String frameworkName, String zkHost, String zkPathPrefix) {
    configTargetPath = zkPathPrefix + frameworkName + "/config_target";
    zkClient = KafkaStateUtils.createZkClient(zkHost);
    //TODO(nick): Just pass full ZK path (ie KafkaConfigService.getZkRoot()) to ConfigState?
    configState = new ConfigState(frameworkName, zkPathPrefix, zkClient);
    zkConfigRootPath = zkPathPrefix + frameworkName + "/configurations/";
    zkVersionPath = zkConfigRootPath + "versions";
  }

  public KafkaSchedulerConfiguration fetch(String version) throws StateStoreException {
    return fetchForPath(getVersionPath(version));
  }

  private String getVersionPath(String version) {
    return zkVersionPath + "/" + version;
  }

  private void logDataSize(byte[] data) {
    log.info(String.format("Size of config to serialize: %s b.", data.length));
  }

  private KafkaSchedulerConfiguration fetchForPath(String path) {
    try {
      byte[] data = zkClient.getData().forPath(path);
      if (data == null) {
        return null;
      } else {
        logDataSize(data);
        return JSONSerializer.fromBytes(data, KafkaSchedulerConfiguration.class);
      }
    } catch (Exception e) {
      String msg = "Failure to fetch configurations.";
      log.error(msg);
      throw new StateStoreException(msg, e);
    }
  }


  /**
   * Returns whether a current target configuration exists.
   */
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

  /**
   * Returns the content of the current target configuration.
   */
  public KafkaSchedulerConfiguration getTargetConfig() {
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
  public void store(KafkaSchedulerConfiguration configuration, String version) throws StateStoreException {
    ensurePath(getVersionPath(version));
    try {
      final byte[] data = JSONSerializer.toBytes(configuration);
      logDataSize(data);
      zkClient.setData().forPath(getVersionPath(version), data);
    } catch (Exception e) {
      String msg = "Failure to store configurations.";
      log.error(msg);
      throw new StateStoreException(msg, e);
    }
  }

  private void ensurePath(String path) {
    try {
      zkClient.create().creatingParentsIfNeeded().forPath(path);
    } catch (Exception e) {
      log.debug(String.format("Path for %s already exists!", path));
    }
  }

  /**
   * Sets the name of the target configuration to be used in the future.
   * The name must match a 'version' previously passed to {@link #store(KafkaSchedulerConfiguration, String)}.
   */
  public void setTargetName(String targetConfigName) {
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

  public void syncConfigs(KafkaStateService state) {
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

  public void cleanConfigs(KafkaStateService state) {
    Set<String> activeConfigs = new HashSet<String>();
    activeConfigs.add(getTargetName());
    activeConfigs.addAll(getTaskConfigs(state));

    log.info("Cleaning all configs which are NOT in the active list: " + activeConfigs); 

    for (String configName : getConfigNames()) {
      if (!activeConfigs.contains(configName)) {
        log.info("Removing config: " + configName);
        configState.clear(configName);
      }
    }
  }

  private Set<String> getTaskConfigs(KafkaStateService state) {
    Set<String> activeConfigs = new HashSet<String>();

    try {
      for (TaskInfo taskInfo : state.getTaskInfos()) {
        Labels labels = taskInfo.getLabels();
        for (Label label : labels.getLabelsList()) {
          if (label.getKey().equals(CONFIG_KEY)) {
            activeConfigs.add(label.getValue());
          }
        }
      }
    } catch (Exception ex) {
      log.error("Failed to fetch configurations from TaskInfos with exception: " + ex);
    }

    return activeConfigs;
  }

  private void replaceDuplicateConfig(KafkaStateService state, TaskInfo taskInfo, List<String> duplicateConfigs, String targetName) {
    try {
      String taskConfig = OfferUtils.getConfigName(taskInfo);

      for (String duplicateConfig : duplicateConfigs) {
        if (taskConfig.equals(duplicateConfig)) {
          Labels labels = new LabelBuilder()
            .addLabel(CONFIG_KEY, targetName)
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
    KafkaSchedulerConfiguration targetConfig = getTargetConfig();

    List<String> duplicateConfigs = new ArrayList<String>();
    for (String configName : configState.getVersions()) {
      KafkaSchedulerConfiguration currConfig = fetch(configName);

      if (currConfig.equals(targetConfig)) {
        log.info("Duplicate config detected: " + configName);
        duplicateConfigs.add(configName);
      }
    }

    return duplicateConfigs;
  }
}
