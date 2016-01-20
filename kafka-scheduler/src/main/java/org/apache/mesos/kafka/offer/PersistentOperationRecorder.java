package org.apache.mesos.kafka.offer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;

import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.offer.OperationRecorder;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;

public class PersistentOperationRecorder implements OperationRecorder {
  private final Log log = LogFactory.getLog(PersistentOperationRecorder.class);

  private static KafkaStateService state = KafkaStateService.getStateService();

  public void record(Operation operation, Offer offer) throws Exception {
    if (operation.getType() == Operation.Type.LAUNCH) {
      state.recordTasks(operation.getLaunch().getTaskInfosList());
    }
  }
}
