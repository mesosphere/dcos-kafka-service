#!/usr/bin/env bash

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

export REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# GitHub notifier config
_notify_github() {
    $REPO_ROOT_DIR/tools/github_update.py $1 build $2
}

# Build steps for Kafka

_notify_github pending "Build running"

# Scheduler/Executor (Java):

./gradlew --refresh-dependencies distZip
if [ $? -ne 0 ]; then
  _notify_github failure "Gradle build failed"
  exit 1
fi

./gradlew check
if [ $? -ne 0 ]; then
  _notify_github failure "Unit tests failed"
  exit 1
fi

# CLI (Go):

./cli/build-cli.sh
if [ $? -ne 0 ]; then
  _notify_github failure "CLI build failed"
  exit 1
fi
cd $REPO_ROOT_DIR

_notify_github success "Build succeeded"

./tools/publish_aws.py \
  kafka \
  universe/ \
  build/distributions/kafka-scheduler.zip \
  cli/dcos-kafka/dcos-kafka-darwin \
  cli/dcos-kafka/dcos-kafka-linux \
  cli/dcos-kafka/dcos-kafka.exe \
  cli/python/dist/*.whl


