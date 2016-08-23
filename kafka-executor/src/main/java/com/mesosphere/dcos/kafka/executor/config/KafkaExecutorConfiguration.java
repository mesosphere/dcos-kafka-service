package com.mesosphere.dcos.kafka.executor.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class encapsulates the configuration of the Executor for the Kafka Framework.
 */
public class KafkaExecutorConfiguration {
    @JsonProperty("service")
    private final ExecutorServiceConfiguration executorServiceConfiguration;

    @JsonProperty("kafka")
    private final ExecutorKafkaConfiguration executorKafkaConfiguration;

    @JsonCreator
    public KafkaExecutorConfiguration(
            @JsonProperty("service") ExecutorServiceConfiguration executorServiceConfiguration,
            @JsonProperty("kafka") ExecutorKafkaConfiguration executorKafkaConfiguration) {
        this.executorServiceConfiguration = executorServiceConfiguration;
        this.executorKafkaConfiguration = executorKafkaConfiguration;
    }

    public ExecutorServiceConfiguration getExecutorServiceConfiguration() {
        return executorServiceConfiguration;
    }

    public ExecutorKafkaConfiguration getExecutorKafkaConfiguration() {
        return executorKafkaConfiguration;
    }
}
