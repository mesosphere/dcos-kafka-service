package com.mesosphere.dcos.kafka.executor;

import com.codahale.metrics.health.HealthCheck;
import com.mesosphere.dcos.kafka.commons.state.KafkaState;
import com.mesosphere.dcos.kafka.config.ZookeeperConfiguration;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * This HealthCheck determines whether a given BrokerId is present in Kafka's Zookeeper.
 */
public class BrokerRegisterCheck extends HealthCheck {
    public static final String NAME = "BrokerRegistered";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final KafkaState kafkaState;
    private final String brokerId;

    public BrokerRegisterCheck(String brokerId, ZookeeperConfiguration zkConfig) {
        this.brokerId = brokerId;
        kafkaState = new KafkaState(zkConfig);
    }

    @Override
    protected Result check() {
        try {
            Optional<JSONObject> optionalBrokerJson = kafkaState.getBroker(brokerId);
            if (kafkaState.getBroker(brokerId).isPresent()) {
                return Result.healthy("Broker Id is present in Zookeeper.");
            } else {
                return Result.unhealthy("Broker Id is NOT present in Zookeeper.");
            }
        } catch (Exception e) {
            logger.error("Failed to fecth Broker Id with exception: ", e);
            return Result.unhealthy("Failed to fetch Broker Id.");
        }
    }
}
