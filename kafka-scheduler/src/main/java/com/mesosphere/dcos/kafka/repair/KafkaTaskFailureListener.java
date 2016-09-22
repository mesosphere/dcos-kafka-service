package com.mesosphere.dcos.kafka.repair;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.scheduler.recovery.TaskFailureListener;
import org.apache.mesos.state.StateStore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Arrays;
import java.util.Optional;

public class KafkaTaskFailureListener implements TaskFailureListener {
    private final Log log = LogFactory.getLog(getClass());
    private final StateStore stateStore;

    public KafkaTaskFailureListener(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public void taskFailed(Protos.TaskID taskId) {
        try {
            Optional<Protos.TaskInfo> taskInfoOption = stateStore.fetchTask(TaskUtils.toTaskName(taskId));
            if (taskInfoOption.isPresent()) {
                Protos.TaskInfo taskInfo = FailureUtils.markFailed(taskInfoOption.get());
                stateStore.storeTasks(Arrays.asList(taskInfo));
            } else {
                throw new TaskException("Unable to fetch task with taskID: " + taskId);
            }
        } catch (TaskException e) {
            log.error("Failed to fetch Task for taskId: " + taskId + " with exception:", e);
        }
    }
}
