package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class KafkaConfiguration {
    @JsonProperty("kafkaAdvertiseHostIp")
    private boolean kafkaAdvertiseHostIp;

    @JsonProperty("kafkaVerName")
    private String kafkaVerName;

    @JsonProperty("kafkaSandboxPath")
    private String kafkaSandboxPath;

    @JsonProperty("kafkaZkUri")
    private String kafkaZkUri;

    @JsonProperty("overrides")
    private Map<String, String> overrides;

    public boolean isKafkaAdvertiseHostIp() {
        return kafkaAdvertiseHostIp;
    }

    @JsonProperty("kafkaAdvertiseHostIp")
    public void setKafkaAdvertiseHostIp(boolean kafkaAdvertiseHostIp) {
        this.kafkaAdvertiseHostIp = kafkaAdvertiseHostIp;
    }

    public String getKafkaVerName() {
        return kafkaVerName;
    }

    @JsonProperty("kafkaVerName")
    public void setKafkaVerName(String kafkaVerName) {
        this.kafkaVerName = kafkaVerName;
    }

    public String getKafkaSandboxPath() {
        return kafkaSandboxPath;
    }

    @JsonProperty("kafkaSandboxPath")
    public void setKafkaSandboxPath(String kafkaSandboxPath) {
        this.kafkaSandboxPath = kafkaSandboxPath;
    }

    public String getKafkaZkUri() {
        return kafkaZkUri;
    }

    @JsonProperty("kafkaZkUri")
    public void setKafkaZkUri(String kafkaZkUri) {
        this.kafkaZkUri = kafkaZkUri;
    }

    public Map<String, String> getOverrides() {
        return overrides;
    }

    @JsonProperty("overrides")
    public void setOverrides(Map<String, String> overrides) {
        this.overrides = overrides;
    }

    @Override
    public String toString() {
        return "KafkaConfiguration{" +
                "kafkaAdvertiseHostIp=" + kafkaAdvertiseHostIp +
                ", kafkaVerName='" + kafkaVerName + '\'' +
                ", kafkaSandboxPath='" + kafkaSandboxPath + '\'' +
                ", kafkaZkUri='" + kafkaZkUri + '\'' +
                ", overrides=" + overrides +
                '}';
    }
}
