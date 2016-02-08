package org.apache.mesos.kafka.offer;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
import org.apache.mesos.Protos.TaskInfo;

public class SandboxOfferRequirementProvider implements KafkaOfferRequirementProvider {
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
    ConfigurationService config = configState.fetch(configName);
    TaskInfo newTaskInfo = getTaskInfo(configName, config, OfferUtils.nameToId(taskInfo.getName()));
    newTaskInfo = TaskInfo.newBuilder(newTaskInfo).setTaskId(taskInfo.getTaskId()).build();

    return getReplacementOfferRequirement(newTaskInfo);
  }

  private KafkaConfigService getConfigService(TaskInfo taskInfo) {
    String configName = OfferUtils.getConfigName(taskInfo);
    return configState.fetch(configName);
  }

  private TaskInfo getTaskInfo(String configName, ConfigurationService config, int brokerId) {
    String brokerName = "broker-" + brokerId;
    String taskId = brokerName + "__" + UUID.randomUUID();
    int port = 9092 + brokerId;

    List<String> commands = new ArrayList<>();

    // Do not use the /bin/bash-specific "source"
    commands.add(". $MESOS_SANDBOX/container-hook/container-hook.sh");

    // Export the JRE and log the environment 
    commands.add("export PATH=$PATH:$MESOS_SANDBOX/jre/bin");
    commands.add("env");

    // Run Kafka
    String kafkaStartCmd = OfferRequirementUtils.getKafkaStartCmd(config, brokerId, port, "kafka-logs"); 
    commands.add(kafkaStartCmd);

    String command = Joiner.on(" && ").join(commands);

    Labels labels = new LabelBuilder()
      .addLabel("config_target", configName)
      .build();

    double cpus = Double.parseDouble(config.get("BROKER_CPUS"));
    double mem = Double.parseDouble(config.get("BROKER_MEM"));

    return new TaskInfoBuilder(taskId, brokerName, "" /* slaveId */)
        .addResource(ResourceBuilder.cpus(cpus))
        .addResource(ResourceBuilder.mem(mem))
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
}
