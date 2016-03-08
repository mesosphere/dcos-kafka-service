package org.apache.mesos.kafka.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.offer.LogOperationRecorder;
import org.apache.mesos.kafka.offer.KafkaOfferRequirementProvider;
import org.apache.mesos.kafka.offer.PersistentOfferRequirementProvider;
import org.apache.mesos.kafka.offer.PersistentOperationRecorder;
import org.apache.mesos.kafka.offer.SandboxOfferRequirementProvider;
import org.apache.mesos.kafka.plan.KafkaReconcilePhase;
import org.apache.mesos.kafka.plan.KafkaUpdatePhase;
import org.apache.mesos.kafka.plan.PlanUtils;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.kafka.web.KafkaApiServer;

import org.apache.mesos.config.ConfigurationChangeDetector;
import org.apache.mesos.config.ConfigurationChangeNamespaces;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.DefaultPlan;
import org.apache.mesos.scheduler.plan.DefaultPlanScheduler;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.scheduler.plan.StrategyPlanManager;
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

  private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
  private final KafkaConfigService envConfig;
  private final KafkaStateService state;
  private final DefaultPlanScheduler planScheduler;
  private final KafkaRepairScheduler repairScheduler;
  private final KafkaApiServer apiServer;
  private final OfferAccepter offerAccepter;
  private final Reconciler reconciler;

  private static StrategyPlanManager planManager = null;
  private static final Integer restartLock = 0;
  private static List<String> tasksToRestart = new ArrayList<String>();
  private static final Integer rescheduleLock = 0;
  private static List<String> tasksToReschedule = new ArrayList<String>();
  private static KafkaConfigState configState;

  public KafkaScheduler() {
    envConfig = KafkaConfigService.getEnvConfig();
    state = KafkaStateService.getStateService();
    configState = new KafkaConfigState(envConfig.getFrameworkName(), envConfig.getZookeeperAddress(), "/");
    apiServer = new KafkaApiServer();
    reconciler = new Reconciler();

    addObserver(state);
    addObserver(reconciler);

    offerAccepter =
      new OfferAccepter(Arrays.asList(
            new LogOperationRecorder(),
            new PersistentOperationRecorder(state)));

    handleConfigChange();

    Plan plan = DefaultPlan.fromArgs(
        new KafkaReconcilePhase(reconciler),
        new KafkaUpdatePhase(configState.getTargetName(), getOfferRequirementProvider()));
    planManager = new StrategyPlanManager(plan, PlanUtils.getPhaseStrategyFactory(envConfig));
    addObserver(planManager);

    planScheduler = new DefaultPlanScheduler(offerAccepter);
    repairScheduler = new KafkaRepairScheduler(configState, getOfferRequirementProvider(), offerAccepter);
  }

  private void handleConfigChange() {
    if (!configState.hasTarget()) {
      String targetConfigName = UUID.randomUUID().toString();
      configState.store(envConfig, targetConfigName);
      configState.setTargetName(targetConfigName);
    } else {
      KafkaConfigService currTarget = configState.getTargetConfig();
      KafkaConfigService newTarget = envConfig;

      ConfigurationChangeDetector changeDetector = new ConfigurationChangeDetector(
          currTarget.getNsPropertyMap(),
          newTarget.getNsPropertyMap(),
          new ConfigurationChangeNamespaces("*", "*"));

      if (changeDetector.isChangeDetected()) {
        log.info("Detected changed properties.");
        logConfigChange(changeDetector);
        setTargetConfig(newTarget);
        configState.syncConfigs();
      } else {
        log.info("No change detected.");
      }
    }
  }

  private void logConfigChange(ConfigurationChangeDetector changeDetector) {
    log.info("Extra config properties detected: " + Arrays.toString(changeDetector.getExtraConfigs().toArray()));
    log.info("Missing config properties detected: " + Arrays.toString(changeDetector.getMissingConfigs().toArray()));
    log.info("Changed config properties detected: " + Arrays.toString(changeDetector.getChangedProperties().toArray()));
  }

  private void setTargetConfig(KafkaConfigService newTargetConfig) {
      String targetConfigName = UUID.randomUUID().toString();
      configState.store(newTargetConfig, targetConfigName);
      configState.setTargetName(targetConfigName);
      log.info("Set new target config: " + targetConfigName);
  }

  public static void restartTasks(List<String> taskIds) {
    synchronized (restartLock) {
      tasksToRestart.addAll(taskIds);
    }
  }

  public static void rescheduleTasks(List<String> taskIds) {
    synchronized (rescheduleLock) {
      tasksToReschedule.addAll(taskIds);
    }
  }

  public static KafkaConfigState getConfigState() {
    return configState;
  }

  public static StrategyPlanManager getPlanManager() {
    return planManager;
  }

  private KafkaOfferRequirementProvider getOfferRequirementProvider() {
    boolean persistentVolumesEnabled = Boolean.parseBoolean(envConfig.get("BROKER_PV"));

    if (persistentVolumesEnabled) {
      return new PersistentOfferRequirementProvider(state, configState);
    } else {
      return new SandboxOfferRequirementProvider(state, configState);
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
    apiServer.start(configState, envConfig, state, planManager);
  }

  @Override
  public void reregistered(SchedulerDriver driver, MasterInfo masterInfo) {
    log.info("Reregistered framework.");
    reconcile(driver);
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
    processTaskOperations(driver);

    List<OfferID> acceptedOffers = new ArrayList<OfferID>();

    if (reconciler.complete()) {
      Block block = planManager.getCurrentBlock();
      acceptedOffers = planScheduler.resourceOffers(driver, offers, block);
      List<Offer> unacceptedOffers = filterAcceptedOffers(offers, acceptedOffers);
      acceptedOffers.addAll(repairScheduler.resourceOffers(driver, unacceptedOffers, block));
    }

    declineOffers(driver, acceptedOffers, offers);
  }

  private List<Offer> filterAcceptedOffers(List<Offer> offers, List<OfferID> acceptedOfferIds) {
    List<Offer> filteredOffers = new ArrayList<Offer>();

    for (Offer offer : offers) {
      if (!offerAccepted(offer, acceptedOfferIds)) {
        filteredOffers.add(offer);
      }
    }

    return filteredOffers;
  }

  private boolean offerAccepted(Offer offer, List<OfferID> acceptedOfferIds) {
    for (OfferID acceptedOfferId: acceptedOfferIds) {
      if(acceptedOfferId.equals(offer.getId())) {
        return true;
      }
    }

    return false;
  }

  private void processTaskOperations(SchedulerDriver driver) {
    processTasksToRestart(driver);
    processTasksToReschedule(driver);
  }

  private void processTasksToRestart(SchedulerDriver driver) {
    synchronized (restartLock) {
      for (String taskId : tasksToRestart) {
        log.info("Restarting task: " + taskId);
        driver.killTask(TaskID.newBuilder().setValue(taskId).build());
      }

      tasksToRestart = new ArrayList<String>();
    }
  }

  private void processTasksToReschedule(SchedulerDriver driver) {
    synchronized (rescheduleLock) {
      for (String taskId : tasksToReschedule) {
        log.info("Rescheduling task: " + taskId);
        state.deleteTask(taskId);
        driver.killTask(TaskID.newBuilder().setValue(taskId).build());
      }

      tasksToReschedule = new ArrayList<String>();
    }
  }

  @Override
  public void slaveLost(SchedulerDriver driver, SlaveID slaveId) {
    log.info("Slave lost slaveId: " + slaveId.getValue());
  }

  @Override
  public void run() {
    String zkPath = "zk://" + envConfig.getZookeeperAddress() + "/mesos";
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

  private FrameworkInfo getFrameworkInfo() {
    String fwkName = envConfig.getFrameworkName();

    FrameworkInfo.Builder fwkInfoBuilder = FrameworkInfo.newBuilder()
      .setName(fwkName)
      .setFailoverTimeout(TWO_WEEK_SEC)
      .setUser(envConfig.get("USER"))
      .setRole(envConfig.getRole())
      .setPrincipal(envConfig.getPrincipal())
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
