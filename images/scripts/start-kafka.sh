#!/usr/bin/env bash

${KAFKA_HOME}/generate-configuration.sh > ${KAFKA_HOME}/server.properties

echo "starting the kafka broker using broker.id ${HOSTNAME##*-}..."
KAFKA_OPTS="${KAFKA_OPTS} ${METRICS_OPTS}" exec ${KAFKA_HOME}/bin/kafka-server-start.sh ${KAFKA_HOME}/server.properties
