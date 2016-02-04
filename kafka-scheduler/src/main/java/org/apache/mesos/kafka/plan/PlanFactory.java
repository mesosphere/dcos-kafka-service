package org.apache.mesos.kafka.plan;

import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;

public class PlanFactory {
  public static KafkaUpdatePlan getPlan(
      String configName,
      KafkaOfferRequirementProvider offerReqProvider) {

    return new KafkaUpdatePlan(configName, offerReqProvider);
  }
}
