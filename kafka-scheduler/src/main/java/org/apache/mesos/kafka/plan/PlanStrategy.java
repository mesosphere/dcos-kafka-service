package org.apache.mesos.kafka.plan;

import org.apache.mesos.scheduler.plan.Block;

public interface PlanStrategy {
  Block getCurrentBlock();

  void proceed();
  void interrupt();
}
