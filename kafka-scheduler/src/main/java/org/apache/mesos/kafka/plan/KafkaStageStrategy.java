package org.apache.mesos.kafka.plan;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.DefaultStageStrategy;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.scheduler.plan.Status;

import org.apache.mesos.kafka.offer.OfferUtils;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.kafka.state.KafkaStateService;

public class KafkaStageStrategy extends DefaultStageStrategy {
  private final Log log = LogFactory.getLog(DefaultStageStrategy.class);
  private KafkaStateService state = KafkaStateService.getStateService();

  public KafkaStageStrategy(Plan plan) {
    super(plan);
  }

  public void restart(int phaseIndex, int blockIndex, boolean force) throws IndexOutOfBoundsException {
    Phase phase = getPlan().getPhases().get(phaseIndex);
    Block block = phase.getBlocks().get(blockIndex);

    if (force) {
      String blockName = block.getName();
      Integer brokerId = OfferUtils.nameToId(blockName);

      String taskId = null;
      try {
        taskId = state.getTaskIdForBroker(brokerId);
      } catch (Exception ex) {
        log.error("Failed to retrieve TaskID for broker: " + brokerId);
        return;
      }

      KafkaScheduler.rescheduleTasks(Arrays.asList(taskId));
      block.setStatus(Status.Complete);
    } else {
      super.restart(phaseIndex, blockIndex, force);
    }
  }
}
