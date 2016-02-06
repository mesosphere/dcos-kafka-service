package org.apache.mesos.kafka.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.SchedulerDriver;

import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.OfferRecommendation;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.PlanManager;

import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;
import org.apache.mesos.kafka.state.KafkaStateService;

public class KafkaRepairScheduler {
  private final Log log = LogFactory.getLog(KafkaRepairScheduler.class);

  private KafkaStateService state = null;
  private PlanManager planManager = null;

  private OfferAccepter offerAccepter = null;
  private KafkaOfferRequirementProvider offerReqProvider = null;

  public KafkaRepairScheduler(
      PlanManager planManager,
      KafkaOfferRequirementProvider offerReqProvider,
      OfferAccepter offerAccepter) {

    state = KafkaStateService.getStateService();
    this.planManager = planManager;
    this.offerReqProvider = offerReqProvider;
    this.offerAccepter = offerAccepter;
  }

  public List<OfferID> resourceOffers(SchedulerDriver driver, List<Offer> offers) {
    List<OfferID> acceptedOffers = new ArrayList<OfferID>();
    List<TaskInfo> terminatedTasks = getTerminatedTasks();

    if (terminatedTasks.size() > 0) {
      TaskInfo terminatedTask = terminatedTasks.get(new Random().nextInt(terminatedTasks.size()));
      OfferRequirement offerReq =  offerReqProvider.getReplacementOfferRequirement(terminatedTask);
      OfferEvaluator offerEvaluator = new OfferEvaluator(offerReq);
      List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
      acceptedOffers = offerAccepter.accept(driver, recommendations);
    }

    return acceptedOffers;
  }

  private List<TaskInfo> getTerminatedTasks() {
    List<TaskInfo> filteredTerminatedTasks = new ArrayList<TaskInfo>();

    try {
      Block currBlock = planManager.getCurrentBlock();
      if (currBlock == null) {
        return state.getTerminatedTaskInfos();
      }

      String brokerName = currBlock.getName();
      for (TaskInfo taskInfo : state.getTerminatedTaskInfos()) {
        if (!taskInfo.getName().equals(brokerName)) {
          filteredTerminatedTasks.add(taskInfo);
        }
      }
    } catch(Exception ex) {
      log.error("Failed to fetch terminated tasks.");
    }

    return filteredTerminatedTasks;
  }
}
