package org.apache.mesos.kafka.plan;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.PhaseStrategyFactory;
import org.apache.mesos.scheduler.plan.Stage;
import org.apache.mesos.scheduler.plan.Status;
import org.apache.mesos.scheduler.plan.StrategyStageManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaStageManager extends StrategyStageManager {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final KafkaStateService state;

  public KafkaStageManager(Stage stage, PhaseStrategyFactory strategyFactory, KafkaStateService state) {
    super(stage, strategyFactory);
    this.state = state;
  }

  @Override
  public void forceComplete(UUID phaseId, UUID blockId) {
    Block block = getBlock(phaseId, blockId);

    if (block == null) {
      log.error(String.format("Failed to find Block for phaseId: %s and blockId: %s", phaseId, blockId));
    }

    if (block instanceof KafkaUpdateBlock) {
      KafkaUpdateBlock kafkaBlock = (KafkaUpdateBlock) block;
      int brokerId = kafkaBlock.getBrokerId();

      try {
        List<String> taskIds =
          Arrays.asList(state.getTaskIdForBroker(brokerId));
        KafkaScheduler.rescheduleTasks(taskIds);
      } catch (Exception ex) {
        log.error("Failed to force completion of Block: " + block.getId() + "with exception: " + ex);
        return;
      }
    }

    block.setStatus(Status.Complete);
  }

  private Block getBlock(UUID phaseId, UUID blockId) {
    Phase phase = getPhase(phaseId);
    return (phase != null) ? phase.getBlock(blockId) : null;
  }
}
