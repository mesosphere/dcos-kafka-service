package org.apache.mesos.kafka.plan;

import java.util.ArrayList;
import java.util.List;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;

public class KafkaPhase implements Phase {
  private List<Block> blocks = null;

  public KafkaPhase(String configName) {
    this.blocks = createBlocks(configName);
  }

  public List<Block> getBlocks() {
    return blocks;
  }

  private List<Block> createBlocks(String configName) {
    List<Block> blocks = new ArrayList<Block>();
    KafkaConfigService config = KafkaScheduler.getConfigState().fetch(configName); 

    for (int i=0; i<config.getBrokerCount(); i++) {
      blocks.add(new KafkaBlock(configName, i));
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

