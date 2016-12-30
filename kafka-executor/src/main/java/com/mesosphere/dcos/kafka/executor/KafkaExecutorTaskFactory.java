package com.mesosphere.dcos.kafka.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.executor.ExecutorTask;
import org.apache.mesos.executor.ExecutorTaskException;
import org.apache.mesos.executor.ExecutorTaskFactory;
import org.apache.mesos.executor.ProcessTask;

import java.io.IOException;

public class KafkaExecutorTaskFactory implements ExecutorTaskFactory {
    @Override
    public ExecutorTask createTask(Protos.TaskInfo taskInfo, ExecutorDriver executorDriver) throws ExecutorTaskException {
        try {
            return ProcessTask.create(executorDriver, taskInfo);
        } catch (IOException e) {
            throw new ExecutorTaskException(e);
        }
    }
}
