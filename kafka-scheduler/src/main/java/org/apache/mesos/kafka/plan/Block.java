package org.apache.mesos.kafka.plan;

public interface Block {
  boolean isInProgress();
  boolean isComplete();
}
