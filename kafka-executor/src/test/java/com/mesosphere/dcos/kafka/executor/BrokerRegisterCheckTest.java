package com.mesosphere.dcos.kafka.executor;

import com.codahale.metrics.health.HealthCheck;
import com.mesosphere.dcos.kafka.config.ZookeeperConfiguration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.testing.CuratorTestUtils;
import org.junit.*;

/**
 * This class tests the BrokerRegisterCheck class.
 */
public class BrokerRegisterCheckTest {
    private static TestingServer testingServer;
    private static CuratorFramework client;
    private static ZookeeperConfiguration zookeeperConfiguration;
    private static String dummyJson = "{dummy:json}";

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
        client = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new RetryOneTime(100));
        client.start();
        zookeeperConfiguration = new ZookeeperConfiguration(
                "framework-name",
                "mesos-zk-uri",
                testingServer.getConnectString());
    }

    @AfterClass
    public static void afterAll() {
        client.close();
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testingServer);
    }

    @Test
    public void testSuccessfulCheck() throws Exception {
        String brokerId = "0";
        client.create().creatingParentsIfNeeded()
                .forPath(zookeeperConfiguration.getBrokerIdPath() + "/" + brokerId, dummyJson.getBytes());
        BrokerRegisterCheck brokerRegisterCheck = new BrokerRegisterCheck(brokerId, zookeeperConfiguration);
        Assert.assertEquals(
                HealthCheck.Result.healthy("Broker Id is present in Zookeeper."),
                brokerRegisterCheck.check());
    }

    @Test
    public void testFailedExceptionCheck() throws Exception {
        String brokerId = "0";
        BrokerRegisterCheck brokerRegisterCheck = new BrokerRegisterCheck(brokerId, zookeeperConfiguration);
        Assert.assertEquals(
                HealthCheck.Result.unhealthy("Failed to fetch Broker Id."),
                brokerRegisterCheck.check());
    }

    @Test
    public void testFailedNotPresentCheck() throws Exception {
        String badBrokerId = "foo";
        client.create().creatingParentsIfNeeded()
                .forPath(zookeeperConfiguration.getBrokerIdPath() + "/" + badBrokerId, dummyJson.getBytes());
        BrokerRegisterCheck brokerRegisterCheck = new BrokerRegisterCheck("0", zookeeperConfiguration);
        Assert.assertEquals(
                HealthCheck.Result.unhealthy("Broker Id is NOT present in Zookeeper."),
                brokerRegisterCheck.check());
    }
}
