package org.apache.mesos.kafka.plan;

import org.apache.mesos.kafka.config.KafkaConfigService;

public class KafkaBlock implements Block {
  private KafkaConfigService config = null;

  public KafkaBlock(KafkaConfigService config) {
    this.config = config;
  }

  public boolean isComplete() {
    return false;
  }
}

