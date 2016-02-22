package org.apache.mesos.kafka.plan;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.KafkaConfigService;

import org.apache.mesos.scheduler.plan.DefaultInstallStrategy;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.PhaseStrategy;

public class PlanUtils {
  private static final Log log = LogFactory.getLog(PlanUtils.class);

  public static PhaseStrategy getPhaseStrategy(KafkaConfigService config, Phase phase) {
    String strategy = config.getStrategy();

    switch (strategy) {
      case "INSTALL":
        return new DefaultInstallStrategy(phase);
      case "STAGE":
        return new KafkaStageStrategy(phase);
      default:
        log.warn("Unknown strategy: " + strategy);
        return new KafkaStageStrategy(phase);
    }
  }
}
