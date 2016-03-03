package org.apache.mesos.kafka.offer;

import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.Protos.TaskInfo;

public interface KafkaOfferRequirementProvider {
  OfferRequirement getNewOfferRequirement(String configName, int brokerId);
  OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo);
  OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo info);
}
