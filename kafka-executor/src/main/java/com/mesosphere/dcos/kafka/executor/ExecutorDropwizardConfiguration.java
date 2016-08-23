package com.mesosphere.dcos.kafka.executor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.dcos.kafka.executor.config.KafkaExecutorConfiguration;
import io.dropwizard.Configuration;

/**
 * This class encapsulates the root configuration object for the Kafka Framework's Executor.
 */
public class ExecutorDropwizardConfiguration extends Configuration {
    @JsonProperty("executorConfiguration")
    private KafkaExecutorConfiguration executorConfiguration;

    @JsonCreator
    public ExecutorDropwizardConfiguration(
            @JsonProperty("executorConfiguration") KafkaExecutorConfiguration executorConfiguration) {
        this.executorConfiguration = executorConfiguration;
    }

    public KafkaExecutorConfiguration getExecutorConfiguration() {
        return executorConfiguration;
    }
}
