package org.apache.mesos.kafka.offer;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.OfferRequirement;

/**
 * Interface for acquire all the Offer Requirement providers.
 */
public interface KafkaOfferRequirementProvider {
  OfferRequirement getNewOfferRequirement(String configName, int brokerId);

  OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo);

  OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo info);
}
