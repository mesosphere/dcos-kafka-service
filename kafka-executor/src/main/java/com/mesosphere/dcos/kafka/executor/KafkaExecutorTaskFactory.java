package com.mesosphere.dcos.kafka.executor;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.executor.ExecutorTask;
import org.apache.mesos.executor.ExecutorTaskException;
import org.apache.mesos.executor.ExecutorTaskFactory;
import org.apache.mesos.executor.ProcessTask;

public class KafkaExecutorTaskFactory implements ExecutorTaskFactory {
    @Override
    public ExecutorTask createTask(Protos.TaskInfo taskInfo, ExecutorDriver executorDriver) throws ExecutorTaskException {
        try {
            return ProcessTask.create(executorDriver, taskInfo);
        } catch (InvalidProtocolBufferException e) {
            throw new ExecutorTaskException(e);
        }
    }
}
