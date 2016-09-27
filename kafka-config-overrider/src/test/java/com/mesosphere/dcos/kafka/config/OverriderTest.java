package com.mesosphere.dcos.kafka.config;

import com.mesosphere.dcos.kafka.scheduler.Main;
import com.mesosphere.dcos.kafka.test.KafkaDropwizardAppRule;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.curator.test.TestingServer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.when;

/**
 * This class tests the Overrider class.
 */
public class OverriderTest {

    @ClassRule
    public static KafkaDropwizardAppRule<DropwizardConfiguration> RULE =
            new KafkaDropwizardAppRule<>(Main.class, ResourceHelpers.resourceFilePath("scheduler.yml"));

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Mock private DropwizardConfiguration dropwizardConfiguration;
    @Mock private OverriderEnvironment overriderEnvironment;
    private KafkaSchedulerConfiguration kafkaSchedulerConfiguration;
    private ZookeeperConfiguration zookeeperConfiguration;

    private TestingServer testingServer;
    private String testConfigId;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        testingServer = new TestingServer();
        testingServer.start();

        final Main main = (Main) RULE.getApplication();
        kafkaSchedulerConfiguration = main.getDropwizardConfiguration().getSchedulerConfiguration();
        zookeeperConfiguration = kafkaSchedulerConfiguration.getZookeeperConfig();

        when(dropwizardConfiguration.getSchedulerConfiguration())
                .thenReturn(kafkaSchedulerConfiguration);

        KafkaConfigState configState = new KafkaConfigState(zookeeperConfiguration);
        testConfigId = configState.store(kafkaSchedulerConfiguration).toString();
    }

    @Test
    public void testRunEmptyEnvironment() throws Exception {
        Overrider overrider = new Overrider();
        exit.expectSystemExitWithStatus(Overrider.OverriderExitCode.MISSING_CONFIG_ID.ordinal());
        overrider.run(dropwizardConfiguration, null);
    }

    @Test
    public void testRun() throws Exception {
        Overrider overrider = new Overrider(overriderEnvironment);
        exit.expectSystemExitWithStatus(Overrider.OverriderExitCode.FAILED_TO_UPDATE_PROPERTIES_FILE.ordinal());
        when(overriderEnvironment.getConfigurationId()).thenReturn(testConfigId);
        overrider.run(dropwizardConfiguration, null);
    }
}
