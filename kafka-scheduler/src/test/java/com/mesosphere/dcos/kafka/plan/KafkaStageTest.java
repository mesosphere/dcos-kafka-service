package com.mesosphere.dcos.kafka.plan;

import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import com.mesosphere.dcos.kafka.config.ServiceConfiguration;
import com.mesosphere.dcos.kafka.offer.PersistentOfferRequirementProvider;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.strategy.CanaryStrategy;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
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
    private Plan planStage;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(serviceConfiguration.getCount()).thenReturn(3);
        when(schedulerConfiguration.getServiceConfiguration()).thenReturn(serviceConfiguration);
        plan = getTestPlan(new SerialStrategy<>());
        planStage = getTestPlan(new CanaryStrategy<>());
    }

    @Test
    public void testStageConstruction() {
        Assert.assertEquals(2, plan.getChildren().size());
        Assert.assertEquals(1, plan.getChildren().get(0).getChildren().size());
        Assert.assertEquals(3, plan.getChildren().get(1).getChildren().size());
    }

    @Test
    public void testGetCurrentPhase() {
        PlanManager stageManager = new DefaultPlanManager(plan);
        Assert.assertNotNull(stageManager.getPlan().getChildren());
    }

    @Test
    public void testHasDecisionPoint() {
        PlanManager stageManager = new DefaultPlanManager(plan);
        Step firstBrokerStep = stageManager.getPlan().getChildren().get(1).getChildren().get(0);
        Assert.assertFalse(stageManager.getCandidates(Collections.emptyList()).equals(firstBrokerStep));

        stageManager = new DefaultPlanManager(planStage);
        firstBrokerStep = stageManager.getPlan().getChildren().get(1).getChildren().get(0);
        Assert.assertFalse(stageManager.getCandidates(Collections.emptyList()).equals(firstBrokerStep));
    }

    private Plan getTestPlan(Strategy strategy) {
        List<Phase> phases = Arrays.asList(
                ReconciliationPhase.create(reconciler),
                KafkaUpdatePhase.create(
                        "target-config-name",
                        schedulerConfiguration,
                        frameworkState,
                        offerRequirementProvider));

        return new DefaultPlan("test-plan", phases, strategy);
    }
}
