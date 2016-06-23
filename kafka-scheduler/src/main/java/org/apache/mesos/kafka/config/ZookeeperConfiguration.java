package org.apache.mesos.kafka.config;

/**
 * POJO storage of Zookeeper connection settings
 */
public class ZookeeperConfiguration {

    private final String zkAddress;
    private final String zkRoot;

    ZookeeperConfiguration(KafkaConfiguration kafkaConfig, ServiceConfiguration serviceConfig) {
        this.zkAddress = kafkaConfig.getZkAddress();
        this.zkRoot = "/" + serviceConfig.getName(); // use framework name as config path
    }

    public String getZkAddress() {
        return zkAddress;
    }

    public String getZkRoot() {
        return zkRoot;
    }
}
