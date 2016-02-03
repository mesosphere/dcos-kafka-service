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
import org.apache.mesos.scheduler.plan.Block;

import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.offer.OfferRequirementProvider;
import org.apache.mesos.kafka.plan.KafkaPlanManager;
import org.apache.mesos.kafka.plan.KafkaUpdatePlan;
import org.apache.mesos.kafka.state.KafkaStateService;

public class KafkaPlanScheduler {
  private final Log log = LogFactory.getLog(KafkaPlanScheduler.class);

  private KafkaStateService state = null;
  private KafkaConfigState configState = null;
  private KafkaPlanManager planManager = null;

  private OfferAccepter offerAccepter = null;
  private OfferRequirementProvider offerReqProvider = null;
 
  public KafkaPlanScheduler(
      KafkaPlanManager planManager,
      KafkaConfigState configState,
      OfferRequirementProvider offerReqProvider,
      OfferAccepter offerAccepter) {

    state = KafkaStateService.getStateService();
    this.planManager = planManager;
    this.configState = configState;
    this.offerReqProvider = offerReqProvider;
    this.offerAccepter = offerAccepter;
  }

  public List<OfferID> resourceOffers(SchedulerDriver driver, List<Offer> offers) {
    List<OfferID> acceptedOffers = new ArrayList<OfferID>();

    if (planManager.planIsComplete()) {
      log.info("Plan complete.");
      return acceptedOffers;
    }

    Block currBlock = planManager.getCurrentBlock();

    if (currBlock.isPending()) {
      OfferRequirement offerReq = currBlock.start();
      if (offerReq != null) {
        OfferEvaluator offerEvaluator = new OfferEvaluator(offerReq);
        List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
        acceptedOffers = offerAccepter.accept(driver, recommendations);
      }
    }

    return acceptedOffers;
  }
}
