package org.apache.mesos.kafka.plan;

import org.apache.mesos.scheduler.plan.DefaultInstallStrategy;
import org.apache.mesos.scheduler.plan.DefaultStageStrategy;

import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.PhaseStrategy;
import org.apache.mesos.scheduler.plan.PhaseStrategyFactory;

/**
 * Generates DefaultPhaseStrategy objects when provided Phases.
 */
public class KafkaStagePhaseStrategyFactory implements PhaseStrategyFactory {

  @Override
  public PhaseStrategy getStrategy(Phase phase) {
    if (phase.getId() == 0) {
      return new DefaultInstallStrategy(phase);
    } else {
      return new DefaultStageStrategy(phase);
    }
  }
}
