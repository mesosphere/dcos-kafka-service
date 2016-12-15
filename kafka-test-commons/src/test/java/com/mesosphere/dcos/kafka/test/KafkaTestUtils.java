package com.mesosphere.dcos.kafka.test;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskUtils;

import java.util.UUID;

/**
 * This class provides commons utilities for Kafka tests.
 */
public class KafkaTestUtils {
    public static final String testRole = "test-role";
    public static final String testPrincipal = "test-principal";
    public static final String testResourceId = "test-resource-id";
    public static final String testTaskName = "broker-0";
    public static final Protos.TaskID testTaskId = TaskUtils.toTaskId(testTaskName);
    public static final String testSlaveId = "test-slave-id";
    public static final String testConfigName = UUID.randomUUID().toString();
    public static final String testFrameworkName = "test-framework-name";
    public static final String testUser = "test-user";
    public static final String testPlacementStrategy = "test-placement-strategy";
    public static final String testPhaseStrategy = "test-phase-strategy";
    public static final String testDiskType = "test-disk-type";
    public static final String testKafkaUri = "test-kafka-uri";
    public static final String testJavaUri = "test-java-uri";
    public static final String testOverriderUri = "test-overrider-uri";
    public static final Long testPort = 9092L;
    public static final int testJMXPort = 9010;
    public static final String testStatsdHost = "localhost";
    public static final int testStatsdPort = 8124;
    public static final String testExecutorName = "test-executor-name";
    public static final String testExecutorUri = "test-executor-uri";
    public static final String testKafkaVerName = "test-kafka-ver-name";
    public static final String testKafkaSandboxPath = "test-kafka-sandbox-path";
    public static final String testKafkaZkUri = "test-kafka-zk-uri";
    public static final String testMesosZkUri = "test-mesos-zk-uri";
    public static final String testOfferId = "test-offer-id";
    public static final String testHostname = "test-hostname";
    public static final Protos.FrameworkID testFrameworkId =
            Protos.FrameworkID.newBuilder()
                    .setValue("test-kafka-framework-id")
                    .build();
}

