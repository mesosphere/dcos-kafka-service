package com.mesosphere.dcos.kafka.offer;

import com.google.common.annotations.VisibleForTesting;
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
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.offer.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PersistentOfferRequirementProvider implements KafkaOfferRequirementProvider {
    private static final Log log = LogFactory.getLog(PersistentOfferRequirementProvider.class);

    private static final String LOG_DIR_ENV_NAME = KafkaEnvConfigUtils.toEnvName("log.dirs");
    private static final String BROKER_ID_ENV_NAME = KafkaEnvConfigUtils.toEnvName("broker.id");

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
        KafkaSchedulerConfiguration config = configState.fetch(UUID.fromString(configName));
        String containerPath = "kafka-volume-" + getUUID();
        Long port = config.getBrokerConfiguration().getPort();
        if (port == 0) {
            port = getDynamicPort();
        }

        TaskInfo taskInfo = getNewTaskInfo(config, configName, brokerId, containerPath, port);

        ExecutorConfiguration executorConfiguration = config.getExecutorConfiguration();
        String role = config.getServiceConfiguration().getRole();
        String principal = config.getServiceConfiguration().getPrincipal();
        String logdir = containerPath + "/" + OfferUtils.brokerIdToTaskName(brokerId);

        ExecutorInfo executorInfo = ExecutorInfo.newBuilder()
                .setName(OfferUtils.brokerIdToTaskName(brokerId))
                .setExecutorId(ExecutorID.newBuilder().setValue("").build()) // Set later by ExecutorRequirement
                .setFrameworkId(schedulerState.getStateStore().fetchFrameworkId().get())
                .setCommand(getExecutorCmd(config, configName, brokerId, logdir, port))
                .addResources(ResourceUtils.getDesiredScalar(role, principal, "cpus", executorConfiguration.getCpus()))
                .addResources(ResourceUtils.getDesiredScalar(role, principal, "mem", executorConfiguration.getMem()))
                .addResources(DynamicPortRequirement.getDesiredDynamicPort("API_PORT", role, principal))
                .build();

        log.info(String.format("Got new OfferRequirement: TaskInfo: '%s' ExecutorInfo: '%s'",
                TextFormat.shortDebugString(taskInfo),
                TextFormat.shortDebugString(executorInfo)));

        return new OfferRequirement(
                BROKER_TASK_TYPE,
                Arrays.asList(taskInfo),
                Optional.of(executorInfo),
                placementStrategyManager.getPlacementStrategy(config, taskInfo));
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

        TaskInfo replaceTaskInfo = replacementTaskInfo
                .clearExecutor()
                .setTaskId(TaskID.newBuilder().setValue("").build()) // Set later by TaskRequirement
                .build();
        ExecutorInfo replaceExecutorInfo = ExecutorInfo.newBuilder(existingTaskInfo.getExecutor())
                .setExecutorId(ExecutorID.newBuilder().setValue("").build()) // Set later by ExecutorRequirement
                .build();

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

        Value.Builder valueBuilder = Value.newBuilder().setType(Value.Type.SCALAR);
        valueBuilder.getScalarBuilder().setValue(brokerConfig.getCpus());
        taskBuilder = updateValue(taskBuilder, "cpus", valueBuilder.build());

        valueBuilder.getScalarBuilder().setValue(brokerConfig.getMem());
        taskBuilder = updateValue(taskBuilder, "mem", valueBuilder.build());

        taskBuilder.clearData();
        taskBuilder.setCommand(CommandInfo.newBuilder(taskBuilder.getCommand())
                .setValue(getBrokerCmd(config))
                .clearEnvironment() // Clear any task envvars from Kafka 1.1.16-0.1.0.0 and older
                .build());

        Long port = brokerConfig.getPort();
        if (port != 0) {
            // only override previous port if non-random value is specified:
            valueBuilder = Value.newBuilder().setType(Value.Type.RANGES);
            valueBuilder.getRangesBuilder().addRangeBuilder().setBegin(port).setEnd(port);
            taskBuilder = updateValue(taskBuilder, "ports", valueBuilder.build());
        }

        taskBuilder.setTaskId(TaskID.newBuilder().setValue("").build()); // Set later by TaskRequirement

        taskBuilder.clearExecutor();

        // determine our broker id and log dir by searching the prior environment:
        Environment taskEnv = taskInfo.getCommand().getEnvironment();
        Environment executorEnv = taskInfo.getExecutor().getCommand().getEnvironment();
        String brokerIdStr = getEnvVal(taskEnv, executorEnv, BROKER_ID_ENV_NAME);
        String logdir = getEnvVal(taskEnv, executorEnv, LOG_DIR_ENV_NAME);
        if (brokerIdStr == null || logdir == null) {
            String errStr = String.format("Unable to find %s and/or %s in prior environments: executorEnv[%s] taskEnv[%s]",
                    BROKER_ID_ENV_NAME,
                    LOG_DIR_ENV_NAME,
                    TextFormat.shortDebugString(executorEnv),
                    TextFormat.shortDebugString(taskEnv));
            log.error(errStr);
            throw new InvalidRequirementException(errStr);
        }

        TaskInfo updatedTaskInfo = TaskUtils.setTargetConfiguration(taskBuilder.build(), UUID.fromString(configName));
        // Throw away any prior executor command state (except for brokerId and logdir retrieved from prior env):
        ExecutorInfo updatedExecutorInfo = ExecutorInfo.newBuilder(taskInfo.getExecutor())
                .setCommand(getExecutorCmd(config, configName, Integer.valueOf(brokerIdStr), logdir, port))
                .setExecutorId(ExecutorID.newBuilder().setValue("").build()) // Set later by ExecutorRequirement
                .build();

        try {
            OfferRequirement offerRequirement = new OfferRequirement(
                    BROKER_TASK_TYPE,
                    Arrays.asList(updatedTaskInfo),
                    Optional.of(updatedExecutorInfo));

            log.info(String.format("Got updated OfferRequirement: TaskInfo: '%s' ExecutorInfo: '%s'",
                    TextFormat.shortDebugString(updatedTaskInfo),
                    TextFormat.shortDebugString(updatedExecutorInfo)));

            return offerRequirement;
        } catch (InvalidRequirementException e) {
            throw new InvalidRequirementException(String.format(
                    "Failed to create update OfferRequirement with OrigTaskInfo[%s] NewTaskInfo[%s]",
                    taskInfo, updatedTaskInfo), e);
        }
    }

    /**
     * Exposed to allow enforcing consistent behavior in test subclasses.
     */
    @VisibleForTesting
    protected UUID getUUID() {
        return UUID.randomUUID();
    }

    private static TaskInfo.Builder updateValue(TaskInfo.Builder taskBuilder, String name, Value updatedValue) {
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

    private static TaskInfo.Builder updateConfigTarget(TaskInfo.Builder taskBuilder, String configName) {
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

        for (Map.Entry<String, String> entry : labelMap.entrySet()) {
            taskBuilder.getLabelsBuilder().addLabelsBuilder()
                .setKey(entry.getKey())
                .setValue(entry.getValue());
        }
        return taskBuilder;
    }

    private static Long getDynamicPort() {
        return 9092 + ThreadLocalRandom.current().nextLong(0, 1000);
    }

    private TaskInfo getNewTaskInfo(KafkaSchedulerConfiguration config, String configName, int brokerId, String containerPath, long port)
            throws IOException, URISyntaxException {

        BrokerConfiguration brokerConfiguration = config.getBrokerConfiguration();
        String brokerName = OfferUtils.brokerIdToTaskName(brokerId);
        String role = config.getServiceConfiguration().getRole();
        String principal = config.getServiceConfiguration().getPrincipal();

        TaskInfo.Builder taskBuilder = TaskInfo.newBuilder()
                .setName(brokerName)
                .setTaskId(TaskID.newBuilder().setValue("").build()) // Set later by TaskRequirement
                .setSlaveId(SlaveID.newBuilder().setValue("").build()) // Set later
                .setCommand(CommandInfo.newBuilder()
                        .setValue(getBrokerCmd(config))
                        .build())
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
                DiscoveryInfo.Builder discoveryInfoBuilder = taskBuilder.getDiscoveryBuilder()
                        .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                        .setName(brokerName);
                discoveryInfoBuilder.getPortsBuilder().addPortsBuilder()
                        .setNumber((int) port)
                        .setProtocol("tcp")
                        .getLabelsBuilder().addLabelsBuilder()
                                .setKey("VIP_" + getUUID())
                                .setValue("broker:9092");
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

    private static String getBrokerCmd(KafkaSchedulerConfiguration config) {
        return Joiner.on(" && ").join(Arrays.asList(
                "export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jre*/)", // find directory that starts with "jre"
                "export PATH=$JAVA_HOME/bin:$PATH",
                "env",
                "$MESOS_SANDBOX/overrider/bin/kafka-config-overrider server $MESOS_SANDBOX/overrider/conf/scheduler.yml",
                String.format(
                        "exec $MESOS_SANDBOX/%1$s/bin/kafka-server-start.sh "+
                                "$MESOS_SANDBOX/%1$s/config/server.properties ",
                        config.getKafkaConfiguration().getKafkaVerName())));
    }

    private static CommandInfo.Builder getExecutorCmd(
            KafkaSchedulerConfiguration config, String configName, int brokerId, String logdir, long port)
            throws ConfigStoreException {
        BrokerConfiguration brokerConfiguration = config.getBrokerConfiguration();

        Map<String, String> envMap = new HashMap<>();
        envMap.put("TASK_TYPE", KafkaTask.BROKER.name());
        envMap.put("FRAMEWORK_NAME", config.getServiceConfiguration().getName());
        envMap.put("KAFKA_VER_NAME", config.getKafkaConfiguration().getKafkaVerName());
        envMap.put("KAFKA_ZOOKEEPER_URI", config.getKafkaConfiguration().getKafkaZkUri());
        envMap.put("KAFKA_HEAP_OPTS", String.format("-Xms%1$dM -Xmx%1$dM", config.getBrokerConfiguration().getHeap().getSizeMb()));
        envMap.put("KAFKA_JMX_OPTS", KafkaJmxConfigUtils.toJavaOpts(config.getBrokerConfiguration().getJmx()));
        if (config.getBrokerConfiguration().getStatsd().isReady()) {
            envMap.put("STATSD_UDP_HOST", config.getBrokerConfiguration().getStatsd().getHost());
            envMap.put("STATSD_UDP_PORT", config.getBrokerConfiguration().getStatsd().getPortString());
        }
        envMap.put(CONFIG_ID_KEY, configName);
        envMap.put(KafkaEnvConfigUtils.toEnvName("zookeeper.connect"), config.getFullKafkaZookeeperPath());
        envMap.put(BROKER_ID_ENV_NAME, Integer.toString(brokerId));
        envMap.put(LOG_DIR_ENV_NAME, logdir);
        envMap.put(KafkaEnvConfigUtils.toEnvName("listeners"), "PLAINTEXT://:" + port);
        envMap.put(KafkaEnvConfigUtils.toEnvName("port"), Long.toString(port));

        CommandInfo.Builder cmdBuilder = CommandInfo.newBuilder()
                .setValue(Joiner.on(" && ").join(Arrays.asList(
                        "export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jre*/)", // find directory that starts with "jre"
                        "env",
                        "./executor/bin/kafka-executor server ./executor/conf/executor.yml")))
                .setEnvironment(OfferUtils.environment(envMap));

        cmdBuilder.addUrisBuilder().setValue(brokerConfiguration.getJavaUri());
        cmdBuilder.addUrisBuilder().setValue(brokerConfiguration.getKafkaUri());
        cmdBuilder.addUrisBuilder().setValue(brokerConfiguration.getOverriderUri());
        cmdBuilder.addUrisBuilder().setValue(config.getExecutorConfiguration().getExecutorUri());

        return cmdBuilder;
    }

    private static String getEnvVal(Environment taskEnv, Environment executorEnv, String name) {
        for (Environment.Variable var : taskEnv.getVariablesList()) {
            if (var.getName().equals(name)) {
                return var.getValue();
            }
        }
        for (Environment.Variable var : executorEnv.getVariablesList()) {
            if (var.getName().equals(name)) {
                return var.getValue();
            }
        }
        return null;
    }
}
