package org.apache.mesos.kafka.plan;

import java.util.List;

import org.apache.mesos.kafka.config.KafkaConfigService;

public class KafkaUpdatePlan implements Plan {
  private KafkaConfigService targetConfig;
  private KafkaPhase phase;

  public KafkaUpdatePlan(KafkaConfigService targetConfig) {
    this.targetConfig = targetConfig;
    phase = new KafkaPhase(targetConfig);
  }

  public List<Phase> getPhases() {
    return null;
  }

  public Phase getCurrentPhase() {
    return null;
  }

  public boolean isComplete() {
    return false;
  }
}
