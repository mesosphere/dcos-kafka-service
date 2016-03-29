package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;

public class BrokerConfiguration {
    @JsonProperty("count")
    private int count;
    @JsonProperty("cpus")
    private double cpus;
    @JsonProperty("mem")
    private double mem;
    @JsonProperty("disk")
    private double disk;
    @JsonProperty("diskType")
    private String diskType;

    public int getCount() {
        return count;
    }

    @JsonProperty("count")
    public void setCount(int count) {
        this.count = count;
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

    @Override
    public String toString() {
        return "BrokerConfiguration{" +
                "count=" + count +
                ", cpus=" + cpus +
                ", mem=" + mem +
                ", disk=" + disk +
                ", diskType='" + diskType + '\'' +
                '}';
    }
}
