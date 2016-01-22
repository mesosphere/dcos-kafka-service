package org.apache.mesos.kafka.offer;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;

public class NodePlacementStrategyService implements PlacementStrategyService {
  private final Log log = LogFactory.getLog(NodePlacementStrategyService.class);
  private KafkaStateService state = KafkaStateService.getStateService();

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

    List<SlaveID> agentIds = getAgents(state.getTaskInfos());
    for (SlaveID agentId : agentIds) {
      if (!agentId.equals(taskInfo.getSlaveId())) {
        agentsToAvoid.add(agentId);
      }
    }

    return agentsToAvoid;
  }

  private static List<SlaveID> getAgents(List<TaskInfo> taskInfos) {
    List<SlaveID> slaveIds = new ArrayList<SlaveID>();

    for (TaskInfo taskInfo : taskInfos) {
      slaveIds.add(taskInfo.getSlaveId());
    }

    return slaveIds;
  }
}
