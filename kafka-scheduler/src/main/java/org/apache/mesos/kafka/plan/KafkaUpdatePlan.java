package org.apache.mesos.kafka.plan;

import java.util.Arrays;
import java.util.List;

import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;

import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.Status;

public class KafkaUpdatePlan implements Plan {
  private KafkaPhase phase;

  public KafkaUpdatePlan(String configName, KafkaOfferRequirementProvider offerReqProvider) {
    phase = new KafkaPhase(configName, offerReqProvider);
  }

  public List<? extends Phase> getPhases() {
    return Arrays.asList(phase);
  }

  public Phase getCurrentPhase() {
    return phase;
  }

  public boolean isComplete() {
    return phase.isComplete();
  }

  public Status getStatus() {
    return getCurrentPhase().getStatus();
  }
}
