package org.apache.mesos.kafka.offer;

import org.apache.mesos.config.ConfigurationService;

public class PlacementStrategy {
  public static PlacementStrategyService getPlacementStrategyService(ConfigurationService config) {
    String placementStrategy = config.get("PLACEMENT_STRATEGY");

    switch (placementStrategy) {
      case "ANY":
        return new AnyPlacementStrategyService();
      case "NODE":
        return new NodePlacementStrategyService(); 
      default:
        return new AnyPlacementStrategyService();
    }
  }
}
