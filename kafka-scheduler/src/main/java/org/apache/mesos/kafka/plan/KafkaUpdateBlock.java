package org.apache.mesos.kafka.plan;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;
import org.apache.mesos.kafka.offer.OfferUtils;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Status;

import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;

public class KafkaUpdateBlock implements Block {
  private final Log log = LogFactory.getLog(KafkaUpdateBlock.class);

  private Status status = Status.Pending;
  private KafkaOfferRequirementProvider offerReqProvider;
  private String targetConfigName = null;
  private KafkaStateService state = null;
  private int brokerId;
  private TaskInfo taskInfo;
  private final Integer taskLock = 0;
  private List<TaskID> pendingTasks;

  public KafkaUpdateBlock(
      KafkaOfferRequirementProvider offerReqProvider,
      String targetConfigName,
      int brokerId) {

    state = KafkaStateService.getStateService();
    this.offerReqProvider = offerReqProvider;
    this.targetConfigName = targetConfigName;
    this.brokerId = brokerId;
    this.taskInfo = getTaskInfo();

    pendingTasks = getUpdateIds();
    initializeStatus();
  }

  private void initializeStatus() {
    log.info("Setting initial status for: " + getName());

    if (taskInfo != null) {
      String configName = OfferUtils.getConfigName(taskInfo);

      if (configName.equals(targetConfigName)) {
        setStatus(Status.Complete);
      } else {
        setStatus(Status.Pending);
      }
    }
  }

  public void setStatus(Status newStatus) {
    Status oldStatus = status;
    status = newStatus;
    log.info(getName() + ": changed status from: " + oldStatus + " to: " + status);
  }

  private TaskInfo getTaskInfo() {
    try {
      List<TaskInfo> allTasks = state.getTaskInfos();

      for (TaskInfo taskInfo : allTasks) {
        if (taskInfo.getName().equals(getBrokerName())) {
          return taskInfo;
        }
      }
    } catch (Exception ex) {
      log.error("Failed to retrieve TaskInfo with exception: " + ex);
    }

    return null;
  }

  private OfferRequirement getOfferRequirement() {
    if (taskInfo == null) {
      return offerReqProvider.getNewOfferRequirement(targetConfigName, brokerId);
    } else {
      return offerReqProvider.getUpdateOfferRequirement(targetConfigName, taskInfo);
    }
  }

  public int getId() {
    return brokerId;
  }

  public Status getStatus() {
    return status;
  }

  public OfferRequirement start() {
    log.info("Starting block: " + getName() + " with status: " + getStatus());

    if(!isPending()) {
      return null;
    }

    if (taskIsRunning() || taskIsStaging()) {
      KafkaScheduler.restartTasks(taskIdsToStrings(getUpdateIds()));
      return null;
    }

    setStatus(Status.InProgress);
    OfferRequirement offerReq = getOfferRequirement();
    setPendingTasks(offerReq);
    return offerReq;
  }

  private void setPendingTasks(OfferRequirement offerReq) {
    pendingTasks = new ArrayList<TaskID>();

    for (TaskInfo taskInfo : offerReq.getTaskInfos()) {
      pendingTasks.add(taskInfo.getTaskId());
    }
  }

  private List<String> taskIdsToStrings(List<TaskID> taskIds) {
    List<String> taskIdStrings = new ArrayList<String>();

    for (TaskID taskId : taskIds) {
      taskIdStrings.add(taskId.getValue());
    }

    return taskIdStrings;
  }

  public List<TaskID> getUpdateIds() {
    List<TaskID> taskIds = new ArrayList<TaskID>();

    if (taskInfo != null) {
      taskIds.add(taskInfo.getTaskId());
    }

    return taskIds;
  }

  public void update(TaskStatus taskStatus) {
    synchronized(taskLock) {
      log.info(getName() + " has pending tasks: " + pendingTasks);

      if (!isRelevantStatus(taskStatus) || isPending()) {
        log.warn("Received irrelevant TaskStatus: " + taskStatus);
        return;
      }

      if (taskStatus.getState().equals(TaskState.TASK_RUNNING)) {
        List<TaskID> updatedPendingTasks = new ArrayList<TaskID>();
        for (TaskID pendingTaskId : pendingTasks) {
          if (!taskStatus.getTaskId().equals(pendingTaskId)) {
            updatedPendingTasks.add(pendingTaskId);
          }
        }

        pendingTasks = updatedPendingTasks;
      } else if (isInProgress() && isTerminalState(taskStatus)) {
        log.info("Received terminal while InProgress TaskStatus: " + taskStatus);
        setStatus(Status.Pending);
        return;
      } else {
        log.warn("TaskStatus with no effect encountered: " + taskStatus);
      }

      if (pendingTasks.size() == 0) {
        setStatus(Status.Complete);
      }
    }
  }

  public boolean isRelevantStatus(TaskStatus taskStatus) {
    if (taskStatus.getReason().equals(TaskStatus.Reason.REASON_RECONCILIATION)) {
      return false;
    }

    for (TaskID pendingTaskId : pendingTasks) {
      if (taskStatus.getTaskId().equals(pendingTaskId)) {
        return true;
      }
    }

    return false;
  }

  public boolean isPending() {
    return status == Status.Pending;
  }

  public boolean isInProgress() {
    return status == Status.InProgress;
  }

  public boolean isComplete() {
    return status == Status.Complete;
  }

  private boolean taskIsRunning() {
    return taskInState(TaskState.TASK_RUNNING);
  }

  private boolean taskIsStaging() {
    return taskInState(TaskState.TASK_STAGING);
  }

  private boolean taskInState(TaskState state) {
    TaskStatus taskStatus = getTaskStatus();

    if (null != taskStatus) {
      return taskStatus.getState().equals(state);
    } else {
      return false;
    }
  }

  public TaskStatus getTaskStatus() {
    if (null != taskInfo) {
      try {
        String taskId = taskInfo.getTaskId().getValue();
        return state.getTaskStatus(taskId);
      } catch (Exception ex) {
        log.error("Failed to retrieve TaskStatus with exception: " + ex);
      }
    } else {
      return null;
    }

    return null;
  }

  public String getBrokerName() {
    return "broker-" + brokerId;
  }

  public String getName() {
    return getBrokerName();
  }

  private boolean isTerminalState(TaskStatus taskStatus) {
    return taskStatus.getState().equals(TaskState.TASK_FAILED)
      || taskStatus.getState().equals(TaskState.TASK_FINISHED)
      || taskStatus.getState().equals(TaskState.TASK_KILLED)
      || taskStatus.getState().equals(TaskState.TASK_LOST)
      || taskStatus.getState().equals(TaskState.TASK_ERROR);
  }
}

