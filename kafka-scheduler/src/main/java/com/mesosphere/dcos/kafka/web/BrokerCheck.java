package com.mesosphere.dcos.kafka.web;

import com.codahale.metrics.health.HealthCheck;
import com.mesosphere.dcos.kafka.plan.KafkaUpdatePhase;
import com.mesosphere.dcos.kafka.scheduler.KafkaScheduler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.Plan;

public class BrokerCheck extends HealthCheck {
  public static final String NAME = "broker_count";
  private final Log log = LogFactory.getLog(BrokerCheck.class);

  private final KafkaScheduler kafkaScheduler;

  public BrokerCheck(KafkaScheduler kafkaScheduler) {
    this.kafkaScheduler = kafkaScheduler;
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

      int runningBrokerCount = kafkaScheduler.getFrameworkState().getRunningBrokersCount();
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
    Plan plan = kafkaScheduler.getPlanManager().getPlan();

    for (Phase phase : plan.getPhases()) {
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
