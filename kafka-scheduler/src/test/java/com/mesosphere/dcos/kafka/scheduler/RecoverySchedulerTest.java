package com.mesosphere.dcos.kafka.scheduler;

import com.google.common.collect.Iterators;
import com.mesosphere.dcos.kafka.config.*;
import com.mesosphere.dcos.kafka.offer.KafkaOfferRequirementProvider;
import com.mesosphere.dcos.kafka.offer.PersistentOfferRequirementProvider;
import com.mesosphere.dcos.kafka.offer.PersistentOperationRecorder;
import com.mesosphere.dcos.kafka.repair.FailureUtils;
import com.mesosphere.dcos.kafka.repair.KafkaFailureMonitor;
import com.mesosphere.dcos.kafka.repair.KafkaRecoveryRequirementProvider;
import com.mesosphere.dcos.kafka.state.ClusterState;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import com.mesosphere.dcos.kafka.test.ConfigTestUtils;
import com.mesosphere.dcos.kafka.test.KafkaTestUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.RecoveryConfiguration;
import org.apache.mesos.dcos.Capabilities;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.scheduler.DefaultTaskKiller;
import org.apache.mesos.scheduler.plan.DefaultPlanScheduler;
import org.apache.mesos.scheduler.recovery.DefaultRecoveryPlanManager;
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

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.*;

//TODO(Mehmet): this file is really not testing anything specific Kafka,
/**
 * This class tests the Kafka RecoveryScheduler
 */
public class RecoverySchedulerTest {
    private static final UUID testTargetConfig = UUID.randomUUID();
    @Mock private FrameworkState schedulerState;
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
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        StateStore stateStore = mock(StateStore.class);
        when(stateStore.fetchFrameworkId()).thenReturn(Optional.of(KafkaTestUtils.testFrameworkId));
        when(schedulerState.getStateStore()).thenReturn(stateStore);
        when(configState.fetch(UUID.fromString(KafkaTestUtils.testConfigName)))
            .thenReturn(ConfigTestUtils.getTestKafkaSchedulerConfiguration());
        when(serviceConfiguration.getCount()).thenReturn(3);
        when(recoveryConfiguration.isReplacementEnabled()).thenReturn(false);
        when(configState.getConfigStore()).thenReturn(configStore);
        when(configState.fetch(testTargetConfig)).thenReturn(ConfigTestUtils.getTestKafkaSchedulerConfiguration());
        when(configStore.getTargetConfig()).thenReturn(testTargetConfig);
    }

    @Test
    public void testKafkaRecoverySchedulerConstruction() throws Exception {
        Assert.assertNotNull(getTestKafkaRecoveryPlanManager());
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
        List<Protos.TaskStatus> statuses = getTestKafkaStatuses(replaceTaskInfo,taskInfos);
        when(stateStore.fetchTasks()).thenReturn(taskInfos);
        when(stateStore.fetchStatuses()).thenReturn(statuses);
        when(stateStore.fetchTask(any())).thenReturn(Optional.of(replaceTaskInfo));
        when(stateStore.fetchStatus(any())).thenReturn(Optional.of(statuses.get(0)));

        DefaultRecoveryPlanManager recoveryPlanManager = getTestKafkaRecoveryPlanManager();
        DefaultPlanScheduler planScheduler = getTestKafkaScheduler();

        List<Protos.OfferID> acceptedOfferIds = new ArrayList<>(
                planScheduler.resourceOffers(
                      driver,
                      Arrays.asList(getTestOfferSufficientForNewBroker()),
                      recoveryPlanManager.getCandidates(Collections.emptyList()))
        );

        Assert.assertEquals(1, acceptedOfferIds.size());
        Assert.assertEquals(KafkaTestUtils.testOfferId, acceptedOfferIds.get(0).getValue());
        verify(driver, times(1)).acceptOffers(
                offerIdCaptor.capture(),
                operationCaptor.capture(),
                anyObject());

        Assert.assertTrue(offerIdCaptor.getValue().containsAll(acceptedOfferIds));
        int expectedOperationCount = 9;
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
        List<Protos.TaskStatus> statuses = getTestKafkaStatuses(replaceTaskInfo,taskInfos);
        when(stateStore.fetchTasks()).thenReturn(taskInfos);
        when(stateStore.fetchStatuses()).thenReturn(statuses);
        when(stateStore.fetchTask(any())).thenReturn(Optional.of(replaceTaskInfo));
        when(stateStore.fetchStatus(any())).thenReturn(Optional.of(statuses.get(0)));

        DefaultRecoveryPlanManager recoveryPlanManager = getTestKafkaRecoveryPlanManager(
                new TestingLaunchConstrainer(), new KafkaFailureMonitor(recoveryConfiguration));
        DefaultPlanScheduler planScheduler = getTestKafkaScheduler();

        List<Protos.OfferID> acceptedOfferIds = new ArrayList<>(
                planScheduler.resourceOffers(
                        driver,
                        Arrays.asList(getTestOfferSufficientForNewBroker()),
                        recoveryPlanManager.getCandidates(Collections.emptyList()))
        );
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
        List<Protos.TaskStatus> statuses = getTestKafkaStatuses(replaceTaskInfo,taskInfos);
        when(stateStore.fetchTasks()).thenReturn(taskInfos);
        when(stateStore.fetchStatuses()).thenReturn(statuses);
        when(stateStore.fetchTask(any())).thenReturn(Optional.of(replaceTaskInfo));
        when(stateStore.fetchStatus(any())).thenReturn(Optional.of(statuses.get(0)));

        DefaultRecoveryPlanManager recoveryPlanManager = getTestKafkaRecoveryPlanManager();
        DefaultPlanScheduler planScheduler = getTestKafkaScheduler();

        List<Protos.OfferID> acceptedOfferIds = new ArrayList<>(
                planScheduler.resourceOffers(
                        driver,
                        Arrays.asList(getTestOfferSufficientForNewBroker()),
                        recoveryPlanManager.getCandidates(Collections.emptyList()))
        );
        Assert.assertEquals(1, acceptedOfferIds.size());
        Assert.assertEquals(KafkaTestUtils.testOfferId, acceptedOfferIds.get(0).getValue());
        verify(driver, times(1)).acceptOffers(
                offerIdCaptor.capture(),
                operationCaptor.capture(),
                anyObject());

        Assert.assertTrue(offerIdCaptor.getValue().containsAll(acceptedOfferIds));
        int expectedOperationCount = 9;
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
        List<Protos.TaskStatus> statuses = getTestKafkaStatuses(replaceTaskInfo,taskInfos);
        when(stateStore.fetchTasks()).thenReturn(taskInfos);
        when(stateStore.fetchStatuses()).thenReturn(statuses);
        when(stateStore.fetchTask(any())).thenReturn(Optional.of(replaceTaskInfo));
        when(stateStore.fetchStatus(any())).thenReturn(Optional.of(statuses.get(2)));

        DefaultRecoveryPlanManager recoveryPlanManager = getTestKafkaRecoveryPlanManager(
                new TestingLaunchConstrainer(),
                new KafkaFailureMonitor(recoveryConfiguration));
        DefaultPlanScheduler planScheduler = getTestKafkaScheduler();

        List<Protos.OfferID> acceptedOfferIds = new ArrayList<>(
                planScheduler.resourceOffers(
                        driver,
                        Arrays.asList(getTestOfferSufficientForNewBroker()),
                        recoveryPlanManager.getCandidates(Collections.emptyList()))
        );
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

    private List<Protos.TaskStatus> getTestKafkaStatuses(Protos.TaskInfo failedTask,
                                    Collection<Protos.TaskInfo> allTasks) {
        List<Protos.TaskStatus> statuses = new ArrayList<>();
        for (Protos.TaskInfo task : allTasks) {
            if (task.getTaskId().equals(failedTask.getTaskId())) {
                statuses.add(getDummyBrokerTaskStatusFail(failedTask));
            } else {
                statuses.add(getDummyBrokerTaskStatus(task));
            }
        }
        return statuses;
    }

    private Protos.TaskStatus getDummyBrokerTaskStatus(Protos.TaskInfo taskInfo) {
        return Protos.TaskStatus
                    .newBuilder().setState(Protos.TaskState.TASK_STAGING).setTaskId(taskInfo.getTaskId()).build();
    }

    private Protos.TaskStatus getDummyBrokerTaskStatusFail(Protos.TaskInfo taskInfo) {
         return Protos.TaskStatus.newBuilder()
                 .setTaskId(taskInfo.getTaskId())
                 .setState(Protos.TaskState.TASK_FAILED).build();
    }

    private DefaultPlanScheduler getTestKafkaScheduler(){
        return new DefaultPlanScheduler(
                getTestOfferAccepter(),
                new OfferEvaluator(stateStore),
                new DefaultTaskKiller(stateStore,failureListener,driver));
    }

    private DefaultRecoveryPlanManager getTestKafkaRecoveryPlanManager() throws Exception {
        return getTestKafkaRecoveryPlanManager(
                new UnconstrainedLaunchConstrainer(),
                new KafkaFailureMonitor(recoveryConfiguration));
    }

    private DefaultRecoveryPlanManager getTestKafkaRecoveryPlanManager(
                                             LaunchConstrainer constrainer,
                                             FailureMonitor monitor) throws Exception {
        return new DefaultRecoveryPlanManager(stateStore, getTestOfferRequirementProvider(),
                    constrainer,
                    monitor);
    }

    private OfferAccepter getTestOfferAccepter() {
        return new OfferAccepter(Arrays.asList(new PersistentOperationRecorder(schedulerState)));
    }

    private KafkaRecoveryRequirementProvider getTestOfferRequirementProvider() throws Exception {
        KafkaOfferRequirementProvider kafkaOfferRequirementProvider = new PersistentOfferRequirementProvider(schedulerState, configState, clusterState);
        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsNamedVips()).thenReturn(false);
        when(clusterState.getCapabilities()).thenReturn(capabilities);
        return new KafkaRecoveryRequirementProvider(kafkaOfferRequirementProvider, configState.getConfigStore());
    }

    public Protos.Offer getTestOfferSufficientForNewBroker() {
        BrokerConfiguration brokerConfiguration = ConfigTestUtils.getTestBrokerConfiguration();
        ExecutorConfiguration executorConfiguration = ConfigTestUtils.getTestExecutorConfiguration();

        Protos.Resource cpu = ResourceUtils.getUnreservedScalar("cpus", brokerConfiguration.getCpus() + executorConfiguration.getCpus());
        Protos.Resource mem = ResourceUtils.getUnreservedScalar("mem", brokerConfiguration.getMem() + executorConfiguration.getMem());
        Protos.Resource disk = ResourceUtils.getUnreservedRootVolume(brokerConfiguration.getDisk() + executorConfiguration.getDisk());
        Protos.Value.Range portRange = Protos.Value.Range.newBuilder()
                .setBegin(0)
                .setEnd(65536)
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
