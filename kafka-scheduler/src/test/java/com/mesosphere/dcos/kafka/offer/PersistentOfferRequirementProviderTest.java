package com.mesosphere.dcos.kafka.offer;

import com.mesosphere.dcos.kafka.commons.KafkaTask;
import com.mesosphere.dcos.kafka.config.HeapConfig;
import com.mesosphere.dcos.kafka.config.KafkaConfigState;
import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import org.apache.mesos.Protos.*;
import com.mesosphere.dcos.kafka.state.FrameworkState;
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
import com.mesosphere.dcos.kafka.test.KafkaTestUtils;

import java.util.*;

import static org.mockito.Mockito.when;

public class PersistentOfferRequirementProviderTest {

  @Mock private FrameworkState state;
  @Mock private KafkaConfigState configState;
  private KafkaSchedulerConfiguration schedulerConfig;

  @Before
  public void beforeEach() {
    MockitoAnnotations.initMocks(this);
    schedulerConfig = KafkaTestUtils.getTestKafkaSchedulerConfiguration();
  }

  @Test
  public void testConstructor() {
    PersistentOfferRequirementProvider provider = new PersistentOfferRequirementProvider(state, configState);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNewRequirement() throws Exception {
    when(configState.fetch(UUID.fromString(KafkaTestUtils.testConfigName))).thenReturn(schedulerConfig);
    when(state.getFrameworkId()).thenReturn(FrameworkID.newBuilder().setValue("abcd").build());
    PersistentOfferRequirementProvider provider = new PersistentOfferRequirementProvider(state, configState);
    OfferRequirement req = provider.getNewOfferRequirement(KafkaTestUtils.testConfigName, 0);

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
    Assert.assertEquals(KafkaTestUtils.testRole, cpusResource.getRole());
    Assert.assertEquals(KafkaTestUtils.testPrincipal, cpusResource.getReservation().getPrincipal());
    Assert.assertEquals("resource_id", cpusResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals("", cpusResource.getReservation().getLabels().getLabelsList().get(0).getValue());

    Resource memResource = resources.get(1);
    Assert.assertEquals("mem", memResource.getName());
    Assert.assertEquals(Value.Type.SCALAR, memResource.getType());
    Assert.assertEquals(1000.0, memResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(KafkaTestUtils.testRole, memResource.getRole());
    Assert.assertEquals(KafkaTestUtils.testPrincipal, memResource.getReservation().getPrincipal());
    Assert.assertEquals("resource_id", memResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals("", memResource.getReservation().getLabels().getLabelsList().get(0).getValue());

    Resource portsResource = resources.get(2);
    Assert.assertEquals("ports", portsResource.getName());
    Assert.assertEquals(Value.Type.RANGES, portsResource.getType());
    Assert.assertTrue(portsResource.getRanges().getRangeList().get(0).getBegin() >= 9092);
    Assert.assertTrue(portsResource.getRanges().getRangeList().get(0).getEnd() >= 9092);
    Assert.assertEquals(KafkaTestUtils.testRole, portsResource.getRole());
    Assert.assertEquals(KafkaTestUtils.testPrincipal, portsResource.getReservation().getPrincipal());
    Assert.assertEquals("resource_id", portsResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals("", portsResource.getReservation().getLabels().getLabelsList().get(0).getValue());

    Resource diskResource = resources.get(3);
    Assert.assertEquals("disk", diskResource.getName());
    Assert.assertEquals(Value.Type.SCALAR, diskResource.getType());
    Assert.assertEquals(5000.0, diskResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(KafkaTestUtils.testRole, diskResource.getRole());
    Assert.assertTrue(diskResource.hasDisk());
    Assert.assertTrue(diskResource.getDisk().hasPersistence());
    Assert.assertEquals("", diskResource.getDisk().getPersistence().getId());
    Assert.assertTrue(diskResource.getDisk().hasVolume());
    Assert.assertEquals(Volume.Mode.RW, diskResource.getDisk().getVolume().getMode());
    Assert.assertTrue(diskResource.getDisk().getVolume().getContainerPath().contains("kafka-volume"));
    Assert.assertEquals(KafkaTestUtils.testPrincipal, diskResource.getReservation().getPrincipal());
    Assert.assertEquals("resource_id", diskResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals("", diskResource.getReservation().getLabels().getLabelsList().get(0).getValue());

    Labels labels = taskInfo.getLabels();
    Assert.assertEquals("config_target", labels.getLabelsList().get(0).getKey());
    Assert.assertEquals(KafkaTestUtils.testConfigName, labels.getLabelsList().get(0).getValue());

    final ExecutorInfo executorInfo = req.getExecutorRequirement().getExecutorInfo();

    CommandInfo cmd = executorInfo.getCommand();
    Assert.assertEquals(4, cmd.getUrisList().size());
    Assert.assertEquals(KafkaTestUtils.testJavaUri, cmd.getUrisList().get(0).getValue());
    Assert.assertEquals(KafkaTestUtils.testKafkaUri, cmd.getUrisList().get(1).getValue());
    Assert.assertEquals(KafkaTestUtils.testOverriderUri, cmd.getUrisList().get(2).getValue());
    Assert.assertEquals(KafkaTestUtils.testExecutorUri, cmd.getUrisList().get(3).getValue());

    String portString = String.valueOf(portsResource.getRanges().getRangeList().get(0).getBegin());

    final CommandInfo kafkaTaskData = CommandInfo.parseFrom(taskInfo.getData());
    final Map<String, String> envFromTask = TaskUtils.fromEnvironmentToMap(kafkaTaskData.getEnvironment());

    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put("KAFKA_ZOOKEEPER_URI", KafkaTestUtils.testKafkaZkUri);
    expectedEnvMap.put("KAFKA_OVERRIDE_ZOOKEEPER_CONNECT", KafkaTestUtils.testKafkaZkUri + "/" + KafkaTestUtils.testFrameworkName);
    expectedEnvMap.put("FRAMEWORK_NAME", KafkaTestUtils.testFrameworkName);
    expectedEnvMap.put("KAFKA_OVERRIDE_LOG_DIRS", "kafka-volume-9a67ba10-644c-4ef2-b764-e7df6e6a66e5/broker-0");
    expectedEnvMap.put("KAFKA_OVERRIDE_LISTENERS", "PLAINTEXT://:123a");
    expectedEnvMap.put("KAFKA_VER_NAME", KafkaTestUtils.testKafkaVerName);
    expectedEnvMap.put("CONFIG_ID", KafkaTestUtils.testConfigName);
    expectedEnvMap.put("KAFKA_OVERRIDE_PORT", portString);
    expectedEnvMap.put("KAFKA_DYNAMIC_BROKER_PORT", Boolean.toString(false));
    expectedEnvMap.put("KAFKA_OVERRIDE_BROKER_ID", String.valueOf(0));
    expectedEnvMap.put("KAFKA_HEAP_OPTS", "-Xms500M -Xmx500M");
    expectedEnvMap.put("TASK_TYPE", KafkaTask.BROKER.name());

    Assert.assertEquals(expectedEnvMap.size(), envFromTask.size());

    System.out.println(expectedEnvMap);

    for (String expectedEnvKey : expectedEnvMap.keySet()) {
      Assert.assertTrue("Cannot find env key: " + expectedEnvKey, envFromTask.containsKey(expectedEnvKey));

      final String envVarValue = envFromTask.get(expectedEnvKey);
      if ("KAFKA_OVERRIDE_LOG_DIRS".equals(expectedEnvKey)) {
        Assert.assertTrue(envVarValue.contains("kafka-volume"));
        Assert.assertTrue(envVarValue.contains(KafkaTestUtils.testTaskName));
      } else if ("KAFKA_OVERRIDE_LISTENERS".equals(expectedEnvKey)) {
        Assert.assertTrue(envVarValue.contains("PLAINTEXT"));
        Assert.assertTrue(envVarValue.contains(portString));
      } else {
        Assert.assertTrue("Cannot find env value: " + envVarValue, expectedEnvMap.containsValue(envVarValue));
      }
    }

    Assert.assertEquals(286, kafkaTaskData.getValue().length());
    Assert.assertEquals(83, cmd.getValue().length());
  }

  @Test
  public void testReplaceOfferRequirement() throws Exception {
    PersistentOfferRequirementProvider provider = new PersistentOfferRequirementProvider(state, configState);
    Resource cpu = ResourceBuilder.reservedCpus(0.5, KafkaTestUtils.testRole, KafkaTestUtils.testPrincipal, KafkaTestUtils.testResourceId);
    TaskInfo inTaskInfo = getTaskInfo(Arrays.asList(cpu));
    OfferRequirement req = provider.getReplacementOfferRequirement(inTaskInfo);
    TaskInfo outTaskInfo = req.getTaskRequirements().iterator().next().getTaskInfo();
    Assert.assertEquals(cpu, outTaskInfo.getResourcesList().get(0));
  }

  @Test
  public void testUpdateRequirement() throws Exception {
    when(configState.fetch(UUID.fromString(KafkaTestUtils.testConfigName))).thenReturn(schedulerConfig);
    Resource oldCpu = ResourceBuilder.reservedCpus(0.5, KafkaTestUtils.testRole, KafkaTestUtils.testPrincipal, KafkaTestUtils.testResourceId);
    Resource oldMem = ResourceBuilder.reservedMem(500, KafkaTestUtils.testRole, KafkaTestUtils.testPrincipal, KafkaTestUtils.testResourceId);
    Resource oldDisk = ResourceBuilder.reservedDisk(2500, KafkaTestUtils.testRole, KafkaTestUtils.testPrincipal, KafkaTestUtils.testResourceId);
    final HeapConfig oldHeapConfig = new HeapConfig(256);

    TaskInfo oldTaskInfo = getTaskInfo(Arrays.asList(oldCpu, oldMem, oldDisk));
    oldTaskInfo = configKafkaHeapOpts(oldTaskInfo, oldHeapConfig);

    PersistentOfferRequirementProvider provider = new PersistentOfferRequirementProvider(state, configState);
    OfferRequirement req = provider.getUpdateOfferRequirement(KafkaTestUtils.testConfigName, oldTaskInfo);
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
    TaskInfoBuilder builder = new TaskInfoBuilder(
            KafkaTestUtils.testTaskId.getValue(),
            KafkaTestUtils.testTaskName,
            KafkaTestUtils.testSlaveId);

    for (Resource resource : resources) {
      builder.addResource(resource);
    }

    final CommandInfo fakeCommand = CommandInfoBuilder.createCmdInfo("/bin/true", Arrays.asList(), Arrays.asList());
    builder.setCommand(fakeCommand);

    builder.setExecutorInfo(ExecutorInfoBuilder.createExecutorInfoBuilder()
            .setName(KafkaTestUtils.testExecutorName)
            .setCommand(fakeCommand)
            .setExecutorId(ExecutorID.newBuilder().setValue(""))
            .build());

    return builder.build();
  }
}
