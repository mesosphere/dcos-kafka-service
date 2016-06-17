package com.mesosphere.dcos.kafka.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.executor.CustomExecutor;
import org.apache.mesos.executor.MesosExecutorDriverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main entry point for the Cassandra executor program.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final KafkaExecutorTaskFactory kafkaExecutorTaskFactory = new KafkaExecutorTaskFactory();
        final CustomExecutor kafkaExecutor = new CustomExecutor(executorService, kafkaExecutorTaskFactory);
        final MesosExecutorDriverFactory mesosExecutorDriverFactory = new MesosExecutorDriverFactory();
        final ExecutorDriver driver = mesosExecutorDriverFactory.getDriver(kafkaExecutor);
        final Protos.Status status = driver.run();

        LOGGER.info("Driver stopped: status = {}", status);
        System.exit(0);
    }
}
