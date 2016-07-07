package com.mesosphere.dcos.kafka.state;

import com.mesosphere.dcos.kafka.config.ZookeeperConfiguration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.KeeperException;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * This class tests the KafkaState class.
 */
public class KafkaStateTest {
    private static final String testFrameworkName = "kafka";
    private static final String testRoot = "/" + testFrameworkName;

    private TestingServer testingServer;
    private KafkaState kafkaState;
    private CuratorFramework zkClient;
    private ZookeeperConfiguration zkConfig;

    @Before
    public void beforeEach() throws Exception {
        testingServer = new TestingServer();
        zkConfig = new ZookeeperConfiguration(testingServer.getConnectString(), testRoot);
        kafkaState = new KafkaState(zkConfig);
        zkClient = CuratorFrameworkFactory.newClient(
                testingServer.getConnectString(),
                new RetryNTimes(0, 0));
        zkClient.start();
    }

    @Test
    public void testGetEmptyBrokerIds() throws Exception {
        JSONArray brokerIds = kafkaState.getBrokerIds();
        Assert.assertEquals(0, brokerIds.length());
    }

    @Test
    public void testGetOneBrokerId() throws Exception {
        zkClient.create().creatingParentsIfNeeded().forPath(testRoot + "/brokers/ids/0", "foo".getBytes());
        JSONArray brokerIds = kafkaState.getBrokerIds();
        Assert.assertEquals(1, brokerIds.length());
        Assert.assertEquals("0", brokerIds.get(0));
    }

    @Test
    public void testGetMultipleBrokerId() throws Exception {
        zkClient.create().creatingParentsIfNeeded().forPath(testRoot + "/brokers/ids/0", "foo".getBytes());
        zkClient.create().creatingParentsIfNeeded().forPath(testRoot + "/brokers/ids/1", "foo".getBytes());
        JSONArray brokerIds = kafkaState.getBrokerIds();
        Assert.assertEquals(2, brokerIds.length());
        Assert.assertEquals("0", brokerIds.get(0));
        Assert.assertEquals("1", brokerIds.get(1));
    }

    @Test
    public void testGetEmptyBrokerEndpoints() throws Exception {
        Assert.assertEquals(0, kafkaState.getBrokerEndpoints().size());
    }

    @Test
    public void testGetOneBrokerEndpoint() throws Exception {
        zkClient.create().creatingParentsIfNeeded().forPath(testRoot + "/brokers/ids/0", "{host:host, port:9092}".getBytes());
        List<String> brokerEndpoints = kafkaState.getBrokerEndpoints();
        Assert.assertEquals(1, brokerEndpoints.size());
        Assert.assertEquals("host:9092", brokerEndpoints.get(0));
    }

    @Test
    public void testGetEmptyBrokerDNSEndpoints() throws Exception {
        Assert.assertEquals(0, kafkaState.getBrokerDNSEndpoints(testFrameworkName).size());
    }

    @Test
    public void testGetOneBrokerDNSEndpoint() throws Exception {
        zkClient.create().creatingParentsIfNeeded().forPath(testRoot + "/brokers/ids/0", "{host:host, port:9092}".getBytes());
        List<String> brokerDNSEndpoints = kafkaState.getBrokerDNSEndpoints(testFrameworkName);
        Assert.assertEquals(1, brokerDNSEndpoints.size());
        Assert.assertEquals("broker-0.kafka.mesos:9092", brokerDNSEndpoints.get(0));
    }

    @Test
    public void testGetEmptyTopics() throws Exception {
        Assert.assertEquals(0, kafkaState.getTopics().length());
    }

    @Test(expected= KeeperException.NoNodeException.class)
    public void testGetNonExistantTopic() throws Exception {
        kafkaState.getTopic("fake-topic-name");
    }
}
