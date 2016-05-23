package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * HeapConfig contains the configuration for the JVM heap for a Kafka
 * broker.
 */
public class HeapConfig {
    @JsonProperty("size_mb")
    private final int sizeMb;

    public HeapConfig(final int sizeMb) {
        this.sizeMb = sizeMb;
    }

    public int getSizeMb() {
        return sizeMb;
    }

    @JsonCreator
    public static HeapConfig create(
            @JsonProperty("size_mb") final int sizeMb) {
        return new HeapConfig(sizeMb);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HeapConfig that = (HeapConfig) o;
        return sizeMb == that.sizeMb;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sizeMb);
    }

    @Override
    public String toString() {
        return "HeapConfig{" +
                "sizeMb=" + sizeMb +
                '}';
    }
}
