package org.apache.mesos.kafka.offer;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

public class OfferRequirementProvider {
  private final Log log = LogFactory.getLog(OfferRequirementProvider.class);

  private static int brokerId = 0;
  private ConfigurationService config = KafkaConfigService.getConfigService();
  private KafkaStateService state = KafkaStateService.getStateService();

  public OfferRequirement getNextRequirement() {
    if (belowTargetBrokerCount()) {
      String brokerId = getNextBrokerId();
      String taskId = getNextTaskId(brokerId);
      List<TaskInfo> taskInfos = getTaskInfos(taskId, brokerId);
      return new OfferRequirement(taskInfos);
    } else {
      try{
        List<TaskInfo> terminatedTaskInfos = state.getTerminatedTaskInfos();
        if (terminatedTaskInfos.size() > 0) {
          return new OfferRequirement(Arrays.asList(terminatedTaskInfos.get(0)));
        }
      } catch (Exception ex) {
        log.error("Failed to get terminated task list with exception: " + ex);
        return null;
      }
    }

    return null;
  }

  private boolean belowTargetBrokerCount() {
    int targetBrokerCount = Integer.parseInt(config.get("BROKER_COUNT"));
    int currentBrokerCount = 0;

    try {
      currentBrokerCount = state.getTaskNames().size();
    } catch(Exception ex) {
      log.error("Failed to retrieve current broker count with exception: " + ex);
      return false;
    }

    if (currentBrokerCount < targetBrokerCount) {
      return true;
    } else {
      return false;
    }
  }

  private String getNextBrokerId() {
    try {
      List<String> taskNames = state.getTaskNames();
      for (String taskName : taskNames) {
        log.warn("TaskName: " + taskName);
      }

      int targetBrokerCount = Integer.parseInt(config.get("BROKER_COUNT"));
      for (int i=0; i<targetBrokerCount; i++) {
        String brokerId = "broker-" + i;
        log.warn("Searching for: " + brokerId);
        if (!taskNames.contains(brokerId)) {
          return brokerId;
        }
      }
    } catch (Exception ex) {
      log.error("Failed to get task names with exception: " + ex);
      return null;
    }

    return null;
  }

  private String getNextTaskId(String brokerId) {
    return brokerId + "__" + UUID.randomUUID();
  }

  private List<TaskInfo> getTaskInfos(String taskId, String name) {
    Resource cpus = ResourceBuilder.cpus(1.0);

    CommandInfoBuilder cmdInfoBuilder = new CommandInfoBuilder();
    cmdInfoBuilder.setCommand("ls -l && sleep 60");
    cmdInfoBuilder.addUri(config.get("KAFKA_URI"));

    TaskInfoBuilder taskBuilder = new TaskInfoBuilder(taskId, name, "");
    taskBuilder.addResource(cpus);
    taskBuilder.setCommand(cmdInfoBuilder.build());

    return Arrays.asList(taskBuilder.build());
  }
}
