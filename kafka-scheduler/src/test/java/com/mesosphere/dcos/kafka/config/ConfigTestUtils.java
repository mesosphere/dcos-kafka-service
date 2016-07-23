package com.mesosphere.dcos.kafka.config;

import com.mesosphere.dcos.kafka.test.KafkaTestUtils;

public class ConfigTestUtils {
    public static ServiceConfiguration getTestServiceConfiguration() {
        return new ServiceConfiguration(
                1,
                KafkaTestUtils.testFrameworkName,
                KafkaTestUtils.testUser,
                KafkaTestUtils.testPlacementStrategy,
                KafkaTestUtils.testPhaseStrategy,
                KafkaTestUtils.testRole,
                KafkaTestUtils.testPrincipal);
    }

    public static BrokerConfiguration getTestBrokerConfiguration() {
        return new BrokerConfiguration(
                1,
                1000,
                new HeapConfig(500),
                5000,
                KafkaTestUtils.testDiskType,
                KafkaTestUtils.testKafkaUri,
                KafkaTestUtils.testJavaUri,
                KafkaTestUtils.testOverriderUri,
                KafkaTestUtils.testPort);
    }

    public static KafkaConfiguration getTestKafkaConfiguration() {
        return new KafkaConfiguration(
                true,
                KafkaTestUtils.testKafkaVerName,
                KafkaTestUtils.testKafkaSandboxPath,
                KafkaTestUtils.testKafkaZkUri,
                KafkaTestUtils.testMesosZkUri,
                null);
    }

    public static ExecutorConfiguration getTestExecutorConfiguration() {
        return new ExecutorConfiguration(
                1,
                256,
                0,
                KafkaTestUtils.testExecutorUri
        );
    }

    public static KafkaSchedulerConfiguration getTestKafkaSchedulerConfiguration() {
        return new KafkaSchedulerConfiguration(
                getTestServiceConfiguration(),
                getTestBrokerConfiguration(),
                getTestKafkaConfiguration(),
                getTestExecutorConfiguration());
    }
}
