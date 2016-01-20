package org.apache.mesos.kafka.offer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.google.common.base.Joiner;

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

    List<String> commands = new ArrayList<>();

    // Run the hook script to perform any additional preparation work ...
    boolean containerHooksEnabled = true; // TODO this could be a config option?
    if (containerHooksEnabled) {
      commands.add("echo \"### HOOK BEGIN\"");
      // Do not use the /bin/bash-specific "source"
      commands.add(". $MESOS_SANDBOX/container-hook/container-hook.sh");
      commands.add("echo \"### HOOK END\"");
    }

    // ... then some quick housekeeping ...
    commands.add("export PATH=$PATH:$MESOS_SANDBOX/jre/bin");
    commands.add("echo \"### ENV BEGIN\"");
    commands.add("env");
    commands.add("echo \"### ENV END\"");

    // ... then run Kafka itself.
    // TODO any tuning flags could be added here
    String kafkaStartCmd = String.format("$MESOS_SANDBOX/%1$s/bin/kafka-server-start.sh "
        + "$MESOS_SANDBOX/%1$s/config/server.properties "
        + "--override zookeeper.connect=%2$s/%3$s "
        + "--override broker.id=%4$s "
        + "--override log.dirs=$MESOS_SANDBOX/kafka-logs",
        config.get("KAFKA_VER_NAME"), // #1
        config.get("ZOOKEEPER_ADDR"), // #2
        config.get("FRAMEWORK_NAME"), // #3
        brokerId); // #4
    if (containerHooksEnabled) {
      commands.add("echo Additional Kafka args: $CONTAINER_HOOK_FLAGS");
      kafkaStartCmd += " $CONTAINER_HOOK_FLAGS";
    }
    commands.add(kafkaStartCmd);
    String command = Joiner.on(" && ").join(commands);
    log.info("Built command: " + command);

    return Arrays.asList(new TaskInfoBuilder(taskId, brokerName, "" /* slaveId */)
        .addResource(cpus)
        .addResource(mem)
        .setCommand(new CommandInfoBuilder()
            .addUri(config.get("KAFKA_URI"))
            .addUri(config.get("CONTAINER_HOOK_URI"))
            .addUri(config.get("JAVA_URI"))
            .setCommand(command)
            .build())
        .build());
  }
}
