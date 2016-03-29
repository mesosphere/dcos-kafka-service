package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class KafkaConfiguration {
    @JsonProperty("kafkaAdvertiseHostIp")
    private boolean kafkaAdvertiseHostIp;

    @JsonProperty("overrides")
    private Map<String, String> overrides;

    public boolean isKafkaAdvertiseHostIp() {
        return kafkaAdvertiseHostIp;
    }

    @JsonProperty("kafkaAdvertiseHostIp")
    public void setKafkaAdvertiseHostIp(boolean kafkaAdvertiseHostIp) {
        this.kafkaAdvertiseHostIp = kafkaAdvertiseHostIp;
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
                "kafkaAdvertiseHostIp='" + kafkaAdvertiseHostIp + '\'' +
                ", overrides=" + overrides +
                '}';
    }
}
