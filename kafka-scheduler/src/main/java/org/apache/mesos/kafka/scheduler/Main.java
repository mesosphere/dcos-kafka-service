package org.apache.mesos.kafka.scheduler;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableLookup;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.java8.Java8Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.mesos.kafka.config.KafkaSchedulerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Scheduler.
 */
public final class Main extends Application<KafkaSchedulerConfiguration> {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    new Main().run(args);
  }

  protected Main() {
    super();
  }

  @Override
  public String getName() {
    return "DCOS Kafka Service";
  }

  @Override
  public void initialize(Bootstrap<KafkaSchedulerConfiguration> bootstrap) {
    super.initialize(bootstrap);

    StrSubstitutor strSubstitutor = new StrSubstitutor(new EnvironmentVariableLookup(false));
    strSubstitutor.setEnableSubstitutionInVariables(true);

    bootstrap.addBundle(new Java8Bundle());
    bootstrap.setConfigurationSourceProvider(
            new SubstitutingSourceProvider(
                    bootstrap.getConfigurationSourceProvider(),
                    strSubstitutor));
  }

  @Override
  public void run(KafkaSchedulerConfiguration configuration, Environment environment) throws Exception {
    logConfiguration(configuration);

    final KafkaSchedulerModule kafkaSchedulerModule = new KafkaSchedulerModule(configuration, environment);
    Injector injector = Guice.createInjector(kafkaSchedulerModule);

    registerManagedObjects(environment, injector);
  }

  private void registerManagedObjects(Environment environment, Injector injector) {
    environment.lifecycle().manage(
            injector.getInstance(KafkaScheduler.class));
  }

  private void logConfiguration(KafkaSchedulerConfiguration configuration) {
  }
}
