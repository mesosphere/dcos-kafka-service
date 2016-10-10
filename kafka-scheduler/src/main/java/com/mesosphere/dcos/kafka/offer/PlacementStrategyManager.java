package com.mesosphere.dcos.kafka.offer;

import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;
import org.apache.mesos.offer.constrain.PlacementUtils;

import java.util.Collections;
import java.util.Optional;

class PlacementStrategyManager {
  private static final Log log = LogFactory.getLog(PlacementStrategyManager.class);

  private final FrameworkState frameworkState;

  PlacementStrategyManager(FrameworkState frameworkState) {
    this.frameworkState = frameworkState;
  }

  public Optional<PlacementRuleGenerator> getPlacementStrategy(
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
