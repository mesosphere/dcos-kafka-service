package com.mesosphere.dcos.kafka.scheduler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.TextFormat;
import com.mesosphere.dcos.kafka.cmd.CmdExecutor;
import com.mesosphere.dcos.kafka.commons.state.KafkaState;
import com.mesosphere.dcos.kafka.config.ConfigStateUpdater;
import com.mesosphere.dcos.kafka.config.ConfigStateValidator.ValidationError;
import com.mesosphere.dcos.kafka.config.ConfigStateValidator.ValidationException;
import com.mesosphere.dcos.kafka.config.KafkaConfigState;
import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import com.mesosphere.dcos.kafka.offer.KafkaOfferRequirementProvider;
import com.mesosphere.dcos.kafka.offer.PersistentOfferRequirementProvider;
import com.mesosphere.dcos.kafka.offer.PersistentOperationRecorder;
import com.mesosphere.dcos.kafka.plan.KafkaUpdatePhase;
import com.mesosphere.dcos.kafka.repair.KafkaFailureMonitor;
import com.mesosphere.dcos.kafka.repair.KafkaRecoveryRequirementProvider;
import com.mesosphere.dcos.kafka.state.ClusterState;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import com.mesosphere.dcos.kafka.web.BrokerController;
import com.mesosphere.dcos.kafka.web.ConnectionController;
import com.mesosphere.dcos.kafka.web.InterruptProceed;
import com.mesosphere.dcos.kafka.web.TopicController;
import io.dropwizard.setup.Environment;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.api.JettyApiServer;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.RecoveryConfiguration;
import org.apache.mesos.config.api.ConfigResource;
import org.apache.mesos.dcos.DCOSCertInstaller;
import org.apache.mesos.dcos.DcosCluster;
import org.apache.mesos.offer.*;
import org.apache.mesos.reconciliation.DefaultReconciler;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.*;
import org.apache.mesos.scheduler.Observable;
import org.apache.mesos.scheduler.Observer;
import org.apache.mesos.scheduler.api.TaskResource;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.api.PlansResource;
import org.apache.mesos.scheduler.plan.strategy.CanaryStrategy;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.recovery.DefaultRecoveryPlanManager;
import org.apache.mesos.scheduler.recovery.DefaultTaskFailureListener;
import org.apache.mesos.scheduler.recovery.RecoveryRequirementProvider;
import org.apache.mesos.scheduler.recovery.TaskFailureListener;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.constrain.TimedLaunchConstrainer;
import org.apache.mesos.state.api.JsonPropertyDeserializer;
import org.apache.mesos.state.api.StateResource;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kafka Framework Scheduler.
 */
public class KafkaScheduler implements Scheduler, Observer, Runnable {
    private static final Log log = LogFactory.getLog(KafkaScheduler.class);

    private static final int TWO_WEEK_SEC = 2 * 7 * 24 * 60 * 60;
    private static TaskKiller taskKiller;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final KafkaConfigState configState;
    private final KafkaSchedulerConfiguration envConfig;
    private final FrameworkState frameworkState;
    private final KafkaState kafkaState;
    private final ClusterState clusterState;

    private final TaskFailureListener taskFailureListener;

    private final OfferAccepter offerAccepter;
    private final Reconciler reconciler;
    private final DefaultPlan installPlan;
    private final PersistentOfferRequirementProvider offerRequirementProvider;
    private final Environment environment;
    private final KafkaSchedulerConfiguration kafkaSchedulerConfiguration;
    private PlanManager planManager;
    private PlanManager repairPlanManager;
    private PlanCoordinator planCoordinator;
    private PlanScheduler planScheduler;
    private SchedulerDriver driver;
    private boolean isRegistered = false;

    public KafkaScheduler(KafkaSchedulerConfiguration configuration, Environment environment)
            throws ConfigStoreException, URISyntaxException {
        this.kafkaSchedulerConfiguration = configuration;
        this.environment = environment;
        ConfigStateUpdater configStateUpdater = new ConfigStateUpdater(configuration);
        List<String> stageErrors = new ArrayList<>();
        KafkaSchedulerConfiguration targetConfigToUse;

        try {
            targetConfigToUse = configStateUpdater.getTargetConfig();
        } catch (ValidationException e) {
            // New target config failed to validate and was not used. Fall back to previous target config.
            log.error("Got " + e.getValidationErrors().size() +
                    " errors from new config. Falling back to last valid config.");
            targetConfigToUse = configStateUpdater.getConfigState().getTargetConfig();
            for (ValidationError err : e.getValidationErrors()) {
                stageErrors.add(err.toString());
            }
        }

        configState = configStateUpdater.getConfigState();
        frameworkState = configStateUpdater.getFrameworkState();
        kafkaState = configStateUpdater.getKafkaState();

        envConfig = targetConfigToUse;
        reconciler = new DefaultReconciler(frameworkState.getStateStore());
        clusterState = new ClusterState();

        offerAccepter =
                new OfferAccepter(Arrays.asList(new PersistentOperationRecorder(frameworkState)));

        offerRequirementProvider =
                new PersistentOfferRequirementProvider(frameworkState, configState, clusterState);

        List<Phase> phases = Arrays.asList(
                ReconciliationPhase.create(reconciler),
                new KafkaUpdatePhase(
                        configState.getTargetName().toString(),
                        envConfig,
                        frameworkState,
                        offerRequirementProvider,
                        getPhaseStrategyFactory(envConfig)));

        // If config validation had errors, expose them via the Stage.
        installPlan =  new DefaultPlan("deploy", phases, new SerialStrategy<>(), stageErrors);
        taskFailureListener = new DefaultTaskFailureListener(frameworkState.getStateStore());
        planManager = createDeployPlanManager(installPlan);
        repairPlanManager = createRecoveryPlanManager(offerRequirementProvider);

        planManager.subscribe(this);
        repairPlanManager.subscribe(this);
    }

    private void initialize(SchedulerDriver driver) {
        taskKiller = new DefaultTaskKiller(frameworkState.getStateStore(), taskFailureListener, driver);
        planScheduler = new DefaultPlanScheduler(
                offerAccepter,
                new OfferEvaluator(frameworkState.getStateStore()),
                taskKiller);
        planCoordinator = new DefaultPlanCoordinator(ImmutableList.of(planManager, repairPlanManager), planScheduler);

        startApiServer();

        String javaHome = System.getenv("JAVA_HOME");
        boolean certInstalled = DCOSCertInstaller.installCertificate(javaHome);
        log.info("Attempt to install cert into: " + javaHome + " was " + (certInstalled ? "successful" : "a failure"));
    }

    private void startApiServer() {
        // Kafka-specific APIs:
        Collection<Object> resources = new ArrayList<>();
        resources.add(new ConnectionController(
                kafkaSchedulerConfiguration.getFullKafkaZookeeperPath(),
                getConfigState(),
                getKafkaState(),
                new ClusterState(new DcosCluster()),
                kafkaSchedulerConfiguration.getZookeeperConfig().getFrameworkName()));
        resources.add(new BrokerController(this));
        resources.add(new TopicController(
                new CmdExecutor(kafkaSchedulerConfiguration, this),
                this));

        // APIs from dcos-commons:
        resources.add(new ConfigResource<>(
                getConfigState().getConfigStore()));
        resources.add(new TaskResource(
                getFrameworkState().getStateStore(),
                taskKiller,
                kafkaSchedulerConfiguration.getServiceConfiguration().getName()));
        resources.add(new InterruptProceed(getPlanManager()));
        resources.add(new PlansResource(ImmutableMap.of(
                "deploy", getPlanManager(),
                "recovery", getRepairManager())));
        resources.add(new StateResource(frameworkState.getStateStore(), new JsonPropertyDeserializer()));
        executor.execute(() -> {
            try {
                new JettyApiServer(Integer.valueOf(System.getenv("PORT1")), resources).start();
            } catch (Exception e) {
                log.error("Failed to start API server.", e);
            }
        });
    }
    // dcos-commons 0.8.1 upgrade: just to be consistent with the existing java tests (see KafkaSchedulerTest.java)
    protected PlanManager createDeployPlanManager(Plan installPlan) {
        return new DefaultPlanManager(installPlan);
    }

    protected PlanManager createRecoveryPlanManager(KafkaOfferRequirementProvider offerRequirementProvider) {
        RecoveryConfiguration recoveryConfiguration = envConfig.getRecoveryConfiguration();
        LaunchConstrainer constrainer = new TimedLaunchConstrainer(
                Duration.ofSeconds(recoveryConfiguration.getRecoveryDelaySecs()));
        RecoveryRequirementProvider recoveryRequirementProvider =
                new KafkaRecoveryRequirementProvider(
                        offerRequirementProvider,
                        configState.getConfigStore());

            return new DefaultRecoveryPlanManager(
                    frameworkState.getStateStore(),
                    recoveryRequirementProvider,
                    constrainer,
                    new KafkaFailureMonitor(recoveryConfiguration));
    }

    private static Strategy getPhaseStrategyFactory(KafkaSchedulerConfiguration config) {
        String strategy = config.getServiceConfiguration().getPhaseStrategy();
        switch (strategy) {
            case "INSTALL":
                return new SerialStrategy<>();
            case "STAGE":
                return new CanaryStrategy<>();
            default:
                log.warn("Unknown strategy: " + strategy);
                return new CanaryStrategy<>();
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
    public void slaveLost(SchedulerDriver driver, SlaveID slaveId) {
        log.info("Slave lost slaveId: " + slaveId.getValue());
    }

    @Override
    public void frameworkMessage(
            SchedulerDriver driver, ExecutorID executorID, SlaveID slaveID, byte[] data) {
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
        try {
            initialize(driver);
            frameworkState.getStateStore().storeFrameworkId(frameworkId);
            isRegistered = true;
            reviveOffers(driver);
        } catch (Exception e) {
            isRegistered = false;
            log.error(String.format(
                    "Unable to store registered framework ID '%s'", frameworkId.getValue()), e);
            //TODO(nick): exit process?
        }
    }

    private boolean hasOperations() {
        log.debug("deploy = isComplete? " + planManager.getPlan().isComplete());
        log.debug("recovery = isComplete? " + repairPlanManager.getPlan().isComplete());
        boolean hasOperations = !(planManager.getPlan().isComplete() &&
                repairPlanManager.getPlan().isComplete());
        log.debug(hasOperations ?
                "Scheduler has operations to perform." :
                "Scheduler has no operations to perform.");
        return hasOperations;
    }

    private void reviveOffers(SchedulerDriver driver) {
        log.info("Reviving offers.");
        driver.reviveOffers();
        frameworkState.setSuppressed(false);
    }

    private void suppressOffers(SchedulerDriver driver) {
        log.info("Suppressing offers.");
        driver.suppressOffers();
        frameworkState.setSuppressed(true);
    }

    @Override
    public void reregistered(SchedulerDriver driver, MasterInfo masterInfo) {
        log.info("Re-registered framework, exiting.");
        hardExit(SchedulerErrorCode.RE_REGISTRATION);
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    @SuppressWarnings({"DM_EXIT"})
    private void hardExit(SchedulerErrorCode errorCode) {
        System.exit(errorCode.ordinal());
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, TaskStatus status) {
        log.info(String.format(
                "Received status update for taskId=%s state=%s message='%s'",
                status.getTaskId().getValue(),
                status.getState().toString(),
                status.getMessage()));
        // Store status, then pass status to StageManager => Plan => Steps
        try {
            frameworkState.updateStatus(status);
        } catch (Exception e) {
            log.warn("Failed to update TaskStatus received from Mesos. "
                    + "This may be expected if Mesos sent stale status information: " + status, e);
        }
        if (!planManager.getPlan().isWaiting()) {
            planManager.update(status);
        }
        repairPlanManager.update(status);
        reconciler.update(status);

        if (TaskUtils.needsRecovery(status) && frameworkState.isSuppressed()){
            reviveOffers(driver);
        }
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Offer> offers) {
        try {
            logOffers(offers);
            reconciler.reconcile(driver);

            List<OfferID> acceptedOffers = new ArrayList<>();

            if (!reconciler.isReconciled()) {
                log.info("Accepting no offers: Reconciler is still in progress");
            } else {

                acceptedOffers.addAll(planCoordinator.processOffers(driver, offers));

                List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(offers, acceptedOffers);
                offers.clear();
                offers.addAll(unusedOffers);

                ResourceCleanerScheduler cleanerScheduler = getCleanerScheduler();
                if (cleanerScheduler != null) {
                    acceptedOffers.addAll(getCleanerScheduler().resourceOffers(driver, offers));
                }
                unusedOffers = OfferUtils.filterOutAcceptedOffers(offers, acceptedOffers);
                offers.clear();
                offers.addAll(unusedOffers);
            }
            log.info(String.format("Accepted %d of %d offers: %s",
                    acceptedOffers.size(), offers.size(), acceptedOffers));
            declineOffers(driver, acceptedOffers, offers);

            if (!hasOperations()) {
                suppressOffers(driver);
                this.driver = driver;
            }

        } catch (Exception ex) {
            log.error("Unexpected exception encountered when processing offers", ex);
        }
    }

    private ResourceCleanerScheduler getCleanerScheduler() {
        try {
            ResourceCleaner cleaner = new ResourceCleaner(frameworkState.getStateStore());
            return new ResourceCleanerScheduler(cleaner, offerAccepter);
        } catch (Exception ex) {
            log.error("Failed to construct ResourceCleaner", ex);
            return null;
        }
    }

    @Override
    public void run() {
        Thread.currentThread().setName("KafkaScheduler");
        Thread.currentThread().setUncaughtExceptionHandler(getUncaughtExceptionHandler());

        String zkPath = "zk://" + envConfig.getKafkaConfiguration().getMesosZkUri() + "/mesos";
        FrameworkInfo fwkInfo = getFrameworkInfo();
        log.info("Registering framework with: " + fwkInfo);
        registerFramework(this, fwkInfo, zkPath);
    }

    @Override
    public void update(Observable observable) {
        // before suppress, we updated this.driver
        if (hasOperations() && frameworkState.isSuppressed()) {
            reviveOffers(driver);
        }
    }

    private FrameworkInfo getFrameworkInfo() {
        FrameworkInfo.Builder fwkInfoBuilder = FrameworkInfo.newBuilder()
                .setName(envConfig.getServiceConfiguration().getName())
                .setFailoverTimeout(TWO_WEEK_SEC)
                .setUser(envConfig.getServiceConfiguration().getUser())
                .setRole(envConfig.getServiceConfiguration().getRole())
                .setPrincipal(envConfig.getServiceConfiguration().getPrincipal())
                .setCheckpoint(true);

        Optional<FrameworkID> fwkId = frameworkState.getStateStore().fetchFrameworkId();
        if (fwkId.isPresent()) {
            fwkInfoBuilder.setId(fwkId.get());
        }
        return fwkInfoBuilder.build();
    }

    private void logOffers(List<Offer> offers) {
        if (offers == null) {
            return;
        }

        log.info(String.format("Received %d offers:", offers.size()));
        for (int i = 0; i < offers.size(); ++i) {
            // offer protos are very long. print each as a single line:
            log.info(String.format("- Offer %d: %s", i + 1, TextFormat.shortDebugString(offers.get(i))));
        }
    }

    private void declineOffers(SchedulerDriver driver, List<OfferID> acceptedOffers, List<Offer> offers) {
        for (Offer offer : offers) {
            OfferID offerId = offer.getId();
            if (!acceptedOffers.contains(offerId)) {
                log.info("Declining offer: " + offerId.getValue());
                driver.declineOffer(offerId);
            }
        }
    }

    private void registerFramework(KafkaScheduler sched, FrameworkInfo frameworkInfo, String masterUri) {
        log.info("Registering without authentication");
        driver = new SchedulerDriverFactory().create(sched, frameworkInfo, masterUri);
        driver.run();
    }

    private Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {

        return new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                final String msg = "Scheduler exiting due to uncaught exception";
                log.error(msg, e);
                log.fatal(msg, e);
                System.exit(2);
            }
        };
    }

    public KafkaConfigState getConfigState() {
        return configState;
    }

    public FrameworkState getFrameworkState() {
        return frameworkState;
    }

    public KafkaState getKafkaState() {
        return kafkaState;
    }

    public PlanManager getPlanManager() {
        return planManager;
    }
    public PlanManager getRepairManager() {
        return repairPlanManager;
    }

    public static void restartTasks(TaskInfo taskInfo) {
        if (taskInfo != null) {
            taskKiller.killTask(taskInfo.getName(), false);
        }
    }

    public static void rescheduleTask(TaskInfo taskInfo) {
        taskKiller.killTask(taskInfo.getName(), true);
    }
}
