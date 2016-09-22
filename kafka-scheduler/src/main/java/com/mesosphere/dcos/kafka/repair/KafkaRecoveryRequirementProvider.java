package com.mesosphere.dcos.kafka.repair;

import com.mesosphere.dcos.kafka.offer.KafkaOfferRequirementProvider;
import com.mesosphere.dcos.kafka.offer.OfferUtils;
import org.apache.mesos.config.ConfigStore;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.scheduler.recovery.DefaultRecoveryRequirement;
import org.apache.mesos.scheduler.recovery.RecoveryRequirement;
import org.apache.mesos.scheduler.recovery.RecoveryRequirementProvider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the {@link RecoveryRequirementProvider} interface for the Kafka framework.
 */
public class KafkaRecoveryRequirementProvider implements RecoveryRequirementProvider {
    private static final Log log = LogFactory.getLog(KafkaOfferRequirementProvider.class);

    private final KafkaOfferRequirementProvider offerRequirementProvider;
    private final ConfigStore configStore;

    public KafkaRecoveryRequirementProvider(
            KafkaOfferRequirementProvider offerRequirementProvider,
            ConfigStore configStore) {
        this.offerRequirementProvider = offerRequirementProvider;
        this.configStore = configStore;
    }

    /**
     * This creates a replacement {@link OfferRequirement} from the terminated {@link TaskInfo}
     * by delegating to {@link KafkaOfferRequirementProvider}.
     * @param stoppedTasks The tasks that have stopped and need to be relaunched.
     * @return A list of OfferRequirements generated from the failed Tasks.
     */
    @Override
    public List<RecoveryRequirement> getTransientRecoveryRequirements(List<TaskInfo> stoppedTasks) {
        List<RecoveryRequirement> transientRecoveryRequirements = new ArrayList<>();

        for (TaskInfo taskInfo : stoppedTasks) {
            try {
                transientRecoveryRequirements.add(
                        new DefaultRecoveryRequirement(
                                offerRequirementProvider.getReplacementOfferRequirement(taskInfo),
                                RecoveryRequirement.RecoveryType.TRANSIENT));
            } catch (InvalidRequirementException e) {
                log.error("Failed to create an OfferRequirement for the transiently failed task: " + taskInfo, e);
            }
        }

        return transientRecoveryRequirements;
    }

    /**
     * This returns all the OfferRequirements for the reported permanently failed Tasks.
     *
     * The actual replacement offer requirements come from the {@link KafkaOfferRequirementProvider}.
     *
     * @param failedTasks The list of permanently failed Tasks.
     * @return A list of OfferRequirements generated from the failed Tasks.
     */
    @Override
    public List<RecoveryRequirement> getPermanentRecoveryRequirements(List<TaskInfo> failedTasks) {
        List<RecoveryRequirement> permanentRecoveryRequirements = new ArrayList<>();

        for (TaskInfo taskInfo : failedTasks) {
            int brokerId = OfferUtils.nameToId(taskInfo.getName());
            try {
                permanentRecoveryRequirements.add(
                        new DefaultRecoveryRequirement(
                                offerRequirementProvider.getNewOfferRequirement(
                                        configStore.getTargetConfig().toString(),
                                        brokerId),
                                RecoveryRequirement.RecoveryType.PERMANENT));
            } catch (InvalidRequirementException|IOException|URISyntaxException e) {
                log.error("Failed to create an OfferRequirement for the permanently failed task: " + taskInfo, e);
            }
        }

        return permanentRecoveryRequirements;
    }
}
