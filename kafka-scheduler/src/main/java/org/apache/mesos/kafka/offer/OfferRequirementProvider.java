package org.apache.mesos.kafka.offer;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

public class OfferRequirementProvider {
  private static int brokerId = 0;

  public OfferRequirement getNextRequirement() {
    String brokerId = getNextBrokerId();
    String taskId = getNextTaskId(brokerId);
    List<TaskInfo> taskInfos = getTaskInfos(taskId, brokerId);
    return new OfferRequirement(taskInfos);
  }

  private String getNextBrokerId() {
    return "broker-" + brokerId++;
  }

  private String getNextTaskId(String brokerId) {
    return brokerId + "-" + UUID.randomUUID();
  }

  private List<TaskInfo> getTaskInfos(String taskId, String name) {
    Resource cpus = ResourceBuilder.cpus(1.0);

    CommandInfoBuilder cmdInfoBuilder = new CommandInfoBuilder();
    cmdInfoBuilder.setCommand("ls -l");
    cmdInfoBuilder.addUri("https://s3-us-west-2.amazonaws.com/gabriel-kafka-test/kafka_2.10-0.9.0.0.tgz");

    TaskInfoBuilder taskBuilder = new TaskInfoBuilder(taskId, name, "");
    taskBuilder.addResource(cpus);
    taskBuilder.setCommand(cmdInfoBuilder.build());

    return Arrays.asList(taskBuilder.build());
  }
}
