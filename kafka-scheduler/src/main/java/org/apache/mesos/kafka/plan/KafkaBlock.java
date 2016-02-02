package org.apache.mesos.kafka.plan;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.offer.OfferUtils;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;

public class KafkaBlock implements Block {
  private final Log log = LogFactory.getLog(KafkaBlock.class);

  private String targetConfigName = null;
  private KafkaStateService state = null;
  private int brokerId;

  public KafkaBlock(String targetConfigName, int brokerId) {
    state = KafkaStateService.getStateService();
    this.targetConfigName = targetConfigName;
    this.brokerId = brokerId;
  }

  public boolean isInProgress() {
    try {
      return hasBeenTerminated() || hasNeverBeenLaunched();
    } catch (Exception ex) {
      log.error("Failed to determine whether the block was in progress with exception: " + ex);
      return false;
    }
  }

  public boolean hasBeenTerminated() throws Exception {
    List<TaskInfo> terminatedTasks = state.getTerminatedTaskInfos();

    for (TaskInfo taskInfo : terminatedTasks) {
      if (taskInfo.getName().equals("broker-" + brokerId)) {
        return true;
      }
    }

    return false;
  }

  public boolean hasNeverBeenLaunched() throws Exception {
    return null == getTaskInfo();
  }

  public boolean isStaging() {
    return taskIsStaging();
  }

  public boolean isComplete() {
    if (isInProgress()) {
      return false;
    } else {
      String currConfigName = OfferUtils.getConfigName(getTaskInfo());
      if (targetConfigName.equals(currConfigName) &&
          taskIsRunning()) {
        return true;
      }
    }

    return false;
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

  private boolean taskIsRunning() {
    return taskInState(TaskState.TASK_RUNNING);
  }

  private boolean taskIsStaging() {
    return taskInState(TaskState.TASK_STAGING);
  }

  private boolean taskInState(TaskState state) {
    TaskStatus status = getTaskStatus();

    if (null != status) {
      return status.getState().equals(state);
    } else {
      return false;
    }
  }

  public TaskStatus getTaskStatus() {
    TaskInfo taskInfo = getTaskInfo();

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

  public int getBrokerId() {
    return brokerId;
  }
}

