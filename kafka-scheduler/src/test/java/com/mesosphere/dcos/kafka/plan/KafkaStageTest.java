package com.mesosphere.dcos.kafka.plan;

import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import com.mesosphere.dcos.kafka.config.ServiceConfiguration;
import com.mesosphere.dcos.kafka.offer.PersistentOfferRequirementProvider;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.plan.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * This tests the construction of Kafka Stages.
 */
public class KafkaStageTest {
    @Mock KafkaSchedulerConfiguration schedulerConfiguration;
    @Mock ServiceConfiguration serviceConfiguration;
    @Mock FrameworkState frameworkState;
    @Mock PersistentOfferRequirementProvider offerRequirementProvider;
    @Mock Reconciler reconciler;

    private Plan plan;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(serviceConfiguration.getCount()).thenReturn(3);
        when(schedulerConfiguration.getServiceConfiguration()).thenReturn(serviceConfiguration);
        plan = getTestPlan();
    }

    @Test
    public void testStageConstruction() {
        Assert.assertEquals(2, plan.getPhases().size());
        Assert.assertEquals(1, plan.getPhases().get(0).getBlocks().size());
        Assert.assertEquals(3, plan.getPhases().get(1).getBlocks().size());
    }

    @Test
    public void testGetCurrentPhase() {
        PlanManager stageManager = new DefaultPlanManager(plan, new DefaultStrategyFactory());
        Assert.assertNotNull(stageManager.getCurrentPhase());
    }

    @Test
    public void testHasDecisionPoint() {
        PlanManager stageManager = new DefaultPlanManager(plan, new StageStrategyFactory());
        Block firstBrokerBlock = stageManager.getPlan().getPhases().get(1).getBlock(0);
        Assert.assertTrue(stageManager.hasDecisionPoint(firstBrokerBlock));
    }

    private Plan getTestPlan() {
        List<Phase> phases = Arrays.asList(
                ReconciliationPhase.create(reconciler),
                new KafkaUpdatePhase(
                        "target-config-name",
                        schedulerConfiguration,
                        frameworkState,
                        offerRequirementProvider));

        return DefaultPlan.fromList(phases);
    }
}
