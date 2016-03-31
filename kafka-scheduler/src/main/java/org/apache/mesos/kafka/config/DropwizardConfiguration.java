package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

import java.util.Objects;

public class DropwizardConfiguration extends Configuration {
    @JsonProperty("schedulerConfiguration")
    KafkaSchedulerConfiguration schedulerConfiguration;

    @JsonCreator
    public DropwizardConfiguration(
            @JsonProperty("schedulerConfiguration") KafkaSchedulerConfiguration schedulerConfiguration) {
        this.schedulerConfiguration = schedulerConfiguration;
    }

    public KafkaSchedulerConfiguration getSchedulerConfiguration() {
        return schedulerConfiguration;
    }

    @JsonProperty("schedulerConfiguration")
    public void setSchedulerConfiguration(KafkaSchedulerConfiguration schedulerConfiguration) {
        this.schedulerConfiguration = schedulerConfiguration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DropwizardConfiguration that = (DropwizardConfiguration) o;
        return Objects.equals(schedulerConfiguration, that.schedulerConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schedulerConfiguration);
    }

    @Override
    public String toString() {
        return "DropwizardConfiguration{" +
                "schedulerConfiguration=" + schedulerConfiguration +
                '}';
    }
}
