package org.apache.mesos.kafka.offer;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;

public class PlacementStrategy {
  private static ConfigurationService config = KafkaConfigService.getConfigService();

  public static PlacementStrategyService getPlacementStrategyService() {
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
