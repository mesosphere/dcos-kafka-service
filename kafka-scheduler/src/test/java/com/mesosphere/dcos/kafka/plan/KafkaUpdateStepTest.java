package com.mesosphere.dcos.kafka.plan;

import com.mesosphere.dcos.kafka.config.KafkaConfigState;
import com.mesosphere.dcos.kafka.offer.PersistentOfferRequirementProvider;
import com.mesosphere.dcos.kafka.state.ClusterState;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import com.mesosphere.dcos.kafka.test.ConfigTestUtils;
import com.mesosphere.dcos.kafka.test.KafkaTestUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.dcos.Capabilities;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.state.StateStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class tests the KafkaUpdateStep class.
 */
public class KafkaUpdateStepTest {
    @Mock private FrameworkState frameworkState;
    @Mock private KafkaConfigState configState;
    @Mock private ClusterState clusterState;
    @Mock private Capabilities capabilities;
    private PersistentOfferRequirementProvider offerRequirementProvider;
    private KafkaUpdateStep updateStep;

    private static final Protos.Offer.Operation operation = Protos.Offer.Operation.newBuilder()
            .setType(Protos.Offer.Operation.Type.LAUNCH)
            .build();
    private static final Collection<Protos.Offer.Operation> nonEmptyOperations =
            Arrays.asList(operation);

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        StateStore stateStore = mock(StateStore.class);
        when(stateStore.fetchFrameworkId()).thenReturn(Optional.of(KafkaTestUtils.testFrameworkId));
        when(frameworkState.getStateStore()).thenReturn(stateStore);
        when(frameworkState.getTaskStatusForBroker(any())).thenReturn(Optional.empty());
        when(configState.fetch(UUID.fromString(KafkaTestUtils.testConfigName))).thenReturn(
                ConfigTestUtils.getTestKafkaSchedulerConfiguration());
        when(capabilities.supportsNamedVips()).thenReturn(true);
        when(clusterState.getCapabilities()).thenReturn(capabilities);
        offerRequirementProvider = new PersistentOfferRequirementProvider(frameworkState, configState, clusterState);
        updateStep =
                new KafkaUpdateStep(
                        frameworkState,
                        offerRequirementProvider,
                        KafkaTestUtils.testConfigName,
                        0);
    }

    @Test
    public void testKafkaUpdateStepConstruction() {
        Assert.assertNotNull(updateStep);
    }

    @Test
    public void testStart() {
        OfferRequirement offerRequirement = updateStep.start().get();
        Assert.assertNotNull(offerRequirement);
        Assert.assertEquals(1, offerRequirement.getTaskRequirements().size());
        Assert.assertTrue(offerRequirement.getExecutorRequirementOptional().isPresent());
    }

    @Test
    public void testUpdateWhilePending() {
        Assert.assertTrue(updateStep.isPending());
        updateStep.update(getRunningTaskStatus("bad-task-id"));
        Assert.assertTrue(updateStep.isPending());
    }

    @Test
    public void testUpdateUnknownTaskId() {
        Assert.assertTrue(updateStep.isPending());
        updateStep.start();
        updateStep.updateOfferStatus(nonEmptyOperations);
        Assert.assertTrue(updateStep.isInProgress());
        updateStep.update(getRunningTaskStatus("bad-task-id"));
        Assert.assertTrue(updateStep.isInProgress());
    }

    @Test
    public void testReconciliationUpdate() {
        Assert.assertTrue(updateStep.isPending());
        updateStep.start();
        updateStep.updateOfferStatus(nonEmptyOperations);
        Assert.assertTrue(updateStep.isInProgress());
        Protos.TaskID taskId = updateStep.getPendingTaskIds().get(0);
        Protos.TaskStatus reconciliationTaskStatus = getRunningTaskStatus(taskId.getValue());
        reconciliationTaskStatus = Protos.TaskStatus.newBuilder(reconciliationTaskStatus)
                .setReason(Protos.TaskStatus.Reason.REASON_RECONCILIATION)
                .build();
        updateStep.update(reconciliationTaskStatus);
        Assert.assertTrue(updateStep.isInProgress());
    }

    @Test
    public void testUpdateExpectedTaskIdRunning() {
        Assert.assertTrue(updateStep.isPending());
        updateStep.start();
        updateStep.updateOfferStatus(nonEmptyOperations);
        Assert.assertTrue(updateStep.isInProgress());
        Protos.TaskID taskId = updateStep.getPendingTaskIds().get(0);
        updateStep.update(getRunningTaskStatus(taskId.getValue()));
        Assert.assertTrue(updateStep.isComplete());
    }

    @Test
    public void testUpdateExpectedTaskIdTerminated() {
        Assert.assertTrue(updateStep.isPending());
        updateStep.start();
        updateStep.updateOfferStatus(nonEmptyOperations);
        Assert.assertTrue(updateStep.isInProgress());
        Protos.TaskID taskId = updateStep.getPendingTaskIds().get(0);
        updateStep.update(getFailedTaskStatus(taskId.getValue()));
        Assert.assertTrue(updateStep.isPending());
    }

    private Protos.TaskStatus getRunningTaskStatus(String taskId) {
        return getTaskStatus(taskId, Protos.TaskState.TASK_RUNNING);
    }

    private Protos.TaskStatus getFailedTaskStatus(String taskId) {
        return getTaskStatus(taskId, Protos.TaskState.TASK_FAILED);
    }

    private Protos.TaskStatus getTaskStatus(String taskId, Protos.TaskState state) {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(taskId))
                .setState(state)
                .build();
    }
}
