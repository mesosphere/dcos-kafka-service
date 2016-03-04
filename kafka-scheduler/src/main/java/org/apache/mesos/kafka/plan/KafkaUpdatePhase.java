package org.apache.mesos.kafka.plan;

import java.util.ArrayList;
import java.util.List;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;

public class KafkaUpdatePhase implements Phase {
  private final List<Block> blocks;
  private final String configName;
  private final KafkaConfigService config;

  public KafkaUpdatePhase(
      String targetConfigName,
      KafkaConfigService targetConfig,
      KafkaStateService state,
      KafkaOfferRequirementProvider offerReqProvider) {
    this.configName = targetConfigName;
    this.config = targetConfig;
    this.blocks = createBlocks(configName, config.getBrokerCount(), state, offerReqProvider);
  }

  @Override
  public List<Block> getBlocks() {
    return blocks;
  }

  @Override
  public int getId() {
    // Kafka only has two Phases: Reconciliation which has an Id of 0
    // and the Update Phase which has an Id of 1
    return 1;
  }

  @Override
  public String getName() {
    return "Update to: " + configName;
  }

  @Override
  public boolean isComplete() {
    for (Block block : blocks) {
      if (!block.isComplete()) {
        return false;
      }
    }

    return true;
  }

  private static List<Block> createBlocks(
      String configName,
      int brokerCount,
      KafkaStateService state,
      KafkaOfferRequirementProvider offerReqProvider) {

    List<Block> blocks = new ArrayList<Block>();

    for (int i=0; i<brokerCount; i++) {
      blocks.add(new KafkaUpdateBlock(state, offerReqProvider, configName, i));
    }

    return blocks;
  }
}

