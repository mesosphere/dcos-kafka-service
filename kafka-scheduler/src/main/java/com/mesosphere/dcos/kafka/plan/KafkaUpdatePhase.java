package com.mesosphere.dcos.kafka.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import com.mesosphere.dcos.kafka.offer.KafkaOfferRequirementProvider;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;

public class KafkaUpdatePhase implements Phase {
  private final List<Block> blocks;
  private final String configName;
  private final KafkaSchedulerConfiguration config;
  private final UUID id;

  public KafkaUpdatePhase(
      String targetConfigName,
      KafkaSchedulerConfiguration targetConfig,
      FrameworkState frameworkState,
      KafkaOfferRequirementProvider offerReqProvider) {
    this.configName = targetConfigName;
    this.config = targetConfig;
    this.blocks = createBlocks(configName, config.getServiceConfiguration().getCount(), frameworkState, offerReqProvider);
    this.id = UUID.randomUUID();
  }

  @Override
  public List<Block> getBlocks() {
    return blocks;
  }

  @Override
  public Block getBlock(UUID id) {
    for (Block block : getBlocks()) {
      if (block.getId().equals(id)) {
        return block;
      }
    }

    return null;
  }

  @Override
  public Block getBlock(int index){
    return getBlocks().get(index);
  }


  @Override
  public UUID getId() {
    return id;
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
      FrameworkState frameworkState,
      KafkaOfferRequirementProvider offerReqProvider) {

    List<Block> blocks = new ArrayList<Block>();

    for (int i=0; i<brokerCount; i++) {
      blocks.add(new KafkaUpdateBlock(frameworkState, offerReqProvider, configName, i));
    }

    return blocks;
  }
}
