package com.mesosphere.dcos.kafka.state;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mesosphere.dcos.kafka.config.ZookeeperConfiguration;
import com.mesosphere.dcos.kafka.offer.OfferUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.reconciliation.TaskStatusProvider;
import org.apache.mesos.state.CuratorStateStore;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreException;

/**
 * Read/write interface for storing and retrieving information about Framework tasks.
 * The underlying data is stored against Executor IDs of "broker-0", "broker-1", etc.
 */
public class FrameworkState implements TaskStatusProvider {
    private static final Log log = LogFactory.getLog(FrameworkState.class);

    private final StateStore stateStore;

    public FrameworkState(ZookeeperConfiguration zkConfig) {
        this.stateStore = new CuratorStateStore(zkConfig.getZkRoot(), zkConfig.getZkAddress());
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
        List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();

        for (TaskStatus taskStatus : getTaskStatuses()) {
            if (TaskUtils.isTerminated(taskStatus)) {
                taskInfos.add(stateStore.fetchTask(
                        TaskUtils.toTaskName(taskStatus.getTaskId())));
            }
        }

        return taskInfos;
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
     * Returns the full Task ID (including UUID) for the provided Broker index, or {@code null} if
     * none is found.
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

    public void deleteTask(TaskID taskId) {
        try {
            String taskName = TaskUtils.toTaskName(taskId);
            log.info(String.format("Clearing task from state store: %s", taskName));
            stateStore.clearTask(taskName);
        } catch (StateStoreException | TaskException ex) {
            log.error("Failed to delete Task: " + taskId, ex);
        }
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
