package org.apache.mesos.kafka.plan;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.scheduler.plan.Block;

public class KafkaStageStrategy implements PlanStrategy {
  private final Log log = LogFactory.getLog(KafkaStageStrategy.class);
  private Plan plan = null;
  private boolean shouldProceed = false;
  private boolean shouldInterrupt = false;
  private Block interruptBlock = null;

  public KafkaStageStrategy(Plan plan) {
    this.plan = plan;
  }

  public Block getCurrentBlock() {
    if (shouldInterrupt) {
      return interruptBlock;
    }

    for (Phase phase : plan.getPhases()) {
      if (!phase.isComplete()) {
        int i = 0;
        for (Block block : phase.getBlocks()) {
          if (i == 0 && !shouldProceed) {
            return block;
          } else if (!block.isComplete()) {
            if (i != 0) {
              shouldProceed = false;
            }

            log.info("Returning current block: " + block);
            return block;
          }
        }
      }
    }

    return null;
  }

  public void proceed() {
    shouldProceed = true;
    shouldInterrupt = false;
    interruptBlock = null;
  }

  public void interrupt() {
    interruptBlock = getCurrentBlock();
    shouldInterrupt = true;
  }
}
