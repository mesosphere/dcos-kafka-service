package org.apache.mesos.kafka.plan;

import org.apache.mesos.scheduler.plan.DefaultStrategyFactory;
import org.apache.mesos.scheduler.plan.StageStrategyFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.KafkaConfigService;

import org.apache.mesos.scheduler.plan.PhaseStrategyFactory;

public class PlanUtils {
  private static final Log log = LogFactory.getLog(PlanUtils.class);

  public static PhaseStrategyFactory getPhaseStrategyFactory(KafkaConfigService config) {
    String strategy = config.getStrategy();

    switch (strategy) {
      case "INSTALL":
        return new DefaultStrategyFactory();
      case "STAGE":
        return new StageStrategyFactory();
      default:
        log.warn("Unknown strategy: " + strategy);
        return new StageStrategyFactory();
    }
  }
}
