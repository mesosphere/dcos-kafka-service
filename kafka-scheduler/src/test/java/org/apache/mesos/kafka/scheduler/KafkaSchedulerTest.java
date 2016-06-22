package org.apache.mesos.kafka.scheduler;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.mesos.kafka.config.DropwizardConfiguration;
import org.apache.mesos.kafka.config.KafkaSchedulerConfiguration;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * This class tests the KafkaScheduler class.
 */
public class KafkaSchedulerTest {
    private static KafkaSchedulerConfiguration kafkaSchedulerConfiguration;
    private static Environment environment;
    public static Injector injector;

    @ClassRule
    public static KafkaDropwizardAppRule<DropwizardConfiguration> RULE =
            new KafkaDropwizardAppRule<>(Main.class, ResourceHelpers.resourceFilePath("scheduler.yml"));

    @BeforeClass
    public static void before() {
        final Main main = (Main) RULE.getApplication();
        kafkaSchedulerConfiguration = main.getKafkaSchedulerConfiguration();
        environment = main.getEnvironment();
        injector = Guice.createInjector(new TestModule(kafkaSchedulerConfiguration, environment));
    }

    @Test
    public void testKafkaSchedulerConstructor() throws Exception {
       Assert.assertNotNull(new KafkaScheduler(kafkaSchedulerConfiguration, environment));
    }
}