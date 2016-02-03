package org.apache.mesos.kafka.plan;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.scheduler.plan.Block;

public class KafkaInstallStrategy implements PlanStrategy {
  private final Log log = LogFactory.getLog(KafkaInstallStrategy.class);
  private Plan plan = null;

  public KafkaInstallStrategy(Plan plan) {
    this.plan = plan;
  }

  public Block getCurrentBlock() {
    for (Phase phase : plan.getPhases()) {
      if (!phase.isComplete()) {
        for (Block block : phase.getBlocks()) {
          if (!block.isComplete()) {
            log.info("Returning current block: " + block);
            return block;
          }
        }
      }
    }

    return null;
  }

  public void proceed() {}
  public void interrupt() {}
}
