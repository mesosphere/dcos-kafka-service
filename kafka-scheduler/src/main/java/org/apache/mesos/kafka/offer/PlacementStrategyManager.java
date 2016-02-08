package org.apache.mesos.kafka.offer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.offer.AnyPlacementStrategy;
import org.apache.mesos.offer.PlacementStrategy;

public class PlacementStrategyManager {
  private final static Log log = LogFactory.getLog(PlacementStrategy.class);

  public static PlacementStrategy getPlacementStrategy(ConfigurationService config) {
    String placementStrategy = config.get("PLACEMENT_STRATEGY");

    log.info("Using placement strategy: " + placementStrategy);

    switch (placementStrategy) {
      case "ANY":
        log.info("Returning ANY strategy");
        return new AnyPlacementStrategy();
      case "NODE":
        log.info("Returning NODE strategy");
        return new NodePlacementStrategy(); 
      default:
        log.info("Returning DEFAULT strategy");
        return new AnyPlacementStrategy();
    }
  }
}
