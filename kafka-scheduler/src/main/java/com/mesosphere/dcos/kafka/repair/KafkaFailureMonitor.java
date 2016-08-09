package com.mesosphere.dcos.kafka.repair;

import org.apache.mesos.Protos;
import org.apache.mesos.config.RecoveryConfiguration;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.scheduler.recovery.monitor.NeverFailureMonitor;
import org.apache.mesos.scheduler.recovery.monitor.TimedFailureMonitor;

/**
 * The KafkaFailureMonitor determines whether or not a Task has failed.  A task can be marked as terminally failed
 * by the manual replace endpoint available through the Scheduler's REST API.  This accounts for the check on the
 * TaskInfo labels to determine if a Task has been marked as permanently failed.
 */
public class KafkaFailureMonitor implements FailureMonitor {
    private final FailureMonitor autoFailureMonitor;

    public KafkaFailureMonitor(RecoveryConfiguration recoveryConfiguration) {
        if (recoveryConfiguration.isReplacementEnabled()) {
            autoFailureMonitor = new TimedFailureMonitor(recoveryConfiguration.getGracePeriodMins());
        } else {
            autoFailureMonitor = new NeverFailureMonitor();
        }
    }

    @Override
    public boolean hasFailed(Protos.TaskInfo taskInfo) {
        return autoFailureMonitor.hasFailed(taskInfo) || FailureUtils.labeledAsFailed(taskInfo);
    }
}
