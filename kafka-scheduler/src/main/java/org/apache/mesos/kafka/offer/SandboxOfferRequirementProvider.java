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
import org.apache.mesos.offer.PlacementStrategy;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

public class SandboxOfferRequirementProvider implements KafkaOfferRequirementProvider {
  private static final Log log = LogFactory.getLog(SandboxOfferRequirementProvider.class);

  private final KafkaConfigState configState;
  private final PlacementStrategyManager placementStrategyManager;

  public SandboxOfferRequirementProvider(
      KafkaStateService kafkaStateService, KafkaConfigState configState) {
    this.configState = configState;
    this.placementStrategyManager = new PlacementStrategyManager(kafkaStateService);
  }

  public OfferRequirement getNewOfferRequirement(String configName, int brokerId) {
    KafkaConfigService config = configState.fetch(configName);
    PlacementStrategy placementStrategy = placementStrategyManager.getPlacementStrategy(config);
    TaskInfo taskInfo = getTaskInfo(configName, config, brokerId);

    return new OfferRequirement(
        Arrays.asList(taskInfo),
        placementStrategy.getAgentsToAvoid(taskInfo),
        placementStrategy.getAgentsToColocate(taskInfo));
  }

  public OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo) {
    KafkaConfigService config = getConfigService(taskInfo);
    PlacementStrategy placementStrategy = placementStrategyManager.getPlacementStrategy(config);

    return new OfferRequirement(
        Arrays.asList(taskInfo),
        placementStrategy.getAgentsToAvoid(taskInfo),
        placementStrategy.getAgentsToColocate(taskInfo));
  }

  public OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo taskInfo) {
    log.info("Fetching config: " + configName);
    KafkaConfigService config = configState.fetch(configName);
    log.info("Fetched config: " + config);
    TaskInfo newTaskInfo = getTaskInfo(configName, config, OfferUtils.nameToId(taskInfo.getName()));
    newTaskInfo = TaskInfo.newBuilder(newTaskInfo).setTaskId(taskInfo.getTaskId()).build();

    return getReplacementOfferRequirement(newTaskInfo);
  }

  private KafkaConfigService getConfigService(TaskInfo taskInfo) {
    String configName = OfferUtils.getConfigName(taskInfo);
    return configState.fetch(configName);
  }

  private TaskInfo getTaskInfo(String configName, KafkaConfigService config, int brokerId) {
    String brokerName = "broker-" + brokerId;
    String taskId = brokerName + "__" + UUID.randomUUID();
    List<Resource> resources = getResources(config);
    return OfferRequirementUtils.getTaskInfo(configName, config, resources, brokerId, taskId, "kafka-logs");
  }

  private List<Resource> getResources(KafkaConfigService config) {
    KafkaConfigService.BrokerResources brokerResources = config.getBrokerResources();
    List<Resource> resources = new ArrayList<>();
    resources.add(ResourceBuilder.cpus(brokerResources.cpus));
    resources.add(ResourceBuilder.mem(brokerResources.mem));
    //TODO disk?
    return resources;
  }
}
