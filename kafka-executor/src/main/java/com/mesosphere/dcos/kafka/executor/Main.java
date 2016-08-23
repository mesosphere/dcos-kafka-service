package com.mesosphere.dcos.kafka.executor;

import com.mesosphere.dcos.kafka.config.ZookeeperConfiguration;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.executor.CustomExecutor;
import org.apache.mesos.executor.MesosExecutorDriverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main entry point for the Kafka executor program.
 */
public class Main extends Application<ExecutorDropwizardConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final int MIN_EXECUTOR_SERVICE_THREADS = 1;
    private static final int MAX_EXECUTOR_SERVICE_THREADS = 10;
    private ExecutorService executorService = null;

    public Main() {
        super();
    }

    public static void main(String[] args) throws Exception {
        new Main().run(args);
    }

    @Override
    public void run(ExecutorDropwizardConfiguration configuration, Environment environment) throws Exception {
        ZookeeperConfiguration zkConfig = new ZookeeperConfiguration(
                configuration.getExecutorConfiguration().getExecutorServiceConfiguration().getName(),
                configuration.getExecutorConfiguration().getExecutorKafkaConfiguration().getMesosZkUri(),
                configuration.getExecutorConfiguration().getExecutorKafkaConfiguration().getKafkaZkUri());

        environment.healthChecks().register(
                BrokerRegisterCheck.NAME,
                new BrokerRegisterCheck(
                       configuration.getExecutorConfiguration().getExecutorKafkaConfiguration().getBrokerId(),
                        zkConfig));


        final KafkaExecutorTaskFactory kafkaExecutorTaskFactory = new KafkaExecutorTaskFactory();
        final CustomExecutor kafkaExecutor = new CustomExecutor(Executors.newCachedThreadPool(), kafkaExecutorTaskFactory);
        final ExecutorDriver driver = new MesosExecutorDriverFactory().getDriver(kafkaExecutor);

        executorService = environment.lifecycle().
                executorService("KafkaExecutor")
                .minThreads(MIN_EXECUTOR_SERVICE_THREADS)
                .maxThreads(MAX_EXECUTOR_SERVICE_THREADS)
                .build();
        executorService.submit(new RunnableExecutor(driver));
    }

    @Override
    public void initialize(Bootstrap<ExecutorDropwizardConfiguration> bootstrap) {
        super.initialize(bootstrap);

        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor()));
    }

    private static class RunnableExecutor implements Runnable {
        private final ExecutorDriver executorDriver;

        public RunnableExecutor(ExecutorDriver executorDriver) {
            this.executorDriver = executorDriver;
        }

        @Override
        public void run() {
            Protos.Status status = executorDriver.run();
            LOGGER.info("Driver stopped: status = {}", status);
            System.exit(0);
        }
    }
}
