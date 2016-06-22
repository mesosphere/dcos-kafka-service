package org.apache.mesos.kafka.state;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.executor.ExecutorTaskException;
import org.apache.mesos.executor.ExecutorUtils;
import org.apache.mesos.kafka.offer.OfferUtils;
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
public class FrameworkStateService implements Observer, TaskStatusProvider {
    private static final Log log = LogFactory.getLog(FrameworkStateService.class);

    private final StateStore stateStore;

    public FrameworkStateService(String zkHost, String zkRoot) {
        this.stateStore = new CuratorStateStore(zkRoot, zkHost);
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

    public void setFrameworkId(FrameworkID fwkId) {
        try {
            log.info(String.format("Storing framework id: %s", fwkId));
            stateStore.storeFrameworkId(fwkId);
        } catch (StateStoreException ex) {
            log.error("Failed to set FrameworkID: " + fwkId, ex);
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

    public void update(Observable observable, Object obj) {
        TaskStatus taskStatus = (TaskStatus) obj;
        log.info(String.format("Recording updated TaskStatus to state store: %s", taskStatus));
        try {
            recordTaskStatus(taskStatus);
        } catch (Exception ex) {
            log.error("Failed to update TaskStatus: " + taskStatus, ex);
        }
    }

    public List<TaskInfo> getTerminatedTaskInfos() throws Exception {
        List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();

        for (TaskStatus taskStatus : getTaskStatuses()) {
            if (TaskUtils.isTerminated(taskStatus)) {
                taskInfos.add(stateStore.fetchTask(
                        TaskUtils.toTaskName(taskStatus.getTaskId()),
                        ExecutorUtils.toExecutorName(taskStatus.getExecutorId())));
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

    public TaskStatus fetchStatus(TaskInfo taskInfo) throws StateStoreException {
        return stateStore.fetchStatus(taskInfo.getName(), taskInfo.getExecutor().getName());
    }

    @Override
    public Set<TaskStatus> getTaskStatuses() throws StateStoreException {
        Set<TaskStatus> taskStatuses = new HashSet<TaskStatus>();
        for (String execName : stateStore.fetchExecutorNames()) {
            taskStatuses.addAll(stateStore.fetchStatuses(execName));
        }
        return taskStatuses;
    }

    public List<TaskInfo> getTaskInfos() throws StateStoreException {
        List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();
        for (String execName : stateStore.fetchExecutorNames()) {
            taskInfos.addAll(stateStore.fetchTasks(execName));
        }
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
            return null;
        }
        return resources;
    }

    /**
     * Returns the full Task ID (including UUID) for the provided Broker index, or {@code null} if
     * none is found.
     */
    public TaskID getTaskIdForBroker(Integer brokerId) throws Exception {
        String brokerName = OfferUtils.idToName(brokerId);
        for (TaskInfo taskInfo : getTaskInfos()) {
            if (taskInfo.getName().equals(brokerName)) {
                return taskInfo.getTaskId();
            }
        }
        return null;
    }

    public void recordTaskInfo(TaskInfo taskInfo) throws StateStoreException {
        log.info(String.format("Recording updated TaskInfo to state store: %s", taskInfo));
        stateStore.storeTasks(Arrays.asList(taskInfo));
    }

    public void deleteTask(TaskID taskId) {
        try {
            // Shortcut: Treat the 'broker-N' task name as the executor name. For us they're the same.
            // Alternate: Scan all tasks for an exact match on the Task ID directly.
            String executorName = TaskUtils.toTaskName(taskId);
            log.info(String.format("Clearing task from state store: %s", executorName));
            stateStore.clearExecutor(executorName);
        } catch (StateStoreException | TaskException ex) {
            log.error("Failed to delete Task: " + taskId, ex);
        }
    }

    private void recordTaskStatus(TaskStatus taskStatus) {
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
        String execName;
        try {
            taskName = TaskUtils.toTaskName(taskStatus.getTaskId());
            execName = ExecutorUtils.toExecutorName(taskStatus.getExecutorId());
        } catch (TaskException | ExecutorTaskException e) {
            throw new StateStoreException(String.format(
                    "Failed to get TaskName/ExecName from TaskStatus %s", taskStatus), e);
        }
        try {
            stateStore.fetchStatus(taskName, execName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
