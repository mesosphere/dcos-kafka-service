package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.Yaml;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.Configuration;

import java.util.Objects;

@JsonSerialize
public class KafkaSchedulerConfiguration implements Configuration {
    private static final Log LOGGER = LogFactory.getLog(KafkaSchedulerConfiguration.class);

    public static final String KAFKA_OVERRIDE_PREFIX = "KAFKA_OVERRIDE_";

    @JsonProperty("service")
    private ServiceConfiguration serviceConfiguration;

    @JsonProperty("broker")
    private BrokerConfiguration brokerConfiguration;

    @JsonProperty("kafka")
    private KafkaConfiguration kafkaConfiguration;

    @JsonProperty("executor")
    private ExecutorConfiguration executorConfiguration;

    public KafkaSchedulerConfiguration() {
    }

    @JsonCreator
    public KafkaSchedulerConfiguration(
            @JsonProperty("service")ServiceConfiguration serviceConfiguration,
            @JsonProperty("broker")BrokerConfiguration brokerConfiguration,
            @JsonProperty("kafka")KafkaConfiguration kafkaConfiguration,
            @JsonProperty("executor")ExecutorConfiguration executorConfiguration) {
        this.serviceConfiguration = serviceConfiguration;
        this.brokerConfiguration = brokerConfiguration;
        this.kafkaConfiguration = kafkaConfiguration;
        this.executorConfiguration = executorConfiguration;
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

    public ExecutorConfiguration getExecutorConfiguration() {
        return executorConfiguration;
    }

    @JsonProperty("executor")
    public void setExecutorConfiguration(ExecutorConfiguration executorConfiguration) {
        this.executorConfiguration = executorConfiguration;
    }

    @Override
    public String toString() {
        return "KafkaSchedulerConfiguration{" +
                "serviceConfiguration=" + serviceConfiguration +
                ", brokerConfiguration=" + brokerConfiguration +
                ", kafkaConfiguration=" + kafkaConfiguration +
                ", executorConfiguration=" + executorConfiguration +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        KafkaSchedulerConfiguration that = (KafkaSchedulerConfiguration) o;
        return Objects.equals(serviceConfiguration, that.serviceConfiguration) &&
                Objects.equals(brokerConfiguration, that.brokerConfiguration) &&
                Objects.equals(executorConfiguration, that.executorConfiguration) &&
                Objects.equals(kafkaConfiguration, that.kafkaConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceConfiguration, brokerConfiguration, kafkaConfiguration, executorConfiguration);
    }

    @Override
    public byte[] getBytes() throws ConfigStoreException {
        try {
            final Yaml yaml = new Yaml();
            return yaml.dump(this).getBytes();
//            final YAMLFactory yamlFactory = new YAMLFactory();
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            yamlFactory.createGenerator(baos).writeObject(this);
//            return baos.toByteArray();
        } catch (Exception e) {
            LOGGER.error("Error occured while serializing the object: " + e);
            throw new ConfigStoreException(e);
        }
    }
}
