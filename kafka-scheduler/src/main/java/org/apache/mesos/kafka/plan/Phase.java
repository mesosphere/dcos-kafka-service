package org.apache.mesos.kafka.plan;

import java.util.List;

import org.apache.mesos.scheduler.plan.Block;

public interface Phase {
  List<Block> getBlocks();

  Block getCurrentBlock();

  boolean isComplete();
}
