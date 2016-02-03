package org.apache.mesos.kafka.plan;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.Protos.TaskStatus;

import org.apache.mesos.scheduler.plan.Block;

public class KafkaPlanManager implements Observer {
  private final Log log = LogFactory.getLog(KafkaPlanManager.class);

  Plan plan = null;

  public KafkaPlanManager(Plan plan) {
    this.plan = plan;
  }

  public List<Phase> getPhases() {
    return plan.getPhases();
  }

  public void update(Observable observable, Object obj) {
    TaskStatus taskStatus = (TaskStatus) obj;
    log.info("Publishing TaskStatus update: " + taskStatus);

    Block currBlock = getCurrentBlock();

    if (currBlock != null) {
      getCurrentBlock().update(taskStatus);
    }
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

  public boolean planIsComplete() {
    return getCurrentBlock() == null;
  }
}
