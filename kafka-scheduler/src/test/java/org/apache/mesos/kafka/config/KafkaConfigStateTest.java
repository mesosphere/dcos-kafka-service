package org.apache.mesos.kafka.config;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;

import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.kafka.state.KafkaState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.UUID;

/**
 * This class tests the KafkaConfigState class.
 */
public class KafkaConfigStateTest {

    private static final String testFrameworkName = "test-framework-name";
    private static final RetryPolicy retryNeverPolicy = new RetryNTimes(0, 1000);

    private TestingServer testZk;
    private KafkaConfigState configState;
    private KafkaSchedulerConfiguration config;

    @Mock
    KafkaState state;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        testZk = new TestingServer();
        configState = new KafkaConfigState(testFrameworkName, testZk.getConnectString(), retryNeverPolicy);
        config = new KafkaSchedulerConfiguration(
                new ServiceConfiguration(),
                new BrokerConfiguration(),
                new KafkaConfiguration(),
                new ExecutorConfiguration());
    }

    @Test(expected=ConfigStoreException.class)
    public void testFetchFailure() throws Exception {
        configState.fetch(UUID.randomUUID());
    }

    @Test
    public void testStoreFetchSuccess() throws Exception {
        UUID id = configState.store(config);
        Assert.assertNotNull(id);
        KafkaSchedulerConfiguration outConfig = configState.fetch(id);
        Assert.assertNotNull(outConfig);
    }

    @Test(expected=ConfigStoreException.class)
    public void testStoreFetchFailure() throws Exception {
        configState.store(config);
        configState.fetch(UUID.randomUUID());
    }

    @Test
    public void testHasTarget() throws Exception {
        Assert.assertFalse(configState.hasTarget());
        UUID id = UUID.randomUUID();
        configState.setTargetName(id);
        Assert.assertTrue(configState.hasTarget());
    }

    @Test(expected=ConfigStoreException.class)
    public void testGetEmptyTargetNameFailure() throws Exception {
        configState.getTargetName();
    }

    @Test
    public void testSetGetTargetNameSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        configState.setTargetName(id);
        Assert.assertEquals(id, configState.getTargetName());
    }

    @Test
    public void testGetTargetConfig() throws Exception {
        UUID id = configState.store(config);
        configState.setTargetName(id);
        Assert.assertEquals(config, configState.getTargetConfig());
    }

    @Test
    public void testGetEmptyConfigNames() {
        Assert.assertEquals(Collections.EMPTY_LIST, configState.getConfigNames());
    }

    @Test
    public void testGetConfigNames() throws Exception {
        configState.store(config);
        configState.store(config);
        Assert.assertEquals(2, configState.getConfigNames().size());
    }

    @Test(expected=ConfigStoreException.class)
    public void testStoreFailure() throws Exception {
        testZk.stop();
        configState.store(config);
    }

    @Test(expected=ConfigStoreException.class)
    public void testSetTargetFailure() throws Exception {
        testZk.stop();
        configState.setTargetName(UUID.randomUUID());
    }
}
