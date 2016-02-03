package org.apache.mesos.kafka.offer;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.Protos.TaskInfo;

public interface OfferRequirementProvider {
  OfferRequirement getNewOfferRequirement(String configName, int brokerId);
  OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo);
}
