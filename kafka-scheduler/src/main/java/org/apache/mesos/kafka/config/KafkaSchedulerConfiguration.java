package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import io.dropwizard.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class KafkaSchedulerConfiguration extends Configuration {
    private static final Log log = LogFactory.getLog(KafkaSchedulerConfiguration.class);

    @JsonProperty("service")
    private ServiceConfiguration serviceConfiguration;

    @JsonProperty("broker")
    private BrokerConfiguration brokerConfiguration;

    @JsonProperty("kafka")
    private KafkaConfiguration kafkaConfiguration;

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
}
