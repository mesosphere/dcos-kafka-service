package org.apache.mesos.kafka.offer;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

public class OfferRequirementProvider {
  private static int brokerId = 0;
  private ConfigurationService config = KafkaConfigService.getConfigService();

  public OfferRequirement getNextRequirement() {
    if (moreBrokersNeeded()) {
      int brokerId = getNextBrokerId();
      String brokerName = String.format("broker-%d", brokerId);
      String taskId = getNextTaskId(brokerName);
      List<TaskInfo> taskInfos = getTaskInfos(taskId, brokerName, brokerId);
      return new OfferRequirement(taskInfos);
    } else {
      return null;
    }
  }

  private boolean moreBrokersNeeded() {
    int brokerCount = Integer.parseInt(config.get("BROKER_COUNT"));

    if (brokerId >= brokerCount) {
      return false;
    } else {
      return true;
    }
  }

  private int getNextBrokerId() {
    return brokerId++;
  }

  private String getNextTaskId(String brokerId) {
    return brokerId + "-" + UUID.randomUUID();
  }

  private List<TaskInfo> getTaskInfos(String taskId, String brokerName, int brokerId) {
    Resource cpus = ResourceBuilder.cpus(1.0);

    CommandInfoBuilder cmdInfoBuilder = new CommandInfoBuilder();
    cmdInfoBuilder.addEnvironmentVar("KAFKA_FMWK_BROKER_ID", String.valueOf(brokerId));
    cmdInfoBuilder.addEnvironmentVar("KAFKA_FMWK_ZK_ENDPOINT", config.get("ZOOKEEPER_ADDR"));
    cmdInfoBuilder.setCommand(String.format("sh kafka-executor/kafka-executor.sh"));
    cmdInfoBuilder.addUri("https://s3-us-west-2.amazonaws.com/gabriel-kafka-test/kafka_2.10-0.9.0.0.tgz");
    cmdInfoBuilder.addUri("https://s3-us-west-2.amazonaws.com/gabriel-kafka-test/kafka-executor-0.1.0.tgz");

    TaskInfoBuilder taskBuilder = new TaskInfoBuilder(taskId, brokerName, "");
    taskBuilder.addResource(cpus);
    taskBuilder.setCommand(cmdInfoBuilder.build());

    return Arrays.asList(taskBuilder.build());
  }
}
