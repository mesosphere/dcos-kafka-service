package com.mesosphere.dcos.kafka.config;

/**
 * This class encapsulates retrieval of values expected in the Environment variables of the Overrider.
 */
public class OverriderEnvironment {
    public String getConfigurationId() {
        return System.getenv("CONFIG_ID");
    }

    public String getStatsdUdpHost() {
        return System.getenv("STATSD_UDP_HOST");
    }

    public String getStatsdUdpPort() {
        return System.getenv("STATSD_UDP_PORT");
    }

    public String getMesosSandbox() {
        return System.getenv("MESOS_SANDBOX");
    }
}
