package org.apache.mesos.kafka.offer;

import java.util.*;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.config.KafkaSchedulerConfiguration;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.kafka.config.ServiceConfiguration;
import org.apache.mesos.kafka.config.BrokerConfiguration;
import org.apache.mesos.kafka.config.HeapConfig;
import org.apache.mesos.kafka.config.KafkaConfiguration;
import org.apache.mesos.offer.OfferRequirement;

import org.apache.mesos.Protos.*;
import org.apache.mesos.protobuf.*;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

public class PersistentOfferRequirementProviderTest {
  private final Log log = LogFactory.getLog(getClass());

  private final String testRole = "test-role";
  private final String testPrincipal = "test-principal";
  private final String testResourceId = "test-resource-id";
  private final String testTaskName = "broker-0";
  private final String testTaskId = "test-task-id";
  private final String testSlaveId = "test-slave-id";
  private final String testConfigName = "test-config-name";
  private final String testFrameworkName = "test-framework-name";
  private final String testUser = "test-user";
  private final String testPlacementStrategy = "test-placement-strategy";
  private final String testPhaseStrategy = "test-phase-strategy";
  private final String testDiskType = "test-disk-type";
  private final String testKafkaUri = "test-kafka-uri";
  private final String testJavaUri = "test-java-uri";
  private final String testOverriderUri = "test-overrider-uri";
  private final String testKafkaVerName = "test-kafka-ver-name";
  private final String testKafkaSandboxPath = "test-kafka-sandbox-path";
  private final String testKafkaZkUri = "test-kafka-zk-uri";
  private final String testKafkaZkAddress = "test-kafka-zk-address";

  @Mock private KafkaStateService state;
  @Mock private KafkaConfigState configState;
  private KafkaSchedulerConfiguration schedulerConfig;
  private ServiceConfiguration serviceConfig;
  private BrokerConfiguration brokerConfig;
  private KafkaConfiguration kafkaConfig;

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
        testOverriderUri);
    kafkaConfig = new KafkaConfiguration(
        true,
        testKafkaVerName,
        testKafkaSandboxPath,
        testKafkaZkUri,
        testKafkaZkAddress,
        null);
    schedulerConfig = new KafkaSchedulerConfiguration(
        serviceConfig,
        brokerConfig,
        kafkaConfig);
  }

  @Test
  public void testConstructor() {
    PersistentOfferRequirementProvider provider = new PersistentOfferRequirementProvider(state, configState);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNewRequirement() {
    when(configState.fetch(anyString())).thenReturn(schedulerConfig);
    PersistentOfferRequirementProvider provider = new PersistentOfferRequirementProvider(state, configState);
    OfferRequirement req = provider.getNewOfferRequirement(testConfigName, 0);

    TaskInfo taskInfo = req.getTaskInfo();
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
    Assert.assertEquals("test-config-name", labels.getLabelsList().get(0).getValue());

    CommandInfo cmd = taskInfo.getCommand();
    Assert.assertEquals(3, cmd.getUrisList().size());
    Assert.assertEquals(testJavaUri, cmd.getUrisList().get(0).getValue());
    Assert.assertEquals(testKafkaUri, cmd.getUrisList().get(1).getValue());
    Assert.assertEquals(testOverriderUri, cmd.getUrisList().get(2).getValue());

    String portString = String.valueOf(portsResource.getRanges().getRangeList().get(0).getBegin());
    Assert.assertEquals(8, cmd.getEnvironment().getVariablesList().size());
    Assert.assertEquals("KAFKA_OVERRIDE_ZOOKEEPER_CONNECT", cmd.getEnvironment().getVariablesList().get(0).getName());
    Assert.assertEquals(testKafkaZkAddress + "/" + testFrameworkName, cmd.getEnvironment().getVariablesList().get(0).getValue());
    Assert.assertEquals("FRAMEWORK_NAME", cmd.getEnvironment().getVariablesList().get(1).getName());
    Assert.assertEquals(testFrameworkName, cmd.getEnvironment().getVariablesList().get(1).getValue());
    Assert.assertEquals("KAFKA_OVERRIDE_LOG_DIRS", cmd.getEnvironment().getVariablesList().get(2).getName());
    Assert.assertTrue(cmd.getEnvironment().getVariablesList().get(2).getValue().contains("kafka-volume"));
    Assert.assertTrue(cmd.getEnvironment().getVariablesList().get(2).getValue().contains(testTaskName));
    Assert.assertEquals("KAFKA_OVERRIDE_LISTENERS", cmd.getEnvironment().getVariablesList().get(3).getName());
    Assert.assertTrue(cmd.getEnvironment().getVariablesList().get(3).getValue().contains("PLAINTEXT"));
    Assert.assertTrue(cmd.getEnvironment().getVariablesList().get(3).getValue().contains(portString));
    Assert.assertEquals("KAFKA_VER_NAME", cmd.getEnvironment().getVariablesList().get(4).getName());
    Assert.assertEquals(testKafkaVerName, cmd.getEnvironment().getVariablesList().get(4).getValue());
    Assert.assertEquals("CONFIG_ID", cmd.getEnvironment().getVariablesList().get(5).getName());
    Assert.assertEquals(testConfigName, cmd.getEnvironment().getVariablesList().get(5).getValue());
    Assert.assertEquals("KAFKA_OVERRIDE_PORT", cmd.getEnvironment().getVariablesList().get(6).getName());
    Assert.assertEquals(portString, cmd.getEnvironment().getVariablesList().get(6).getValue());
    Assert.assertEquals("KAFKA_OVERRIDE_BROKER_ID", cmd.getEnvironment().getVariablesList().get(7).getName());
    Assert.assertEquals(String.valueOf(0), cmd.getEnvironment().getVariablesList().get(7).getValue());

    Assert.assertEquals(288, cmd.getValue().length());
  }

  @Test
  public void testUpdateRequirement() {
    when(configState.fetch(anyString())).thenReturn(schedulerConfig);
    Resource oldCpu = ResourceBuilder.reservedCpus(0.5, testRole, testPrincipal, testResourceId); 
    Resource oldMem = ResourceBuilder.reservedMem(500, testRole, testPrincipal, testResourceId); 
    Resource oldDisk = ResourceBuilder.reservedDisk(2500, testRole, testPrincipal, testResourceId); 
    TaskInfo oldTaskInfo = getTaskInfo(Arrays.asList(oldCpu, oldMem, oldDisk));

    PersistentOfferRequirementProvider provider = new PersistentOfferRequirementProvider(state, configState);
    OfferRequirement req = provider.getUpdateOfferRequirement(testConfigName, oldTaskInfo);
    Assert.assertNotNull(req);

    Resource updatedCpus = getResource(req.getTaskInfo(), "cpus");
    Assert.assertEquals("cpus", updatedCpus.getName());
    Assert.assertEquals(1, updatedCpus.getScalar().getValue(), 0.0);

    Resource updatedMem = getResource(req.getTaskInfo(), "mem");
    Assert.assertEquals("mem", updatedMem.getName());
    Assert.assertEquals(1000, updatedMem.getScalar().getValue(), 0.0);

    Resource updatedDisk = getResource(req.getTaskInfo(), "disk");
    Assert.assertEquals("disk", updatedDisk.getName());
    Assert.assertEquals(5000, updatedDisk.getScalar().getValue(), 0.0);
  }

  private Resource getResource(TaskInfo taskInfo, String name) {
    for (Resource resource : taskInfo.getResourcesList()) {
      if (name.equals(resource.getName())) {
        return resource;
      }
    }

    return null;
  }
   

  private TaskInfo getTaskInfo(Resource resource) {
    return getTaskInfo(Arrays.asList(resource));
  }

  private TaskInfo getTaskInfo(List<Resource> resources) {
    TaskInfoBuilder builder = new TaskInfoBuilder(testTaskId, testTaskName, testSlaveId);

    for (Resource resource : resources) {
      builder.addResource(resource);
    }

    return builder.build();
  }
}
