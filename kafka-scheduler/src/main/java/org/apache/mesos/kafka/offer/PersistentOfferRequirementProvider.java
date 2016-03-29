package org.apache.mesos.kafka.offer;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.*;
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

  @Override
  public OfferRequirement getNewOfferRequirement(String configName, int brokerId) {
    return getNewOfferRequirementInternal(configName, brokerId);
  }

  @Override
  public OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo) {
    return getExistingOfferRequirement(taskInfo);
  }

  @Override
  public OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo taskInfo) {
    log.info("Getting update OfferRequirement for: " + configName);

    KafkaSchedulerConfiguration config = configState.fetch(configName);

    String brokerName = taskInfo.getName();
    Integer brokerId = OfferUtils.nameToId(brokerName);
    String taskId = getTaskId(brokerName);
    String persistenceId = OfferUtils.getPersistenceId(taskInfo);
    Long port = OfferUtils.getPort(taskInfo);

    TaskInfo newTaskInfo = getTaskInfo(configName, config, persistenceId, brokerId, taskId, port);

    log.info("Update TaskInfo: " + newTaskInfo);

    return getExistingOfferRequirement(newTaskInfo);
  }

  private OfferRequirement getNewOfferRequirementInternal(String configName, int brokerId) {
    log.info("Getting new OfferRequirement for: " + configName);

    KafkaSchedulerConfiguration config = configState.fetch(configName);

    String brokerName = "broker-" + brokerId;
    String taskId = getTaskId(brokerName);
    String persistenceId = UUID.randomUUID().toString();
    TaskInfo taskInfo = getTaskInfo(configName, config, persistenceId, brokerId, taskId);
    return getCreateOfferRequirement(taskInfo);
  }

  private String getTaskId(String taskName) {
    return taskName + "__" + UUID.randomUUID();
  }

  private OfferRequirement getCreateOfferRequirement(TaskInfo taskInfo) {
    log.info("Getting create OfferRequirement");

    KafkaSchedulerConfiguration config = getConfigService(taskInfo);
    PlacementStrategy placementStrategy = placementStrategyManager.getPlacementStrategy(config);

    List<SlaveID> avoidAgents = placementStrategy.getAgentsToAvoid(taskInfo);
    List<SlaveID> colocateAgents = placementStrategy.getAgentsToColocate(taskInfo);

    log.info("Avoiding agents       : " + avoidAgents);
    log.info("Colocating with agents: " + colocateAgents);

    final ServiceConfiguration serviceConfiguration = config.getServiceConfiguration();
    return new OfferRequirement(
        serviceConfiguration.getRole(),
        serviceConfiguration.getPrincipal(),
        Arrays.asList(taskInfo),
        avoidAgents,
        colocateAgents,
        VolumeMode.CREATE);
  }

  private OfferRequirement getExistingOfferRequirement(TaskInfo taskInfo) {
    log.info("Getting existing OfferRequirement");

    String configName = OfferUtils.getConfigName(taskInfo);
    KafkaSchedulerConfiguration config = getConfigService(taskInfo);
    String persistenceId = OfferUtils.getPersistenceId(taskInfo);
    String brokerName = taskInfo.getName();
    Integer brokerId = OfferUtils.nameToId(brokerName);
    String taskId = getTaskId(brokerName);
    Long port = OfferUtils.getPort(taskInfo);

    TaskInfo newTaskInfo = getTaskInfo(configName, config, persistenceId, brokerId, taskId, port);

    final ServiceConfiguration serviceConfiguration = config.getServiceConfiguration();
    return new OfferRequirement(
        serviceConfiguration.getRole(),
        serviceConfiguration.getPrincipal(),
        Arrays.asList(newTaskInfo),
        null,
        null,
        VolumeMode.EXISTING);
  }

  private KafkaSchedulerConfiguration getConfigService(TaskInfo taskInfo) {
    String configName = OfferUtils.getConfigName(taskInfo);
    return configState.fetch(configName);
  }

  private boolean hasVolume(TaskInfo taskInfo) {
    String containerPath = ResourceUtils.getVolumeContainerPath(taskInfo.getResourcesList());
    return containerPath != null;
  }

  private TaskInfo getTaskInfo(
      String configName,
      KafkaSchedulerConfiguration config,
      String persistenceId,
      int brokerId,
      String taskId) {

    Long port = 9092 + ThreadLocalRandom.current().nextLong(0, 1000);
    return getTaskInfo(configName, config, persistenceId, brokerId, taskId, port);
  }

  private TaskInfo getTaskInfo(
      String configName,
      KafkaSchedulerConfiguration config,
      String persistenceId,
      int brokerId,
      String taskId,
      Long port) {

    String containerPath = "kafka-volume-" + UUID.randomUUID();
    List<Resource> resources = getResources(config, port, persistenceId, containerPath);
    return OfferRequirementUtils.getTaskInfo(configName, config, resources, brokerId, taskId, containerPath, port);
  }

  private List<Resource> getResources(
      KafkaSchedulerConfiguration config,
      Long port,
      String persistenceId,
      String containerPath) {

    final BrokerConfiguration brokerResources = config.getBrokerConfiguration();
    String role = config.getServiceConfiguration().getRole();
    String principal = config.getServiceConfiguration().getPrincipal();

    List<Resource> resources = new ArrayList<>();
    resources.add(ResourceBuilder.reservedCpus(brokerResources.getCpus(), role, principal));
    resources.add(ResourceBuilder.reservedMem(brokerResources.getMem(), role, principal));
    resources.add(ResourceBuilder.reservedPorts(port, port, role, principal));
    resources.add(getVolumeResource(brokerResources.getDisk(), role, principal, containerPath, persistenceId));

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
