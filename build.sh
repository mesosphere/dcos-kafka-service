#!/bin/bash

# This script does a full build/upload of Kafka artifacts.
# This script is invoked by Jenkins CI, as well as by build-docker.sh.

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

if [ -z "$AWS_ACCESS_KEY_ID" -o -z "$AWS_SECRET_ACCESS_KEY" ]; then
    echo "Missing required AWS access info: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY"
    exit 1
fi

# In theory, we could use Jenkins' "Multi SCM" script, but good luck with getting auto-build to work with that
# Instead, clone the secondary 'dcos-commons' repo manually.
if [ -d dcos-commons ]; then
  cd dcos-commons/
  git pull origin master
  cd ..
else
  git clone --depth 1 --branch master git@github.com:mesosphere/dcos-commons.git
fi
echo Running with dcos-commons tools rev: $(git --git-dir=dcos-commons/.git rev-parse HEAD)

# GitHub notifier config
_notify_github() {
    $REPO_ROOT_DIR/dcos-commons/tools/github_update.py $1 build $2
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

./dcos-commons/tools/ci-upload.sh \
  kafka \
  universe/ \
  kafka-scheduler/build/distributions/*.zip \
  kafka-config-overrider/build/distributions/*zip \
  kafka-executor/build/distributions/*.zip \
  cli/dcos-kafka/dcos-kafka-darwin \
  cli/dcos-kafka/dcos-kafka-linux \
  cli/dcos-kafka/dcos-kafka.exe \
  cli/python/dist/*.whl
