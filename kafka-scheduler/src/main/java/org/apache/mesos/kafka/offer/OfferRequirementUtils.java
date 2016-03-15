package org.apache.mesos.kafka.offer;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.mesos.kafka.config.KafkaConfigService;

import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.LabelBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

public class OfferRequirementUtils {

  public static String getKafkaStartCmd(KafkaConfigService config) {
    return String.format(
        "$MESOS_SANDBOX/%1$s/bin/kafka-server-start.sh " +
        "$MESOS_SANDBOX/%1$s/config/server.properties " +
        "$CONTAINER_HOOK_FLAGS",
        config.getKafkaVersionName());
  }

  public static TaskInfo getTaskInfo(
      String configName,
      KafkaConfigService config,
      List<Resource> resources,
      int brokerId,
      String taskId,
      String containerPath,
      Long port) {

    String brokerName = OfferUtils.idToName(brokerId);
    List<String> commands = new ArrayList<>();

    // Do not use the /bin/bash-specific "source"
    commands.add(". $MESOS_SANDBOX/container-hook/container-hook.sh");

    // Export the JRE and log the environment
    commands.add("export PATH=$PATH:$MESOS_SANDBOX/jre/bin");
    commands.add("env");
    commands.add("java -cp $MESOS_SANDBOX/kafka-config-overrider-*.jar org.apache.mesos.kafka.config.Overrider " + configName);

    // Run Kafka
    String kafkaStartCmd = OfferRequirementUtils.getKafkaStartCmd(config);
    commands.add(kafkaStartCmd);

    String command = Joiner.on(" && ").join(commands);

    String overridePrefix = config.getOverridePrefix();
    Map<String, String> taskEnv = new HashMap<>();
    taskEnv.put("FRAMEWORK_NAME", config.getFrameworkName());
    taskEnv.put("KAFKA_VER_NAME", config.getKafkaVersionName());
    taskEnv.put(overridePrefix + "ZOOKEEPER_CONNECT", config.getZookeeperAddress() + config.getZkRoot());
    taskEnv.put(overridePrefix + "BROKER_ID", Integer.toString(brokerId));
    taskEnv.put(overridePrefix + "LOG_DIRS", containerPath);
    taskEnv.put(overridePrefix + "PORT", Long.toString(port));
    taskEnv.put(overridePrefix + "LISTENERS", "PLAINTEXT://:" + port);

    Labels labels = new LabelBuilder()
      .addLabel("config_target", configName)
      .build();

    CommandInfoBuilder commandInfoBuilder = new CommandInfoBuilder()
        .addEnvironmentMap(taskEnv)
        .setCommand(command);
    for (String resourceUri : config.getBrokerResourceUris()) {
      commandInfoBuilder.addUri(resourceUri);
    }

    return new TaskInfoBuilder(taskId, brokerName, "" /* slaveId */)
      .addAllResources(resources)
      .setCommand(commandInfoBuilder.build())
      .setLabels(labels)
      .build();
  }
}
