package com.mesosphere.dcos.kafka.repair;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.scheduler.recovery.TaskFailureListener;
import org.apache.mesos.state.StateStore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Arrays;

/**
 * Created by gabriel on 8/8/16.
 */
public class KafkaTaskFailureListener implements TaskFailureListener {
    private final Log log = LogFactory.getLog(getClass());
    private final StateStore stateStore;

    public KafkaTaskFailureListener(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public void taskFailed(Protos.TaskID taskId) {
        try {
            Protos.TaskInfo taskInfo = stateStore.fetchTask(TaskUtils.toTaskName(taskId));
            taskInfo = FailureUtils.markFailed(taskInfo);
            stateStore.storeTasks(Arrays.asList(taskInfo));
        } catch (TaskException e) {
            log.error("Failed to fetch Task for taskId: " + taskId + " with exception:", e);
        }
    }
}
