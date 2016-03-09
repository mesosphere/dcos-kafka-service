package org.apache.mesos.kafka.offer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.OfferRequirement.VolumeMode;
import org.apache.mesos.offer.PlacementStrategy;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Volume;

public class PersistentOfferRequirementProvider implements KafkaOfferRequirementProvider {
  private final Log log = LogFactory.getLog(PersistentOfferRequirementProvider.class);

  private final KafkaConfigState configState;
  private final PlacementStrategyManager placementStrategyManager;

  public PersistentOfferRequirementProvider(
      KafkaStateService kafkaStateService, KafkaConfigState configState) {
    this.configState = configState;
    this.placementStrategyManager = new PlacementStrategyManager(kafkaStateService);
  }

  public OfferRequirement getNewOfferRequirement(String configName, int brokerId) {
    return getNewOfferRequirementInternal(configName, brokerId);
  }

  public OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo) {
    if (hasVolume(taskInfo)) {
      return getExistingOfferRequirement(taskInfo);
    } else {
      KafkaConfigService config = getConfigService(taskInfo);
      PlacementStrategy placementStrategy = placementStrategyManager.getPlacementStrategy(config);

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
    TaskInfo taskInfo = getTaskInfo(configName, config, persistenceId, brokerId, taskId);
    return getCreateOfferRequirement(taskInfo);
  }

  public OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo taskInfo) {
    log.info("Getting update OfferRequirement for: " + configName);

    KafkaConfigService config = configState.fetch(configName);

    String brokerName = taskInfo.getName();
    Integer brokerId = OfferUtils.nameToId(brokerName);
    String taskId = taskInfo.getTaskId().getValue();

    String persistenceId = OfferUtils.getPersistenceId(taskInfo);
    if (persistenceId == null) {
      persistenceId = UUID.randomUUID().toString();
    }

    TaskInfo newTaskInfo = getTaskInfo(configName, config, persistenceId, brokerId, taskId);

    log.info("newTaskInfo: " + newTaskInfo);

    if (hasVolume(taskInfo)) {
      return getExistingOfferRequirement(newTaskInfo);
    } else {
      return getCreateOfferRequirement(newTaskInfo);
    }
  }

  private OfferRequirement getCreateOfferRequirement(TaskInfo taskInfo) {
    log.info("Getting create OfferRequirement");

    KafkaConfigService config = getConfigService(taskInfo);
    PlacementStrategy placementStrategy = placementStrategyManager.getPlacementStrategy(config);

    List<SlaveID> avoidAgents = placementStrategy.getAgentsToAvoid(taskInfo);
    List<SlaveID> colocateAgents = placementStrategy.getAgentsToColocate(taskInfo);

    log.info("Avoiding agents       : " + avoidAgents);
    log.info("Colocating with agents: " + colocateAgents);

    return new OfferRequirement(
        config.getRole(),
        config.getPrincipal(),
        Arrays.asList(taskInfo),
        avoidAgents,
        colocateAgents,
        VolumeMode.CREATE);
  }

  private OfferRequirement getExistingOfferRequirement(TaskInfo taskInfo) {
    log.info("Getting existing OfferRequirement");

    KafkaConfigService config = getConfigService(taskInfo);

    return new OfferRequirement(
        config.getRole(),
        config.getPrincipal(),
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

  private TaskInfo getTaskInfo(
      String configName,
      KafkaConfigService config,
      String persistenceId,
      int brokerId,
      String taskId) {

    String containerPath = "kafka-volume-" + UUID.randomUUID();
    List<Resource> resources = getResources(config, persistenceId, containerPath);
    return OfferRequirementUtils.getTaskInfo(configName, config, resources, brokerId, taskId, containerPath);
  }

  private List<Resource> getResources(
      KafkaConfigService config,
      String persistenceId,
      String containerPath) {

    KafkaConfigService.BrokerResources brokerResources = config.getBrokerResources();
    String role = config.getRole();
    String principal = config.getPrincipal();

    List<Resource> resources = new ArrayList<>();
    resources.add(ResourceBuilder.reservedCpus(brokerResources.cpus, role, principal));
    resources.add(ResourceBuilder.reservedMem(brokerResources.mem, role, principal));
    resources.add(getVolumeResource(brokerResources.disk, role, principal, containerPath, persistenceId));

    return resources;
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
