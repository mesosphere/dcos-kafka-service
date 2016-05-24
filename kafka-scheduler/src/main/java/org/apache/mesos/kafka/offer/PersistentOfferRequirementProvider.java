package org.apache.mesos.kafka.offer;

import com.google.common.base.Joiner;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.Environment.Variable;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.Value.Range;

import org.apache.mesos.kafka.config.BrokerConfiguration;
import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.config.KafkaSchedulerConfiguration;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.PlacementStrategy;
import org.apache.mesos.offer.ResourceUtils;

import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.EnvironmentBuilder;
import org.apache.mesos.protobuf.LabelBuilder;
import org.apache.mesos.protobuf.ValueBuilder;

public class PersistentOfferRequirementProvider implements KafkaOfferRequirementProvider {
  private final Log log = LogFactory.getLog(PersistentOfferRequirementProvider.class);

  public static final String CONFIG_ID_KEY = "CONFIG_ID";
  public static final String CONFIG_TARGET_KEY = "config_target";

  private final KafkaConfigState configState;
  private final PlacementStrategyManager placementStrategyManager;

  public PersistentOfferRequirementProvider(
      KafkaStateService kafkaStateService, KafkaConfigState configState) {
    this.configState = configState;
    this.placementStrategyManager = new PlacementStrategyManager(kafkaStateService);
  }

  @Override
  public OfferRequirement getNewOfferRequirement(String configName, int brokerId) {
    OfferRequirement offerRequirement = getNewOfferRequirementInternal(configName, brokerId);
    log.info("Got new OfferRequirement with TaskInfo: " + offerRequirement.getTaskInfo());

    return offerRequirement;
  }

  @Override
  public OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo) {
    OfferRequirement offerRequirement = new OfferRequirement(taskInfo, null, null);
    log.info("Got replacement OfferRequirement with TaskInfo: " + offerRequirement.getTaskInfo());

    return offerRequirement;
  }

  @Override
  public OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo taskInfo) {
    KafkaSchedulerConfiguration config = configState.fetch(configName);
    BrokerConfiguration brokerConfig = config.getBrokerConfiguration();

    TaskInfo.Builder taskBuilder = TaskInfo.newBuilder(taskInfo);
    taskBuilder = updateConfigTarget(taskBuilder, configName);
    taskBuilder = updateCpu(taskBuilder, brokerConfig);
    taskBuilder = updateMem(taskBuilder, brokerConfig);
    taskBuilder = updateDisk(taskBuilder, brokerConfig);
    taskBuilder = updateCmd(taskBuilder, configName);

    OfferRequirement offerRequirement = new OfferRequirement(taskBuilder.build(), null, null);
    log.info("Got updated OfferRequirement with TaskInfo: " + offerRequirement.getTaskInfo());

    return offerRequirement;
  }

  private TaskInfo.Builder updateCpu(TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfig) {
    ValueBuilder valBuilder = new ValueBuilder(Value.Type.SCALAR);
    valBuilder.setScalar(brokerConfig.getCpus());
    return updateValue(taskBuilder, "cpus", valBuilder.build());
  }

  private TaskInfo.Builder updateMem(TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfig) {
    ValueBuilder valBuilder = new ValueBuilder(Value.Type.SCALAR);
    valBuilder.setScalar(brokerConfig.getMem());
    return updateValue(taskBuilder, "mem", valBuilder.build());
  }

  private TaskInfo.Builder updateDisk(TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfig) {
    ValueBuilder valBuilder = new ValueBuilder(Value.Type.SCALAR);
    valBuilder.setScalar(brokerConfig.getDisk());
    return updateValue(taskBuilder, "disk", valBuilder.build());
  }

  private TaskInfo.Builder updateValue(TaskInfo.Builder taskBuilder, String name, Value updatedValue) {
    List<Resource> updatedResources = new ArrayList<Resource>();

    for (Resource resource : taskBuilder.getResourcesList()) {
      if (name.equals(resource.getName())) {
        updatedResources.add(ResourceUtils.setValue(resource, updatedValue));
      } else {
        updatedResources.add(resource);
      }
    }

    taskBuilder.clearResources();
    taskBuilder.addAllResources(updatedResources);
    return taskBuilder;
  }

  private TaskInfo.Builder updateConfigTarget(TaskInfo.Builder taskBuilder, String configName) {
    LabelBuilder labelBuilder = new LabelBuilder();
 
    // Copy everything except config target label 
    for (Label label : taskBuilder.getLabels().getLabelsList()) {
      String key = label.getKey();
      String value = label.getValue();
 
      if (!key.equals(CONFIG_TARGET_KEY)) {
        labelBuilder.addLabel(key, value);
      }
    }
 
    labelBuilder.addLabel(CONFIG_TARGET_KEY, configName);
    taskBuilder.setLabels(labelBuilder.build());
    return taskBuilder;
  }

  private TaskInfo.Builder updateCmd(TaskInfo.Builder taskBuilder, String configName) {
    EnvironmentBuilder envBuilder = new EnvironmentBuilder();

    for (Variable variable : taskBuilder.getCommand().getEnvironment().getVariablesList()) {
      if (variable.getName().equals(CONFIG_ID_KEY)) {
        envBuilder.addVariable(CONFIG_ID_KEY, configName);
      } else {
        envBuilder.addVariable(variable.getName(), variable.getValue());
      }
    }

    CommandInfo.Builder cmdBuilder = CommandInfo.newBuilder(taskBuilder.getCommand());
    cmdBuilder.setEnvironment(envBuilder.build());
    taskBuilder.setCommand(cmdBuilder.build());

    return taskBuilder;
  }

  private OfferRequirement getNewOfferRequirementInternal(String configName, int brokerId) {
    log.info("Getting new OfferRequirement for: " + configName);
    String overridePrefix = KafkaSchedulerConfiguration.KAFKA_OVERRIDE_PREFIX;
    String brokerName = OfferUtils.idToName(brokerId);
    Long port = 9092 + ThreadLocalRandom.current().nextLong(0, 1000);
    String containerPath = "kafka-volume-" + UUID.randomUUID();

    KafkaSchedulerConfiguration config = configState.fetch(configName);
    BrokerConfiguration brokerConfig = config.getBrokerConfiguration();

    String role = config.getServiceConfiguration().getRole();
    String principal = config.getServiceConfiguration().getPrincipal();
    String frameworkName = config.getServiceConfiguration().getName();

    Map<String, String> taskEnv = new HashMap<>();
    taskEnv.put("FRAMEWORK_NAME", frameworkName);
    taskEnv.put("KAFKA_VER_NAME", config.getKafkaConfiguration().getKafkaVerName());
    taskEnv.put(CONFIG_ID_KEY, configName);
    taskEnv.put(overridePrefix + "ZOOKEEPER_CONNECT", config.getKafkaConfiguration().getZkAddress() + "/" + frameworkName);
    taskEnv.put(overridePrefix + "BROKER_ID", Integer.toString(brokerId));
    taskEnv.put(overridePrefix + "LOG_DIRS", containerPath + "/" + brokerName);
    taskEnv.put(overridePrefix + "PORT", Long.toString(port));
    taskEnv.put(overridePrefix + "LISTENERS", "PLAINTEXT://:" + port);

    List<String> commands = new ArrayList<>();
    commands.add("export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH"); // find directory that starts with "jre" containing "bin"
    commands.add("env");
    commands.add("$MESOS_SANDBOX/overrider/bin/kafka-config-overrider server $MESOS_SANDBOX/overrider/conf/scheduler.yml");
    commands.add(String.format(
        "$MESOS_SANDBOX/%1$s/bin/kafka-server-start.sh " +
        "$MESOS_SANDBOX/%1$s/config/server.properties ",
        config.getKafkaConfiguration().getKafkaVerName()));
    String command = Joiner.on(" && ").join(commands);
    CommandInfoBuilder commandInfoBuilder = new CommandInfoBuilder()
      .addEnvironmentMap(taskEnv)
      .setCommand(command)
      .addUri(brokerConfig.getJavaUri())
      .addUri(brokerConfig.getKafkaUri())
      .addUri(brokerConfig.getOverriderUri());

    TaskInfo.Builder taskBuilder = TaskInfo.newBuilder();
    taskBuilder
      .setName(brokerName)
      .setTaskId(TaskID.newBuilder().setValue("").build())
      .setSlaveId(SlaveID.newBuilder().setValue("").build())
      .addResources(ResourceUtils.getDesiredScalar(role, principal, "cpus", brokerConfig.getCpus()))
      .addResources(ResourceUtils.getDesiredScalar(role, principal, "mem", brokerConfig.getMem()))
      .addResources(ResourceUtils.getDesiredRanges(
            role,
            principal,
            "ports",
            Arrays.asList(
              Range.newBuilder()
              .setBegin(port)
              .setEnd(port).build())));

    if (brokerConfig.getDiskType().equals("MOUNT")) {
      taskBuilder.addResources(ResourceUtils.getDesiredMountVolume(
            role,
            principal,
            brokerConfig.getDisk(),
            containerPath));
    } else {
      taskBuilder.addResources(ResourceUtils.getDesiredRootVolume(
            role,
            principal,
            brokerConfig.getDisk(),
            containerPath));
    }

    taskBuilder
      .setLabels(Labels.newBuilder()
          .addLabels(Label.newBuilder()
            .setKey(CONFIG_TARGET_KEY)
            .setValue(configName))
          .build())
      .setCommand(commandInfoBuilder.build());

    TaskInfo taskInfo = taskBuilder.build();

    PlacementStrategy placementStrategy = placementStrategyManager.getPlacementStrategy(config);
    List<SlaveID> avoidAgents = placementStrategy.getAgentsToAvoid(taskInfo);
    List<SlaveID> colocateAgents = placementStrategy.getAgentsToColocate(taskInfo);

    return new OfferRequirement(taskInfo, avoidAgents, colocateAgents);
  }
}
