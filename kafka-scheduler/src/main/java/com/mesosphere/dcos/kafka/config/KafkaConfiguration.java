package com.mesosphere.dcos.kafka.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

public class KafkaConfiguration {
    @JsonProperty("kafkaAdvertiseHostIp")
    private boolean kafkaAdvertiseHostIp;

    @JsonProperty("kafkaVerName")
    private String kafkaVerName;

    @JsonProperty("kafkaSandboxPath")
    private String kafkaSandboxPath;

    @JsonProperty("kafkaZkUri")
    private String kafkaZkUri;

    @JsonProperty("mesosZkUri")
    private String mesosZkUri;

    @JsonProperty("overrides")
    private Map<String, String> overrides;

    public KafkaConfiguration() {

    }

    @JsonCreator
    public KafkaConfiguration(
            @JsonProperty("kafkaAdvertiseHostIp")boolean kafkaAdvertiseHostIp,
            @JsonProperty("kafkaVerName")String kafkaVerName,
            @JsonProperty("kafkaSandboxPath")String kafkaSandboxPath,
            @JsonProperty("kafkaZkUri")String kafkaZkUri,
            @JsonProperty("mesosZkUri")String mesosZkUri,
            @JsonProperty("overrides")Map<String, String> overrides) {
        this.kafkaAdvertiseHostIp = kafkaAdvertiseHostIp;
        this.kafkaVerName = kafkaVerName;
        this.kafkaSandboxPath = kafkaSandboxPath;
        this.kafkaZkUri = kafkaZkUri;
        this.mesosZkUri = mesosZkUri;
        this.overrides = overrides;
    }

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

    @JsonProperty("mesosZkUri")
    public String getMesosZkUri() {
        return mesosZkUri;
    }

    @JsonProperty("mesosZkUri")
    public void setMesosZkUri(String mesosZkUri) {
        this.mesosZkUri = mesosZkUri;
    }

    public Map<String, String> getOverrides() {
        return overrides;
    }

    @JsonProperty("overrides")
    public void setOverrides(Map<String, String> overrides) {
        this.overrides = overrides;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
          return true;
        }

        if (o == null || getClass() != o.getClass()) {
          return false;
        }

        KafkaConfiguration that = (KafkaConfiguration) o;
        return kafkaAdvertiseHostIp == that.kafkaAdvertiseHostIp &&
                Objects.equals(kafkaVerName, that.kafkaVerName) &&
                Objects.equals(kafkaZkUri, that.kafkaZkUri) &&
                Objects.equals(mesosZkUri, that.mesosZkUri) &&
                Objects.equals(overrides, that.overrides);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kafkaAdvertiseHostIp, kafkaVerName, kafkaZkUri, mesosZkUri, overrides);
    }

    @Override
    public String toString() {
        return "KafkaConfiguration{" +
                "kafkaAdvertiseHostIp=" + kafkaAdvertiseHostIp +
                ", kafkaVerName='" + kafkaVerName + '\'' +
                ", kafkaSandboxPath='" + kafkaSandboxPath + '\'' +
                ", kafkaZkUri='" + kafkaZkUri + '\'' +
                ", mesosZkUri='" + mesosZkUri + '\'' +
                ", overrides=" + overrides +
                '}';
    }
}
