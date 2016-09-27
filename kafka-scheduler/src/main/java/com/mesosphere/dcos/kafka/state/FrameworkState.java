package com.mesosphere.dcos.kafka.state;

import com.google.protobuf.TextFormat;
import com.mesosphere.dcos.kafka.config.ZookeeperConfiguration;
import com.mesosphere.dcos.kafka.offer.OfferUtils;
import org.apache.mesos.Protos.*;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.reconciliation.TaskStatusProvider;
import org.apache.mesos.state.SchedulerState;
import org.apache.mesos.state.StateStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Read/write interface for storing and retrieving information about Framework tasks. The underlying data is stored
 * against Executor IDs of "broker-0", "broker-1", etc.
 */
public class FrameworkState extends SchedulerState implements TaskStatusProvider {
    private static final Logger log = LoggerFactory.getLogger(FrameworkState.class);

    public FrameworkState(ZookeeperConfiguration zkConfig) {
        super(new CuratorStateStore(zkConfig.getFrameworkName(), zkConfig.getMesosZkUri()));
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

        getStateStore().storeTasks(taskInfos);
        for (TaskStatus taskStatus : taskStatuses) {
            recordTaskStatus(taskStatus);
        }
    }

    public void updateStatus(TaskStatus taskStatus) throws StateStoreException {
        log.info(String.format("Recording updated TaskStatus to state store: %s", taskStatus));
        recordTaskStatus(taskStatus);
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
        taskStatuses.addAll(getStateStore().fetchStatuses());
        return taskStatuses;
    }

    public List<TaskInfo> getTaskInfos() throws StateStoreException {
        List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();
        taskInfos.addAll(getStateStore().fetchTasks());
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
        Optional<TaskInfo> taskInfoOptional = getTaskInfoForBroker(brokerId);
        return taskInfoOptional.isPresent() ? taskInfoOptional.get().getTaskId() : null;
    }

    /**
     * Returns the TaskInfo for the provided Broker index, or {@code null} if none is found.
     */
    public Optional<TaskInfo> getTaskInfoForBroker(Integer brokerId) throws Exception {
        try {
            return getStateStore().fetchTask(OfferUtils.brokerIdToTaskName(brokerId));
        } catch (StateStoreException e) {
            log.warn(String.format(
                    "Failed to get TaskInfo for broker %d. This is expected when the service is "
                            + "starting for the first time.", brokerId), e);
            return Optional.empty();
        }
    }

    /**
     * Returns the TaskStatus for the provided Broker index, or {@code null} if none is found.
     */
    public Optional<TaskStatus> getTaskStatusForBroker(Integer brokerId) throws Exception {
        try {
            return getStateStore().fetchStatus(OfferUtils.brokerIdToTaskName(brokerId));
        } catch (StateStoreException e) {
            log.warn(String.format(
                    "Failed to get TaskStatus for broker %d. This is expected when the service is "
                            + "starting for the first time.", brokerId), e);
            return Optional.empty();
        }
    }

    public void recordTaskInfo(TaskInfo taskInfo) throws StateStoreException {
        log.info(String.format("Recording updated TaskInfo to state store: %s", taskInfo));
        getStateStore().storeTasks(Arrays.asList(taskInfo));
    }

    private void recordTaskStatus(TaskStatus taskStatus) throws StateStoreException {
        if (!taskStatus.getState().equals(TaskState.TASK_STAGING)
                && !taskStatusExists(taskStatus)) {
            log.warn("Dropping non-STAGING status update because the ZK path doesn't exist: "
                    + taskStatus);
        } else {
            getStateStore().storeStatus(taskStatus);
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
            getStateStore().fetchStatus(taskName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}