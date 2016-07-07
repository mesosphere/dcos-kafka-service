package com.mesosphere.dcos.kafka.offer;

import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.offer.OperationRecorder;

/**
 * Records the state of accepted offers.
 */
public class PersistentOperationRecorder implements OperationRecorder {
  private final FrameworkState state;

  public PersistentOperationRecorder(FrameworkState state) {
    this.state = state;
  }

  public void record(Operation operation, Offer offer) throws Exception {
    if (operation.getType() == Operation.Type.LAUNCH) {
      state.recordTasks(operation.getLaunch().getTaskInfosList());
    }
  }
}
