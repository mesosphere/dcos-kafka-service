package com.mesosphere.dcos.kafka.config;

import com.mesosphere.dcos.kafka.commons.state.KafkaState;
import com.mesosphere.dcos.kafka.test.ConfigTestUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.testing.CuratorTestUtils;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.UUID;

/**
 * This class tests the KafkaConfigState class.
 */
public class KafkaConfigStateTest {

    private static final String testZkRoot = "/test-framework-name";
    private static final RetryPolicy retryNeverPolicy = new RetryNTimes(0, 0);

    private static TestingServer testZk;
    private KafkaConfigState configState;
    private KafkaSchedulerConfiguration config;

    @Mock
    KafkaState state;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);

        try {
            CuratorTestUtils.clear(testZk);
        } catch (Exception ex) {
            testZk = new TestingServer();
        }

        configState = new KafkaConfigState(testZkRoot, testZk.getConnectString(), retryNeverPolicy);
        config = ConfigTestUtils.getTestKafkaSchedulerConfiguration();
    }

    @After
    public void afterEach() throws Exception {
        testZk.start();
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
    public void testGetEmptyConfigNames() throws Exception {
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
