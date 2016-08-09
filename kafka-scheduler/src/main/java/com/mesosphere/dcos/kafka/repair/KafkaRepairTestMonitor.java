package com.mesosphere.dcos.kafka.repair;

import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.recovery.monitor.TimedFailureMonitor;

/**
 * This implementation can be used to test a simple 3 node kafka setup, by preventing broker 1 from ever appearing as
 * failed (it can only be stopped). This makes it easier to observe the behavior of stopped tasks being restarted.
 */
public class KafkaRepairTestMonitor extends TimedFailureMonitor {
    public KafkaRepairTestMonitor() {
        super(1);
    }

    @Override
    public boolean hasFailed(Protos.TaskInfo terminatedTask) {
        return terminatedTask.getName().contains("1") || super.hasFailed(terminatedTask);
    }
}
