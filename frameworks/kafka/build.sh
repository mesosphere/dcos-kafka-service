#!/usr/bin/env bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT_DIR=$(dirname $(dirname $FRAMEWORK_DIR))

# grab TEMPLATE_x vars for use in universe template:
source $FRAMEWORK_DIR/versions.sh

# Build/test scheduler.zip/CLIs/setup-helper.zip
${REPO_ROOT_DIR}/gradlew -p ${FRAMEWORK_DIR} check distZip
$FRAMEWORK_DIR/cli/build.sh
$FRAMEWORK_DIR/setup-helper/build.sh

# Build package with our scheduler.zip/CLIs/setup-helper.zip and the SDK artifacts we built:
$REPO_ROOT_DIR/tools/build_package.sh \
    kafka \
    $FRAMEWORK_DIR \
    -a "$FRAMEWORK_DIR/build/distributions/$(basename $FRAMEWORK_DIR)-scheduler.zip" \
    -a "$FRAMEWORK_DIR/cli/dcos-service-cli-linux" \
    -a "$FRAMEWORK_DIR/cli/dcos-service-cli-darwin" \
    -a "$FRAMEWORK_DIR/cli/dcos-service-cli.exe" \
    -a "$FRAMEWORK_DIR/setup-helper/setup-helper.zip" \
    $@
