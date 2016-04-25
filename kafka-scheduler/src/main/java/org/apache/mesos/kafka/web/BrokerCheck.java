package org.apache.mesos.kafka.web;

import com.codahale.metrics.health.HealthCheck;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.plan.KafkaStageManager;
import org.apache.mesos.kafka.plan.KafkaUpdatePhase;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.scheduler.plan.*;

public class BrokerCheck extends HealthCheck {
  public static final String NAME = "brokerCount";
  private final Log log = LogFactory.getLog(BrokerCheck.class);

  private final int NO_UPDATE_PHASE_CODE = 512;
  private final int NOT_ENOUGH_BROKERS_CODE = 513;

  private final KafkaStageManager stageManager;
  private final KafkaStateService state;

  public BrokerCheck(KafkaStageManager stageManager, KafkaStateService state) {
    this.stageManager = stageManager;
    this.state = state;
  }

  @Override
  protected Result check() throws Exception {
    String errMsg = "";

    try {
      Phase updatePhase = getUpdatePhase();

      if (updatePhase == null) {
        errMsg = "Health check failed because of failure to find an update phase.";
        log.error(errMsg);
        return Result.unhealthy(errMsg);
      }

      int runningBrokerCount = state.getRunningBrokersCount();
      int completedBrokerBlockCount = getCompleteBrokerBlockCount(updatePhase);

      if (runningBrokerCount < completedBrokerBlockCount) {
        errMsg = "Health check failed because running Broker count is less than completed Broker Blocks: running = " + runningBrokerCount + " completed blocks = " + completedBrokerBlockCount;
        log.warn(errMsg);
        return Result.unhealthy(errMsg);
      }

      return Result.healthy("All expected Brokers running");
    } catch (Exception ex) {
      errMsg = "Failed to determine Broker counts with exception: " + ex;
      log.error(errMsg);
      return Result.unhealthy(errMsg);
    }
  }

  private Phase getUpdatePhase() {
    Stage stage = stageManager.getStage();

    for (Phase phase : stage.getPhases()) {
      if (phase instanceof KafkaUpdatePhase) {
        return phase;
      }
    }

    return null;
  }

  private int getCompleteBrokerBlockCount(Phase phase) {
    int completeCount = 0;

    for (Block block : phase.getBlocks()) {
      if (block.isComplete()) {
        completeCount++;
      }
    }

    return completeCount;
  }
}
