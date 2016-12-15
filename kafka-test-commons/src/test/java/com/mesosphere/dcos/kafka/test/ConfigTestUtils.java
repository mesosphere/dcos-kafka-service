package com.mesosphere.dcos.kafka.test;

import com.mesosphere.dcos.kafka.config.*;
import org.apache.mesos.config.RecoveryConfiguration;

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
                KafkaTestUtils.testPort,
                new JmxConfig(true, KafkaTestUtils.testJMXPort, false, false),
                new StatsdConfig(KafkaTestUtils.testStatsdHost,KafkaTestUtils.testStatsdPort));
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

    public static ZookeeperConfiguration getTestZookeeperConfiguration() {
        return new ZookeeperConfiguration(
                KafkaTestUtils.testFrameworkName,
                KafkaTestUtils.testMesosZkUri,
                KafkaTestUtils.testKafkaZkUri);
    }

    public static KafkaHealthCheckConfiguration getTestHealthCheckConfiguration() {
        return new KafkaHealthCheckConfiguration(true, 15, 10, 20, 3, 10);
    }

    public static RecoveryConfiguration getTestRecoveryConfiguration() {
        return new RecoveryConfiguration(1200, 600, false);
    }

    public static KafkaSchedulerConfiguration getTestKafkaSchedulerConfiguration() {
        return new KafkaSchedulerConfiguration(
                getTestServiceConfiguration(),
                getTestBrokerConfiguration(),
                getTestKafkaConfiguration(),
                getTestExecutorConfiguration(),
                getTestRecoveryConfiguration(),
                getTestHealthCheckConfiguration());
    }
}
