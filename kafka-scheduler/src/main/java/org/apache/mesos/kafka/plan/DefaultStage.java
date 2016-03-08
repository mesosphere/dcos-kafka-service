package org.apache.mesos.kafka.plan;

import java.util.List;

import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.Stage;

import com.google.common.collect.Lists;

public class DefaultStage implements Stage {

  private final List<? extends Phase> phases;
  private final List<String> errors;

  public DefaultStage(List<String> errors, Phase... phases) {
    this.phases = Lists.newArrayList(phases);
    this.errors = errors;
  }

  @Override
  public boolean isComplete() {
    if (!errors.isEmpty()) {
      // never complete
      return false;
    }
    for (Phase phase : phases) {
      if (!phase.isComplete()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public List<? extends Phase> getPhases() {
    return phases;
  }

  @Override
  public List<String> getErrors() {
    return errors;
  }

}
