package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;

public class ServiceConfiguration {
    @JsonProperty("name")
    private String name;
    @JsonProperty("user")
    private String user;
    @JsonProperty("placementStrategy")
    private String placementStrategy;
    @JsonProperty("planStrategy")
    private String planStrategy;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getUser() {
        return user;
    }

    @JsonProperty("user")
    public void setUser(String user) {
        this.user = user;
    }

    public String getPlacementStrategy() {
        return placementStrategy;
    }

    @JsonProperty("placementStrategy")
    public void setPlacementStrategy(String placementStrategy) {
        this.placementStrategy = placementStrategy;
    }

    public String getPlanStrategy() {
        return planStrategy;
    }

    @JsonProperty("planStrategy")
    public void setPlanStrategy(String planStrategy) {
        this.planStrategy = planStrategy;
    }

    @Override
    public String toString() {
        return "ServiceConfiguration{" +
                "name='" + name + '\'' +
                ", user='" + user + '\'' +
                ", placementStrategy='" + placementStrategy + '\'' +
                ", planStrategy='" + planStrategy + '\'' +
                '}';
    }
}
