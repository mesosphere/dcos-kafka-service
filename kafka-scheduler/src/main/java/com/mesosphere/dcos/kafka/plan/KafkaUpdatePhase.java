package com.mesosphere.dcos.kafka.plan;

import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import com.mesosphere.dcos.kafka.offer.KafkaOfferRequirementProvider;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.scheduler.plan.DefaultPhase;
import org.apache.mesos.scheduler.plan.Step;
import org.apache.mesos.scheduler.plan.strategy.Strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class KafkaUpdatePhase extends DefaultPhase {

  public KafkaUpdatePhase(
    String targetConfigName,
    KafkaSchedulerConfiguration targetConfig,
    FrameworkState frameworkState,
    KafkaOfferRequirementProvider offerReqProvider,
    Strategy strategy) {
    super(targetConfigName,
              createSteps(targetConfigName,
                      targetConfig.getServiceConfiguration().getCount(),
                      frameworkState, offerReqProvider),
              strategy,
              Collections.emptyList() 
              );
  }

  @Override
  public String getName() {
    return "Update to: " + super.getName();
  }

  @Override
  public boolean isComplete() {
    for (Step step : getChildren()) {
      if (!step.isComplete()) {
        return false;
      }
    }
    return true;
  }

  private static List<Step> createSteps(
      String configName,
      int brokerCount,
      FrameworkState frameworkState,
      KafkaOfferRequirementProvider offerReqProvider) {

    List<Step> steps = new ArrayList<Step>();

    for (int i=0; i<brokerCount; i++) {
      steps.add(new KafkaUpdateStep(frameworkState, offerReqProvider, configName, i));
    }
    return steps;
  }
}
