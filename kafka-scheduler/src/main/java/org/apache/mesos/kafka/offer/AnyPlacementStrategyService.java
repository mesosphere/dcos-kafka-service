package org.apache.mesos.kafka.offer;

import java.util.List;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;

public class AnyPlacementStrategyService implements PlacementStrategyService {

  public List<SlaveID> getAgentsToAvoid(TaskInfo taskInfo) {
    return null;
  }

  public List<SlaveID> getAgentsToColocate(TaskInfo taskInfo) {
    return null;
  }
}
