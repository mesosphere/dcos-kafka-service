package com.mesosphere.dcos.kafka.web;

import com.mesosphere.dcos.kafka.plan.KafkaUpdatePhase;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.Stage;
import org.apache.mesos.scheduler.plan.StageManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * This class tests the BrokerCheck class.
 */
public class BrokerCheckTest {
    @Mock private StageManager stageManager;
    @Mock private Stage stage;
    @Mock private FrameworkState frameworkState;
    @Mock private KafkaUpdatePhase kafkaUpdatePhase;
    @Mock private Block block;
    private BrokerCheck brokerCheck;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        brokerCheck = new BrokerCheck(stageManager, frameworkState);
    }

    @Test
    public void testBrokerCheckConstruction() {
        Assert.assertNotNull(brokerCheck);
    }

    @Test
    public void testCheckException() throws Exception {
        Assert.assertFalse(brokerCheck.check().isHealthy());
    }

    @Test
    public void testCheckNoUpdatePhase() throws Exception {
        when(stageManager.getStage()).thenReturn(stage);
        when(stage.getPhases()).thenReturn(Collections.emptyList());
        Assert.assertFalse(brokerCheck.check().isHealthy());
    }

    @Test
    public void testCheckBelowBrokerCount() throws Exception {
        when(stageManager.getStage()).thenReturn(stage);
        when(frameworkState.getRunningBrokersCount()).thenReturn(0);
        when(block.isComplete()).thenReturn(true);
        when(kafkaUpdatePhase.getBlocks()).thenReturn(Arrays.asList(block));
        Mockito.<List<? extends Phase>>when(stage.getPhases()).thenReturn(getMockPhases());
        Assert.assertFalse(brokerCheck.check().isHealthy());
    }

    @Test
    public void testCheckAtBrokerCount() throws Exception {
        when(stageManager.getStage()).thenReturn(stage);
        when(frameworkState.getRunningBrokersCount()).thenReturn(1);
        when(block.isComplete()).thenReturn(true);
        when(kafkaUpdatePhase.getBlocks()).thenReturn(Arrays.asList(block));
        Mockito.<List<? extends Phase>>when(stage.getPhases()).thenReturn(getMockPhases());
        Assert.assertTrue(brokerCheck.check().isHealthy());
    }

    private List<? extends Phase> getMockPhases() {
        return Arrays.asList(kafkaUpdatePhase);
    }
}