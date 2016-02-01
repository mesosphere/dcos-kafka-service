package org.apache.mesos.kafka.offer;

import org.apache.mesos.config.ConfigurationService;

public class OfferRequirementUtils {

  public static String getKafkaStartCmd(ConfigurationService config, Integer brokerId, Integer port, String containerPath) {
    return String.format(
        "$MESOS_SANDBOX/%1$s/bin/kafka-server-start.sh "
        + "$MESOS_SANDBOX/%1$s/config/server.properties "
        + "--override zookeeper.connect=%2$s/%3$s "
        + "--override broker.id=%4$s "
        + "--override log.dirs=$MESOS_SANDBOX/%6$s "
        + "--override port=%5$d "
        + "--override listeners=PLAINTEXT://:%5$d "
        + "--override delete.topic.enable=%7$s "
        + "$CONTAINER_HOOK_FLAGS",
        config.get("KAFKA_VER_NAME"), // #1
        config.get("ZOOKEEPER_ADDR"), // #2
        config.get("FRAMEWORK_NAME"), // #3
        brokerId, // #4
        port, // #5
        containerPath,
        config.get("BROKER_DELETE_TOPIC_ENABLE")); // #6
  }
}
