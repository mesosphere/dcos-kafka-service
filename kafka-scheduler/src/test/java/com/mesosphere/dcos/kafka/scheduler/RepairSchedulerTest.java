package com.mesosphere.dcos.kafka.scheduler;

import com.google.common.collect.Iterators;
import com.mesosphere.dcos.kafka.config.*;
import com.mesosphere.dcos.kafka.offer.KafkaOfferRequirementProvider;
import com.mesosphere.dcos.kafka.repair.FailureUtils;
import com.mesosphere.dcos.kafka.repair.KafkaFailureMonitor;
import com.mesosphere.dcos.kafka.repair.KafkaRepairOfferRequirementProvider;
import com.mesosphere.dcos.kafka.state.ClusterState;
import com.mesosphere.dcos.kafka.test.KafkaTestUtils;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.RecoveryConfiguration;
import org.apache.mesos.dcos.Capabilities;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.config.ConfigStoreException;
import com.mesosphere.dcos.kafka.offer.PersistentOfferRequirementProvider;
import com.mesosphere.dcos.kafka.offer.PersistentOperationRecorder;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.scheduler.recovery.DefaultRecoveryScheduler;
import org.apache.mesos.scheduler.recovery.TaskFailureListener;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.constrain.TestingLaunchConstrainer;
import org.apache.mesos.scheduler.recovery.constrain.UnconstrainedLaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.state.StateStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.*;

/**
 * This class tests the Kafka RepairScheduler
 */
public class RepairSchedulerTest {
    private static final UUID testTargetConfig = UUID.randomUUID();
    @Mock private FrameworkState frameworkState;
    @Mock private ConfigStore configStore;
    @Mock private KafkaSchedulerConfiguration kafkaSchedulerConfiguration;
    @Mock private StateStore stateStore;
    @Mock private TaskFailureListener failureListener;
    @Mock private KafkaConfigState configState;
    @Mock private SchedulerDriver driver;
    @Mock private ServiceConfiguration serviceConfiguration;
    @Mock private RecoveryConfiguration recoveryConfiguration;
    @Mock private ClusterState clusterState;
    @Captor private ArgumentCaptor<Protos.TaskID> taskIdCaptor;
    @Captor private ArgumentCaptor<Collection<Protos.OfferID>> offerIdCaptor;
    @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationCaptor;

    @Before
    public void beforeEach() throws ConfigStoreException {
        MockitoAnnotations.initMocks(this);
        when(frameworkState.getFrameworkId()).thenReturn(KafkaTestUtils.testFrameworkId);
        when(configState.fetch(UUID.fromString(KafkaTestUtils.testConfigName)))
            .thenReturn(ConfigTestUtils.getTestKafkaSchedulerConfiguration());
        when(serviceConfiguration.getCount()).thenReturn(3);
        when(recoveryConfiguration.isReplacementEnabled()).thenReturn(false);
        when(configState.getConfigStore()).thenReturn(configStore);
        when(configState.fetch(testTargetConfig)).thenReturn(ConfigTestUtils.getTestKafkaSchedulerConfiguration());
        when(configStore.getTargetConfig()).thenReturn(testTargetConfig);
    }

    @Test
    public void testKafkaRepairSchedulerConstruction() throws Exception {
        Assert.assertNotNull(getTestKafkaRepairScheduler());
    }

    @Test
    public void testReplaceNonLastBroker() throws Exception {
        // Test replacement of Broker-0 when expecting 3 Brokers of Ids(0, 1, and 2)
        Protos.TaskInfo replaceTaskInfo = getDummyBrokerTaskInfo(0);
        replaceTaskInfo = FailureUtils.markFailed(replaceTaskInfo);
        List<Protos.TaskInfo> taskInfos = Arrays.asList(
                replaceTaskInfo,
                getDummyBrokerTaskInfo(1),
                getDummyBrokerTaskInfo(2));
        when(stateStore.fetchTasks()).thenReturn(taskInfos);
        when(stateStore.fetchTerminatedTasks()).thenReturn(Arrays.asList(replaceTaskInfo));

        DefaultRecoveryScheduler repairScheduler = getTestKafkaRepairScheduler();
        List<Protos.OfferID> acceptedOfferIds = repairScheduler.resourceOffers(driver, Arrays.asList(getTestOfferSufficientForNewBroker()), null);
        Assert.assertEquals(1, acceptedOfferIds.size());
        Assert.assertEquals(KafkaTestUtils.testOfferId, acceptedOfferIds.get(0).getValue());
        verify(driver, times(1)).acceptOffers(
                offerIdCaptor.capture(),
                operationCaptor.capture(),
                anyObject());

        Assert.assertTrue(offerIdCaptor.getValue().containsAll(acceptedOfferIds));
        int expectedOperationCount = 8;
        Assert.assertEquals(expectedOperationCount, operationCaptor.getValue().size());
        Protos.Offer.Operation launchOperation = Iterators.get(operationCaptor.getValue().iterator(), expectedOperationCount - 1);
        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals("broker-0", launchOperation.getLaunch().getTaskInfos(0).getName());
    }

    @Test
    public void testReplaceNonLastBrokerConstrained() throws Exception {
        // Test replacement of Broker-0 doesn't occur when expecting 3 Brokers of Ids(0, 1, and 2)
        Protos.TaskInfo replaceTaskInfo = getDummyBrokerTaskInfo(0);
        replaceTaskInfo = FailureUtils.markFailed(replaceTaskInfo);
        List<Protos.TaskInfo> taskInfos = Arrays.asList(
                replaceTaskInfo,
                getDummyBrokerTaskInfo(1),
                getDummyBrokerTaskInfo(2));
        when(stateStore.fetchTasks()).thenReturn(taskInfos);
        when(stateStore.fetchTerminatedTasks()).thenReturn(Arrays.asList(replaceTaskInfo));

        DefaultRecoveryScheduler repairScheduler = getTestKafkaRepairScheduler(new TestingLaunchConstrainer(), new KafkaFailureMonitor(recoveryConfiguration));
        List<Protos.OfferID> acceptedOfferIds = repairScheduler.resourceOffers(driver, Arrays.asList(getTestOfferSufficientForNewBroker()), null);
        Assert.assertEquals(0, acceptedOfferIds.size());
    }

    @Test
    public void testReplaceLastBroker() throws Exception {
        // Test replacement of Broker-2 when expecting 3 Brokers of Ids(0, 1, and 2)
        Protos.TaskInfo replaceTaskInfo = getDummyBrokerTaskInfo(2);
        replaceTaskInfo = FailureUtils.markFailed(replaceTaskInfo);
        List<Protos.TaskInfo> taskInfos = Arrays.asList(
                getDummyBrokerTaskInfo(0),
                getDummyBrokerTaskInfo(1),
                replaceTaskInfo);
        when(stateStore.fetchTasks()).thenReturn(taskInfos);
        when(stateStore.fetchTerminatedTasks()).thenReturn(Arrays.asList(replaceTaskInfo));

        DefaultRecoveryScheduler repairScheduler = getTestKafkaRepairScheduler();
        List<Protos.OfferID> acceptedOfferIds = repairScheduler.resourceOffers(driver, Arrays.asList(getTestOfferSufficientForNewBroker()), null);
        Assert.assertEquals(1, acceptedOfferIds.size());
        Assert.assertEquals(KafkaTestUtils.testOfferId, acceptedOfferIds.get(0).getValue());
        verify(driver, times(1)).acceptOffers(
                offerIdCaptor.capture(),
                operationCaptor.capture(),
                anyObject());

        Assert.assertTrue(offerIdCaptor.getValue().containsAll(acceptedOfferIds));
        int expectedOperationCount = 8;
        Assert.assertEquals(expectedOperationCount, operationCaptor.getValue().size());
        Protos.Offer.Operation launchOperation = Iterators.get(operationCaptor.getValue().iterator(), expectedOperationCount - 1);
        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals("broker-2", launchOperation.getLaunch().getTaskInfos(0).getName());
    }

    @Test
    public void testReplaceLastBrokerConstrained() throws Exception {
        // Test replacement of Broker-2 doesn't occur when expecting 3 Brokers of Ids(0, 1, and 2)
        Protos.TaskInfo replaceTaskInfo = getDummyBrokerTaskInfo(2);
        replaceTaskInfo = FailureUtils.markFailed(replaceTaskInfo);
        List<Protos.TaskInfo> taskInfos = Arrays.asList(
                getDummyBrokerTaskInfo(0),
                getDummyBrokerTaskInfo(1),
                replaceTaskInfo);
        when(stateStore.fetchTasks()).thenReturn(taskInfos);
        when(stateStore.fetchTerminatedTasks()).thenReturn(Arrays.asList(replaceTaskInfo));

        DefaultRecoveryScheduler repairScheduler = getTestKafkaRepairScheduler(new TestingLaunchConstrainer(), new KafkaFailureMonitor(recoveryConfiguration));
        List<Protos.OfferID> acceptedOfferIds = repairScheduler.resourceOffers(driver, Arrays.asList(getTestOfferSufficientForNewBroker()), null);
        Assert.assertEquals(0, acceptedOfferIds.size());
    }

    private Protos.TaskInfo getDummyBrokerTaskInfo(Integer id) {
        return Protos.TaskInfo.newBuilder()
                .setName("broker-" + id)
                .setTaskId(Protos.TaskID.newBuilder()
                        .setValue("broker-" + id + "__" + UUID.randomUUID())
                        .build())
                .setSlaveId(Protos.SlaveID.newBuilder()
                        .setValue(KafkaTestUtils.testSlaveId)
                        .build())
                .build();
    }

    private DefaultRecoveryScheduler getTestKafkaRepairScheduler() throws Exception {
        return getTestKafkaRepairScheduler(new UnconstrainedLaunchConstrainer(), new KafkaFailureMonitor(recoveryConfiguration));
    }

    private DefaultRecoveryScheduler getTestKafkaRepairScheduler(LaunchConstrainer constrainer, FailureMonitor monitor) throws Exception {
        return new DefaultRecoveryScheduler(
                stateStore,
                failureListener,
                getTestOfferRequirementProvider(),
                getTestOfferAccepter(),
                constrainer,
                monitor,
                new AtomicReference<>());
    }

    private OfferAccepter getTestOfferAccepter() {
        return new OfferAccepter(Arrays.asList(new PersistentOperationRecorder(frameworkState)));
    }

    private KafkaRepairOfferRequirementProvider getTestOfferRequirementProvider() throws Exception {
        KafkaOfferRequirementProvider kafkaOfferRequirementProvider = new PersistentOfferRequirementProvider(frameworkState, configState, clusterState);
        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsNamedVips()).thenReturn(false);
        when(clusterState.getCapabilities()).thenReturn(capabilities);
        return new KafkaRepairOfferRequirementProvider(kafkaOfferRequirementProvider, configState.getConfigStore());
    }

    public Protos.Offer getTestOfferSufficientForNewBroker() {
        BrokerConfiguration brokerConfiguration = ConfigTestUtils.getTestBrokerConfiguration();
        ExecutorConfiguration executorConfiguration = ConfigTestUtils.getTestExecutorConfiguration();

        Protos.Resource cpu = ResourceUtils.getUnreservedScalar("cpus", brokerConfiguration.getCpus() + executorConfiguration.getCpus());
        Protos.Resource mem = ResourceUtils.getUnreservedScalar("mem", brokerConfiguration.getMem() + executorConfiguration.getMem());
        Protos.Resource disk = ResourceUtils.getUnreservedRootVolume(brokerConfiguration.getDisk() + executorConfiguration.getDisk());
        Protos.Value.Range portRange = Protos.Value.Range.newBuilder()
                .setBegin(brokerConfiguration.getPort())
                .setEnd(brokerConfiguration.getPort())
                .build();
        Protos.Resource ports = ResourceUtils.getUnreservedRanges("ports",  Arrays.asList(portRange));

        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(KafkaTestUtils.testOfferId))
                .setFrameworkId(KafkaTestUtils.testFrameworkId)
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(KafkaTestUtils.testSlaveId))
                .setHostname(KafkaTestUtils.testHostname)
                .addResources(cpu)
                .addResources(mem)
                .addResources(disk)
                .addResources(ports)
                .build();
    }
}
