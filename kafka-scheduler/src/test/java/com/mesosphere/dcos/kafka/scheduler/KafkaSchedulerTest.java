package com.mesosphere.dcos.kafka.scheduler;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mesosphere.dcos.kafka.test.KafkaDropwizardAppRule;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.dcos.kafka.config.DropwizardConfiguration;
import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import static org.mockito.Mockito.*;

/**
 * This class tests the KafkaScheduler class.
 */
public class KafkaSchedulerTest {
    private static KafkaSchedulerConfiguration kafkaSchedulerConfiguration;
    private static Environment environment;
    private static final Protos.TaskID testTaskId = Protos.TaskID.newBuilder().setValue("test-task-id").build();
    private static final String testFrameworkId = "test-framework-id";
    private KafkaScheduler kafkaScheduler;

    public static Injector injector;

    @Mock
    private SchedulerDriver driver;

    @ClassRule
    public static KafkaDropwizardAppRule<DropwizardConfiguration> RULE =
            new KafkaDropwizardAppRule<>(Main.class, ResourceHelpers.resourceFilePath("scheduler.yml"));

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        kafkaScheduler = getTestKafkaScheduler();
    }

    @BeforeClass
    public static void before() {
        final Main main = (Main) RULE.getApplication();
        kafkaSchedulerConfiguration = main.getKafkaSchedulerConfiguration();
        environment = main.getEnvironment();
        injector = Guice.createInjector(new TestModule(kafkaSchedulerConfiguration, environment));
    }

    @Test
    public void testResourceOffersEmpty() {
        kafkaScheduler.resourceOffers(driver, Collections.emptyList());
        verify(driver, times(1)).reconcileTasks(anyObject());
    }

    @Test
    public void testRestartEmptyTasks() {
        KafkaScheduler.restartTasks(Collections.emptyList());
        kafkaScheduler.resourceOffers(driver, Collections.emptyList());
        verify(driver, times(0)).killTask(anyObject());
    }

    @Test
    public void testRestartOneTask() {
        KafkaScheduler.restartTasks(Arrays.asList(testTaskId));
        kafkaScheduler.resourceOffers(driver, Collections.emptyList());
        verify(driver, times(1)).killTask(anyObject());
    }

    @Test
    public void testRestartMultipleTasks()  {
        KafkaScheduler.restartTasks(Arrays.asList(testTaskId, testTaskId));
        kafkaScheduler.resourceOffers(driver, Collections.emptyList());
        verify(driver, times(2)).killTask(anyObject());
    }

    @Test
    public void testRescheduleOneTask() {
        KafkaScheduler.rescheduleTask(testTaskId);
        kafkaScheduler.resourceOffers(driver, Collections.emptyList());
        verify(driver, times(1)).killTask(anyObject());
    }

    @Test
    public void testRegistered() {
        Assert.assertNull(kafkaScheduler.getFrameworkState().getFrameworkId());
        kafkaScheduler.registered(driver, getTestFrameworkId(), null);
        Assert.assertEquals(getTestFrameworkId(), kafkaScheduler.getFrameworkState().getFrameworkId());
    }

    @Test
    public void testStatusUpdate() {
        kafkaScheduler.statusUpdate(driver, getTestTaskStatus());
    }

    @Test
    public void testReconciled() {
        kafkaScheduler.reregistered(driver, null);
    }

    private KafkaScheduler getTestKafkaScheduler() throws Exception {
        return new KafkaScheduler(kafkaSchedulerConfiguration, environment);
    }

    private Protos.FrameworkID getTestFrameworkId() {
        return Protos.FrameworkID.newBuilder()
                .setValue(testFrameworkId)
                .build();
    }

    private Protos.TaskStatus getTestTaskStatus() {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(testTaskId)
                .setState(Protos.TaskState.TASK_RUNNING)
                .build();
    }
}