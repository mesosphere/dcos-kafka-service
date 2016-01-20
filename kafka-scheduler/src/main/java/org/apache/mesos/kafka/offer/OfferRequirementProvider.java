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
      Integer brokerId = getNextBrokerId();
      List<TaskInfo> taskInfos = getTaskInfos(brokerId);
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

  private Integer getNextBrokerId() {
    try {
      List<String> taskNames = state.getTaskNames();

      int targetBrokerCount = Integer.parseInt(config.get("BROKER_COUNT"));
      for (int i=0; i<targetBrokerCount; i++) {
        String brokerName = "broker-" + i;
        if (!taskNames.contains(brokerName)) {
          return i; 
        }
      }
    } catch (Exception ex) {
      log.error("Failed to get task names with exception: " + ex);
      return null;
    }

    return null;
  }

  private List<TaskInfo> getTaskInfos(int brokerId) {
    String brokerName = "broker-" + brokerId;
    String taskId =  brokerName + "__" + UUID.randomUUID();

    Resource cpus = ResourceBuilder.cpus(1.0);
    Resource mem = ResourceBuilder.mem(2048);

    String statsdCmdFmt = "";
    String statsdCmd = "";

    String kafkaCmdFmt = "export PATH=$PATH:$MESOS_SANDBOX/jre/bin && env && "
                       + "$MESOS_SANDBOX/$KAFKA_VER_NAME/bin/kafka-server-start.sh "
                       + "$MESOS_SANDBOX/$KAFKA_VER_NAME/config/server.properties "
                       + "--override zookeeper.connect=$KAFKA_ZK/$FRAMEWORK_NAME "
                       + "--override broker.id=%d "
                       + "--override log.dirs=$MESOS_SANDBOX/kafka-logs "
                       + "$STATSD_OVERRIDES";

    String kafkaCmd = String.format(kafkaCmdFmt, brokerId);

    CommandInfoBuilder cmdInfoBuilder = new CommandInfoBuilder();
    cmdInfoBuilder.addEnvironmentVar("KAFKA_ZK", config.get("ZOOKEEPER_ADDR"));
    cmdInfoBuilder.addEnvironmentVar("KAFKA_VER_NAME", config.get("KAFKA_VER_NAME"));
    cmdInfoBuilder.addEnvironmentVar("FRAMEWORK_NAME", config.get("FRAMEWORK_NAME"));
    cmdInfoBuilder.addUri(config.get("KAFKA_URI"));
    cmdInfoBuilder.addUri(config.get("JAVA_URI"));
    cmdInfoBuilder.setCommand(statsdCmd + " && " + kafkaCmd);

    TaskInfoBuilder taskBuilder = new TaskInfoBuilder(taskId, brokerName, "");
    taskBuilder.addResource(cpus);
    taskBuilder.addResource(mem);
    taskBuilder.setCommand(cmdInfoBuilder.build());

    return Arrays.asList(taskBuilder.build());
  }
}
