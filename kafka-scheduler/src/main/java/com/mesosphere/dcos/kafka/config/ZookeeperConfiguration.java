package com.mesosphere.dcos.kafka.config;

/**
 * POJO storage of Zookeeper connection settings
 */
public class ZookeeperConfiguration {

    private final String mesosZkUri;
    private final String mesosZkRoot;

    private final String kafkaZkUri;
    private final String kafkaZkRoot;

    public ZookeeperConfiguration(KafkaConfiguration kafkaConfig, ServiceConfiguration serviceConfig) {
        this(kafkaConfig.getMesosZkUri(),
             "/" + serviceConfig.getName(),
             kafkaConfig.getKafkaZkUri(),
             "/" + serviceConfig.getName());
    }

    public ZookeeperConfiguration(
            String mesosZkUri,
            String mesosZkRoot,
            String kafkaZkUri,
            String kafkaZkRoot) {
        this.mesosZkUri = mesosZkUri;
        this.mesosZkRoot = mesosZkRoot;
        this.kafkaZkUri = kafkaZkUri;
        this.kafkaZkRoot = kafkaZkRoot;
    }

    public String getMesosZkUri() {
        return mesosZkUri;
    }

    public String getMesosZkRoot() {
        return mesosZkRoot;
    }

    public String getKafkaZkUri() {
        return kafkaZkUri;
    }

    public String getKafkaZkRoot() {
        return kafkaZkRoot;
    }
}
