package org.apache.mesos.kafka.scheduler;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * The Kafka Framework will have a few global objects found here in the application
 * context.  At a minimum it provides access to the guice injector.
 */
public class ApplicationContext {

  private static Injector injector;

  public static Injector getInjector() {

    if (injector == null) {
      injector = Guice.createInjector(new KafkaSchedulerModule());
    }
    return injector;
  }
}
