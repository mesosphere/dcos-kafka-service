package com.mesosphere.dcos.kafka.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.Yaml;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.Configuration;
import org.apache.mesos.config.ConfigurationFactory;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@JsonSerialize
public class KafkaSchedulerConfiguration implements Configuration {

    private static final Log LOGGER = LogFactory.getLog(KafkaSchedulerConfiguration.class);
    private static final ConfigurationFactory<KafkaSchedulerConfiguration> FACTORY = new Factory();

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

    public ZookeeperConfiguration getZookeeperConfig() {
        ZookeeperConfiguration zkSettings = new ZookeeperConfiguration(
                getKafkaConfiguration(), getServiceConfiguration());
        LOGGER.info(String.format("Using Zookeeper settings: address '%s', path '%s'",
                zkSettings.getZkAddress(), zkSettings.getZkRoot()));
        return zkSettings;
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
            return new Yaml().dump(this).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Error occured while serializing the object: " + e);
            throw new ConfigStoreException(e);
        }
    }

    public static ConfigurationFactory<KafkaSchedulerConfiguration> getFactoryInstance() {
        return FACTORY;
    }

    private static class Factory implements ConfigurationFactory<KafkaSchedulerConfiguration> {
        @Override
        public KafkaSchedulerConfiguration parse(byte[] bytes) throws ConfigStoreException {
            try {
                final String yamlStr = new String(bytes, StandardCharsets.UTF_8);
                return new Yaml().loadAs(yamlStr, KafkaSchedulerConfiguration.class);
            } catch (Exception e) {
                throw new ConfigStoreException(e);
            }
        }
    }

    @Override
    public String toJsonString() throws Exception {
        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(this);
    }
}
