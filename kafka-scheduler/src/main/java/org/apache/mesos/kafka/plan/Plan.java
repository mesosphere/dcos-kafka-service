package org.apache.mesos.kafka.plan;

import java.util.List;

public interface Plan {
  List<Phase> getPhases();

  Phase getCurrentPhase();

  boolean isComplete();
}
