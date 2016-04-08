package org.apache.mesos.kafka.offer;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.offer.OperationRecorder;

/**
 * Records the state of accepted offers.
 */
public class PersistentOperationRecorder implements OperationRecorder {
  private final KafkaStateService state;

  public PersistentOperationRecorder(KafkaStateService state) {
    this.state = state;
  }

  public void record(Operation operation, Offer offer) throws Exception {
    if (operation.getType() == Operation.Type.LAUNCH) {
      state.recordTasks(operation.getLaunch().getTaskInfosList());
    }
  }
}
