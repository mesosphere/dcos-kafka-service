package org.apache.mesos.kafka.plan;

import java.util.List;

public class KafkaPhase implements Phase {
  public List<Block> getBlocks() {
    return null;
  }

  public Block getCurrentBlock() {
    return null;
  }

  public boolean isComplete() {
    return false;
  }
}

