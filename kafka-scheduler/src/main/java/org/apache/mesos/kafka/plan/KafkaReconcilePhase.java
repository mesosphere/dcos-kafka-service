package org.apache.mesos.kafka.plan;

import java.util.Arrays;
import java.util.List;

import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.PhaseStrategy;
import org.apache.mesos.scheduler.plan.Status;

public class KafkaReconcilePhase implements Phase {
  private Block block = null;

  public KafkaReconcilePhase(Reconciler reconciler) {
    block = new KafkaReconcileBlock(reconciler);
  }

  public List<Block> getBlocks() {
    return Arrays.asList(block);
  }

  public Block getCurrentBlock() {
    if (block.isComplete()) {
      return null;
    } else {
      return block;
    }
  }

  public boolean isComplete() {
    return block.isComplete();
  }

  public String getName() {
    return "Reconciliation";
  }

  public int getId() {
    return 0;
  }

  public Status getStatus() {
    return block.getStatus();
  }

  public PhaseStrategy getStrategy() {
    return null;
  }
}

