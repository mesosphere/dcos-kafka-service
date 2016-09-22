package com.mesosphere.dcos.kafka.repair;

import com.mesosphere.dcos.kafka.offer.OfferUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.recovery.RecoveryRequirement;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * This implementation can be used to test a simple 3 node kafka setup, by preventing broker 2 from ever launching.
 * This makes it easier to observe the behavior of stopped tasks in the API.
 */
public class KafkaRecoveryTestConstrainer implements LaunchConstrainer {
    @Override
    public void launchHappened(Protos.Offer.Operation launchOperation, RecoveryRequirement.RecoveryType recoveryType) {
        // this doesn't affect this implementation's behavior
    }

    @Override
    public boolean canLaunch(RecoveryRequirement recoveryRequirement) {
        Set<String> names = recoveryRequirement.getOfferRequirement().getTaskRequirements().stream()
                .map(it -> it.getTaskInfo().getName())
                .collect(Collectors.toSet());
        if (names.contains(OfferUtils.brokerIdToTaskName(2))) {
            return false;
        }
        return true;
    }
}
