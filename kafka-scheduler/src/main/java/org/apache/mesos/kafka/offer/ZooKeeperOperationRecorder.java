package org.apache.mesos.kafka.offer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.offer.OperationRecorder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;

public class ZooKeeperOperationRecorder implements OperationRecorder {
  private final Log log = LogFactory.getLog(ZooKeeperOperationRecorder.class);

  public void record(Operation operation, Offer offer) throws Exception {
    log.info("Offer: " + offer);
    log.info("Operation: " + operation);
  }
}
