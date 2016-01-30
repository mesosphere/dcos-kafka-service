#!/bin/sh

#
# This script is used to build a stub universe for Kafka, which may be used to install unreleased code via dcos-cli.
#
# When running this script, the user must provide a base path for the location of the build artifacts.
# For example, if the packages were stored in https://aws-hello-2.com/path/to/kafka, then that should be the arg.
#

PROJ_ROOT_PATH="$(dirname $0)"
INPUT_DIR="${PROJ_ROOT_PATH}/stub-universe"
OUTPUT_DIR="${PROJ_ROOT_PATH}/build"
OUTPUT_DIR_CONTENT="${OUTPUT_DIR}/stub-universe"
OUTPUT_RESOURCE_JSON="${OUTPUT_DIR_CONTENT}/repo/packages/K/kafka/2/resource.json"
RESOURCE_JSON_SEARCH="{{artifact-dir}}"
OUTPUT_ZIP="stub-universe.zip"

# Validate path protocol
case "$1" in
    "")
        echo "Syntax: $0 <artifact base path>"
        echo "  Artifact base path contains container-hook-x.y.z.tgz and kafka-scheduler-x.y.z-uber.jar"
        echo "  Example (HTTP): $ $0 http://example.com/path/to/artifacts/"
        echo "  Example (files): $ $0 file:///path/to/artifacts/"
        exit 1
        ;;
    http://*)
        ;; # OK!
    https://*)
        ;; # OK!
    file://*)
        ;; # OK!
    *)
        echo "Unsupported protocol (expects http://, https://, or file://): $1"
        exit 1
        ;;
esac

# Normalize path: Remove / if present
case "$1" in
    */)
        ARTIFACT_PATH="${1%?}"
        ;;
    *)
        ARTIFACT_PATH="$1"
        ;;
esac

# Initialize output dir, remove any preexisting artifacts
mkdir -p $(dirname ${OUTPUT_DIR})
if [ $? -ne 0 ]; then
   echo "output mkdir failed, exiting"
   exit 1
fi

rm -rf ${OUTPUT_DIR_CONTENT}
if [ $? -ne 0 ]; then
   echo "output cleanup rm failed, exiting"
   exit 1
fi

cp -av ${INPUT_DIR} ${OUTPUT_DIR}
if [ $? -ne 0 ]; then
   echo "input->output copy failed, exiting"
   exit 1
fi

# Update urls in resource.json. Use commas as delimiter in sed
echo "Inserting base path '${ARTIFACT_PATH}' into ${OUTPUT_RESOURCE_JSON}"
sed -i "s,${RESOURCE_JSON_SEARCH},${ARTIFACT_PATH},g" ${OUTPUT_RESOURCE_JSON}
if [ $? -ne 0 ]; then
   echo "sed failed, exiting"
   exit 1
fi
echo "Edited version:"
cat ${OUTPUT_RESOURCE_JSON}

# Create artifact
OLD_PWD=pwd
cd ${OUTPUT_DIR}

rm -f ${OUTPUT_ZIP}
if [ $? -ne 0 ]; then
    echo "zip cleanup rm failed, exiting"
    exit 1
fi

zip -r ${OUTPUT_ZIP} $(basename ${OUTPUT_DIR_CONTENT})
if [ $? -ne 0 ]; then
    echo "zip ${OUTPUT_DIR_CONTENT} -> ${OUTPUT_ZIP} failed, exiting"
    exit 1
fi

cd ${OLD_PWD}

echo "---"
echo "Built Stub Universe package: ${OUTPUT_ZIP}"
echo "---"
