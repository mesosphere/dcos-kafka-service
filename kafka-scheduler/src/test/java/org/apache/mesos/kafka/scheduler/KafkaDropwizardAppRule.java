package org.apache.mesos.kafka.scheduler;

import io.dropwizard.Application;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.kafka.config.DropwizardConfiguration;

import javax.annotation.Nullable;

/**
 * This class is a DropwizarAppRule needed to run KafkaScheduler unit tests.
 */
public class KafkaDropwizardAppRule<C extends DropwizardConfiguration> extends DropwizardAppRule {
    private static final Log log = LogFactory.getLog(KafkaScheduler.class);
    private TestingServer server;

    public KafkaDropwizardAppRule(Class<? extends Application<C>> applicationClass, @Nullable String configPath, ConfigOverride... configOverrides) {
        super(applicationClass, configPath, configOverrides);
    }

    @Override
    protected void before() {
        try {
            server = new TestingServer(40000);
            super.before();
        } catch (Exception e) {
            log.error("Failed to prepare in before() with exception: ", e);
        }
    }
}
