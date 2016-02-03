package org.apache.mesos.kafka.plan;

import org.apache.mesos.kafka.offer.OfferRequirementProvider;

public class PlanFactory {
  public static KafkaUpdatePlan getPlan(
      String configName,
      OfferRequirementProvider offerReqProvider) {

    return new KafkaUpdatePlan(configName, offerReqProvider);
  }
}
