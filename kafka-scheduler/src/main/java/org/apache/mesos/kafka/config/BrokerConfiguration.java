package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class BrokerConfiguration {
    @JsonProperty("cpus")
    private double cpus;
    @JsonProperty("mem")
    private double mem;
    @JsonProperty("disk")
    private double disk;
    @JsonProperty("diskType")
    private String diskType;
    @JsonProperty("kafkaUri")
    private String kafkaUri;
    @JsonProperty("containerHookUri")
    private String containerHookUri;
    @JsonProperty("javaUri")
    private String javaUri;
    @JsonProperty("overriderUri")
    private String overriderUri;

    @JsonCreator
    public BrokerConfiguration(
            @JsonProperty("cpus")double cpus,
            @JsonProperty("mem")double mem,
            @JsonProperty("disk")double disk,
            @JsonProperty("diskType")String diskType,
            @JsonProperty("kafkaUri")String kafkaUri,
            @JsonProperty("containerHookUri")String containerHookUri,
            @JsonProperty("javaUri")String javaUri,
            @JsonProperty("overriderUri")String overriderUri) {
        this.cpus = cpus;
        this.mem = mem;
        this.disk = disk;
        this.diskType = diskType;
        this.kafkaUri = kafkaUri;
        this.containerHookUri = containerHookUri;
        this.javaUri = javaUri;
        this.overriderUri = overriderUri;
    }

    public double getCpus() {
        return cpus;
    }

    @JsonProperty("cpus")
    public void setCpus(double cpus) {
        this.cpus = cpus;
    }

    public double getMem() {
        return mem;
    }

    @JsonProperty("mem")
    public void setMem(double mem) {
        this.mem = mem;
    }

    public double getDisk() {
        return disk;
    }

    @JsonProperty("disk")
    public void setDisk(double disk) {
        this.disk = disk;
    }

    public String getDiskType() {
        return diskType;
    }

    @JsonProperty("diskType")
    public void setDiskType(String diskType) {
        this.diskType = diskType;
    }

    public String getKafkaUri() {
        return kafkaUri;
    }

    @JsonProperty("kafkaUri")
    public void setKafkaUri(String kafkaUri) {
        this.kafkaUri = kafkaUri;
    }

    public String getContainerHookUri() {
        return containerHookUri;
    }

    @JsonProperty("containerHookUri")
    public void setContainerHookUri(String containerHookUri) {
        this.containerHookUri = containerHookUri;
    }

    public String getJavaUri() {
        return javaUri;
    }

    @JsonProperty("javaUri")
    public void setJavaUri(String javaUri) {
        this.javaUri = javaUri;
    }

    public String getOverriderUri() {
        return overriderUri;
    }

    @JsonProperty("overriderUri")
    public void setOverriderUri(String overriderUri) {
        this.overriderUri = overriderUri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
          return true;
        }

        if (o == null || getClass() != o.getClass()) {
          return false;
        }

        BrokerConfiguration that = (BrokerConfiguration) o;
        return Double.compare(that.cpus, cpus) == 0 &&
                Double.compare(that.mem, mem) == 0 &&
                Double.compare(that.disk, disk) == 0 &&
                Objects.equals(diskType, that.diskType) &&
                Objects.equals(kafkaUri, that.kafkaUri) &&
                Objects.equals(containerHookUri, that.containerHookUri) &&
                Objects.equals(javaUri, that.javaUri) &&
                Objects.equals(overriderUri, that.overriderUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpus, mem, disk, diskType, kafkaUri, containerHookUri, javaUri, overriderUri);
    }

    @Override
    public String toString() {
        return "BrokerConfiguration{" +
                "cpus=" + cpus +
                ", mem=" + mem +
                ", disk=" + disk +
                ", diskType='" + diskType + '\'' +
                ", kafkaUri='" + kafkaUri + '\'' +
                ", containerHookUri='" + containerHookUri + '\'' +
                ", javaUri='" + javaUri + '\'' +
                ", overriderUri='" + overriderUri + '\'' +
                '}';
    }
}
