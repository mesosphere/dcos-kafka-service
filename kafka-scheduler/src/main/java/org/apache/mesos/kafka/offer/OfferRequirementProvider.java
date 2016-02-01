package org.apache.mesos.kafka.offer;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.Protos.TaskInfo;

public interface OfferRequirementProvider {
  OfferRequirement getNewOfferRequirement(ConfigurationService config);
  OfferRequirement getReplacementOfferRequirement(ConfigurationService config, TaskInfo taskInfo);
}
