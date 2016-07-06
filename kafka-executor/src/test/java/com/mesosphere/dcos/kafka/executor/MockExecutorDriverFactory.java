package com.mesosphere.dcos.kafka.executor;

import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.executor.ExecutorDriverFactory;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * This class is used to test the start of Kafka's Executor in MainTest.
 */
public class MockExecutorDriverFactory implements ExecutorDriverFactory {
    @Mock
    private ExecutorDriver executorDriver;

    public MockExecutorDriverFactory() {
        MockitoAnnotations.initMocks(this);
    }

    @Override
    public ExecutorDriver getDriver(Executor executor) {
        return executorDriver;
    }
}
