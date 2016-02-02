package org.apache.mesos.kafka.offer;


import java.util.List;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.offer.OfferUtils;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.apache.mesos.Protos.TaskInfo;

public class MasterOfferRequirementProvider {
  private final Log log = LogFactory.getLog(MasterOfferRequirementProvider.class);
  private OfferRequirementProvider provider;
  private KafkaStateService state = KafkaStateService.getStateService();
  private KafkaConfigState configState = null;

  public MasterOfferRequirementProvider(
      OfferRequirementProvider provider,
      KafkaConfigState configState) {
    this.provider = provider;
    this.configState = configState;
  }

  public OfferRequirement getNextRequirement() {
    List<TaskInfo> terminatedTasks = getTerminatedTasks();

    if (terminatedTasks != null && terminatedTasks.size() > 0) {
      TaskInfo terminatedTask = terminatedTasks.get(new Random().nextInt(terminatedTasks.size()));
      KafkaConfigService config = KafkaConfigService.getEnvConfig();
      return provider.getReplacementOfferRequirement(terminatedTask);
    } else if (OfferUtils.belowTargetBrokerCount()) {
      return provider.getNewOfferRequirement(configState.getTargetName());
    } else {
      return null;
    }
  }

  private List<TaskInfo> getTerminatedTasks() {
    try {
      return state.getTerminatedTaskInfos();
    } catch(Exception ex) {
      log.error("Failed to fetch terminated tasks.");
    }

    return null;
  }
}
