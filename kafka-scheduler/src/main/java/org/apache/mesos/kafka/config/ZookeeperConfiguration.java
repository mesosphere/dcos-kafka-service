package org.apache.mesos.kafka.config;

/**
 * POJO storage of Zookeeper connection settings
 */
public class ZookeeperConfiguration {

    private final String zkAddress;
    private final String zkRoot;

    public ZookeeperConfiguration(KafkaConfiguration kafkaConfig, ServiceConfiguration serviceConfig) {
        this(kafkaConfig.getZkAddress(), "/" + serviceConfig.getName());
    }

    public ZookeeperConfiguration(String zkAddress, String zkRoot) {
        this.zkAddress = zkAddress;
        this.zkRoot = zkRoot;
    }

    public String getZkAddress() {
        return zkAddress;
    }

    public String getZkRoot() {
        return zkRoot;
    }
}
