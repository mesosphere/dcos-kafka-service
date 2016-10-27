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
import org.apache.mesos.Protos.Value.Range;
import org.apache.mesos.Protos.Value.Ranges;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.offer.*;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PersistentOfferRequirementProvider implements KafkaOfferRequirementProvider {
    private final Log log = LogFactory.getLog(PersistentOfferRequirementProvider.class);

    public static final String CONFIG_ID_KEY = "CONFIG_ID";
    public static final String CONFIG_TARGET_KEY = "target_configuration";
    public static final String BROKER_TASK_TYPE = "broker";

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

        final TaskInfo.Builder replacementTaskInfo;
        try {
            replacementTaskInfo = TaskInfo.newBuilder(TaskUtils.unpackTaskInfo(existingTaskInfo));
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidRequirementException(e);
        }

        replacementTaskInfo.clearExecutor();
        replacementTaskInfo.setTaskId(TaskID.newBuilder().setValue("").build()); // Set later by TaskRequirement

        final ExecutorInfo.Builder replacementExecutor = ExecutorInfo.newBuilder(existingTaskInfo.getExecutor());
        replacementExecutor.setExecutorId(ExecutorID.newBuilder().setValue("").build()); // Set later by ExecutorRequirement

        TaskInfo replaceTaskInfo = replacementTaskInfo.build();
        ExecutorInfo replaceExecutorInfo = replacementExecutor.build();
        OfferRequirement offerRequirement = new OfferRequirement(
                BROKER_TASK_TYPE,
                Arrays.asList(replaceTaskInfo),
                Optional.of(replaceExecutorInfo));

        log.info(String.format("Got replacement OfferRequirement: TaskInfo: '%s' ExecutorInfo: '%s'",
                TextFormat.shortDebugString(replaceTaskInfo),
                TextFormat.shortDebugString(replaceExecutorInfo)));

        return offerRequirement;
    }

    @Override
    public OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo taskInfo)
            throws InvalidRequirementException, ConfigStoreException {
        try {
            taskInfo = TaskUtils.unpackTaskInfo(taskInfo);
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidRequirementException(e);
        }

        KafkaSchedulerConfiguration config = configState.fetch(UUID.fromString(configName));
        BrokerConfiguration brokerConfig = config.getBrokerConfiguration();

        TaskInfo.Builder taskBuilder = TaskInfo.newBuilder(taskInfo);
        taskBuilder = updateConfigTarget(taskBuilder, configName);
        taskBuilder = updateCpu(taskBuilder, brokerConfig);
        taskBuilder = updateMem(taskBuilder, brokerConfig);
        taskBuilder = updateTaskCmd(taskBuilder, config);
        taskBuilder = updatePort(taskBuilder, brokerConfig);
        taskBuilder.setTaskId(TaskID.newBuilder().setValue("").build()); // Set later by TaskRequirement
        taskBuilder.clearExecutor();
        TaskInfo updatedTaskInfo = taskBuilder.build();
        updatedTaskInfo = TaskUtils.setTargetConfiguration(updatedTaskInfo, UUID.fromString(configName));

        ExecutorInfo.Builder updatedExecutor = ExecutorInfo.newBuilder(taskInfo.getExecutor());
        updatedExecutor = updateExecutorCmd(updatedExecutor, config, configName);
        updatedExecutor.setExecutorId(ExecutorID.newBuilder().setValue("").build()); // Set later by ExecutorRequirement

        try {
            ExecutorInfo updateExecutorInfo = updatedExecutor.build();
            OfferRequirement offerRequirement = new OfferRequirement(
                    BROKER_TASK_TYPE,
                    Arrays.asList(updatedTaskInfo),
                    Optional.of(updateExecutorInfo));

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

    private TaskInfo.Builder updateCpu(TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfig) {
        return updateValue(taskBuilder, "cpus", scalar(brokerConfig.getCpus()));
    }

    private TaskInfo.Builder updateMem(TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfig) {
        return updateValue(taskBuilder, "mem", scalar(brokerConfig.getMem()));
    }

    private TaskInfo.Builder updatePort(TaskInfo.Builder taskBuilder, BrokerConfiguration brokerConfiguration) {
        Long port = brokerConfiguration.getPort();
        if (port == 0) {
            port = getDynamicPort();
        }

        return updateValue(taskBuilder, "ports", range(port, port));
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

    private Map<String, String> getUpdatedTaskEnvironment(KafkaSchedulerConfiguration config, Environment oldEnvironment)
            throws ConfigStoreException {
        final KafkaConfiguration kafkaConfiguration = config.getKafkaConfiguration();
        final BrokerConfiguration brokerConfig = config.getBrokerConfiguration();

        Map<String, String> envMap = OfferUtils.fromEnvironmentToMap(oldEnvironment);
        envMap.put("KAFKA_VER_NAME", kafkaConfiguration.getKafkaVerName());
        envMap.put("KAFKA_HEAP_OPTS", getKafkaHeapOpts(brokerConfig.getHeap()));
        Long port = config.getBrokerConfiguration().getPort();
        if (port != 0) {
            envMap.put(KafkaEnvConfigUtils.toEnvName("port"), Long.toString(port));
        }
        return envMap;
    }

    private Map<String, String> getUpdatedExecutorEnvironment(KafkaSchedulerConfiguration config, String configName)
            throws ConfigStoreException {
        final String frameworkName = config.getServiceConfiguration().getName();
        final ZookeeperConfiguration zkConfig = config.getZookeeperConfig();

        Map<String, String> envMap = new HashMap<>();
        envMap.put("JAVA_HOME", "jre1.8.0_91");
        envMap.put("FRAMEWORK_NAME", frameworkName);
        envMap.put(CONFIG_ID_KEY, configName);
        envMap.put("KAFKA_ZOOKEEPER_URI", zkConfig.getKafkaZkUri());
        return envMap;
    }

    private List<CommandInfo.URI> getUpdatedUris(KafkaSchedulerConfiguration config) throws ConfigStoreException {
        BrokerConfiguration brokerConfiguration = config.getBrokerConfiguration();
        ExecutorConfiguration executorConfiguration = config.getExecutorConfiguration();

        List<CommandInfo.URI> uris = new ArrayList<>();
        uris.add(uri(brokerConfiguration.getJavaUri()));
        uris.add(uri(brokerConfiguration.getKafkaUri()));
        uris.add(uri(brokerConfiguration.getOverriderUri()));
        uris.add(uri(executorConfiguration.getExecutorUri()));
        return uris;
    }

    private TaskInfo.Builder updateTaskCmd(TaskInfo.Builder taskBuilder, KafkaSchedulerConfiguration config)
            throws ConfigStoreException {
        final CommandInfo existingCommandInfo = taskBuilder.getCommand();
        final Environment oldEnvironment = existingCommandInfo.getEnvironment();
        final Map<String, String> newEnvMap = OfferUtils.fromEnvironmentToMap(oldEnvironment);
        newEnvMap.putAll(getUpdatedTaskEnvironment(config, taskBuilder.getCommand().getEnvironment()));

        CommandInfo.Builder cmdBuilder = CommandInfo.newBuilder(existingCommandInfo);

        String brokerCmd = getBrokerCmd(config);
        cmdBuilder.setValue(brokerCmd);

        cmdBuilder.setEnvironment(OfferUtils.environment(newEnvMap));
        taskBuilder.setCommand(cmdBuilder.build());
        taskBuilder.clearData();
        return taskBuilder;
    }

    private ExecutorInfo.Builder updateExecutorCmd(
            ExecutorInfo.Builder updatedExecutor,
            KafkaSchedulerConfiguration config,
            String configName) throws ConfigStoreException {

        final CommandInfo existingCommandInfo = updatedExecutor.getCommand();
        final Environment oldEnvironment = existingCommandInfo.getEnvironment();
        final Map<String, String> newEnvMap = OfferUtils.fromEnvironmentToMap(oldEnvironment);
        newEnvMap.putAll(getUpdatedExecutorEnvironment(config, configName));

        CommandInfo.Builder cmdBuilder = CommandInfo.newBuilder(existingCommandInfo);
        cmdBuilder.setEnvironment(OfferUtils.environment(newEnvMap))
                .clearUris()
                .addAllUris(getUpdatedUris(config));

        return updatedExecutor.setCommand(cmdBuilder);
    }

    private static Long getDynamicPort() {
        return 9092 + ThreadLocalRandom.current().nextLong(0, 1000);
    }

    private TaskInfo getNewTaskInfo(KafkaSchedulerConfiguration config, String configName, int brokerId)
            throws IOException, URISyntaxException {

        BrokerConfiguration brokerConfiguration = config.getBrokerConfiguration();
        String brokerName = OfferUtils.brokerIdToTaskName(brokerId);
        String role = config.getServiceConfiguration().getRole();
        String principal = config.getServiceConfiguration().getPrincipal();

        String containerPath = "kafka-volume-" + UUID.randomUUID();
        Long port = config.getBrokerConfiguration().getPort();
        if (port == 0) {
            port = getDynamicPort();
        }

        CommandInfo commandInfo = getNewBrokerCmd(config, brokerId, port, containerPath);

        TaskInfo.Builder taskBuilder =TaskInfo.newBuilder()
                .setName(brokerName)
                .setTaskId(TaskID.newBuilder().setValue("").build()) // Set later by TaskRequirement
                .setSlaveId(SlaveID.newBuilder().setValue("").build()) // Set later
                .setCommand(commandInfo)
                .addResources(ResourceUtils.getDesiredScalar(
                        role,
                        principal,
                        "cpus",
                        config.getBrokerConfiguration().getCpus()))
                .addResources(ResourceUtils.getDesiredScalar(
                        role,
                        principal,
                        "mem",
                        config.getBrokerConfiguration().getMem()))
                .addResources(ResourceUtils.getDesiredRanges(
                        role,
                        principal,
                        "ports",
                        Arrays.asList(
                                Range.newBuilder()
                                        .setBegin(port)
                                        .setEnd(port).build())));

        if (brokerConfiguration.getDiskType().equals("MOUNT")) {
            taskBuilder.addResources(ResourceUtils.getDesiredMountVolume(
                    role,
                    principal,
                    brokerConfiguration.getDisk(),
                    containerPath));
        } else {
            taskBuilder.addResources(ResourceUtils.getDesiredRootVolume(
                    role,
                    principal,
                    brokerConfiguration.getDisk(),
                    containerPath));
        }

        try {
            if (clusterState.getCapabilities().supportsNamedVips()) {
                DiscoveryInfo discoveryInfo = DiscoveryInfo.newBuilder()
                        .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                        .setName(brokerName)
                        .setPorts(Ports.newBuilder()
                                .addPorts(Port.newBuilder()
                                        .setNumber((int) (long) port)
                                        .setProtocol("tcp")
                                        .setLabels(labels("VIP_" + UUID.randomUUID(), "broker:9092")))
                                .build())
                        .build();
                taskBuilder.setDiscovery(discoveryInfo);
            }
        } catch (Exception e) {
            log.error("Error querying for named vip support. Named VIP support will be unavailable.", e);
        }

        KafkaHealthCheckConfiguration healthCheckConfiguration = config.getHealthCheckConfiguration();

        if (healthCheckConfiguration.isHealthCheckEnabled()) {
            taskBuilder.setHealthCheck(HealthCheck.newBuilder()
                    .setDelaySeconds(healthCheckConfiguration.getHealthCheckDelay().getSeconds())
                    .setIntervalSeconds(healthCheckConfiguration.getHealthCheckInterval().getSeconds())
                    .setTimeoutSeconds(healthCheckConfiguration.getHealthCheckTimeout().getSeconds())
                    .setConsecutiveFailures(healthCheckConfiguration.getHealthCheckMaxFailures())
                    .setGracePeriodSeconds(healthCheckConfiguration.getHealthCheckGracePeriod().getSeconds())
                    .setCommand(CommandInfo.newBuilder()
                            .setValue("curl -f localhost:$API_PORT/admin/healthcheck")
                            .build()));
        }

        return TaskUtils.setTargetConfiguration(taskBuilder.build(), UUID.fromString(configName));
    }

    private String getBrokerCmd(KafkaSchedulerConfiguration config) {
        List<String> commands = new ArrayList<>();
        commands.add("export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH"); // find directory that starts with "jre" containing "bin"
        commands.add("$MESOS_SANDBOX/overrider/bin/kafka-config-overrider server $MESOS_SANDBOX/overrider/conf/scheduler.yml");
        commands.add(String.format(
                "exec $MESOS_SANDBOX/%1$s/bin/kafka-server-start.sh "+
                        "$MESOS_SANDBOX/%1$s/config/server.properties ",
                config.getKafkaConfiguration().getKafkaVerName()));
        return Joiner.on(" && ").join(commands);
    }

    private CommandInfo getNewBrokerCmd(KafkaSchedulerConfiguration config, int brokerId, Long port, String containerPath)
            throws ConfigStoreException {
        String brokerCmd = getBrokerCmd(config);
        String brokerName = OfferUtils.brokerIdToTaskName(brokerId);

        Map<String, String> envMap = new HashMap<>();
        envMap.put("TASK_TYPE", KafkaTask.BROKER.name());
        envMap.put("FRAMEWORK_NAME", config.getServiceConfiguration().getName());
        envMap.put("KAFKA_VER_NAME", config.getKafkaConfiguration().getKafkaVerName());
        envMap.put("KAFKA_ZOOKEEPER_URI", config.getKafkaConfiguration().getKafkaZkUri());
        envMap.put(KafkaEnvConfigUtils.toEnvName("zookeeper.connect"), config.getFullKafkaZookeeperPath());
        envMap.put(KafkaEnvConfigUtils.toEnvName("broker.id"), Integer.toString(brokerId));
        envMap.put(KafkaEnvConfigUtils.toEnvName("log.dirs"), containerPath + "/" + brokerName);
        envMap.put(KafkaEnvConfigUtils.toEnvName("listeners"), "PLAINTEXT://:" + port);
        envMap.put(KafkaEnvConfigUtils.toEnvName("port"), Long.toString(port));
        envMap.put("KAFKA_HEAP_OPTS", getKafkaHeapOpts(config.getBrokerConfiguration().getHeap()));
        return CommandInfo.newBuilder()
                .setValue(brokerCmd)
                .setEnvironment(OfferUtils.environment(envMap))
                .build();
    }

    private CommandInfo getNewExecutorCmd(KafkaSchedulerConfiguration config, String configName, int brokerId)
            throws ConfigStoreException {
        BrokerConfiguration brokerConfiguration = config.getBrokerConfiguration();
        ZookeeperConfiguration zookeeperConfiguration = config.getZookeeperConfig();
        ExecutorConfiguration executorConfiguration = config.getExecutorConfiguration();
        String frameworkName = config.getServiceConfiguration().getName();

        final String executorCommand = "./executor/bin/kafka-executor server ./executor/conf/executor.yml";
        Map<String, String> executorEnvMap = new HashMap<>();
        executorEnvMap.put("JAVA_HOME", "jre1.8.0_91");
        executorEnvMap.put("FRAMEWORK_NAME", frameworkName);
        executorEnvMap.put("KAFKA_ZOOKEEPER_URI", zookeeperConfiguration.getKafkaZkUri());
        executorEnvMap.put(KafkaEnvConfigUtils.KAFKA_OVERRIDE_PREFIX + "BROKER_ID", Integer.toString(brokerId));
        executorEnvMap.put(CONFIG_ID_KEY, configName);
        return CommandInfo.newBuilder()
                .setValue(executorCommand)
                .setEnvironment(OfferUtils.environment(executorEnvMap))
                .addUris(uri(brokerConfiguration.getJavaUri()))
                .addUris(uri(brokerConfiguration.getKafkaUri()))
                .addUris(uri(brokerConfiguration.getOverriderUri()))
                .addUris(uri(executorConfiguration.getExecutorUri()))
                .build();
    }

    private ExecutorInfo getNewExecutorInfo(KafkaSchedulerConfiguration config, String configName, int brokerId)
            throws ConfigStoreException {

        ExecutorConfiguration executorConfiguration = config.getExecutorConfiguration();
        String brokerName = OfferUtils.brokerIdToTaskName(brokerId);
        String role = config.getServiceConfiguration().getRole();
        String principal = config.getServiceConfiguration().getPrincipal();

        return ExecutorInfo.newBuilder()
                .setName(brokerName)
                .setExecutorId(ExecutorID.newBuilder().setValue("").build()) // Set later by ExecutorRequirement
                .setFrameworkId(schedulerState.getStateStore().fetchFrameworkId().get())
                .setCommand(getNewExecutorCmd(config, configName, brokerId))
                .addResources(ResourceUtils.getDesiredScalar(role, principal, "cpus", executorConfiguration.getCpus()))
                .addResources(ResourceUtils.getDesiredScalar(role, principal, "mem", executorConfiguration.getMem()))
                .addResources(DynamicPortRequirement.getDesiredDynamicPort("API_PORT", role, principal))
                .build();
    }

    private OfferRequirement getNewOfferRequirementInternal(String configName, int brokerId)
            throws InvalidRequirementException, IOException, URISyntaxException {

        KafkaSchedulerConfiguration config = configState.fetch(UUID.fromString(configName));
        TaskInfo taskInfo = getNewTaskInfo(config, configName, brokerId);
        ExecutorInfo executorInfo = getNewExecutorInfo(config, configName, brokerId);

        Optional<PlacementRuleGenerator> placement = placementStrategyManager.getPlacementStrategy(config, taskInfo);

        OfferRequirement offerRequirement = new OfferRequirement(
                BROKER_TASK_TYPE,
                Arrays.asList(taskInfo),
                Optional.of(executorInfo),
                placement);

        log.info(String.format("Got new OfferRequirement: TaskInfo: '%s' ExecutorInfo: '%s'",
                TextFormat.shortDebugString(taskInfo),
                TextFormat.shortDebugString(executorInfo)));

        return offerRequirement;
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
