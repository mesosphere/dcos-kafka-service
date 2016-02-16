package org.apache.mesos.kafka.plan;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;
import org.apache.mesos.kafka.offer.OfferUtils;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Status;

import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;

public class KafkaReconcileBlock implements Block {
  private final Log log = LogFactory.getLog(KafkaReconcileBlock.class);

  private Status status = Status.Pending;
  private KafkaStateService state = null;
  private Reconciler reconciler = null;

  public KafkaReconcileBlock(Reconciler reconciler) {
    setStatus(Status.Pending);
    state = KafkaStateService.getStateService();
    this.reconciler = reconciler;
  }

  public void setStatus(Status newStatus) {
    Status oldStatus = status;
    status = newStatus;

    log.info(getName() + ": changed status from: " + oldStatus + " to: " + status);
  }

  public int getId() {
    return 0;
  }

  public Status getStatus() {
    if (reconciler.complete()) {
      setStatus(Status.Complete);
    }

    return status;
  }

  public OfferRequirement start() {
    setStatus(Status.InProgress);
    return null;
  }

  public List<TaskID> getUpdateIds() {
    return new ArrayList<TaskID>();
  }

  public void update(TaskStatus taskStatus) {
    // NOP, reconciler is already receiving taskStatus updates.
  }

  public boolean isPending() {
    return getStatus() == Status.Pending;
  }

  public boolean isInProgress() {
    return getStatus() == Status.InProgress;
  }

  public boolean isComplete() {
    return getStatus() == Status.Complete;
  }

  public String getName() {
    return "Reconciliation";
  }
}

