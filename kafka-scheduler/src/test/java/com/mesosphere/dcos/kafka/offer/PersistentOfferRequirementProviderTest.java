package com.mesosphere.dcos.kafka.offer;

import com.mesosphere.dcos.kafka.config.JmxConfig;
import com.mesosphere.dcos.kafka.config.KafkaConfigState;
import com.mesosphere.dcos.kafka.config.KafkaJmxConfigUtils;
import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import com.mesosphere.dcos.kafka.state.ClusterState;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import com.mesosphere.dcos.kafka.test.ConfigTestUtils;
import com.mesosphere.dcos.kafka.test.KafkaTestUtils;
import org.apache.mesos.Protos.*;
import org.apache.mesos.dcos.Capabilities;
import org.apache.mesos.dcos.DcosConstants;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.state.StateStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PersistentOfferRequirementProviderTest {

  private static final String TEST_UUID_STR = UUID.randomUUID().toString();

  @Mock private FrameworkState state;
  @Mock private KafkaConfigState configState;
  @Mock private ClusterState clusterState;
  @Mock private Capabilities capabilities;
  private KafkaSchedulerConfiguration schedulerConfig;

  @Before
  public void beforeEach() throws IOException, URISyntaxException {
    MockitoAnnotations.initMocks(this);
    StateStore stateStore = mock(StateStore.class);
    when(stateStore.fetchFrameworkId()).thenReturn(Optional.of(KafkaTestUtils.testFrameworkId));
    when(state.getStateStore()).thenReturn(stateStore);
    when(capabilities.supportsNamedVips()).thenReturn(true);
    when(clusterState.getCapabilities()).thenReturn(capabilities);
    schedulerConfig = ConfigTestUtils.getTestKafkaSchedulerConfiguration();
  }

  @Test
  public void testConstructor() {
    PersistentOfferRequirementProvider provider = new TestPersistentOfferRequirementProvider();
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNewRequirement() throws Exception {
    when(configState.fetch(UUID.fromString(KafkaTestUtils.testConfigName))).thenReturn(schedulerConfig);
    when(state.getStateStore().fetchFrameworkId()).thenReturn(
            Optional.of(FrameworkID.newBuilder().setValue("abcd").build()));
    PersistentOfferRequirementProvider provider = new TestPersistentOfferRequirementProvider();
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
    Assert.assertEquals(PersistentOfferRequirementProvider.CONFIG_TARGET_KEY, labels.getLabelsList().get(0).getKey());
    Assert.assertEquals(KafkaTestUtils.testConfigName, labels.getLabelsList().get(0).getValue());

    Assert.assertTrue(taskInfo.hasHealthCheck());
    final HealthCheck healthCheck = taskInfo.getHealthCheck();
    Assert.assertEquals(15, healthCheck.getDelaySeconds(), 0.0);
    Assert.assertEquals(10, healthCheck.getIntervalSeconds(), 0.0);
    Assert.assertEquals(20, healthCheck.getTimeoutSeconds(), 0.0);
    Assert.assertEquals(3, healthCheck.getConsecutiveFailures(), 0.0);
    Assert.assertEquals(10, healthCheck.getGracePeriodSeconds(), 0.0);

    final ExecutorInfo executorInfo = req.getExecutorRequirementOptional().get().getExecutorInfo();

    CommandInfo executorCmd = executorInfo.getCommand();
    Assert.assertEquals(4, executorCmd.getUrisList().size());
    Assert.assertEquals(KafkaTestUtils.testJavaUri, executorCmd.getUrisList().get(0).getValue());
    Assert.assertEquals(KafkaTestUtils.testKafkaUri, executorCmd.getUrisList().get(1).getValue());
    Assert.assertEquals(KafkaTestUtils.testOverriderUri, executorCmd.getUrisList().get(2).getValue());
    Assert.assertEquals(KafkaTestUtils.testExecutorUri, executorCmd.getUrisList().get(3).getValue());

    String portString = String.valueOf(portsResource.getRanges().getRangeList().get(0).getBegin());

    // task env should only contain KAFKA_OVERRIDE_*:
    final Map<String, String> envFromTask = TaskUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
    Assert.assertTrue(envFromTask.isEmpty());

    // executor env should contain everything that isnt KAFKA_OVERRIDE_*:
    final Map<String, String> envFromExecutor = TaskUtils.fromEnvironmentToMap(executorCmd.getEnvironment());
    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put("KAFKA_ZOOKEEPER_URI", KafkaTestUtils.testKafkaZkUri);
    expectedEnvMap.put("TASK_TYPE", "BROKER");
    expectedEnvMap.put("KAFKA_HEAP_OPTS", "-Xms500M -Xmx500M");
    expectedEnvMap.put("KAFKA_JMX_OPTS", KafkaJmxConfigUtils.toJavaOpts(
            new JmxConfig(true, KafkaTestUtils.testJMXPort, false, false)));
    expectedEnvMap.put("STATSD_UDP_HOST", KafkaTestUtils.testStatsdHost);
    expectedEnvMap.put("STATSD_UDP_PORT", Integer.toString(KafkaTestUtils.testStatsdPort));
    expectedEnvMap.put("KAFKA_VER_NAME", KafkaTestUtils.testKafkaVerName);
    expectedEnvMap.put("FRAMEWORK_NAME", KafkaTestUtils.testFrameworkName);
    expectedEnvMap.put("CONFIG_ID", KafkaTestUtils.testConfigName);
    expectedEnvMap.put("KAFKA_OVERRIDE_ZOOKEEPER_CONNECT",
            KafkaTestUtils.testKafkaZkUri + DcosConstants.SERVICE_ROOT_PATH_PREFIX + KafkaTestUtils.testFrameworkName);
    expectedEnvMap.put("KAFKA_OVERRIDE_LOG_DIRS", "kafka-volume-" + TEST_UUID_STR + "/broker-0");
    expectedEnvMap.put("KAFKA_OVERRIDE_LISTENERS", "PLAINTEXT://:" + portString);
    expectedEnvMap.put("KAFKA_OVERRIDE_PORT", portString);
    expectedEnvMap.put("KAFKA_OVERRIDE_BROKER_ID", String.valueOf(0));
    Assert.assertEquals(envFromExecutor.toString(), expectedEnvMap, envFromExecutor);

    Assert.assertEquals(325, taskInfo.getCommand().getValue().length());
    Assert.assertEquals(122, executorCmd.getValue().length());
  }

  @Test
  public void testReplaceOfferRequirement() throws Exception {
    PersistentOfferRequirementProvider provider = new TestPersistentOfferRequirementProvider();
    Resource cpu = ResourceUtils.getDesiredScalar(
            KafkaTestUtils.testRole,
            KafkaTestUtils.testPrincipal,
            "cpus",
            0.5);
    TaskInfo inTaskInfo = getTaskInfo(Arrays.asList(cpu));
    OfferRequirement req = provider.getReplacementOfferRequirement(TaskUtils.packTaskInfo(inTaskInfo));
    TaskInfo outTaskInfo = req.getTaskRequirements().iterator().next().getTaskInfo();
    Assert.assertEquals(cpu, outTaskInfo.getResourcesList().get(0));
  }

  @Test
  public void testUpdateRequirement() throws Exception {
    when(configState.fetch(UUID.fromString(KafkaTestUtils.testConfigName))).thenReturn(schedulerConfig);
    Resource oldCpu = ResourceUtils.getDesiredScalar(
            KafkaTestUtils.testRole,
            KafkaTestUtils.testPrincipal,
            "cpus",
            0.5);
    Resource oldMem = ResourceUtils.getDesiredScalar(
            KafkaTestUtils.testRole,
            KafkaTestUtils.testPrincipal,
            "mem",
            500);
    Resource oldDisk = ResourceUtils.getDesiredScalar(
            KafkaTestUtils.testRole,
            KafkaTestUtils.testPrincipal,
            "disk",
            2500);

    TaskInfo.Builder oldTaskInfoBuilder = getTaskInfo(Arrays.asList(oldCpu, oldMem, oldDisk)).toBuilder();
    // one setting in executor:
    oldTaskInfoBuilder.getExecutorBuilder().getCommandBuilder().getEnvironmentBuilder().addVariablesBuilder()
        .setName("KAFKA_OVERRIDE_LOG_DIRS")
        .setValue("oldLogDirs");
    // other setting in task:
    oldTaskInfoBuilder.getCommandBuilder().getEnvironmentBuilder().addVariablesBuilder()
        .setName("KAFKA_OVERRIDE_BROKER_ID")
        .setValue("1234");
    TaskInfo oldTaskInfo = TaskUtils.packTaskInfo(oldTaskInfoBuilder.build());

    PersistentOfferRequirementProvider provider = new TestPersistentOfferRequirementProvider();
    OfferRequirement req = provider.getUpdateOfferRequirement(KafkaTestUtils.testConfigName, oldTaskInfo);
    Assert.assertNotNull(req);

    Resource updatedCpus = getResource(req, "cpus");
    Assert.assertEquals("cpus", updatedCpus.getName());
    Assert.assertEquals(1, updatedCpus.getScalar().getValue(), 0.0);

    Resource updatedMem = getResource(req, "mem");
    Assert.assertEquals("mem", updatedMem.getName());
    Assert.assertEquals(1000, updatedMem.getScalar().getValue(), 0.0);

    Assert.assertEquals(1, req.getTaskRequirements().size());

    // task env should contain nothing:
    TaskInfo taskInfo = TaskUtils.unpackTaskInfo(req.getTaskRequirements().iterator().next().getTaskInfo());
    final Map<String, String> envFromTask = TaskUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
    Assert.assertTrue(envFromTask.toString(), envFromTask.isEmpty());

    // executor env should contain everything that isn't KAFKA_OVERRIDE_*:
    ExecutorInfo executorInfo = req.getExecutorRequirementOptional().get().getExecutorInfo();
    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put("KAFKA_ZOOKEEPER_URI", KafkaTestUtils.testKafkaZkUri);
    expectedEnvMap.put("TASK_TYPE", "BROKER");
    expectedEnvMap.put("KAFKA_HEAP_OPTS", "-Xms500M -Xmx500M");
    expectedEnvMap.put("KAFKA_VER_NAME", KafkaTestUtils.testKafkaVerName);
    expectedEnvMap.put("FRAMEWORK_NAME", KafkaTestUtils.testFrameworkName);
    expectedEnvMap.put("CONFIG_ID", KafkaTestUtils.testConfigName);
    expectedEnvMap.put("KAFKA_OVERRIDE_ZOOKEEPER_CONNECT",
            KafkaTestUtils.testKafkaZkUri + DcosConstants.SERVICE_ROOT_PATH_PREFIX + KafkaTestUtils.testFrameworkName);
    expectedEnvMap.put("KAFKA_OVERRIDE_LOG_DIRS", "oldLogDirs");
    expectedEnvMap.put("KAFKA_OVERRIDE_LISTENERS", "PLAINTEXT://:9092");
    expectedEnvMap.put("KAFKA_OVERRIDE_PORT", "9092");
    expectedEnvMap.put("KAFKA_OVERRIDE_BROKER_ID", "1234");
    expectedEnvMap.put("KAFKA_JMX_OPTS", KafkaJmxConfigUtils.toJavaOpts(
            new JmxConfig(true, KafkaTestUtils.testJMXPort, false, false)));
    expectedEnvMap.put("STATSD_UDP_HOST", KafkaTestUtils.testStatsdHost);
    expectedEnvMap.put("STATSD_UDP_PORT", Integer.toString(KafkaTestUtils.testStatsdPort));
    final Map<String, String> envFromExecutor = TaskUtils.fromEnvironmentToMap(executorInfo.getCommand().getEnvironment());
    Assert.assertEquals(envFromExecutor.toString(), expectedEnvMap, envFromExecutor);
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

  private TaskInfo getTaskInfo(List<Resource> resources) {
    TaskInfo.Builder builder = TaskInfo.newBuilder()
            .setTaskId(KafkaTestUtils.testTaskId)
            .setName(KafkaTestUtils.testTaskName)
            .setSlaveId(SlaveID.newBuilder().setValue(KafkaTestUtils.testSlaveId));

    for (Resource resource : resources) {
      builder.addResources(resource);
    }

    final CommandInfo fakeCommand = CommandInfo.newBuilder().setValue("/bin/true").build();
    builder.setCommand(fakeCommand);

    builder.setExecutor(ExecutorInfo.newBuilder()
            .setName(KafkaTestUtils.testExecutorName)
            .setCommand(fakeCommand)
            .setExecutorId(ExecutorID.newBuilder().setValue(""))
            .build());

    return builder.build();
  }

  private class TestPersistentOfferRequirementProvider extends PersistentOfferRequirementProvider {

    public TestPersistentOfferRequirementProvider() {
      super(state, configState, clusterState);
    }

    @Override
    protected UUID getUUID() {
      return UUID.fromString(TEST_UUID_STR);
    }
  }
}
