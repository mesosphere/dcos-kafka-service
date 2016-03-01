package org.apache.mesos.kafka.plan;

import java.util.ArrayList;
import java.util.List;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;

public class KafkaUpdatePhase implements Phase {
  private List<Block> blocks = null;
  private String configName = null;
  private KafkaConfigService config = null;

  public KafkaUpdatePhase(
      String configName,
      KafkaOfferRequirementProvider offerReqProvider) {

    this.configName = configName;
    this.config = KafkaScheduler.getConfigState().fetch(configName);
    this.blocks = createBlocks(configName, offerReqProvider);
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

  private List<Block> createBlocks(
      String configName,
      KafkaOfferRequirementProvider offerReqProvider) {

    List<Block> blocks = new ArrayList<Block>();

    for (int i=0; i<config.getBrokerCount(); i++) {
      blocks.add(new KafkaUpdateBlock(offerReqProvider, configName, i));
    }

    return blocks;
  }
}

