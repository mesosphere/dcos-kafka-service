package org.apache.mesos.kafka.offer;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.offer.PlacementStrategy;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;

public class NodePlacementStrategy implements PlacementStrategy {
  private static final Log log = LogFactory.getLog(NodePlacementStrategy.class);

  private final KafkaStateService state;

  public NodePlacementStrategy(KafkaStateService state) {
    this.state = state;
  }

  public List<SlaveID> getAgentsToAvoid(TaskInfo taskInfo) {
    List<SlaveID> agents = null;

    try {
      agents = getAgentsToAvoidInternal(taskInfo);
    } catch(Exception ex) {
      log.error("Failed to retrieve TaskInfos");
    }

    return agents;
  }

  public List<SlaveID> getAgentsToColocate(TaskInfo taskInfo) {
    return null;
  }

  private List<SlaveID> getAgentsToAvoidInternal(TaskInfo taskInfo) throws Exception {
    List<SlaveID> agentsToAvoid = new ArrayList<SlaveID>();

    List<TaskInfo> otherTaskInfos = getOtherTaskInfos(taskInfo);
    for (TaskInfo otherInfo : otherTaskInfos) {
      agentsToAvoid.add(otherInfo.getSlaveId());
      log.info("Avoiding: " + otherInfo.getName() + " on agent: " + otherInfo.getSlaveId());
    }

    return agentsToAvoid;
  }

  private List<TaskInfo> getOtherTaskInfos(TaskInfo thisTaskInfo) throws Exception {
    List<TaskInfo> others = new ArrayList<TaskInfo>();

    for (TaskInfo taskInfo : state.getTaskInfos()) {
      if (!taskInfo.getName().equals(thisTaskInfo.getName())) {
        others.add(taskInfo);
      }
    }

    return others;
  }
}
