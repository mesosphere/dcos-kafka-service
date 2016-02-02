package org.apache.mesos.kafka.plan;

import org.apache.mesos.kafka.config.KafkaConfigService;

public class PlanFactory {
  public static KafkaUpdatePlan getPlan(String configName) {
    return new KafkaUpdatePlan(configName);
  }
}
