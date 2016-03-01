package org.apache.mesos.kafka.plan;

import java.util.Arrays;
import java.util.List;

import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;

import org.apache.mesos.reconciliation.Reconciler;

import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.scheduler.plan.Phase;

public class KafkaUpdatePlan implements Plan {
  private KafkaReconcilePhase reconcilePhase;
  private KafkaUpdatePhase updatePhase;

  public KafkaUpdatePlan(
      String configName,
      KafkaOfferRequirementProvider offerReqProvider,
      Reconciler reconciler) {

    reconcilePhase = new KafkaReconcilePhase(reconciler);
    updatePhase = new KafkaUpdatePhase(configName, offerReqProvider);
  }

  public List<? extends Phase> getPhases() {
    return Arrays.asList(reconcilePhase, updatePhase);
  }

  public boolean isComplete() {
    for (Phase phase : getPhases()) {
      for (Block block : phase.getBlocks()) {
        if (!block.isComplete()) {
          return false;
        }
      }
    }

    return true;
  }
}
