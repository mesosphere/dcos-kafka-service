package com.mesosphere.dcos.kafka.offer;

import com.google.common.base.Joiner;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.mesosphere.dcos.kafka.commons.KafkaTask;
import com.mesosphere.dcos.kafka.config.*;
import com.mesosphere.dcos.kafka.state.ClusterState;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Environment.Variable;
import org.apache.mesos.Protos.Value.Range;
import org.apache.mesos.Protos.Value.Ranges;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.PlacementStrategy;
import org.apache.mesos.offer.ResourceUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class PersistentOfferRequirementProvider implements KafkaOfferRequirementProvider {
  private final Log log = LogFactory.getLog(PersistentOfferRequirementProvider.class);

  public static final String CONFIG_ID_KEY = "CONFIG_ID";
  public static final String CONFIG_TARGET_KEY = "config_target";

  private final KafkaConfigState configState;
  private final FrameworkState schedulerState;
  private final ClusterState clusterState;
  private final PlacementStrategyManager placementStrategyManager;

  public PersistentOfferRequirementProvider(
      FrameworkState schedulerState,
      KafkaConfigState configState,
      ClusterState clusterState) {
    this.configState = configState;
    this.schedulerState = schedulerState;
    this.clusterState = clusterState;
    this.placementStrategyManager = new PlacementStrategyManager(schedulerState);
  }

  @Override
  public OfferRequirement getNewOfferRequirement(String configName, int brokerId)
          throws InvalidRequirementException, IOException, URISyntaxException {
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
            Optional.of(replaceExecutorInfo),
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

    String portVar = KafkaEnvConfigUtils.toEnvName("port");
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

    cmdBuilder.setEnvironment(environment(environmentMap));
    taskBuilder.setData(cmdBuilder.build().toByteString());

    TaskInfo updatedTaskInfo = taskBuilder.build();

    try {
      ExecutorInfo updateExecutorInfo = updatedExecutor.build();
      OfferRequirement offerRequirement = new OfferRequirement(
              Arrays.asList(updatedTaskInfo),
              Optional.of(updateExecutorInfo),
              null,
              null);

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
      newCommandBuilder.setEnvironment(environment(newEnvMap));

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
    return updateValue(taskBuilder, "cpus", scalar(brokerConfig.getCpus()));
  }

  private TaskInfo.Builder updateMem(TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfig) {
    return updateValue(taskBuilder, "mem", scalar(brokerConfig.getMem()));
  }

  private TaskInfo.Builder updateDisk(TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfig) {
    return updateValue(taskBuilder, "disk", scalar(brokerConfig.getDisk()));
  }

  private TaskInfo.Builder updatePort(TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfig) {
    return updateValue(taskBuilder, "ports", range(brokerConfig.getPort(), brokerConfig.getPort()));
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
    Map<String, String> labelMap = new HashMap<>();

    // Copy everything except config target label
    for (Label label : taskBuilder.getLabels().getLabelsList()) {
      String key = label.getKey();
      String value = label.getValue();

      if (!key.equals(CONFIG_TARGET_KEY)) {
        labelMap.put(key, value);
      }
    }

    labelMap.put(CONFIG_TARGET_KEY, configName);
    taskBuilder.setLabels(labels(labelMap));
    return taskBuilder;
  }

  private TaskInfo.Builder updateCmd(TaskInfo.Builder taskBuilder, String configName)
          throws InvalidRequirementException {
    Map<String, String> envMap = new HashMap<>();

    try {
      final CommandInfo existingCommandInfo = CommandInfo.parseFrom(taskBuilder.getData());
      for (Variable variable : existingCommandInfo.getEnvironment().getVariablesList()) {
        if (variable.getName().equals(CONFIG_ID_KEY)) {
          envMap.put(CONFIG_ID_KEY, configName);
        } else {
          envMap.put(variable.getName(), variable.getValue());
        }
      }

      CommandInfo.Builder cmdBuilder = CommandInfo.newBuilder(existingCommandInfo);
      cmdBuilder.setEnvironment(environment(envMap));

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
          throws InvalidRequirementException, IOException, URISyntaxException {
    log.info("Getting new OfferRequirement for: " + configName);
    String brokerName = OfferUtils.brokerIdToTaskName(brokerId);

    String containerPath = "kafka-volume-" + UUID.randomUUID();

    KafkaSchedulerConfiguration config = configState.fetch(UUID.fromString(configName));
    log.warn("KafkaSchedulerConfiguration: " + config);
    BrokerConfiguration brokerConfig = config.getBrokerConfiguration();
    ExecutorConfiguration executorConfig = config.getExecutorConfiguration();
    ZookeeperConfiguration zkConfig = config.getZookeeperConfig();
    KafkaHealthCheckConfiguration healthCheckConfiguration = config.getHealthCheckConfiguration();

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

    Map<String, String> envMap = new HashMap<>();
    envMap.put("TASK_TYPE", KafkaTask.BROKER.name());
    envMap.put("FRAMEWORK_NAME", frameworkName);
    envMap.put("KAFKA_VER_NAME", kafkaConfiguration.getKafkaVerName());
    envMap.put("KAFKA_ZOOKEEPER_URI", zkConfig.getKafkaZkUri());
    envMap.put(CONFIG_ID_KEY, configName);
    envMap.put(KafkaEnvConfigUtils.toEnvName("zookeeper.connect"), config.getFullKafkaZookeeperPath());
    envMap.put(KafkaEnvConfigUtils.toEnvName("broker.id"), Integer.toString(brokerId));
    envMap.put(KafkaEnvConfigUtils.toEnvName("log.dirs"), containerPath + "/" + brokerName);
    envMap.put(KafkaEnvConfigUtils.toEnvName("listeners"), "PLAINTEXT://:" + port);
    envMap.put(KafkaEnvConfigUtils.toEnvName("port"), Long.toString(port));
    envMap.put("KAFKA_DYNAMIC_BROKER_PORT", Boolean.toString(isDynamicPort));
    envMap.put("KAFKA_HEAP_OPTS", getKafkaHeapOpts(brokerConfig.getHeap()));
    CommandInfo brokerTask = CommandInfo.newBuilder()
            .setValue(kafkaLaunchCommand)
            .setEnvironment(environment(envMap))
            .build();

    // Launch command for custom executor
    final String executorCommand = "./executor/bin/kafka-executor server ./executor/conf/executor.yml";
    Long adminPort = getDynamicPort();
    Map<String, String> executorEnvMap = new HashMap<>();
    executorEnvMap.put("JAVA_HOME", "jre1.8.0_91");
    executorEnvMap.put("FRAMEWORK_NAME", frameworkName);
    executorEnvMap.put("API_PORT", String.valueOf(adminPort));
    executorEnvMap.put("KAFKA_ZOOKEEPER_URI", zkConfig.getKafkaZkUri());
    executorEnvMap.put(KafkaEnvConfigUtils.KAFKA_OVERRIDE_PREFIX + "BROKER_ID", Integer.toString(brokerId));
    CommandInfo executorCommandBuilder = CommandInfo.newBuilder()
            .setValue(executorCommand)
            .setEnvironment(environment(executorEnvMap))
            .addUris(uri(brokerConfig.getJavaUri()))
            .addUris(uri(brokerConfig.getKafkaUri()))
            .addUris(uri(brokerConfig.getOverriderUri()))
            .addUris(uri(executorConfig.getExecutorUri()))
            .build();

    // Build Executor
    final ExecutorInfo.Builder executorBuilder = ExecutorInfo.newBuilder();

    executorBuilder
      .setName(brokerName)
      .setExecutorId(ExecutorID.newBuilder().setValue("").build()) // Set later by ExecutorRequirement
      .setFrameworkId(schedulerState.getStateStore().fetchFrameworkId().get())
      .setCommand(executorCommandBuilder)
      .addResources(ResourceUtils.getDesiredScalar(role, principal, "cpus", executorConfig.getCpus()))
      .addResources(ResourceUtils.getDesiredScalar(role, principal, "mem", executorConfig.getMem()))
      .addResources(ResourceUtils.getDesiredRanges(
            role,
            principal,
            "ports",
            Arrays.asList(
              Range.newBuilder()
                .setBegin(adminPort)
                .setEnd(adminPort).build())));

    // Build Task
    TaskInfo.Builder taskBuilder = TaskInfo.newBuilder();
    taskBuilder
      .setName(brokerName)
      .setTaskId(TaskID.newBuilder().setValue("").build()) // Set later by TaskRequirement
      .setSlaveId(SlaveID.newBuilder().setValue("").build()) // Set later
      .setData(brokerTask.toByteString())
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

    taskBuilder.setLabels(labels(CONFIG_TARGET_KEY, configName));

    if (clusterState.getCapabilities().supportsNamedVips()) {
      DiscoveryInfo discoveryInfo = DiscoveryInfo.newBuilder()
              .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
              .setName(brokerName)
              .setPorts(Ports.newBuilder()
                      .addPorts(Port.newBuilder()
                              .setNumber((int) (long)port)
                              .setProtocol("tcp")
                              .setLabels(labels("VIP_" + UUID.randomUUID(), "broker:9092")))
                      .build())
              .build();
      taskBuilder.setDiscovery(discoveryInfo);
    }

    if (healthCheckConfiguration.isHealthCheckEnabled()) {
      taskBuilder.setHealthCheck(HealthCheck.newBuilder()
              .setDelaySeconds(healthCheckConfiguration.getHealthCheckDelay().getSeconds())
              .setIntervalSeconds(healthCheckConfiguration.getHealthCheckInterval().getSeconds())
              .setTimeoutSeconds(healthCheckConfiguration.getHealthCheckTimeout().getSeconds())
              .setConsecutiveFailures(healthCheckConfiguration.getHealthCheckMaxFailures())
              .setGracePeriodSeconds(healthCheckConfiguration.getHealthCheckGracePeriod().getSeconds())
              .setCommand(CommandInfo.newBuilder()
                      .setEnvironment(Environment.newBuilder()
                              .addVariables(Variable.newBuilder()
                                      .setName("API_PORT")
                                      .setValue(String.valueOf(adminPort))))
                      .setValue("curl -f localhost:$API_PORT/admin/healthcheck")
                      .build()));
    }

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
            Arrays.asList(taskInfo),
            Optional.of(executorInfo),
            avoidAgents,
            colocateAgents);
    log.info(String.format("Got new OfferRequirement: TaskInfo: '%s' ExecutorInfo: '%s'",
            TextFormat.shortDebugString(taskInfo),
            TextFormat.shortDebugString(executorInfo)));
    return offerRequirement;
  }

  private Environment environment(Map<String, String> map) {
    Collection<Variable> vars = map
            .entrySet()
            .stream()
            .map(entrySet -> Variable.newBuilder().setName(entrySet.getKey()).setValue(entrySet.getValue()).build())
            .collect(Collectors.toList());

    return Environment.newBuilder().addAllVariables(vars).build();
  }

  private Value scalar(double d) {
    return Value.newBuilder().setType(Value.Type.SCALAR)
            .setScalar(Value.Scalar.newBuilder().setValue(d))
            .build();
  }

  private Value range(long begin, long end) {
    Range range = Range.newBuilder().setBegin(begin).setEnd(end).build();
    Ranges ranges = Ranges.newBuilder().addRange(range).build();
    return Value.newBuilder().setType(Value.Type.RANGES)
            .setRanges(ranges)
            .build();
  }

  private Label label(String key, String value) {
    return Label.newBuilder().setKey(key).setValue(value).build();
  }

  private Labels labels(String key, String value) {
    return Labels.newBuilder().addLabels(label(key, value)).build();
  }

  private Labels labels(Map<String, String> map) {
    Labels.Builder labels = Labels.newBuilder();
    for (String key : map.keySet()) {
      labels.addLabels(label(key, map.get(key)));
    }
    return labels.build();
  }

  private CommandInfo.URI uri(String uri) {
    return CommandInfo.URI.newBuilder().setValue(uri).build();
  }
}
