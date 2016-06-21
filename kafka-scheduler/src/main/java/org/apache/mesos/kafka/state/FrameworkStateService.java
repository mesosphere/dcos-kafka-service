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
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.executor.ExecutorUtils;
import org.apache.mesos.kafka.offer.OfferUtils;
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
    private static final String EXEC_NAME_SUFFIX = "-executor";
    private static final Log log = LogFactory.getLog(FrameworkStateService.class);

    private final StateStore stateStore;

    public FrameworkStateService(String zkHost, String zkRoot) {
        this.stateStore = new CuratorStateStore(zkRoot, zkHost);
    }

    public FrameworkID getFrameworkId() {
        try {
            return stateStore.fetchFrameworkId();
        } catch (StateStoreException ex) {
            log.error("Failed to get FrameworkID", ex);
        }
        return null;
    }

    public void setFrameworkId(FrameworkID fwkId) {
        try {
            stateStore.storeFrameworkId(fwkId);
        } catch (StateStoreException ex) {
            log.error("Failed to set FrameworkID: " + fwkId, ex);
        }
    }

    public void recordTasks(List<TaskInfo> taskInfos) throws StateStoreException {
        recordTaskInfos(taskInfos);
        List<TaskStatus> taskStatuses = taskInfosToTaskStatuses(taskInfos);
        recordTaskStatuses(taskStatuses);
    }

    public void update(Observable observable, Object obj) {
        TaskStatus taskStatus = (TaskStatus) obj;

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
            if (isRunning(taskStatus)) {
                count++;
            }
        }

        return count;
    }

    public TaskStatus getTaskStatus(String taskName) throws StateStoreException {
        return stateStore.fetchStatus(taskName, toExecName(taskName));
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

    public String getTaskIdForBroker(Integer brokerId) throws Exception {
        String brokerName = OfferUtils.idToName(brokerId);
        for (TaskInfo taskInfo : getTaskInfos()) {
            if (taskInfo.getName().equals(brokerName)) {
                return taskInfo.getTaskId().getValue();
            }
        }
        return null;
    }

    public void recordTaskInfo(TaskInfo taskInfo) throws StateStoreException {
        stateStore.storeTasks(Arrays.asList(taskInfo));
    }

    private boolean isRunning(TaskStatus taskStatus) {
        TaskState taskState = taskStatus.getState();
        return taskState.equals(TaskState.TASK_RUNNING);
    }

    private void recordTaskInfos(List<TaskInfo> taskInfos) throws StateStoreException {
        for (TaskInfo taskInfo : taskInfos) {
            recordTaskInfo(taskInfo);
        }
    }

    private void recordTaskStatus(TaskStatus taskStatus) throws StateStoreException {
        String taskName = OfferUtils.getTaskName(taskStatus.getTaskId().getValue());
        if (!taskStatus.getState().equals(TaskState.TASK_STAGING)
                && !taskStatusExists(taskName)) {
            log.warn("Dropping non-STAGING status update because the ZK path doesn't exist: "
                + taskStatus);
        } else {
            stateStore.storeStatus(taskStatus);
        }
    }

    public void deleteTask(String taskId) {
        try {
            stateStore.clearExecutor(OfferUtils.getTaskName(taskId));
        } catch (StateStoreException ex) {
            log.error("Failed to delete Task: " + taskId + " with exception: " + ex);
        }
    }

    private void recordTaskStatuses(List<TaskStatus> taskStatuses)
            throws StateStoreException {
        for (TaskStatus taskStatus : taskStatuses) {
            recordTaskStatus(taskStatus);
        }
    }

    private List<TaskStatus> taskInfosToTaskStatuses(List<TaskInfo> taskInfos) {
        List<TaskStatus> taskStatuses = new ArrayList<TaskStatus>();

        for (TaskInfo taskInfo : taskInfos) {
            taskStatuses.add(TaskStatus.newBuilder()
                    .setTaskId(taskInfo.getTaskId())
                    .setState(TaskState.TASK_STAGING)
                    .build());
        }

        return taskStatuses;
    }

    private boolean taskStatusExists(String taskName) {
        try {
            getTaskStatus(taskName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String toExecName(String taskName) {
        return taskName + EXEC_NAME_SUFFIX;
    }
}
