package org.apache.mesos.kafka.web;

import java.net.URI;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.config.ConfigurationService;

import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

public class KafkaApiServer {
  private static final Log log = LogFactory.getLog(KafkaApiServer.class);
  private static ConfigurationService config = KafkaConfigService.getEnvConfig();

  public static void start() {
    ResourceConfig resourceConfig = new ResourceConfig();
    resourceConfig.registerInstances(new ClusterController());
    resourceConfig.registerInstances(new BrokerController());
    resourceConfig.registerInstances(new TopicController());
    resourceConfig.registerInstances(new PlanController());

    Logger l = Logger.getLogger("org.glassfish.grizzly.http.server.HttpHandler");
    l.setLevel(Level.FINE);
    l.setUseParentHandlers(false);
    ConsoleHandler ch = new ConsoleHandler();
    ch.setLevel(Level.ALL);
    l.addHandler(ch);

    GrizzlyHttpServerFactory.createHttpServer(getUri(), resourceConfig);
  }

  private static URI getUri() {
    String port0 = config.get("PORT0");
    String host = config.get("LIBPROCESS_IP");

    try {
      return new URI("http://" + host + ":" + port0);
    } catch(Exception ex) {
      log.error("Failed to generate URI with exception: " + ex);
      return null;
    }
  }
}
