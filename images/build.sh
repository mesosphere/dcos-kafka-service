#!/usr/bin/env bash

set -eu

source docker-version.sh
docker image build --build-arg KAFKA_VERSION=${KAFKA_BASE_TECH_VERSION} -t mesosphere/kafka:${DOCKER_IMAGE_VERSION}-${KAFKA_BASE_TECH_VERSION} .

if [[ $1 == "push" ]]; then
    docker push mesosphere/kafka:${DOCKER_IMAGE_VERSION}-${KAFKA_BASE_TECH_VERSION}
fi
