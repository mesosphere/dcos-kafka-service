package org.apache.mesos.kafka.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.OfferRecommendation;
import org.apache.mesos.offer.OfferRequirement;

import org.apache.mesos.protobuf.LabelBuilder;

import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.offer.OfferRequirementProvider;
import org.apache.mesos.kafka.plan.KafkaBlock;
import org.apache.mesos.kafka.plan.KafkaUpdatePlan;
import org.apache.mesos.kafka.state.KafkaStateService;

public class KafkaPlanScheduler {
  private final Log log = LogFactory.getLog(KafkaPlanScheduler.class);

  private KafkaStateService state = null;
  private KafkaConfigState configState = null;
  private KafkaUpdatePlan plan = null;

  private OfferAccepter offerAccepter = null;
  private OfferRequirementProvider offerReqProvider = null;
 
  public KafkaPlanScheduler(
      KafkaUpdatePlan plan,
      KafkaConfigState configState,
      OfferRequirementProvider offerReqProvider,
      OfferAccepter offerAccepter) {

    state = KafkaStateService.getStateService();
    this.plan = plan;
    this.configState = configState;
    this.offerReqProvider = offerReqProvider;
    this.offerAccepter = offerAccepter;
  }

  public List<OfferID> resourceOffers(SchedulerDriver driver, List<Offer> offers) {
    List<OfferID> acceptedOffers = new ArrayList<OfferID>();

    if (plan.isComplete()) {
      log.info("Plan complete.");
      return acceptedOffers;
    }

    KafkaBlock currBlock = getCurrentBlock();
    String currBlockName = currBlock.getBrokerName();

    if (currBlock.isStaging() || currBlock.isComplete()) {
      return acceptedOffers;
    }

    if (currBlock.isInProgress()) {
      OfferRequirement offerReq = null;
      log.info("Processing Block: " + currBlockName);

      try {
        if (currBlock.hasBeenTerminated()) {
          log.info("Block: " + currBlockName + " is terminated.");
          TaskInfo taskInfo = getTaskInfo(currBlock);
          offerReq = offerReqProvider.getReplacementOfferRequirement(taskInfo);
        } else if(currBlock.hasNeverBeenLaunched()) {
          log.info("Block: " + currBlockName + " has never been launched.");
          offerReq = offerReqProvider.getNewOfferRequirement(configState.getTargetName());
        } else {
          log.error("Unexpected block state.");
          return acceptedOffers;
        }
      } catch (Exception ex) {
        log.error("Failed to generate OfferRequirement with exception: " + ex);
        return acceptedOffers;
      }

      OfferEvaluator offerEvaluator = new OfferEvaluator(offerReq);
      List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
      acceptedOffers = offerAccepter.accept(driver, recommendations);
    } else {
      log.info("Starting Block: " + currBlockName + " with status: " + currBlock.getTaskStatus());
      startBlock(currBlock);
    } 

    return acceptedOffers;
  }

  private void startBlock(KafkaBlock block) {
    TaskInfo taskInfo = getTaskInfo(block);
    KafkaScheduler.restartTasks(Arrays.asList(taskInfo.getTaskId().getValue()));
  }

  private KafkaBlock getCurrentBlock() {
    return (KafkaBlock) plan.getCurrentPhase().getCurrentBlock();
  }

  private TaskInfo getTaskInfo(KafkaBlock block) {
    int brokerId = block.getBrokerId();

    try {
      for (TaskInfo taskInfo : state.getTaskInfos()) {
        String brokerName = taskInfo.getName();
        if (brokerName.equals("broker-" + brokerId)) {
          Labels labels = new LabelBuilder()
            .addLabel("config_target", configState.getTargetName())
            .build();

          return TaskInfo.newBuilder(taskInfo)
            .setLabels(labels)
            .build();
        }
      } 
    } catch(Exception ex) {
      log.error("Failed to retrieve TaskInfo with exception: " + ex);
    }

    return null;
  }
}
