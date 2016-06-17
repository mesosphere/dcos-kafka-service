package org.apache.mesos.kafka.offer;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskRequirement;

/**
 * Interface for acquire all the Offer Requirement providers.
 */
public interface KafkaOfferRequirementProvider {
  OfferRequirement getNewOfferRequirement(String configName, int brokerId) throws TaskRequirement.InvalidTaskRequirementException;

  OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo) throws TaskRequirement.InvalidTaskRequirementException;

  OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo info) throws TaskRequirement.InvalidTaskRequirementException;
}
