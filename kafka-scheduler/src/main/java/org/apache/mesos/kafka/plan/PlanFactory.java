package org.apache.mesos.kafka.plan;

import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;
import org.apache.mesos.reconciliation.Reconciler;

public class PlanFactory {
  public static KafkaUpdatePlan getPlan(
      String configName,
      KafkaOfferRequirementProvider offerReqProvider,
      Reconciler reconciler) {

    return new KafkaUpdatePlan(configName, offerReqProvider, reconciler);
  }
}
