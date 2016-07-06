package com.mesosphere.dcos.kafka.executor;

import com.mesosphere.dcos.kafka.common.KafkaTask;

import java.util.List;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.executor.*;

import java.util.Collections;

public class KafkaExecutorTaskFactory implements ExecutorTaskFactory {
    @Override
    public ExecutorTask createTask(
            final String taskType,
            final Protos.TaskInfo task,
            final ExecutorDriver driver) throws ExecutorTaskException {
        switch (KafkaTask.valueOf(taskType)) {
            case BROKER:
                // Launch kafka broker process
                return new ProcessTask(driver, task);
            default:
                throw new ExecutorTaskException("Unknown taskType: " + taskType);
        }
    }

    @Override
    public List<TimedExecutorTask> createTimedTasks(String taskType, Protos.ExecutorInfo executorInfo, ExecutorDriver driver) throws ExecutorTaskException {
        return Collections.emptyList();
    }
}
