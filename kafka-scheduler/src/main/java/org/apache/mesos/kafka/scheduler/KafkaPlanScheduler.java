package org.apache.mesos.kafka.scheduler;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.OfferRecommendation;
import org.apache.mesos.offer.OfferRequirement;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.offer.OfferRequirementProvider;
import org.apache.mesos.kafka.plan.KafkaBlock;
import org.apache.mesos.kafka.plan.KafkaUpdatePlan;
import org.apache.mesos.kafka.state.KafkaStateService;

public class KafkaPlanScheduler {
  private final Log log = LogFactory.getLog(KafkaPlanScheduler.class);


  private KafkaStateService state = null;
  private KafkaConfigService targetConfig = null;
  private KafkaUpdatePlan plan = null;

  private OfferAccepter offerAccepter = null;
  private OfferRequirementProvider offerReqProvider = null;
 
  public KafkaPlanScheduler(
      KafkaUpdatePlan plan,
      OfferRequirementProvider offerReqProvider,
      OfferAccepter offerAccepter) {

    targetConfig = KafkaConfigService.getTargetConfig(); 
    state = KafkaStateService.getStateService();
    this.plan = plan;
    this.offerReqProvider = offerReqProvider;
    this.offerAccepter = offerAccepter;
  }

  public List<OfferID> resourceOffers(SchedulerDriver driver, List<Offer> offers) {
    List<OfferID> acceptedOffers = new ArrayList<OfferID>();

    if (getCurrentBlock().isInProgress()) {
      TaskInfo taskInfo = getTaskInfo();

      OfferRequirement offerReq = null;
      if (null == taskInfo) {
        offerReq = offerReqProvider.getNewOfferRequirement(targetConfig);
      } else {
        offerReq = offerReqProvider.getReplacementOfferRequirement(targetConfig, taskInfo);
      }

      OfferEvaluator offerEvaluator = new OfferEvaluator(offerReq);
      List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
      acceptedOffers = offerAccepter.accept(driver, recommendations);
    } else {
      startBlock();
    }

    return acceptedOffers;
  }

  private void startBlock() {
  }

  private KafkaBlock getCurrentBlock() {
    return (KafkaBlock) plan.getCurrentPhase().getCurrentBlock();
  }

  private TaskInfo getTaskInfo() {
    KafkaBlock currBlock = getCurrentBlock();
    int brokerId = currBlock.getBrokerId();

    try {
      for (TaskInfo taskInfo : state.getTaskInfos()) {
        String brokerName = taskInfo.getName();
        if (brokerName.equals("broker-" + brokerId)) {
          return taskInfo;
        }
      } 
    } catch(Exception ex) {
      log.error("Failed to retrieve TaskInfo with exception: " + ex);
    }

    return null;
  }
}
