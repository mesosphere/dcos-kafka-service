#!/bin/bash

# This script does a full build/upload of Kafka artifacts.
# This script is invoked by Jenkins CI, as well as by build-docker.sh.

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

if [ -z "$AWS_ACCESS_KEY_ID" -o -z "$AWS_SECRET_ACCESS_KEY" ]; then
    echo "Missing required AWS access info: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY"
    exit 1
fi

case ${PWD} in
    *\ * ) echo "PWD=$PWD has at least one space char. This will break pip. Please move to a directory without spaces :)" ;;
    * )  ;;
esac

# In theory, we could use Jenkins' "Multi SCM" script, but good luck with getting auto-build to work with that
# Instead, clone the secondary 'dcos-tests' repo manually.
if [ ! -d dcos-tests ]; then
    git clone --depth 1 git@github.com:mesosphere/dcos-tests.git
fi
echo Running with dcos-tests rev: $(git --git-dir=dcos-tests/.git rev-parse HEAD)

# GitHub notifier config
_notify_github() {
    # IF THIS FAILS FOR YOU, your dcos-tests is out of date!
    # do this: rm -rf kafka-private/dcos-tests/ then run build.sh again
    ./dcos-tests/build/update-github-status.py $1 $2 $3
}

# Build steps for Kafka-private

_notify_github pending build "Build running"

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

make --directory=cli/ all
if [ $? -ne 0 ]; then
  _notify_github error build "CLI build/tests failed"
  exit 1
fi

_notify_github success build "Build succeeded"

./dcos-tests/build/ci-upload.sh \
  kafka \
  universe/index.json \
  universe/package/ \
  kafka-scheduler/build/distributions/*.zip \
  kafka-config-overrider/build/distributions/*zip \
  cli/dist/dcos-kafka-*.tar.gz \
  kafka-executor/build/distributions/*.zip 
