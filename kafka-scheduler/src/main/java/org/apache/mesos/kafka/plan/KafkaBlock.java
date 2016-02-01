package org.apache.mesos.kafka.plan;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.apache.mesos.Protos.TaskInfo;

public class KafkaBlock implements Block {
  private final Log log = LogFactory.getLog(KafkaBlock.class);

  private KafkaConfigService config = null;
  private KafkaStateService state = null;
  private int brokerId;

  public KafkaBlock(KafkaConfigService config, int brokerId) {
    state = KafkaStateService.getStateService();
    this.config = config;
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

  private boolean hasBeenTerminated() throws Exception {
    List<TaskInfo> terminatedTasks = state.getTerminatedTaskInfos();

    for (TaskInfo taskInfo : terminatedTasks) {
      if (taskInfo.getName().equals("broker-" + brokerId)) {
        return true;
      }
    }

    return false;
  }

  private boolean hasNeverBeenLaunched() throws Exception {
    List<TaskInfo> allTasks = state.getTaskInfos();

    for (TaskInfo taskInfo : allTasks) {
      if (taskInfo.getName().equals("broker-" + brokerId)) {
        return false; 
      }
    }

    return true;
  }

  public boolean isComplete() {
    return false;
  }

  public int getBrokerId() {
    return brokerId;
  }
}

