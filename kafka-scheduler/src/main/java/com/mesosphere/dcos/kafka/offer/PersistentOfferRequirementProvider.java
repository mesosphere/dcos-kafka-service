package com.mesosphere.dcos.kafka.offer;

import com.google.common.base.Joiner;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.mesosphere.dcos.kafka.commons.KafkaTask;
import com.mesosphere.dcos.kafka.config.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Environment.Variable;
import org.apache.mesos.Protos.Value.Range;
import org.apache.mesos.Protos.Value.Ranges;
import org.apache.mesos.config.ConfigStoreException;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.offer.*;
import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.EnvironmentBuilder;
import org.apache.mesos.protobuf.LabelBuilder;
import org.apache.mesos.protobuf.ValueBuilder;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PersistentOfferRequirementProvider implements KafkaOfferRequirementProvider {
  private final Log log = LogFactory.getLog(PersistentOfferRequirementProvider.class);

  public static final String CONFIG_ID_KEY = "CONFIG_ID";
  public static final String CONFIG_TARGET_KEY = "config_target";

  private final KafkaConfigState configState;
  private final FrameworkState frameworkState;
  private final PlacementStrategyManager placementStrategyManager;

  public PersistentOfferRequirementProvider(
      FrameworkState frameworkState, KafkaConfigState configState) {
    this.configState = configState;
    this.frameworkState = frameworkState;
    this.placementStrategyManager = new PlacementStrategyManager(frameworkState);
  }

  @Override
  public OfferRequirement getNewOfferRequirement(String configName, int brokerId)
          throws InvalidRequirementException, ConfigStoreException {
    OfferRequirement offerRequirement = getNewOfferRequirementInternal(configName, brokerId);
    return offerRequirement;
  }

  @Override
  public OfferRequirement getReplacementOfferRequirement(TaskInfo existingTaskInfo)
          throws InvalidRequirementException {
    final ExecutorInfo existingExecutor = existingTaskInfo.getExecutor();

    final TaskInfo.Builder replacementTaskInfo = TaskInfo.newBuilder(existingTaskInfo);
    replacementTaskInfo.clearExecutor();
    replacementTaskInfo.setTaskId(TaskID.newBuilder().setValue("").build()); // Set later by TaskRequirement

    final ExecutorInfo.Builder replacementExecutor = ExecutorInfo.newBuilder(existingExecutor);
    replacementExecutor.setExecutorId(ExecutorID.newBuilder().setValue("").build()); // Set later by ExecutorRequirement

    TaskInfo replaceTaskInfo = replacementTaskInfo.build();
    ExecutorInfo replaceExecutorInfo = replacementExecutor.build();
    OfferRequirement offerRequirement = new OfferRequirement(
            Arrays.asList(replaceTaskInfo),
            replaceExecutorInfo,
            null,
            null);

    log.info(String.format("Got replacement OfferRequirement: TaskInfo: '%s' ExecutorInfo: '%s'",
            TextFormat.shortDebugString(replaceTaskInfo),
            TextFormat.shortDebugString(replaceExecutorInfo)));

    return offerRequirement;
  }

  @Override
  public OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo taskInfo)
          throws InvalidRequirementException, ConfigStoreException {
    KafkaSchedulerConfiguration config = configState.fetch(UUID.fromString(configName));
    BrokerConfiguration brokerConfig = config.getBrokerConfiguration();

    TaskInfo.Builder taskBuilder = TaskInfo.newBuilder(taskInfo);
    final ExecutorInfo existingExecutor = taskBuilder.getExecutor();

    taskBuilder = updateConfigTarget(taskBuilder, configName);
    taskBuilder = updateCpu(taskBuilder, brokerConfig);
    taskBuilder = updateMem(taskBuilder, brokerConfig);
    taskBuilder = updateDisk(taskBuilder, brokerConfig);
    taskBuilder = updateCmd(taskBuilder, configName);
    taskBuilder = updateKafkaHeapOpts(taskBuilder, brokerConfig);

    final ExecutorInfo.Builder updatedExecutor = ExecutorInfo.newBuilder(existingExecutor);
    updatedExecutor.clearExecutorId();
    updatedExecutor.setExecutorId(ExecutorID.newBuilder().setValue("").build()); // Set later by ExecutorRequirement

    taskBuilder.clearExecutor();
    taskBuilder.clearTaskId();
    taskBuilder.setTaskId(TaskID.newBuilder().setValue("").build()); // Set later by TaskRequirement

    CommandInfo.Builder cmdBuilder;
    try {
      cmdBuilder = CommandInfo.newBuilder().mergeFrom(taskBuilder.getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequirementException("Unable to rehydrate broker CommandInfo");
    }
    Map<String, String> environmentMap = fromEnvironmentToMap(cmdBuilder.getEnvironment());

    String portVar = KafkaSchedulerConfiguration.KAFKA_OVERRIDE_PREFIX + "PORT";
    String dynamicVar = "KAFKA_DYNAMIC_BROKER_PORT";
    String dynamicValue = environmentMap.get(dynamicVar);
    Long port = brokerConfig.getPort();

    if (port == 0) {
      if (dynamicValue != null && dynamicValue.equals(Boolean.toString(false))) {
        // The previous configuration used a static port, so we should generate a new dynamic port.
        port = getDynamicPort();
        environmentMap.put(portVar, Long.toString(port));
      } else {
        port = Long.parseLong(environmentMap.get(portVar));
      }

      environmentMap.put(dynamicVar, Boolean.toString(true));
      brokerConfig.setPort(port);
    } else {
      environmentMap.put(portVar, Long.toString(port));
      environmentMap.put(dynamicVar, Boolean.toString(false));
    }

    taskBuilder = updatePort(taskBuilder, brokerConfig);

    cmdBuilder.clearEnvironment();
    final List<Variable> newEnvironmentVariables = EnvironmentBuilder.createEnvironment(environmentMap);
    cmdBuilder.setEnvironment(Environment.newBuilder().addAllVariables(newEnvironmentVariables));
    taskBuilder.setData(cmdBuilder.build().toByteString());

    TaskInfo updatedTaskInfo = taskBuilder.build();

    try {
      ExecutorInfo updateExecutorInfo = updatedExecutor.build();
      OfferRequirement offerRequirement = new OfferRequirement(
              Arrays.asList(updatedTaskInfo),
              updateExecutorInfo, null, null);

      log.info(String.format("Got updated OfferRequirement: TaskInfo: '%s' ExecutorInfo: '%s'",
              TextFormat.shortDebugString(updatedTaskInfo),
              TextFormat.shortDebugString(updateExecutorInfo)));

      return offerRequirement;
    } catch (InvalidRequirementException e) {
      throw new InvalidRequirementException(String.format(
          "Failed to create update OfferRequirement with OrigTaskInfo[%s] NewTaskInfo[%s]",
          taskInfo, updatedTaskInfo), e);
    }
  }

  private String getKafkaHeapOpts(HeapConfig heapConfig) {
    return String.format("-Xms%1$dM -Xmx%1$dM", heapConfig.getSizeMb());
  }

  private TaskInfo.Builder updateKafkaHeapOpts(
      TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfig)
          throws InvalidRequirementException {
    try {
      final CommandInfo oldCommand = CommandInfo.parseFrom(taskBuilder.getData());
      final Environment oldEnvironment = oldCommand.getEnvironment();

      final Map<String, String> newEnvMap = fromEnvironmentToMap(oldEnvironment);
      newEnvMap.put("KAFKA_HEAP_OPTS", getKafkaHeapOpts(brokerConfig.getHeap()));

      final CommandInfo.Builder newCommandBuilder = CommandInfo.newBuilder(oldCommand);
      newCommandBuilder.clearEnvironment();

      final List<Variable> newEnvironmentVariables = EnvironmentBuilder.createEnvironment(newEnvMap);
      newCommandBuilder.setEnvironment(Environment.newBuilder().addAllVariables(newEnvironmentVariables));

      taskBuilder.clearData();
      taskBuilder.setData(newCommandBuilder.build().toByteString());

      log.info("Updated env map:" + newEnvMap);
      return taskBuilder;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequirementException("Couldn't update heap opts", e);
    }
  }

  private Map<String, String> fromEnvironmentToMap(Environment environment) {
    Map<String, String> map = new HashMap<>();

    final List<Variable> variablesList = environment.getVariablesList();

    for (Variable variable : variablesList) {
      map.put(variable.getName(), variable.getValue());
    }

    return map;
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

  private TaskInfo.Builder updatePort(TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfig) {
    Range portRange = Range.newBuilder().setBegin(brokerConfig.getPort()).setEnd(brokerConfig.getPort()).build();
    Ranges portRanges = Ranges.newBuilder().addRange(portRange).build();
    ValueBuilder valBuilder = new ValueBuilder(Value.Type.RANGES).setRanges(portRanges);

    return updateValue(taskBuilder, "ports", valBuilder.build());
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

  private TaskInfo.Builder updateCmd(TaskInfo.Builder taskBuilder, String configName)
          throws InvalidRequirementException {
    EnvironmentBuilder envBuilder = new EnvironmentBuilder();

    try {
      final CommandInfo existingCommandInfo = CommandInfo.parseFrom(taskBuilder.getData());
      for (Variable variable : existingCommandInfo.getEnvironment().getVariablesList()) {
        if (variable.getName().equals(CONFIG_ID_KEY)) {
          envBuilder.addVariable(CONFIG_ID_KEY, configName);
        } else {
          envBuilder.addVariable(variable.getName(), variable.getValue());
        }
      }

      CommandInfo.Builder cmdBuilder = CommandInfo.newBuilder(existingCommandInfo);
      cmdBuilder.setEnvironment(envBuilder.build());

      taskBuilder.clearData();
      taskBuilder.setData(cmdBuilder.build().toByteString());

      return taskBuilder;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequirementException("Unable to parse CommandInfo", e);
    }
  }

  private static Long getDynamicPort() {
    return 9092 + ThreadLocalRandom.current().nextLong(0, 1000);
  }

  private OfferRequirement getNewOfferRequirementInternal(String configName, int brokerId)
          throws InvalidRequirementException, ConfigStoreException {
    log.info("Getting new OfferRequirement for: " + configName);
    String overridePrefix = KafkaSchedulerConfiguration.KAFKA_OVERRIDE_PREFIX;
    String brokerName = OfferUtils.idToName(brokerId);

    String containerPath = "kafka-volume-" + UUID.randomUUID();

    KafkaSchedulerConfiguration config = configState.fetch(UUID.fromString(configName));
    BrokerConfiguration brokerConfig = config.getBrokerConfiguration();
    ExecutorConfiguration executorConfig = config.getExecutorConfiguration();

    Long port = brokerConfig.getPort();
    Boolean isDynamicPort = false;
    if (port == 0) {
      port = getDynamicPort();
      isDynamicPort = true;
    }

    String role = config.getServiceConfiguration().getRole();
    String principal = config.getServiceConfiguration().getPrincipal();
    String frameworkName = config.getServiceConfiguration().getName();

    List<String> commands = new ArrayList<>();
    commands.add("export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH"); // find directory that starts with "jre" containing "bin"
    commands.add("$MESOS_SANDBOX/overrider/bin/kafka-config-overrider server $MESOS_SANDBOX/overrider/conf/scheduler.yml");
    final KafkaConfiguration kafkaConfiguration = config.getKafkaConfiguration();
    commands.add(String.format(
        "exec $MESOS_SANDBOX/%1$s/bin/kafka-server-start.sh " +
        "$MESOS_SANDBOX/%1$s/config/server.properties ",
        kafkaConfiguration.getKafkaVerName()));
    final String kafkaLaunchCommand = Joiner.on(" && ").join(commands);

    log.info("Configuring kafkaLaunchCommand as: " + kafkaLaunchCommand);

    final CommandInfoBuilder brokerTaskBuilder = new CommandInfoBuilder();
    brokerTaskBuilder.setCommand(kafkaLaunchCommand);
    brokerTaskBuilder
      .addEnvironmentVar("TASK_TYPE", KafkaTask.BROKER.name())
      .addEnvironmentVar("FRAMEWORK_NAME", frameworkName)
      .addEnvironmentVar("KAFKA_VER_NAME", kafkaConfiguration.getKafkaVerName())
      .addEnvironmentVar(CONFIG_ID_KEY, configName)
      .addEnvironmentVar(overridePrefix + "ZOOKEEPER_CONNECT", kafkaConfiguration.getZkAddress() + "/" + frameworkName)
      .addEnvironmentVar(overridePrefix + "BROKER_ID", Integer.toString(brokerId))
      .addEnvironmentVar(overridePrefix + "LOG_DIRS", containerPath + "/" + brokerName)
      .addEnvironmentVar(overridePrefix + "LISTENERS", "PLAINTEXT://:" + port)
      .addEnvironmentVar(overridePrefix + "PORT", Long.toString(port))
      .addEnvironmentVar("KAFKA_DYNAMIC_BROKER_PORT", Boolean.toString(isDynamicPort));

    // Launch command for custom executor
    final String executorCommand = "./executor/bin/kafka-executor -Dlogback.configurationFile=executor/conf/logback.xml";

    CommandInfoBuilder executorCommandBuilder = new CommandInfoBuilder()
      .setCommand(executorCommand)
      .addEnvironmentVar("JAVA_HOME", "jre1.8.0_91")
      .addUri(brokerConfig.getJavaUri())
      .addUri(brokerConfig.getKafkaUri())
      .addUri(brokerConfig.getOverriderUri())
      .addUri(executorConfig.getExecutorUri());

    // Build Executor
    final ExecutorInfo.Builder executorBuilder = ExecutorInfo.newBuilder();

    executorBuilder
      .setName(brokerName)
      .setExecutorId(ExecutorID.newBuilder().setValue("").build()) // Set later by ExecutorRequirement
      .setFrameworkId(frameworkState.getFrameworkId())
      .setCommand(executorCommandBuilder.build())
      .addResources(ResourceUtils.getDesiredScalar(role, principal, "cpus", executorConfig.getCpus()))
      .addResources(ResourceUtils.getDesiredScalar(role, principal, "mem", executorConfig.getMem()));

    // Build Task
    TaskInfo.Builder taskBuilder = TaskInfo.newBuilder();
    taskBuilder
      .setName(brokerName)
      .setTaskId(TaskID.newBuilder().setValue("").build()) // Set later by TaskRequirement
      .setSlaveId(SlaveID.newBuilder().setValue("").build()) // Set later
      .setData(brokerTaskBuilder.build().toByteString())
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
          .build());

    log.info("TaskInfo.Builder contains executor: " + taskBuilder.hasExecutor());
    // Explicitly clear executor.
    taskBuilder.clearExecutor();
    taskBuilder.clearCommand();

    TaskInfo taskInfo = taskBuilder.build();
    log.info("TaskInfo contains executor: " + taskInfo.hasExecutor());

    final ExecutorInfo executorInfo = executorBuilder.build();

    PlacementStrategy placementStrategy = placementStrategyManager.getPlacementStrategy(config);
    List<SlaveID> avoidAgents = placementStrategy.getAgentsToAvoid(taskInfo);
    List<SlaveID> colocateAgents = placementStrategy.getAgentsToColocate(taskInfo);

    OfferRequirement offerRequirement = new OfferRequirement(
        Arrays.asList(taskInfo), executorInfo, avoidAgents, colocateAgents);
    log.info(String.format("Got new OfferRequirement: TaskInfo: '%s' ExecutorInfo: '%s'",
            TextFormat.shortDebugString(taskInfo),
            TextFormat.shortDebugString(executorInfo)));
    return offerRequirement;
  }
}
