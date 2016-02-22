package org.apache.mesos.kafka.plan;

import java.util.ArrayList;
import java.util.List;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.DefaultStageStrategy;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.PhaseStrategy;
import org.apache.mesos.scheduler.plan.Status;

public class KafkaUpdatePhase implements Phase {
  private List<Block> blocks = null;
  private String configName = null;
  private KafkaConfigService config = null;
  private PhaseStrategy strategy = null;

  public KafkaUpdatePhase(
      String configName,
      KafkaOfferRequirementProvider offerReqProvider) {

    this.configName = configName;
    this.config = KafkaScheduler.getConfigState().fetch(configName);
    this.blocks = createBlocks(configName, offerReqProvider);
    strategy = PlanUtils.getPhaseStrategy(config, this);
  }

  public List<Block> getBlocks() {
    return blocks;
  }

  public PhaseStrategy getStrategy() {
    return strategy;
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

  public Block getCurrentBlock() {
    if (blocks.size() == 0) {
      return null;
    } else {
      return strategy.getCurrentBlock();
    }
  }

  public boolean isComplete() {
    for (Block block : blocks) {
      if (!block.isComplete()) {
        return false;
      }
    }

    return true;
  }

  public String getName() {
    return "Update to: " + configName;
  }

  public int getId() {
    return 1;
  }

  public Status getStatus() {
    for (Block block : getBlocks()) {
      if (!block.getStatus().equals(Status.Complete)) {
        return block.getStatus();
      }
    }

    return Status.Complete;
  }
}

