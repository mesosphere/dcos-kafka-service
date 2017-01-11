package com.mesosphere.dcos.kafka.offer;

import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

class PlacementRuleManager {
  private static final Log log = LogFactory.getLog(PlacementRuleManager.class);

  private final FrameworkState frameworkState;

  PlacementRuleManager(FrameworkState frameworkState) {
    this.frameworkState = frameworkState;
  }

  public Optional<PlacementRule> getPlacementRule(
          KafkaSchedulerConfiguration config,
          Protos.TaskInfo taskInfo) {
    Optional<PlacementRule> constraintRule = getPlacementConstraint(config);
    Optional<PlacementRule> strategyRule = getPlacementStrategy(config, taskInfo);

    if ( constraintRule.isPresent() && strategyRule.isPresent() ) {
      return Optional.empty().of(new AndRule(constraintRule.get(), strategyRule.get()));
    }
    if (! constraintRule.isPresent()) {
      return strategyRule;
    }
    return constraintRule;
  }

  private Optional<PlacementRule> getPlacementConstraint(KafkaSchedulerConfiguration config) {
    try {
      String placementConstraint = config.getServiceConfiguration().getPlacementConstraint();
      PlacementRule rule = MarathonConstraintParser.parse(placementConstraint);
      if (rule instanceof PassthroughRule) {
         log.info("No placement constraints found for marathon-style constraint '"+ placementConstraint + "' : " + rule);
         return Optional.empty();
       }
       log.info("Created placement rule for marathon-style constraint '" + placementConstraint + "' : "+ rule);
       return Optional.of(rule);
    } catch (IOException e) {
      log.error("Failed to construct PlacementRule with Exception: ", e);
      return Optional.empty();
    }
  }

  private Optional<PlacementRule> getPlacementStrategy(
          KafkaSchedulerConfiguration config,
          Protos.TaskInfo taskInfo) {
    String placementStrategy = config.getServiceConfiguration().getPlacementStrategy();

    log.info("Using placement strategy: " + placementStrategy);

    switch (placementStrategy) {
      case "ANY":
        log.info("Returning ANY strategy");
        return Optional.empty();
      case "NODE":
        log.info("Returning NODE strategy");
        NodePlacementStrategy nodePlacementStrategy = new NodePlacementStrategy(frameworkState);
        return PlacementUtils.getAgentPlacementRule(
                nodePlacementStrategy.getAgentsToAvoid(taskInfo),
                Collections.emptyList());
      default:
        log.info("Returning DEFAULT strategy");
        return Optional.empty();
    }
  }
}
