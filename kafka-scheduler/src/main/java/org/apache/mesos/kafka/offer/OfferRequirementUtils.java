package org.apache.mesos.kafka.offer;

import com.google.common.base.Joiner;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.kafka.config.BrokerConfiguration;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.config.KafkaSchedulerConfiguration;
import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.LabelBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OfferRequirementUtils {

  public static String getKafkaStartCmd(KafkaSchedulerConfiguration config) {
    return String.format(
        "$MESOS_SANDBOX/%1$s/bin/kafka-server-start.sh " +
        "$MESOS_SANDBOX/%1$s/config/server.properties " +
        "$CONTAINER_HOOK_FLAGS",
        config.getKafkaConfiguration().getKafkaVerName());
  }

  public static TaskInfo getTaskInfo(
      String configName,
      KafkaSchedulerConfiguration config,
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
    commands.add("export CONFIG_ID=" + configName);
    commands.add("env");
    commands.add("$MESOS_SANDBOX/overrider/bin/kafka-config-overrider server $MESOS_SANDBOX/overrider/conf/scheduler.yml");

    // Run Kafka
    String kafkaStartCmd = OfferRequirementUtils.getKafkaStartCmd(config);
    commands.add(kafkaStartCmd);

    String command = Joiner.on(" && ").join(commands);

    String overridePrefix = KafkaSchedulerConfiguration.KAFKA_OVERRIDE_PREFIX;
    Map<String, String> taskEnv = new HashMap<>();
    final String frameworkName = config.getServiceConfiguration().getName();
    taskEnv.put("FRAMEWORK_NAME", frameworkName);
    taskEnv.put("KAFKA_VER_NAME", config.getKafkaConfiguration().getKafkaVerName());
    taskEnv.put(overridePrefix + "ZOOKEEPER_CONNECT", config.getKafkaConfiguration().getZkAddress() + "/" + frameworkName);
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
    final BrokerConfiguration brokerConfiguration = config.getBrokerConfiguration();
    commandInfoBuilder.addUri(brokerConfiguration.getJavaUri());
    commandInfoBuilder.addUri(brokerConfiguration.getKafkaUri());
    commandInfoBuilder.addUri(brokerConfiguration.getOverriderUri());
    commandInfoBuilder.addUri(brokerConfiguration.getContainerHookUri());

    return new TaskInfoBuilder(taskId, brokerName, "" /* slaveId */)
      .addAllResources(resources)
      .setCommand(commandInfoBuilder.build())
      .setLabels(labels)
      .build();
  }
}
