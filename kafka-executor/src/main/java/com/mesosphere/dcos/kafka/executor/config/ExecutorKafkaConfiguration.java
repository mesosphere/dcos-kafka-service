package com.mesosphere.dcos.kafka.executor.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class encapsulates the configuration relevant to Kafka for the Executor.
 */
public class ExecutorKafkaConfiguration {
    @JsonProperty("mesosZkUri")
    private String mesosZkUri;

    @JsonProperty("kafkaZkUri")
    private String kafkaZkUri;

    @JsonProperty("brokerId")
    private String brokerId;

    @JsonCreator
    public ExecutorKafkaConfiguration (
            @JsonProperty("mesosZkUri") String mesosZkUri,
            @JsonProperty("kafkaZkUri") String kafkaZkUri,
            @JsonProperty("brokerId") String brokerId) {
        this.mesosZkUri = mesosZkUri;
        this.kafkaZkUri = kafkaZkUri;
        this.brokerId = brokerId;
    }

    public String getMesosZkUri() {
        return mesosZkUri;
    }

    public String getKafkaZkUri() {
        return kafkaZkUri;
    }

    public String getBrokerId() {
        return brokerId;
    }
}
