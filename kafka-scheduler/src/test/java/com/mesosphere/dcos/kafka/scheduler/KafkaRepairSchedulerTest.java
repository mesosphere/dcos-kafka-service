package com.mesosphere.dcos.kafka.scheduler;

import com.google.common.collect.Iterators;
import com.mesosphere.dcos.kafka.config.ExecutorConfiguration;
import com.mesosphere.dcos.kafka.offer.KafkaOfferRequirementProvider;
import com.mesosphere.dcos.kafka.state.ClusterState;
import com.mesosphere.dcos.kafka.test.KafkaTestUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.dcos.kafka.config.BrokerConfiguration;
import com.mesosphere.dcos.kafka.config.ConfigTestUtils;
import com.mesosphere.dcos.kafka.config.KafkaConfigState;
import com.mesosphere.dcos.kafka.offer.PersistentOfferRequirementProvider;
import com.mesosphere.dcos.kafka.offer.PersistentOperationRecorder;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.dcos.Capabilities;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.ResourceUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This class tests the KafkaRepairScheduler
 */
public class KafkaRepairSchedulerTest {
    @Mock private FrameworkState frameworkState;
    @Mock private KafkaConfigState configState;
    @Mock private ClusterState clusterState;
    @Mock private Capabilities capabilities;
    @Mock private SchedulerDriver driver;
    @Captor private ArgumentCaptor<Collection<Protos.OfferID>> offerIdCaptor;
    @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationCaptor;

    @Before
    public void beforeEach() throws IOException, URISyntaxException {
        MockitoAnnotations.initMocks(this);
        when(frameworkState.getFrameworkId()).thenReturn(KafkaTestUtils.testFrameworkId);
        when(configState.fetch(UUID.fromString(KafkaTestUtils.testConfigName)))
            .thenReturn(ConfigTestUtils.getTestKafkaSchedulerConfiguration());
        when(clusterState.getCapabilities()).thenReturn(capabilities);
        when(capabilities.supportsNamedVips()).thenReturn(true);
    }

    @Test
    public void testKafkaRepairSchedulerConstruction() {
        Assert.assertNotNull(getTestKafkaRepairScheduler());
    }

    @Test
    public void testReplaceMissingBroker() throws Exception {
        // Test replacement of Broker-1 when expecting 3 Brokers of Ids(0, 1, and 2)
        List<Protos.TaskInfo> taskInfos = Arrays.asList(
                getDummyBrokerTaskInfo(0),
                getDummyBrokerTaskInfo(2));
        when(frameworkState.getTaskInfos()).thenReturn(taskInfos);

        KafkaRepairScheduler repairScheduler = getTestKafkaRepairScheduler();
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
        Assert.assertEquals("broker-1", launchOperation.getLaunch().getTaskInfos(0).getName());
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

    private KafkaRepairScheduler getTestKafkaRepairScheduler() {
        return new KafkaRepairScheduler(
                KafkaTestUtils.testConfigName,
                frameworkState,
                getTestOfferRequirementProvider(),
                getTestOfferAccepter());
    }

    private OfferAccepter getTestOfferAccepter() {
        return new OfferAccepter(Arrays.asList(new PersistentOperationRecorder(frameworkState)));
    }

    private KafkaOfferRequirementProvider getTestOfferRequirementProvider() {
        return new PersistentOfferRequirementProvider(frameworkState, configState, clusterState);
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
