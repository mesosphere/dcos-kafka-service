package org.apache.mesos.kafka.scheduler;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.offer.LogOperationRecorder;
import org.apache.mesos.kafka.offer.MasterOfferRequirementProvider;
import org.apache.mesos.kafka.offer.OfferRequirementProvider;
import org.apache.mesos.kafka.offer.PersistentOfferRequirementProvider;
import org.apache.mesos.kafka.offer.PersistentOperationRecorder;
import org.apache.mesos.kafka.offer.SandboxOfferRequirementProvider;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.kafka.web.KafkaApiServer;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.net.HttpRequestBuilder;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.OfferRecommendation;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.OperationRecorder;
import org.apache.mesos.reconciliation.Reconciler;

import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Protos.MasterInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.SchedulerDriver;

/**
 * Kafka Framework Scheduler.
 */
public class KafkaScheduler extends Observable implements org.apache.mesos.Scheduler, Runnable {
  private final Log log = LogFactory.getLog(KafkaScheduler.class);

  private ConfigurationService config;
  private KafkaStateService state;

  private MasterOfferRequirementProvider offerReqProvider;
  private OfferAccepter offerAccepter;

  private static SchedulerDriver driver;
  private Reconciler reconciler;

  public KafkaScheduler() {
    config = KafkaConfigService.getConfigService();
    state = KafkaStateService.getStateService();
    reconciler = new Reconciler();

    addObserver(state);
    addObserver(reconciler);

    offerReqProvider = new MasterOfferRequirementProvider(getOfferRequirementProvider());

    offerAccepter =
      new OfferAccepter(Arrays.asList(
            new LogOperationRecorder(),
            new PersistentOperationRecorder()));

    String port0 = config.get("PORT0");
  }

  public static void killTasks(List<String> taskIds) {
    for (String taskId : taskIds) {
      killTask(taskId);
    }
  }

  public static void killTask(String taskId) {
    driver.killTask(TaskID.newBuilder().setValue(taskId).build());
  }

  private OfferRequirementProvider getOfferRequirementProvider() {
    boolean persistentVolumesEnabled = Boolean.parseBoolean(config.get("BROKER_PV"));

    if (persistentVolumesEnabled) {
      return new PersistentOfferRequirementProvider();
    } else {
      return new SandboxOfferRequirementProvider();
    }
  }

  @Override
  public void disconnected(SchedulerDriver driver) {
    log.info("Scheduler driver disconnected");
  }

  @Override
  public void error(SchedulerDriver driver, String message) {
    log.error("Scheduler driver error: " + message);
  }

  @Override
  public void executorLost(SchedulerDriver driver, ExecutorID executorID, SlaveID slaveID, int status) {
    log.info("Executor lost: executorId: " + executorID.getValue()
        + " slaveId: " + slaveID.getValue() + " status: " + status);
  }

  @Override
  public void frameworkMessage(SchedulerDriver driver, ExecutorID executorID, SlaveID slaveID,
      byte[] data) {
    log.info("Framework message: executorId: " + executorID.getValue() + " slaveId: "
        + slaveID.getValue() + " data: '" + Arrays.toString(data) + "'");
  }

  @Override
  public void offerRescinded(SchedulerDriver driver, OfferID offerId) {
    log.info("Offer rescinded: offerId: " + offerId.getValue());
  }

  @Override
  public void registered(SchedulerDriver driver, FrameworkID frameworkId, MasterInfo masterInfo) {
    log.info("Registered framework with frameworkId: " + frameworkId.getValue());
    state.setFrameworkId(frameworkId);
    reconcile(driver);
    this.driver = driver;

    KafkaApiServer.start();
  }

  @Override
  public void reregistered(SchedulerDriver driver, MasterInfo masterInfo) {
    log.info("Reregistered framework.");
    reconcile(driver);
    this.driver = driver;
  }

  @Override
  public void statusUpdate(SchedulerDriver driver, TaskStatus status) {
    log.info(String.format(
        "Received status update for taskId=%s state=%s message='%s'",
        status.getTaskId().getValue(),
        status.getState().toString(),
        status.getMessage()));

    setChanged();
    notifyObservers(status);
  }

  @Override
  public void resourceOffers(SchedulerDriver driver, List<Offer> offers) {
    logOffers(offers);

    List<OfferID> acceptedOffers = new ArrayList<OfferID>();

    if (reconciler.complete()) {
      OfferRequirement offerReq = offerReqProvider.getNextRequirement();
      OfferEvaluator offerEvaluator = new OfferEvaluator(offerReq);
      List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
      acceptedOffers = offerAccepter.accept(driver, recommendations);
    }

    declineOffers(driver, acceptedOffers, offers);
  }

  @Override
  public void slaveLost(SchedulerDriver driver, SlaveID slaveId) {
    log.info("Slave lost slaveId: " + slaveId.getValue());
  }

  @Override
  public void run() {
    String zkPath = "zk://" + config.get("ZOOKEEPER_ADDR") + "/mesos";
    FrameworkInfo fwkInfo = getFrameworkInfo();
    log.info("Registering framework with: " + fwkInfo);
    registerFramework(this, fwkInfo, zkPath);
  }

  private void reconcile(SchedulerDriver driver) {
    try {
      reconciler.reconcile(driver, state.getTaskStatuses());
    } catch(Exception ex) {
      log.error("Failed to reconcile with exception: " + ex);
    }
  }

  private String getApiUri() {
    String port = config.get("PORT0");
    String host = config.get("LIBPROCESS_IP");
    return String.format("http://%s:%s", host, port); 
  }

  private FrameworkInfo getFrameworkInfo() {
    FrameworkInfo.Builder fwkInfoBuilder = FrameworkInfo.newBuilder()
      .setName(config.get("FRAMEWORK_NAME"))
      .setFailoverTimeout(2 * 7 * 24 * 60 * 60) // 2 weeks
      .setUser(config.get("USER"))
      .setRole(config.get("ROLE"))
      .setPrincipal(config.get("PRINCIPAL"))
      .setCheckpoint(true);

    FrameworkID fwkId = state.getFrameworkId();
    if (fwkId != null) {
      fwkInfoBuilder.setId(fwkId);
    }

    return fwkInfoBuilder.build();
  }

  private void logOffers(List<Offer> offers) {
    if (offers == null) {
      return;
    }

    log.info(String.format("Received %d offers", offers.size()));

    for (Offer offer : offers) {
      log.info("Received Offer: " + offer);
    }
  }

  private void declineOffers(SchedulerDriver driver, List<OfferID> acceptedOffers, List<Offer> offers) {
    for (Offer offer : offers) {
      if (!acceptedOffers.contains(offer.getId())) {
        declineOffer(driver, offer);
      }
    }
  }

  private void declineOffers(SchedulerDriver driver, List<Offer> offers) {
    if (offers == null) {
      return;
    }

    for (Offer offer : offers) {
      declineOffer(driver, offer);
    }
  }

  private void declineOffer(SchedulerDriver driver, Offer offer) {
    OfferID offerId = offer.getId();
    log.info(String.format("Scheduler declining offer: %s", offerId));
    driver.declineOffer(offerId);
  }

  private void registerFramework(KafkaScheduler sched, FrameworkInfo frameworkInfo, String masterUri) {
    log.info("Registering without authentication");
    new MesosSchedulerDriver(sched, frameworkInfo, masterUri).run();
  }
}
