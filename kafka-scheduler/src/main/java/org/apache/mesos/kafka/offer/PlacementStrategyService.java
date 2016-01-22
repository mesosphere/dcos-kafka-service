package org.apache.mesos.kafka.offer;

import java.util.List;

import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;

public interface PlacementStrategyService {
  List<SlaveID> getAgentsToAvoid(TaskInfo taskInfo);
  List<SlaveID> getAgentsToColocate(TaskInfo taskInfo);
}
