package org.apache.mesos.kafka.plan;

import java.util.List;

public interface Phase {
  List<Block> getBlocks();

  Block getCurrentBlock();

  boolean isComplete();
}
