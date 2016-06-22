package org.apache.mesos.kafka.offer;

import com.mesosphere.dcos.kafka.common.KafkaTask;
import org.apache.mesos.Protos.*;
import org.apache.mesos.kafka.config.*;
import org.apache.mesos.kafka.state.FrameworkStateService;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.ExecutorInfoBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Mockito.when;

public class PersistentOfferRequirementProviderTest {

  private static final String testRole = "test-role";
  private static final String testPrincipal = "test-principal";
  private static final String testResourceId = "test-resource-id";
  private static final String testTaskName = "broker-0";
  private static final TaskID testTaskId = TaskUtils.toTaskId(testTaskName);
  private static final String testSlaveId = "test-slave-id";
  private static final String testConfigName = UUID.randomUUID().toString();
  private static final String testFrameworkName = "test-framework-name";
  private static final String testUser = "test-user";
  private static final String testPlacementStrategy = "test-placement-strategy";
  private static final String testPhaseStrategy = "test-phase-strategy";
  private static final String testDiskType = "test-disk-type";
  private static final String testKafkaUri = "test-kafka-uri";
  private static final String testJavaUri = "test-java-uri";
  private static final String testOverriderUri = "test-overrider-uri";
  private static final Long testPort = 9092L;
  private static final String testExecutorName = "test-executor-name";
  private static final String testExecutorUri = "test-executor-uri";
  private static final String testKafkaVerName = "test-kafka-ver-name";
  private static final String testKafkaSandboxPath = "test-kafka-sandbox-path";
  private static final String testKafkaZkUri = "test-kafka-zk-uri";
  private static final String testKafkaZkAddress = "test-kafka-zk-address";

  @Mock private FrameworkStateService state;
  @Mock private KafkaConfigState configState;
  private KafkaSchedulerConfiguration schedulerConfig;
  private ServiceConfiguration serviceConfig;
  private BrokerConfiguration brokerConfig;
  private KafkaConfiguration kafkaConfig;
  private ExecutorConfiguration executorConfig;

  @Before public void initMocks() {
    MockitoAnnotations.initMocks(this);
    serviceConfig = new ServiceConfiguration(
        1,
        testFrameworkName,
        testUser,
        testPlacementStrategy,
        testPhaseStrategy,
        testRole,
        testPrincipal);
    brokerConfig = new BrokerConfiguration(
        1,
        1000,
        new HeapConfig(500),
        5000,
        testDiskType,
        testKafkaUri,
        testJavaUri,
        testOverriderUri,
        testPort);
    kafkaConfig = new KafkaConfiguration(
        true,
        testKafkaVerName,
        testKafkaSandboxPath,
        testKafkaZkUri,
        testKafkaZkAddress,
        null);
    executorConfig = new ExecutorConfiguration(
        1,
        256,
        0,
        testExecutorUri
    );
    schedulerConfig = new KafkaSchedulerConfiguration(
        serviceConfig,
        brokerConfig,
        kafkaConfig,
        executorConfig);
  }

  @Test
  public void testConstructor() {
    PersistentOfferRequirementProvider provider = new PersistentOfferRequirementProvider(state, configState);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNewRequirement() throws Exception {
    when(configState.fetch(UUID.fromString(testConfigName))).thenReturn(schedulerConfig);
    when(state.getFrameworkId()).thenReturn(FrameworkID.newBuilder().setValue("abcd").build());
    PersistentOfferRequirementProvider provider = new PersistentOfferRequirementProvider(state, configState);
    OfferRequirement req = provider.getNewOfferRequirement(testConfigName, 0);

    TaskInfo taskInfo = req.getTaskRequirements().iterator().next().getTaskInfo();
    Assert.assertEquals(taskInfo.getName(), "broker-0");
    Assert.assertTrue(taskInfo.getTaskId().getValue().contains("broker-0"));
    Assert.assertEquals(taskInfo.getSlaveId().getValue(), "");

    List<Resource> resources = taskInfo.getResourcesList();
    Assert.assertEquals(4, resources.size());

    Resource cpusResource = resources.get(0);
    Assert.assertEquals("cpus", cpusResource.getName());
    Assert.assertEquals(Value.Type.SCALAR, cpusResource.getType());
    Assert.assertEquals(1.0, cpusResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(testRole, cpusResource.getRole());
    Assert.assertEquals(testPrincipal, cpusResource.getReservation().getPrincipal());
    Assert.assertEquals("resource_id", cpusResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals("", cpusResource.getReservation().getLabels().getLabelsList().get(0).getValue());

    Resource memResource = resources.get(1);
    Assert.assertEquals("mem", memResource.getName());
    Assert.assertEquals(Value.Type.SCALAR, memResource.getType());
    Assert.assertEquals(1000.0, memResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(testRole, memResource.getRole());
    Assert.assertEquals(testPrincipal, memResource.getReservation().getPrincipal());
    Assert.assertEquals("resource_id", memResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals("", memResource.getReservation().getLabels().getLabelsList().get(0).getValue());

    Resource portsResource = resources.get(2);
    Assert.assertEquals("ports", portsResource.getName());
    Assert.assertEquals(Value.Type.RANGES, portsResource.getType());
    Assert.assertTrue(portsResource.getRanges().getRangeList().get(0).getBegin() >= 9092);
    Assert.assertTrue(portsResource.getRanges().getRangeList().get(0).getEnd() >= 9092);
    Assert.assertEquals(testRole, portsResource.getRole());
    Assert.assertEquals(testPrincipal, portsResource.getReservation().getPrincipal());
    Assert.assertEquals("resource_id", portsResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals("", portsResource.getReservation().getLabels().getLabelsList().get(0).getValue());

    Resource diskResource = resources.get(3);
    Assert.assertEquals("disk", diskResource.getName());
    Assert.assertEquals(Value.Type.SCALAR, diskResource.getType());
    Assert.assertEquals(5000.0, diskResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(testRole, diskResource.getRole());
    Assert.assertTrue(diskResource.hasDisk());
    Assert.assertTrue(diskResource.getDisk().hasPersistence());
    Assert.assertEquals("", diskResource.getDisk().getPersistence().getId());
    Assert.assertTrue(diskResource.getDisk().hasVolume());
    Assert.assertEquals(Volume.Mode.RW, diskResource.getDisk().getVolume().getMode());
    Assert.assertTrue(diskResource.getDisk().getVolume().getContainerPath().contains("kafka-volume"));
    Assert.assertEquals(testPrincipal, diskResource.getReservation().getPrincipal());
    Assert.assertEquals("resource_id", diskResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals("", diskResource.getReservation().getLabels().getLabelsList().get(0).getValue());

    Labels labels = taskInfo.getLabels();
    Assert.assertEquals("config_target", labels.getLabelsList().get(0).getKey());
    Assert.assertEquals(testConfigName, labels.getLabelsList().get(0).getValue());

    final ExecutorInfo executorInfo = req.getExecutorRequirement().getExecutorInfo();

    CommandInfo cmd = executorInfo.getCommand();
    Assert.assertEquals(4, cmd.getUrisList().size());
    Assert.assertEquals(testJavaUri, cmd.getUrisList().get(0).getValue());
    Assert.assertEquals(testKafkaUri, cmd.getUrisList().get(1).getValue());
    Assert.assertEquals(testOverriderUri, cmd.getUrisList().get(2).getValue());
    Assert.assertEquals(testExecutorUri, cmd.getUrisList().get(3).getValue());

    String portString = String.valueOf(portsResource.getRanges().getRangeList().get(0).getBegin());

    final CommandInfo kafkaTaskData = CommandInfo.parseFrom(taskInfo.getData());
    final Map<String, String> envFromTask = TaskUtils.fromEnvironmentToMap(kafkaTaskData.getEnvironment());

    Assert.assertEquals(11, envFromTask.size());

    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put("KAFKA_OVERRIDE_ZOOKEEPER_CONNECT", testKafkaZkAddress + "/" + testFrameworkName);
    expectedEnvMap.put("FRAMEWORK_NAME", testFrameworkName);
    expectedEnvMap.put("KAFKA_OVERRIDE_LOG_DIRS", "kafka-volume-9a67ba10-644c-4ef2-b764-e7df6e6a66e5/broker-0");
    expectedEnvMap.put("KAFKA_OVERRIDE_LISTENERS", "PLAINTEXT://:123a");
    expectedEnvMap.put("KAFKA_VER_NAME", testKafkaVerName);
    expectedEnvMap.put("CONFIG_ID", testConfigName);
    expectedEnvMap.put("KAFKA_OVERRIDE_PORT", portString);
    expectedEnvMap.put("KAFKA_DYNAMIC_BROKER_PORT", Boolean.toString(false));
    expectedEnvMap.put("KAFKA_OVERRIDE_BROKER_ID", String.valueOf(0));
    expectedEnvMap.put("KAFKA_HEAP_OPTS", "-Xms500M -Xmx500M");
    expectedEnvMap.put("TASK_TYPE", KafkaTask.BROKER.name());
    expectedEnvMap.put("JMX_PORT", "11500");

    System.out.println(expectedEnvMap);

    final Set<String> envVarNames = envFromTask.keySet();
    for (String envVarName:envVarNames) {
      Assert.assertTrue("Cannot find env key: " + envVarName, expectedEnvMap.containsKey(envVarName));

      final String envVarValue = envFromTask.get(envVarName);
      if ("KAFKA_OVERRIDE_LOG_DIRS".equals(envVarName)) {
        Assert.assertTrue(envVarValue.contains("kafka-volume"));
        Assert.assertTrue(envVarValue.contains(testTaskName));
      } else if ("KAFKA_OVERRIDE_LISTENERS".equals(envVarName)) {
        Assert.assertTrue(envVarValue.contains("PLAINTEXT"));
        Assert.assertTrue(envVarValue.contains(portString));
      } else if ("JMX_PORT".equals(envVarName)) {
        final int port = Integer.parseInt(envVarValue);
        Assert.assertTrue(port >= 11000 && port <= 12000);
      } else {
        Assert.assertTrue("Cannot find env value: " + envVarValue, expectedEnvMap.containsValue(envVarValue));
      }
    }

    Assert.assertEquals(286, kafkaTaskData.getValue().length());
    Assert.assertEquals(83, cmd.getValue().length());
  }

  @Test
  public void testUpdateRequirement() throws Exception {
    when(configState.fetch(UUID.fromString(testConfigName))).thenReturn(schedulerConfig);
    Resource oldCpu = ResourceBuilder.reservedCpus(0.5, testRole, testPrincipal, testResourceId);
    Resource oldMem = ResourceBuilder.reservedMem(500, testRole, testPrincipal, testResourceId);
    Resource oldDisk = ResourceBuilder.reservedDisk(2500, testRole, testPrincipal, testResourceId);
    final HeapConfig oldHeapConfig = new HeapConfig(256);

    TaskInfo oldTaskInfo = getTaskInfo(Arrays.asList(oldCpu, oldMem, oldDisk));
    oldTaskInfo = configKafkaHeapOpts(oldTaskInfo, oldHeapConfig);

    PersistentOfferRequirementProvider provider = new PersistentOfferRequirementProvider(state, configState);
    OfferRequirement req = provider.getUpdateOfferRequirement(testConfigName, oldTaskInfo);
    Assert.assertNotNull(req);

    Resource updatedCpus = getResource(req, "cpus");
    Assert.assertEquals("cpus", updatedCpus.getName());
    Assert.assertEquals(1, updatedCpus.getScalar().getValue(), 0.0);

    Resource updatedMem = getResource(req, "mem");
    Assert.assertEquals("mem", updatedMem.getName());
    Assert.assertEquals(1000, updatedMem.getScalar().getValue(), 0.0);

    Resource updatedDisk = getResource(req, "disk");
    Assert.assertEquals("disk", updatedDisk.getName());
    Assert.assertEquals(5000, updatedDisk.getScalar().getValue(), 0.0);

    Assert.assertEquals(1, req.getTaskRequirements().size());
    final TaskInfo taskInfo = req.getTaskRequirements().iterator().next().getTaskInfo();
    final Environment environment = CommandInfo.parseFrom(taskInfo.getData()).getEnvironment();
    List<Environment.Variable> variablesList = environment.getVariablesList();
    List<Environment.Variable> envVariables = new ArrayList<>(variablesList);
    envVariables.sort((v1, v2) -> v1.getName().compareTo(v2.getName()));
    Assert.assertEquals(3, envVariables.size());
    Assert.assertEquals("KAFKA_DYNAMIC_BROKER_PORT", envVariables.get(0).getName());
    Assert.assertEquals(Boolean.toString(false), envVariables.get(0).getValue());
    Assert.assertEquals("KAFKA_HEAP_OPTS", envVariables.get(1).getName());
    Assert.assertEquals("-Xms500M -Xmx500M", envVariables.get(1).getValue());
    Assert.assertEquals("KAFKA_OVERRIDE_PORT", envVariables.get(2).getName());
    Assert.assertEquals("9092", envVariables.get(2).getValue());
  }

  private static Resource getResource(OfferRequirement req, String name) {
    Assert.assertEquals(1, req.getTaskRequirements().size());
    TaskInfo taskInfo = req.getTaskRequirements().iterator().next().getTaskInfo();
    for (Resource resource : taskInfo.getResourcesList()) {
      if (name.equals(resource.getName())) {
        return resource;
      }
    }

    return null;
  }

  private TaskInfo configKafkaHeapOpts(TaskInfo taskInfo, HeapConfig heapConfig) {
    final CommandInfo oldCommand = taskInfo.getCommand();
    final TaskInfo.Builder taskBuilder = TaskInfo.newBuilder(taskInfo);

    final CommandInfo newCommand = CommandInfo.newBuilder(oldCommand)
            .setEnvironment(Environment.newBuilder()
                    .addVariables(Environment.Variable
                            .newBuilder()
                            .setName("KAFKA_HEAP_OPTS")
                            .setValue("-Xms" + heapConfig.getSizeMb() + "M -Xmx" + heapConfig.getSizeMb() + "M")))
            .build();
    taskBuilder.setCommand(newCommand);
    return taskBuilder.build();
  }

  private TaskInfo getTaskInfo(List<Resource> resources) {
    TaskInfoBuilder builder = new TaskInfoBuilder(testTaskId.getValue(), testTaskName, testSlaveId);

    for (Resource resource : resources) {
      builder.addResource(resource);
    }

    final CommandInfo fakeCommand = CommandInfoBuilder.createCmdInfo("/bin/true", Arrays.asList(), Arrays.asList());
    builder.setCommand(fakeCommand);

    builder.setExecutorInfo(ExecutorInfoBuilder.createExecutorInfoBuilder()
            .setCommand(fakeCommand)
            .setName(testExecutorName)
            .setExecutorId(ExecutorID.newBuilder().setValue(""))
            .build());

    return builder.build();
  }
}
