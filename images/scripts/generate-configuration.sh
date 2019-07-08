#!/usr/bin/env bash

KAFKA_BROKER_ID=${HOSTNAME##*-}

# LISTENERS CONFIGURATION
LISTENERS="PLAINTEXT://0.0.0.0:${KAFKA_BROKER_PORT}"
# ADVERTISED LISTENERS
ADVERTISED_LISTENERS="PLAINTEXT://$(hostname -f):${KAFKA_BROKER_PORT}"
LISTENER_SECURITY_PROTOCOL_MAP="PLAINTEXT:PLAINTEXT"
SASL_ENABLED_MECHANISMS=""

if [[ "$KAFKA_CLIENT_ENABLED" = "true" ]]; then
  LISTENERS="${LISTENERS},CLIENT://0.0.0.0:${KAFKA_CLIENT_PORT}"
  ADVERTISED_LISTENERS="${ADVERTISED_LISTENERS},CLIENT://$(hostname -f):${KAFKA_CLIENT_PORT}"

  if [[ "$KAFKA_CLIENT_AUTHENTICATION" = "scram-sha-512" ]]; then
    SASL_ENABLED_MECHANISMS="SCRAM-SHA-512\n$SASL_ENABLED_MECHANISMS"
    LISTENER_SECURITY_PROTOCOL_MAP="${LISTENER_SECURITY_PROTOCOL_MAP},CLIENT:SASL_PLAINTEXT"
    CLIENT_LISTENER=$(cat <<EOF
# CLIENT listener authentication
listener.name.client.scram-sha-512.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required;
EOF
)
  else
    LISTENER_SECURITY_PROTOCOL_MAP="${LISTENER_SECURITY_PROTOCOL_MAP},CLIENT:PLAINTEXT"
  fi
fi

export KAFKA_LOG_DIR_PATH="${LOG_DIR}/log${KAFKA_BROKER_ID}"

if [[ -e ${KAFKA_HOME}/init/rack.id ]]; then
  export RACK_ID=$(cat ${KAFKA_HOME}/init/rack.id)
fi

KAFKA_CONFIGURATION=$(cat /config/server.properties)
# Write the config file
cat <<EOF
broker.id=${KAFKA_BROKER_ID}
broker.rack=${RACK_ID}
# Listeners
listeners=${LISTENERS}
advertised.listeners=${ADVERTISED_LISTENERS}
listener.security.protocol.map=${LISTENER_SECURITY_PROTOCOL_MAP}
inter.broker.listener.name=PLAINTEXT
# Logs
log.dirs=${KAFKA_LOG_DIR_PATH}
# Provided configuration
${KAFKA_CONFIGURATION}
EOF