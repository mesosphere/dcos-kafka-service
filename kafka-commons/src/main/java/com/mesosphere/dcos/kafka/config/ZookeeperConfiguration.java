package com.mesosphere.dcos.kafka.config;

import org.apache.mesos.dcos.DcosConstants;

/**
 * POJO storage of Zookeeper connection settings
 */
public class ZookeeperConfiguration {

    private final String frameworkName;
    private final String mesosZkUri;
    private final String kafkaZkUri;

    public ZookeeperConfiguration(
            KafkaConfiguration kafkaConfig,
            ServiceConfiguration serviceConfig) {
        this(serviceConfig.getName(), kafkaConfig.getMesosZkUri(), kafkaConfig.getKafkaZkUri());
    }

    public ZookeeperConfiguration(
            String frameworkName,
            String mesosZkUri,
            String kafkaZkUri) {
        this.frameworkName = frameworkName;
        this.mesosZkUri = mesosZkUri;
        this.kafkaZkUri = kafkaZkUri;
    }

    public String getFrameworkName() {
        return frameworkName;
    }

    public String getMesosZkUri() {
        return mesosZkUri;
    }

    public String getKafkaZkUri() {
        return kafkaZkUri;
    }

    public String getZkRootPath() {
        return DcosConstants.SERVICE_ROOT_PATH_PREFIX + getFrameworkName();
    }

    public String getBrokerIdPath() {
        return getZkRootPath() + "/brokers/ids";
    }
}
