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
# Instead, clone the secondary 'dcos-tests' repo manually.
if [ -d dcos-tests ]; then
  cd dcos-tests/
  git fetch --depth 1 origin bincli
  git checkout bincli
  cd ..
else
  git clone --depth 1 --branch bincli git@github.com:mesosphere/dcos-tests.git
fi
echo Running with dcos-tests rev: $(git --git-dir=dcos-tests/.git rev-parse HEAD)

# GitHub notifier config
_notify_github() {
    # IF THIS FAILS FOR YOU, your dcos-tests is out of date!
    # do this: rm -rf dcos-kafka-service/dcos-tests/ then run build.sh again
    $REPO_ROOT_DIR/dcos-tests/build/github_update.py $1 $2 $3
}

# Build steps for Kafka

_notify_github pending build "Build running"

# Scheduler/Executor (Java):

./gradlew --refresh-dependencies distZip
if [ $? -ne 0 ]; then
  _notify_github failure build "Gradle build failed"
  exit 1
fi

./gradlew check
if [ $? -ne 0 ]; then
  _notify_github failure build "Unit tests failed"
  exit 1
fi

# CLI (Go):

cd cli && ./build-cli.sh
if [ $? -ne 0 ]; then
    _notify_github failure build "CLI build failed"
    exit 1
fi
cd $REPO_ROOT_DIR

_notify_github success build "Build succeeded"

./dcos-tests/build/ci-upload.sh \
  kafka \
  universe/ \
  kafka-scheduler/build/distributions/*.zip \
  kafka-config-overrider/build/distributions/*zip \
  kafka-executor/build/distributions/*.zip \
  cli/dcos-kafka/dcos-kafka-darwin \
  cli/dcos-kafka/dcos-kafka-linux \
  cli/dcos-kafka/dcos-kafka.exe \
  cli/python/dist/*.whl
