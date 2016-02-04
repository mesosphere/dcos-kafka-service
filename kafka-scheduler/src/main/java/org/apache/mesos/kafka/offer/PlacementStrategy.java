package org.apache.mesos.kafka.offer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;

public class PlacementStrategy {
  private final static Log log = LogFactory.getLog(PlacementStrategy.class);

  public static PlacementStrategyService getPlacementStrategyService(ConfigurationService config) {
    String placementStrategy = config.get("PLACEMENT_STRATEGY");

    log.info("Using placement strategy: " + placementStrategy);

    switch (placementStrategy) {
      case "ANY":
        log.info("Returning ANY strategy");
        return new AnyPlacementStrategyService();
      case "NODE":
        log.info("Returning NODE strategy");
        return new NodePlacementStrategyService(); 
      default:
        log.info("Returning DEFAULT strategy");
        return new AnyPlacementStrategyService();
    }
  }
}
