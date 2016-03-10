package org.apache.mesos.kafka.web;

import java.net.URISyntaxException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.kafka.cmd.CmdExecutor;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.scheduler.plan.StrategyStageManager;

import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

public class KafkaApiServer {
  private static final Log log = LogFactory.getLog(KafkaApiServer.class);

  public KafkaApiServer() {
  }

  public void start(
      KafkaConfigState kafkaConfigState,
      KafkaConfigService kafkaConfigService,
      KafkaStateService kafkaStateService,
      StrategyStageManager strategyStageManager) {
    ResourceConfig resourceConfig = new ResourceConfig()
        .registerInstances(new ClusterController(kafkaConfigService.getKafkaZkUri(), kafkaConfigState, kafkaStateService))
        .registerInstances(new BrokerController(kafkaStateService))
        .registerInstances(new TopicController(new CmdExecutor(kafkaConfigService, kafkaStateService), kafkaStateService))
        .registerInstances(new PlanController(strategyStageManager));

    // Manually enable verbose HTTP logging
    Logger l = Logger.getLogger("org.glassfish.grizzly.http.server.HttpHandler");
    l.setLevel(Level.FINE);
    l.setUseParentHandlers(false);
    ConsoleHandler ch = new ConsoleHandler();
    ch.setLevel(Level.ALL);
    l.addHandler(ch);

    try {
      GrizzlyHttpServerFactory.createHttpServer(kafkaConfigService.getApiUri(), resourceConfig);
    } catch (URISyntaxException e) {
      log.fatal("Unable to start API HTTP server", e);
    }
  }
}
