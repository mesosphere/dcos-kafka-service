package org.apache.mesos.kafka.plan;

import org.apache.mesos.kafka.config.KafkaSchedulerConfiguration;
import org.apache.mesos.kafka.config.ServiceConfiguration;
import org.apache.mesos.kafka.offer.PersistentOfferRequirementProvider;
import org.apache.mesos.kafka.state.FrameworkState;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.plan.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

/**
 * This tests the construction of Kafka Stages.
 */
public class KafkaStageTest {
    @Mock KafkaSchedulerConfiguration schedulerConfiguration;
    @Mock ServiceConfiguration serviceConfiguration;
    @Mock FrameworkState frameworkState;
    @Mock PersistentOfferRequirementProvider offerRequirementProvider;
    @Mock Reconciler reconciler;

    private Stage stage;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(serviceConfiguration.getCount()).thenReturn(3);
        when(schedulerConfiguration.getServiceConfiguration()).thenReturn(serviceConfiguration);
        stage = getTestStage();
    }

    @Test
    public void testStageConstruction() {
        Assert.assertEquals(2, stage.getPhases().size());
        Assert.assertEquals(1, stage.getPhases().get(0).getBlocks().size());
        Assert.assertEquals(3, stage.getPhases().get(1).getBlocks().size());
    }

    @Test
    public void testGetCurrentPhase() {
        StageManager stageManager = new DefaultStageManager(stage, new DefaultStrategyFactory());
        Assert.assertNotNull(stageManager.getCurrentPhase());
    }

    @Test
    public void testHasDecisionPoint() {
        StageManager stageManager = new DefaultStageManager(stage, new StageStrategyFactory());
        Block firstBrokerBlock = stageManager.getStage().getPhases().get(1).getBlock(0);
        Assert.assertTrue(stageManager.hasDecisionPoint(firstBrokerBlock));
    }

    private Stage getTestStage() {
        List<Phase> phases = Arrays.asList(
                ReconciliationPhase.create(reconciler, frameworkState),
                new KafkaUpdatePhase(
                        "target-config-name",
                        schedulerConfiguration,
                        frameworkState,
                        offerRequirementProvider));

        return DefaultStage.fromList(phases);
    }
}
