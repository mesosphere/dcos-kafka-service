package com.mesosphere.dcos.kafka.executor.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class encapsulates the configuration of the ExecutorService.
 */
public class ExecutorServiceConfiguration {
    @JsonProperty("name")
    private String name;

    @JsonCreator
    public ExecutorServiceConfiguration(
            @JsonProperty("name")String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
