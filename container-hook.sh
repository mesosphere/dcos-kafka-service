#!/bin/sh

# This hook performs additional container-side preparation for Kafka before it's started. This file
# should only contain logic that depends on the container environment itself; all other logic
# belongs at the scheduler.

echo "### HOOK BEGIN"

# If our package included a library overlay, copy it onto the Kafka distribution's /libs directory
LIB_OVERLAY_PATH="${MESOS_SANDBOX}/container-hook/libs"
if [ -d "${LIB_OVERLAY_PATH}" ]; then
    # Find the Kafka distribution, which holds the lib destination.
    KAFKA_DISTRIBUTION_SEARCH_PATTERN="${MESOS_SANDBOX}/kafka_*/"
    KAFKA_DISTRIBUTION_ROOT="$(ls -d ${KAFKA_DISTRIBUTION_SEARCH_PATTERN})"
    if [ -d "${KAFKA_DISTRIBUTION_ROOT}" ]; then
        echo "Kafka distribution found: ${KAFKA_DISTRIBUTION_ROOT}"
    else
        echo "Kafka distribution not found, unable to continue with stats. Exiting immediately!: pwd=$(pwd) search=${KAFKA_DISTRIBUTION_SEARCH_PATTERN}"
        exit 1 # fail Kafka startup so that the problem will be discoverable
    fi

    echo "Copying library overlay onto Kafka classpath..."
    echo "--- BEGIN COPY ${LIB_OVERLAY_PATH} => ${KAFKA_DISTRIBUTION_ROOT}"
    cp -vR ${LIB_OVERLAY_PATH} ${KAFKA_DISTRIBUTION_ROOT}
    if [ $? -ne 0 ]; then
        echo "Failed to copy library overlays from ${LIB_OVERLAY_PATH} to ${KAFKA_DISTRIBUTION_ROOT}. Exiting immediately!"
        exit 1 # fail Kafka startup so that the problem will be discoverable
    fi
    echo "--- END COPY ${LIB_OVERLAY_PATH} => ${KAFKA_DISTRIBUTION_ROOT}"
fi


# Export additional override flags to be sent to Kafka start command.

# If the container environment variables specify a statsd endpoint, provide Kafka with a statsd configuration.
if [ -n "${STATSD_UDP_HOST}" -a -n "${STATSD_UDP_PORT}" ]; then
    echo "Statsd endpoint found in container environment: ${STATSD_UDP_HOST}:${STATSD_UDP_PORT}..."
    export CONTAINER_HOOK_FLAGS=" \
--override kafka.metrics.reporters=com.airbnb.kafka.KafkaStatsdMetricsReporter \
--override external.kafka.statsd.reporter.enabled=true \
--override external.kafka.statsd.host=${STATSD_UDP_HOST} \
--override external.kafka.statsd.port=${STATSD_UDP_PORT} \
--override external.kafka.statsd.metrics.exclude_regex=\"\""
    echo "Additional exported Kafka flags:${CONTAINER_HOOK_FLAGS}"
else
    echo "No statsd endpoint found in container environment."
fi

echo "### HOOK END"
