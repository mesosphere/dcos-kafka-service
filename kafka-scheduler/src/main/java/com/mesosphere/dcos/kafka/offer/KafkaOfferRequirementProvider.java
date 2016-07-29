package com.mesosphere.dcos.kafka.offer;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Interface for acquire all the Offer Requirement providers.
 */
public interface KafkaOfferRequirementProvider {
  OfferRequirement getNewOfferRequirement(String configName, int brokerId) throws InvalidRequirementException, IOException, URISyntaxException;

  OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo) throws InvalidRequirementException;

  OfferRequirement getUpdateOfferRequirement(String configName, TaskInfo info) throws InvalidRequirementException, ConfigStoreException;
}
