package org.apache.mesos.kafka.plan;

import org.apache.mesos.kafka.config.KafkaConfigService;

public class PlanFactory {
  public Plan getPlan(KafkaConfigService targetConfig) {
    return new KafkaUpdatePlan(targetConfig);
  }
}
