#!/usr/bin/env bash

# Exit immediately on errors -- the helper scripts all emit github statuses internally
set -e

echo "Beginning integration tests at "`date`

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR


# Get a CCM cluster if not already configured (see available settings in dcos-commons/tools/README.md):
if [ -z "$CLUSTER_URL" ]; then
    echo "CLUSTER_URL is empty/unset, launching new cluster."
    export CCM_AGENTS=5
    CLUSTER_INFO=$(${REPO_ROOT_DIR}/tools/launch_ccm_cluster.py)
    echo "Launched cluster: ${CLUSTER_INFO}"
    # jq emits json strings by default: "value".  Use --raw-output to get value without quotes
    export CLUSTER_URL=$(echo "${CLUSTER_INFO}" | jq --raw-output .url)
    export CLUSTER_ID=$(echo "${CLUSTER_INFO}" | jq .id)
    export CLUSTER_AUTH_TOKEN=$(echo "${CLUSTER_INFO}" | jq --raw-output .auth_token)
    CLUSTER_CREATED="true"
else
    echo "Using provided CLUSTER_URL as cluster: $CLUSTER_URL"
    CLUSTER_CREATED=""
fi


# Build/upload framework scheduler artifact if one is not directly provided:
if [ -z "${!STUB_UNIVERSE_URL}" ]; then
    STUB_UNIVERSE_URL=$(echo "${framework}_STUB_UNIVERSE_URL" | awk '{print toupper($0)}')
    # Build/upload framework scheduler:
    UNIVERSE_URL_PATH=kafka-universe-url
    UNIVERSE_URL_PATH=$UNIVERSE_URL_PATH ./build.sh 

    if [ ! -f "$UNIVERSE_URL_PATH" ]; then
        echo "Missing universe URL file: $UNIVERSE_URL_PATH"
        exit 1
    fi
    export STUB_UNIVERSE_URL=$(cat $UNIVERSE_URL_PATH)
    rm  $UNIVERSE_URL_PATH
    echo "Built/uploaded stub universe: $STUB_UNIVERSE_URL"
else
    echo "Using provided STUB_UNIVERSE_URL: $STUB_UNIVERSE_URL"
fi



echo Security: $SECURITY
if [ "$SECURITY" = "strict" ]; then
#    ${REPO_ROOT_DIR}/tools/setup_permissions.sh nobody kafka-role
    ${REPO_ROOT_DIR}/tools/setup_permissions.sh root kafka-role
fi


# Run shakedown tests:
${REPO_ROOT_DIR}/tools/run_tests.py shakedown ${REPO_ROOT_DIR}/integration/tests/

# Tests succeeded. Out of courtesy, trigger a teardown of the cluster if we created it ourselves.
# Don't wait for the cluster to complete teardown.
if [ -n "${CLUSTER_CREATED}" ]; then
    ./tools/launch_ccm_cluster.py trigger-stop ${CLUSTER_ID}
fi
