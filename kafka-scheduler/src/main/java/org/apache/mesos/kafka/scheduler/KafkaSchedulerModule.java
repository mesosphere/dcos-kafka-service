package org.apache.mesos.kafka.scheduler;

import com.google.inject.AbstractModule;
import io.dropwizard.setup.Environment;
import org.apache.mesos.kafka.config.KafkaSchedulerConfiguration;

/**
 * Guice Module for initializing interfaces to implementations for the HDFS Scheduler.
 */
public class KafkaSchedulerModule extends AbstractModule {

  private final KafkaSchedulerConfiguration configuration;
  private final Environment environment;

  public KafkaSchedulerModule(
          KafkaSchedulerConfiguration configuration,
          Environment environment) {
    this.configuration = configuration;
    this.environment = environment;
  }

  @Override
  protected void configure() {
    bind(Environment.class).toInstance(this.environment);
    bind(KafkaSchedulerConfiguration.class).toInstance(this.configuration);
  }
}
