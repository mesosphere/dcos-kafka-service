#!/usr/bin/env bash

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# Grab dcos-commons build/release tools:
rm -rf dcos-commons-tools/ && curl https://infinity-artifacts.s3.amazonaws.com/dcos-commons-tools.tgz | tar xz

# GitHub notifier config
_notify_github() {
    $REPO_ROOT_DIR/dcos-commons-tools/github_update.py $1 build $2
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

cd cli/ && ./build-cli.sh
if [ $? -ne 0 ]; then
  _notify_github failure "CLI build failed"
  exit 1
fi
cd $REPO_ROOT_DIR

_notify_github success "Build succeeded"

./dcos-commons-tools/publish_aws.py \
  kafka \
  universe/ \
  kafka-scheduler/build/distributions/*.zip \
  kafka-config-overrider/build/distributions/*zip \
  kafka-executor/build/distributions/*.zip \
  cli/dcos-kafka/dcos-kafka-darwin \
  cli/dcos-kafka/dcos-kafka-linux \
  cli/dcos-kafka/dcos-kafka.exe \
  cli/python/dist/*.whl
