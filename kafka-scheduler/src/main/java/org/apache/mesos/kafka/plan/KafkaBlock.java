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

public class KafkaBlock implements Block {
  private final Log log = LogFactory.getLog(KafkaBlock.class);

  private Status status = Status.Pending;
  private KafkaOfferRequirementProvider offerReqProvider;
  private String targetConfigName = null;
  private KafkaStateService state = null;
  private int brokerId;
  private TaskInfo taskInfo;
  private final Integer taskLock = 0;
  private List<TaskID> pendingTasks;

  public KafkaBlock(
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
    log.info(getName() + " setting initial status.");

    if (taskInfo != null) {
      String configName = OfferUtils.getConfigName(taskInfo);

      if (configName.equals(targetConfigName)) {
        setStatus(Status.Complete);
      } else {
        setStatus(Status.Pending);
      }
    }
  }

  private void setStatus(Status newStatus) {
    Status oldStatus = status;
    status = newStatus;

    log.info(getName() + ": changing status from: " + oldStatus + " to: " + status);
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
    log.info("Starting block: " + getName());

    if (taskIsRunning()) {
      KafkaScheduler.restartTasks(taskIdsToStrings(getUpdateIds()));
      return null;
    }

    setStatus(Status.InProgress);
    return getOfferRequirement();
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
      if (!taskStatus.getState().equals(TaskState.TASK_RUNNING) ||
          isPending()) {
        return;
      }

      List<TaskID> updatedPendingTasks = new ArrayList<TaskID>();

      for (TaskID pendingTaskId : pendingTasks) {
        if (!taskStatus.getTaskId().equals(pendingTaskId)) {
          updatedPendingTasks.add(pendingTaskId);
        }
      }

      pendingTasks = updatedPendingTasks;
      log.info(getName() + " has pending tasks: " + pendingTasks);

      if (pendingTasks.size() == 0) {
        setStatus(Status.Complete);
      } else {
        setStatus(Status.Pending);
      }
    }
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
}

