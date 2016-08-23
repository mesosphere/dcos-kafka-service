package com.mesosphere.dcos.kafka.commons.state;

import com.mesosphere.dcos.kafka.config.ZookeeperConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-only interface for retrieving information stored by the Kafka brokers themselves.
 */
public class KafkaState {
    private static final Log log = LogFactory.getLog(KafkaState.class);

    private static final int POLL_DELAY_MS = 1000;
    private static final int CURATOR_MAX_RETRIES = 3;

    private final ZookeeperConfiguration zkConfig;
    private final CuratorFramework kafkaZkClient;

    public KafkaState(ZookeeperConfiguration zkConfig) {
        this.zkConfig = zkConfig;

        this.kafkaZkClient = CuratorFrameworkFactory.newClient(
                zkConfig.getKafkaZkUri(),
                new ExponentialBackoffRetry(POLL_DELAY_MS, CURATOR_MAX_RETRIES));
        this.kafkaZkClient.start();
    }

    public JSONArray getBrokerIds() throws Exception {
        return getIds(zkConfig.getBrokerIdPath());
    }

    public Optional<JSONObject> getBroker(String brokerId) throws Exception {
        List<String> ids = kafkaZkClient.getChildren().forPath(zkConfig.getBrokerIdPath());
        if (!ids.contains(brokerId)) {
            return Optional.empty();
        }

        return Optional.of(
                new JSONObject(
                        new String(
                                kafkaZkClient.getData().forPath(zkConfig.getBrokerIdPath() + "/" + brokerId),
                                "UTF-8")));
    }

    public List<String> getBrokerEndpoints() {
        List<String> endpoints = new ArrayList<String>();

        try {
            List<String> ids = kafkaZkClient.getChildren().forPath(zkConfig.getBrokerIdPath());
            for (String id : ids) {
                byte[] bytes = kafkaZkClient.getData().forPath(zkConfig.getBrokerIdPath() + "/" + id);
                JSONObject broker = new JSONObject(new String(bytes, "UTF-8"));
                String host = (String) broker.get("host");
                Integer port = (Integer) broker.get("port");
                endpoints.add(host + ":" + port);
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve broker endpoints with exception: ", ex);
        }

        return endpoints;
    }

    public List<String> getBrokerDNSEndpoints() {
        List<String> endpoints = new ArrayList<String>();

        try {
            List<String> ids = kafkaZkClient.getChildren().forPath(zkConfig.getBrokerIdPath());
            for (String id : ids) {
                byte[] bytes = kafkaZkClient.getData().forPath(zkConfig.getBrokerIdPath() + "/" + id);
                JSONObject broker = new JSONObject(new String(bytes, "UTF-8"));
                String host = "broker-" + id + "." + zkConfig.getFrameworkName() + ".mesos";
                Integer port = (Integer) broker.get("port");
                endpoints.add(host + ":" + port);
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve broker DNS endpoints with exception: ", ex);
        }

        return endpoints;
    }

    public JSONArray getTopics() throws Exception {
        return getIds(zkConfig.getZkRootPath() + "/brokers/topics");
    }

    public JSONObject getTopic(String topicName) throws Exception {
        String partitionsPath = zkConfig.getZkRootPath() + "/brokers/topics/" + topicName + "/partitions";
        List<String> partitionIds = kafkaZkClient.getChildren()
                .forPath(partitionsPath);

        List<JSONObject> partitions = new ArrayList<JSONObject>();
        for (String partitionId : partitionIds) {
            JSONObject state = getElement(
                    partitionsPath + "/" + partitionId + "/state");
            JSONObject partition = new JSONObject();
            partition.put(partitionId, state);
            partitions.add(partition);
        }

        JSONObject obj = new JSONObject();
        obj.put("partitions", partitions);
        return obj;
    }

    private JSONArray getIds(String path) throws Exception {
        try {
            return new JSONArray(kafkaZkClient.getChildren().forPath(path));
        } catch (NoNodeException e) {
            log.info(
                    "List path " + path
                            + " doesn't exist, returning empty list. Kafka not running yet?",
                    e);
            return new JSONArray();
        }
    }

    private JSONObject getElement(String path) throws Exception {
        byte[] bytes = kafkaZkClient.getData().forPath(path);
        String element = new String(bytes, "UTF-8");
        return new JSONObject(element);
    }
}
