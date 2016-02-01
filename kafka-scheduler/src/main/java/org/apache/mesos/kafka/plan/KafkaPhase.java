package org.apache.mesos.kafka.plan;

import java.util.ArrayList;
import java.util.List;

import org.apache.mesos.kafka.config.KafkaConfigService;

public class KafkaPhase implements Phase {
  private List<Block> blocks = null;

  public KafkaPhase(KafkaConfigService config) {
    this.blocks = createBlocks(config);
  }

  public List<Block> getBlocks() {
    return blocks;
  }

  private List<Block> createBlocks(KafkaConfigService config) {
    List<Block> blocks = new ArrayList<Block>();

    for (int i=0; i<config.getBrokerCount(); i++) {
      blocks.add(new KafkaBlock(config, i));
    }

    return blocks;
  }

  public Block getCurrentBlock() {
    if (blocks.size() == 0) {
      return null;
    }

    for (Block block : blocks) {
      if (!block.isComplete()) {
        return block;
      }
    }
    
    return null;
  }

  public boolean isComplete() {
    for (Block block : blocks) {
      if (!block.isComplete()) {
        return false;
      }
    }

    return true;
  }
}

