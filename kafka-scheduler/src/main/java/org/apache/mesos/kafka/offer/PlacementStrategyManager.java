package org.apache.mesos.kafka.offer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.KafkaSchedulerConfiguration;
import org.apache.mesos.kafka.state.FrameworkStateService;
import org.apache.mesos.offer.AnyPlacementStrategy;
import org.apache.mesos.offer.PlacementStrategy;

class PlacementStrategyManager {
  private static final Log log = LogFactory.getLog(PlacementStrategy.class);

  private final FrameworkStateService frameworkStateService;

  PlacementStrategyManager(FrameworkStateService frameworkStateService) {
    this.frameworkStateService = frameworkStateService;
  }

  public PlacementStrategy getPlacementStrategy(KafkaSchedulerConfiguration config) {
    String placementStrategy = config.getServiceConfiguration().getPlacementStrategy();

    log.info("Using placement strategy: " + placementStrategy);

    switch (placementStrategy) {
      case "ANY":
        log.info("Returning ANY strategy");
        return new AnyPlacementStrategy();
      case "NODE":
        log.info("Returning NODE strategy");
        return new NodePlacementStrategy(frameworkStateService);
      default:
        log.info("Returning DEFAULT strategy");
        return new AnyPlacementStrategy();
    }
  }
}
