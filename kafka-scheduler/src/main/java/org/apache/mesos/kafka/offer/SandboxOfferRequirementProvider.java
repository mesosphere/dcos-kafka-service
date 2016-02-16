package org.apache.mesos.kafka.offer;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.PlacementStrategy;
import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.LabelBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

public class SandboxOfferRequirementProvider implements KafkaOfferRequirementProvider {
  private final Log log = LogFactory.getLog(SandboxOfferRequirementProvider.class);
  private KafkaConfigState configState = null;

  public SandboxOfferRequirementProvider(KafkaConfigState configState) {
    this.configState = configState;
  }

  public OfferRequirement getNewOfferRequirement(String configName, int brokerId) {
    KafkaConfigService config = configState.fetch(configName);
    PlacementStrategy placementStrategy = PlacementStrategyManager.getPlacementStrategy(config);
    TaskInfo taskInfo = getTaskInfo(configName, config, brokerId);

    return new OfferRequirement(
        Arrays.asList(taskInfo),
        placementStrategy.getAgentsToAvoid(taskInfo),
        placementStrategy.getAgentsToColocate(taskInfo));
  }

  public OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo) {
    KafkaConfigService config = getConfigService(taskInfo);
    PlacementStrategy placementStrategy = PlacementStrategyManager.getPlacementStrategy(config);

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

    double cpus = Double.parseDouble(config.get("BROKER_CPUS"));
    double mem = Double.parseDouble(config.get("BROKER_MEM"));

    List<Resource> resources = new ArrayList<>();
    resources.add(ResourceBuilder.cpus(cpus));
    resources.add(ResourceBuilder.mem(mem));

    return resources;
  }
}
