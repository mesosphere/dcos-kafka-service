package com.mesosphere.dcos.kafka.plan;

import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import com.mesosphere.dcos.kafka.offer.KafkaOfferRequirementProvider;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.mesos.scheduler.plan.DefaultPhase;
import org.apache.mesos.scheduler.plan.Step;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;

import java.util.ArrayList;
import java.util.List;

public class KafkaUpdatePhase extends DefaultPhase {

  public static KafkaUpdatePhase create(
    String targetConfigName,
    KafkaSchedulerConfiguration targetConfig,
    FrameworkState frameworkState,
    KafkaOfferRequirementProvider offerReqProvider) {
      return new KafkaUpdatePhase(targetConfigName,
              createSteps(targetConfigName,
                      targetConfig.getServiceConfiguration().getCount(),
                      frameworkState, offerReqProvider)
      );
  }
  public KafkaUpdatePhase(String name, List<Step> steps) {
    super(name, steps, new SerialStrategy<>(), new ArrayList<>());
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
