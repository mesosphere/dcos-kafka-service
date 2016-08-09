package com.mesosphere.dcos.kafka.state;

import com.google.protobuf.TextFormat;
import com.mesosphere.dcos.kafka.config.ZookeeperConfiguration;
import com.mesosphere.dcos.kafka.offer.OfferUtils;
import org.apache.mesos.Protos.*;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.reconciliation.TaskStatusProvider;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Read/write interface for storing and retrieving information about Framework tasks. The underlying data is stored
 * against Executor IDs of "broker-0", "broker-1", etc.
 */
public class FrameworkState implements TaskStatusProvider {
    private static final Logger log = LoggerFactory.getLogger(FrameworkState.class);

    private final StateStore stateStore;

    public FrameworkState(ZookeeperConfiguration zkConfig) {
        this.stateStore = new CuratorStateStore(zkConfig.getFrameworkName(), zkConfig.getMesosZkUri());
    }

    public StateStore getStateStore() {
        return stateStore;
    }

    public FrameworkID getFrameworkId() {
        try {
            return stateStore.fetchFrameworkId();
        } catch (StateStoreException ex) {
            log.warn("Failed to get FrameworkID. "
                    + "This is expected when the service is starting for the first time.", ex);
        }
        return null;
    }

    public void setFrameworkId(FrameworkID fwkId) throws StateStoreException {
        try {
            log.info(String.format("Storing framework id: %s", fwkId));
            stateStore.storeFrameworkId(fwkId);
        } catch (StateStoreException ex) {
            log.error("Failed to set FrameworkID: " + fwkId, ex);
            throw ex;
        }
    }

    public void recordTasks(List<TaskInfo> taskInfos) throws StateStoreException {
        log.info(String.format("Recording %d updated TaskInfos/TaskStatuses:", taskInfos.size()));
        List<TaskStatus> taskStatuses = new ArrayList<>();
        for (TaskInfo taskInfo : taskInfos) {
            TaskStatus taskStatus = TaskStatus.newBuilder()
                    .setTaskId(taskInfo.getTaskId())
                    .setExecutorId(taskInfo.getExecutor().getExecutorId())
                    .setState(TaskState.TASK_STAGING)
                    .build();
            log.info(String.format("- %s => %s", taskInfo, taskStatus));
            log.info("Marking stopped task as failed: {}", TextFormat.shortDebugString(taskInfo));
            taskStatuses.add(taskStatus);
        }

        stateStore.storeTasks(taskInfos);
        for (TaskStatus taskStatus : taskStatuses) {
            recordTaskStatus(taskStatus);
        }
    }

    public void updateStatus(TaskStatus taskStatus) throws StateStoreException {
        log.info(String.format("Recording updated TaskStatus to state store: %s", taskStatus));
        recordTaskStatus(taskStatus);
    }

    public List<TaskInfo> getTerminatedTaskInfos() throws Exception {
        return new ArrayList(stateStore.fetchTerminatedTasks());
    }

    public int getRunningBrokersCount() throws StateStoreException {
        int count = 0;

        for (TaskStatus taskStatus : getTaskStatuses()) {
            if (taskStatus.getState().equals(TaskState.TASK_RUNNING)) {
                count++;
            }
        }

        return count;
    }

    @Override
    public Set<TaskStatus> getTaskStatuses() throws StateStoreException {
        Set<TaskStatus> taskStatuses = new HashSet<TaskStatus>();
        taskStatuses.addAll(stateStore.fetchStatuses());
        return taskStatuses;
    }

    public List<TaskInfo> getTaskInfos() throws StateStoreException {
        List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();
        taskInfos.addAll(stateStore.fetchTasks());
        return taskInfos;
    }

    public List<Resource> getExpectedResources() {
        List<Resource> resources = new ArrayList<>();
        try {
            for (TaskInfo taskInfo : getTaskInfos()) {
                resources.addAll(taskInfo.getResourcesList());
                if (taskInfo.hasExecutor()) {
                    resources.addAll(taskInfo.getExecutor().getResourcesList());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve all Task information", ex);
            return resources;
        }
        return resources;
    }

    /**
     * Returns the full Task ID (including UUID) for the provided Broker index, or {@code null} if none is found.
     */
    public TaskID getTaskIdForBroker(Integer brokerId) throws Exception {
        TaskInfo taskInfo = getTaskInfoForBroker(brokerId);
        return (taskInfo != null) ? taskInfo.getTaskId() : null;
    }

    /**
     * Returns the TaskInfo for the provided Broker index, or {@code null} if none is found.
     */
    public TaskInfo getTaskInfoForBroker(Integer brokerId) throws Exception {
        try {
            return stateStore.fetchTask(OfferUtils.idToName(brokerId));
        } catch (StateStoreException e) {
            log.error(String.format("Unable to retrieve TaskInfo for broker %d", brokerId), e);
            return null;
        }
    }

    /**
     * Returns the TaskStatus for the provided Broker index, or {@code null} if none is found.
     */
    public TaskStatus getTaskStatusForBroker(Integer brokerId) throws Exception {
        try {
            return stateStore.fetchStatus(OfferUtils.idToName(brokerId));
        } catch (StateStoreException e) {
            log.error(String.format("Unable to retrieve TaskStatus for broker %d", brokerId), e);
            return null;
        }
    }

    public void recordTaskInfo(TaskInfo taskInfo) throws StateStoreException {
        log.info(String.format("Recording updated TaskInfo to state store: %s", taskInfo));
        stateStore.storeTasks(Arrays.asList(taskInfo));
    }

    private void recordTaskStatus(TaskStatus taskStatus) throws StateStoreException {
        if (!taskStatus.getState().equals(TaskState.TASK_STAGING)
                && !taskStatusExists(taskStatus)) {
            log.warn("Dropping non-STAGING status update because the ZK path doesn't exist: "
                    + taskStatus);
        } else {
            stateStore.storeStatus(taskStatus);
        }
    }

    private boolean taskStatusExists(TaskStatus taskStatus) throws StateStoreException {
        String taskName;
        try {
            taskName = TaskUtils.toTaskName(taskStatus.getTaskId());
        } catch (TaskException e) {
            throw new StateStoreException(String.format(
                    "Failed to get TaskName/ExecName from TaskStatus %s", taskStatus), e);
        }
        try {
            stateStore.fetchStatus(taskName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
