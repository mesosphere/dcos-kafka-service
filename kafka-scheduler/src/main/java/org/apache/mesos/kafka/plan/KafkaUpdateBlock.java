package org.apache.mesos.kafka.plan;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;
import org.apache.mesos.kafka.offer.OfferUtils;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskRequirement;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KafkaUpdateBlock implements Block {
  private final Log log = LogFactory.getLog(KafkaUpdateBlock.class);

  private Status status = Status.Pending;
  private KafkaOfferRequirementProvider offerReqProvider;
  private String targetConfigName = null;
  private KafkaStateService state = null;
  private int brokerId;
  private UUID blockId;
  private final Object taskLock = new Object();
  private List<TaskID> pendingTasks;
  private TaskInfo cachedTaskInfo;

  public KafkaUpdateBlock(
    KafkaStateService state,
    KafkaOfferRequirementProvider offerReqProvider,
    String targetConfigName,
    int brokerId) {

    this.state = state;
    this.offerReqProvider = offerReqProvider;
    this.targetConfigName = targetConfigName;
    this.brokerId = brokerId;
    this.blockId = UUID.randomUUID();
    this.cachedTaskInfo = getTaskInfo();

    pendingTasks = getUpdateIds();
    initializeStatus();
  }

  @Override
  public Status getStatus() {
    return status;
  }

  @Override
  public void setStatus(Status newStatus) {
    Status oldStatus = status;
    status = newStatus;
    log.info(getName() + ": changed status from: " + oldStatus + " to: " + status);
  }

  @Override
  public boolean isPending() {
    return status == Status.Pending;
  }

  @Override
  public boolean isInProgress() {
    return status == Status.InProgress;
  }

  @Override
  public boolean isComplete() {
    return status == Status.Complete;
  }

  @Override
  public OfferRequirement start() {
    log.info("Starting block: " + getName() + " with status: " + getStatus());

    if (!isPending()) {
      return null;
    }

    if (taskIsRunning() || taskIsStaging()) {
      log.info("Adding to restart task list. Block: " + getName() + " Status: " + getTaskStatus());
      KafkaScheduler.restartTasks(taskIdsToStrings(getUpdateIds()));
      return null;
    }

    setStatus(Status.InProgress);
    OfferRequirement offerReq = getOfferRequirement();
    setPendingTasks(offerReq);
    return offerReq;
  }

  @Override
  public void update(TaskStatus taskStatus) {
    synchronized (taskLock) {
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

  @Override
  public UUID getId() {
    return blockId;
  }

  @Override
  public String getMessage() {
    return "Broker-" + getBrokerId() + " is " + getStatus();
  }

  @Override
  public String getName() {
    return getBrokerName();
  }

  public int getBrokerId() {
    return brokerId;
  }

  private void initializeStatus() {
    log.info("Setting initial status for: " + getName());

    TaskInfo taskInfo = getTaskInfo();

    if (taskInfo != null) {
      String configName = OfferUtils.getConfigName(taskInfo);
      log.info("TargetConfigName: " + targetConfigName + " currentConfigName: " + configName);
      if (configName.equals(targetConfigName)) {
        setStatus(Status.Complete);
      } else {
        setStatus(Status.Pending);
      }
    }

    log.info("Status initialized as " + getStatus() + " for block: " + getName());
  }

  private synchronized TaskInfo getTaskInfo() {
    try {
      List<TaskInfo> allTasks = state.getTaskInfos();

      for (TaskInfo taskInfo : allTasks) {
        if (taskInfo.getName().equals(getBrokerName())) {
          cachedTaskInfo = taskInfo;
          return cachedTaskInfo;
        }
      }
    } catch (Exception ex) {
      log.error("Failed to retrieve TaskInfo with exception: " + ex);
    }

    return cachedTaskInfo;
  }

  private OfferRequirement getOfferRequirement() {
    TaskInfo taskInfo = getTaskInfo();

    if (taskInfo == null) {
      return offerReqProvider.getNewOfferRequirement(targetConfigName, brokerId);
    } else {
      return offerReqProvider.getUpdateOfferRequirement(targetConfigName, taskInfo);
    }
  }

  private void setPendingTasks(OfferRequirement offerReq) {
    pendingTasks = new ArrayList<TaskID>();
    // in practice there should only be one TaskRequirement, see PersistentOfferRequirementProvider
    for (TaskRequirement taskRequirement : offerReq.getTaskRequirements()) {
      pendingTasks.add(taskRequirement.getTaskInfo().getTaskId());
    }
  }

  private List<String> taskIdsToStrings(List<TaskID> taskIds) {
    List<String> taskIdStrings = new ArrayList<String>();

    for (TaskID taskId : taskIds) {
      taskIdStrings.add(taskId.getValue());
    }

    return taskIdStrings;
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

  private TaskStatus getTaskStatus() {
    TaskInfo taskInfo = getTaskInfo();

    if (null != taskInfo) {
      try {
        return state.getTaskStatus(taskInfo.getName());
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

  private boolean isTerminalState(TaskStatus taskStatus) {
    return taskStatus.getState().equals(TaskState.TASK_FAILED)
      || taskStatus.getState().equals(TaskState.TASK_FINISHED)
      || taskStatus.getState().equals(TaskState.TASK_KILLED)
      || taskStatus.getState().equals(TaskState.TASK_LOST)
      || taskStatus.getState().equals(TaskState.TASK_ERROR);
  }

  private List<TaskID> getUpdateIds() {
    List<TaskID> taskIds = new ArrayList<TaskID>();
    TaskInfo taskInfo = getTaskInfo();

    if (taskInfo != null) {
      taskIds.add(taskInfo.getTaskId());
    }

    return taskIds;
  }
}
