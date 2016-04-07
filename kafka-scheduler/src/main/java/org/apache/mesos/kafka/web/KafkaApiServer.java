package org.apache.mesos.kafka.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.kafka.cmd.CmdExecutor;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.scheduler.plan.StrategyStageManager;
import org.apache.mesos.scheduler.plan.api.StageResource;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URISyntaxException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KafkaApiServer {
  private static final Log log = LogFactory.getLog(KafkaApiServer.class);
  private Logger httpLogger;

  public KafkaApiServer() {
  }

  public void start(
    KafkaConfigState kafkaConfigState,
    KafkaConfigService kafkaConfigService,
    KafkaStateService kafkaStateService,
    StrategyStageManager strategyStageManager) {
    ResourceConfig resourceConfig = new ResourceConfig().registerInstances(
      new ClusterController(kafkaConfigService.getKafkaZkUri(), kafkaConfigState, kafkaStateService),
      new BrokerController(kafkaStateService),
      new TopicController(new CmdExecutor(kafkaConfigService, kafkaStateService), kafkaStateService),
      new StageResource(strategyStageManager));

    // Manually enable verbose HTTP logging
    httpLogger = Logger.getLogger("org.glassfish.grizzly.http.server.HttpHandler");
    httpLogger.setLevel(Level.FINE);
    httpLogger.setUseParentHandlers(false);
    ConsoleHandler ch = new ConsoleHandler();
    ch.setLevel(Level.ALL);
    httpLogger.addHandler(ch);

    try {
      GrizzlyHttpServerFactory.createHttpServer(kafkaConfigService.getApiUri(), resourceConfig);
    } catch (URISyntaxException e) {
      log.fatal("Unable to start API HTTP server", e);
    }
  }
}
