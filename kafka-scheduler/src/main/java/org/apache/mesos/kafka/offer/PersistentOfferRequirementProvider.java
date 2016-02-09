package org.apache.mesos.kafka.offer;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.OfferRequirement.VolumeMode;
import org.apache.mesos.offer.PlacementStrategy;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.LabelBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Volume;

public class PersistentOfferRequirementProvider implements KafkaOfferRequirementProvider {
  private final Log log = LogFactory.getLog(PersistentOfferRequirementProvider.class);
  private KafkaConfigState configState = null;

  public PersistentOfferRequirementProvider(KafkaConfigState configState) {
    this.configState = configState;
  }

  public OfferRequirement getNewOfferRequirement(String configName, int brokerId) {
    return getNewOfferRequirementInternal(configName, brokerId);
  }

  public OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo) {
    if (hasVolume(taskInfo)) {
      return getExistingOfferRequirement(taskInfo);
    } else {
      KafkaConfigService config = getConfigService(taskInfo);
      PlacementStrategy placementStrategy = PlacementStrategyManager.getPlacementStrategy(config);

      return new OfferRequirement(
          Arrays.asList(taskInfo),
          placementStrategy.getAgentsToAvoid(taskInfo),
          placementStrategy.getAgentsToColocate(taskInfo));
    }
  }

  private OfferRequirement getNewOfferRequirementInternal(String configName, int brokerId) {
    log.info("Getting new OfferRequirement for: " + configName);

    KafkaConfigService config = configState.fetch(configName);

    String brokerName = "broker-" + brokerId;
    String taskId = brokerName + "__" + UUID.randomUUID();
    String persistenceId = UUID.randomUUID().toString();
    TaskInfo taskInfo = getTaskInfo(configName, config, persistenceId, brokerId, brokerName, taskId);
    return getCreateOfferRequirement(taskInfo);
  }

  public OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo taskInfo) {
    log.info("Getting update OfferRequirement for: " + configName);

    KafkaConfigService config = configState.fetch(configName); 

    String brokerName = taskInfo.getName();
    Integer brokerId = OfferUtils.nameToId(brokerName);
    String taskId = taskInfo.getTaskId().getValue();
    String persistenceId = OfferUtils.getPersistenceId(taskInfo);
    TaskInfo newTaskInfo = getTaskInfo(configName, config, persistenceId, brokerId, brokerName, taskId);

    log.info("newTaskInfo: " + newTaskInfo);

    if (hasVolume(taskInfo)) {
      return getExistingOfferRequirement(newTaskInfo);
    } else {
      return getCreateOfferRequirement(newTaskInfo);
    }
  }

  private OfferRequirement getUpgradeOfferRequirement(TaskInfo taskInfo) {
    log.info("Getting upgrade to pv OfferRequirement");

    Integer brokerId = OfferUtils.nameToId(taskInfo.getName());

    String configName = OfferUtils.getConfigName(taskInfo);
    KafkaConfigService config = getConfigService(taskInfo);

    String brokerName = taskInfo.getName();
    String taskId = taskInfo.getTaskId().getValue();
    String persistenceId = UUID.randomUUID().toString();
    TaskInfo createTaskInfo = getTaskInfo(configName, config, persistenceId, brokerId, brokerName, taskId);

    return getCreateOfferRequirement(createTaskInfo);
  }

  private OfferRequirement getCreateOfferRequirement(TaskInfo taskInfo) {
    log.info("Getting create OfferRequirement");

    KafkaConfigService config = getConfigService(taskInfo);
    PlacementStrategy placementStrategy = PlacementStrategyManager.getPlacementStrategy(config);

    List<SlaveID> avoidAgents = placementStrategy.getAgentsToAvoid(taskInfo);
    List<SlaveID> colocateAgents = placementStrategy.getAgentsToColocate(taskInfo);

    log.info("Avoiding agents       : " + avoidAgents);
    log.info("Colocating with agents: " + colocateAgents);

    return new OfferRequirement(
        getRole(config),
        getPrincipal(config),
        Arrays.asList(taskInfo),
        avoidAgents,
        colocateAgents,
        VolumeMode.CREATE);
  }

  private OfferRequirement getExistingOfferRequirement(TaskInfo taskInfo) {
    log.info("Getting existing OfferRequirement");

    KafkaConfigService config = getConfigService(taskInfo);

    return new OfferRequirement(
        getRole(config),
        getPrincipal(config),
        Arrays.asList(taskInfo),
        null,
        null,
        VolumeMode.EXISTING);
  }

  private KafkaConfigService getConfigService(TaskInfo taskInfo) {
    String configName = OfferUtils.getConfigName(taskInfo);
    return configState.fetch(configName);
  }

  private boolean hasVolume(TaskInfo taskInfo) {
    String containerPath = ResourceUtils.getVolumeContainerPath(taskInfo.getResourcesList());
    return containerPath != null;
  }

  private String getRole(ConfigurationService config) {
    return config.get("ROLE");
  }

  private String getPrincipal(ConfigurationService config) {
    return config.get("PRINCIPAL");
  }

  private TaskInfo getTaskInfo(String configName, ConfigurationService config, String persistenceId, int brokerId, String brokerName, String taskId) {
    int port = 9092 + brokerId;

    List<String> commands = new ArrayList<>();

    // Do not use the /bin/bash-specific "source"
    commands.add(". $MESOS_SANDBOX/container-hook/container-hook.sh");

    // Export the JRE and log the environment 
    commands.add("export PATH=$PATH:$MESOS_SANDBOX/jre/bin");
    commands.add("env");

    String containerPath = "kafka-volume-" + UUID.randomUUID();

    // Run Kafka
    String kafkaStartCmd = OfferRequirementUtils.getKafkaStartCmd(config, brokerId, port, containerPath); 
    commands.add(kafkaStartCmd);

    String command = Joiner.on(" && ").join(commands);

    double cpus = Double.parseDouble(config.get("BROKER_CPUS"));
    double mem = Double.parseDouble(config.get("BROKER_MEM"));
    double disk = Double.parseDouble(config.get("BROKER_DISK"));
    String role = getRole(config);
    String principal = getPrincipal(config);

    Labels labels = new LabelBuilder()
      .addLabel("config_target", configName)
      .build();

    return new TaskInfoBuilder(taskId, brokerName, "" /* slaveId */)
        .addResource(ResourceBuilder.reservedCpus(cpus, role, principal))
        .addResource(ResourceBuilder.reservedMem(mem, role, principal))
        .addResource(getVolumeResource(disk, role, principal, containerPath, persistenceId))
        .addResource(ResourceBuilder.ports(port, port))
        .setCommand(new CommandInfoBuilder()
          .addUri(config.get("KAFKA_URI"))
          .addUri(config.get("CONTAINER_HOOK_URI"))
          .addUri(config.get("JAVA_URI"))
          .setCommand(command)
          .build())
        .setLabels(labels)
        .build();
  }

  private Resource getVolumeResource(double disk, String role, String principal, String containerPath, String persistenceId) {
    DiskInfo diskInfo = createDiskInfo(persistenceId, containerPath);

    Resource resource = ResourceBuilder.reservedDisk(disk, role, principal);
    Resource.Builder builder = Resource.newBuilder(resource);
    builder.setDisk(diskInfo);

    return builder.build();
  }

  private static DiskInfo createDiskInfo(String persistenceId, String containerPath) {
    return DiskInfo.newBuilder()
      .setPersistence(Persistence.newBuilder()
          .setId(persistenceId))
      .setVolume(Volume.newBuilder()
          .setMode(Volume.Mode.RW)
          .setContainerPath(containerPath))
      .build();
  }
}
