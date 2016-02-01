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
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.apache.mesos.Protos.TaskInfo;

public class SandboxOfferRequirementProvider implements OfferRequirementProvider {
  private final Log log = LogFactory.getLog(SandboxOfferRequirementProvider.class);

  public OfferRequirement getNewOfferRequirement(ConfigurationService config) {
    Integer brokerId = OfferUtils.getNextBrokerId();
    if (brokerId == null) {
      log.error("Failed to get broker ID");
      return null;
    }

    PlacementStrategyService placementSvc = PlacementStrategy.getPlacementStrategyService(config);
    TaskInfo taskInfo = getTaskInfo(config, brokerId);

    return new OfferRequirement(
        Arrays.asList(taskInfo),
        placementSvc.getAgentsToAvoid(taskInfo),
        placementSvc.getAgentsToColocate(taskInfo));
  }

  public OfferRequirement getReplacementOfferRequirement(ConfigurationService config, TaskInfo taskInfo) {
    PlacementStrategyService placementSvc = PlacementStrategy.getPlacementStrategyService(config);

    return new OfferRequirement(
        Arrays.asList(taskInfo),
        placementSvc.getAgentsToAvoid(taskInfo),
        placementSvc.getAgentsToColocate(taskInfo));
  }

  private TaskInfo getTaskInfo(ConfigurationService config, int brokerId) {
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

    return new TaskInfoBuilder(taskId, brokerName, "" /* slaveId */)
        .addResource(ResourceBuilder.cpus(1.0))
        .addResource(ResourceBuilder.mem(2048))
        .addResource(ResourceBuilder.ports(port, port))
        .setCommand(new CommandInfoBuilder()
          .addUri(config.get("KAFKA_URI"))
          .addUri(config.get("CONTAINER_HOOK_URI"))
          .addUri(config.get("JAVA_URI"))
          .setCommand(command)
          .build())
        .build();
  }
}
