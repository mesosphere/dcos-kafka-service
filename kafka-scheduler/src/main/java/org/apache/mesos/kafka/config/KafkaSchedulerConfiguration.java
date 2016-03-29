package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import io.dropwizard.Configuration;

import java.util.Objects;

@JsonIgnoreProperties({"server", "logging", "metrics"})
public class KafkaSchedulerConfiguration extends Configuration {
    public static final String KAFKA_OVERRIDE_PREFIX = "KAFKA_OVERRIDE_";

    @JsonProperty("service")
    private ServiceConfiguration serviceConfiguration;

    @JsonProperty("broker")
    private BrokerConfiguration brokerConfiguration;

    @JsonProperty("kafka")
    private KafkaConfiguration kafkaConfiguration;

    @JsonCreator
    public KafkaSchedulerConfiguration(
            @JsonProperty("service")ServiceConfiguration serviceConfiguration,
            @JsonProperty("broker")BrokerConfiguration brokerConfiguration,
            @JsonProperty("kafka")KafkaConfiguration kafkaConfiguration) {
        this.serviceConfiguration = serviceConfiguration;
        this.brokerConfiguration = brokerConfiguration;
        this.kafkaConfiguration = kafkaConfiguration;
    }

    public ServiceConfiguration getServiceConfiguration() {
        return serviceConfiguration;
    }

    @JsonProperty("service")
    public void setServiceConfiguration(ServiceConfiguration serviceConfiguration) {
        this.serviceConfiguration = serviceConfiguration;
    }

    public BrokerConfiguration getBrokerConfiguration() {
        return brokerConfiguration;
    }

    @JsonProperty("broker")
    public void setBrokerConfiguration(BrokerConfiguration brokerConfiguration) {
        this.brokerConfiguration = brokerConfiguration;
    }

    public KafkaConfiguration getKafkaConfiguration() {
        return kafkaConfiguration;
    }

    @JsonProperty("kafka")
    public void setKafkaConfiguration(KafkaConfiguration kafkaConfiguration) {
        this.kafkaConfiguration = kafkaConfiguration;
    }

    @Override
    public String toString() {
        return "KafkaSchedulerConfiguration{" +
                "serviceConfiguration=" + serviceConfiguration +
                ", brokerConfiguration=" + brokerConfiguration +
                ", kafkaConfiguration=" + kafkaConfiguration +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KafkaSchedulerConfiguration that = (KafkaSchedulerConfiguration) o;
        return Objects.equals(serviceConfiguration, that.serviceConfiguration) &&
                Objects.equals(brokerConfiguration, that.brokerConfiguration) &&
                Objects.equals(kafkaConfiguration, that.kafkaConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceConfiguration, brokerConfiguration, kafkaConfiguration);
    }
}
