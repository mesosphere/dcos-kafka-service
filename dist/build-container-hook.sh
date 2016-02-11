#!/bin/sh

# This script may be used to build a new Kafka container hook package. The container hook is run
# within DCOS containers immediately before Kafka is started. The container hook doesn't often
# change, but it may someday need to have different variants depending on the version of Kafka.
#
# The container hook package uses the following structure:
#   container-hook/container-hook.sh
#   container-hook/libs/kafka-statsd-metrics2-0.4.1.jar
#   container-hook/libs/java-dogstatsd-client-2.0.13.jar
#   container-hook/libs/... any other libraries to include in Kafka's classpath ...

CONTAINER_HOOK_VERSION="0.1.0"
CONTAINER_HOOK_BASE_DIR="container-hook"
CONTAINER_HOOK_PACKAGE_FILENAME="${CONTAINER_HOOK_BASE_DIR}-${CONTAINER_HOOK_VERSION}.tgz"
CONTAINER_HOOK_SCRIPT="container-hook.sh"

DOGSTATSD_CLIENT_VERSION="2.0.13"
DOGSTATSD_CLIENT_FILENAME="java-dogstatsd-client-${DOGSTATSD_CLIENT_VERSION}.jar"
DOGSTATSD_CLIENT_DOWNLOAD_URL="http://repo1.maven.org/maven2/com/indeed/java-dogstatsd-client/2.0.13/${DOGSTATSD_CLIENT_FILENAME}"

KAFKA_STATSD_VERSION="0.4.1"
KAFKA_STATSD_FILENAME="kafka-statsd-metrics2-${KAFKA_STATSD_VERSION}.jar"
KAFKA_STATSD_DOWNLOAD_URL="https://bintray.com/artifact/download/airbnb/jars/com/airbnb/kafka-statsd-metrics2/0.4.1/${KAFKA_STATSD_FILENAME}"

DIST_PATH="$(dirname $0)"
PACKAGE_PATH="${DIST_PATH}/build/package"
OUTPUT_PATH="${DIST_PATH}/build"
DOWNLOAD_CACHE_PATH="${PACKAGE_PATH}/download_cache"
STAGING_PATH="${PACKAGE_PATH}/${CONTAINER_HOOK_BASE_DIR}"

download_copy () {
    DOWNLOAD_FILENAME="$(basename $1)"
    DOWNLOAD_DEST="${DOWNLOAD_CACHE_PATH}/${DOWNLOAD_FILENAME}"
    if [ ! -f "${DOWNLOAD_DEST}" ]; then
        echo "Downloading to ${DOWNLOAD_DEST}"
        wget --progress=dot -e dotbytes=1M -O "${DOWNLOAD_DEST}" "$1" || exit 1
    fi
    if [ ! -f "$2/${DOWNLOAD_FILENAME}" ]; then
        echo "Copying ${DOWNLOAD_FILENAME} into $2"
        cp $DOWNLOAD_DEST $2 || exit 1
    fi
}

if [ ! -d "${DOWNLOAD_CACHE_PATH}" ]; then
    echo "Creating download cache dir: ${DOWNLOAD_CACHE_PATH}"
    mkdir -p "${DOWNLOAD_CACHE_PATH}" || exit 1
fi

KAFKA_LIBS_PATH=${STAGING_PATH}/libs
if [ ! -d "${KAFKA_LIBS_PATH}" ]; then
    echo "Creating download cache dir: ${KAFKA_LIBS_PATH}"
    mkdir -p "${KAFKA_LIBS_PATH}" || exit 1
fi

# download/copy supplemental libraries to libs/
download_copy ${DOGSTATSD_CLIENT_DOWNLOAD_URL} ${KAFKA_LIBS_PATH}
download_copy ${KAFKA_STATSD_DOWNLOAD_URL} ${KAFKA_LIBS_PATH}

# copy executor script to /
cp ${DIST_PATH}/${CONTAINER_HOOK_SCRIPT} ${STAGING_PATH} || exit 1

# build executor package of /*
PREV_DIR=$(pwd)
cd ${PACKAGE_PATH}
tar cf "${CONTAINER_HOOK_PACKAGE_FILENAME}" "${CONTAINER_HOOK_BASE_DIR}" || exit 1
cd ${PREV_DIR}
mkdir -p ${OUTPUT_PATH}
mv ${PACKAGE_PATH}/${CONTAINER_HOOK_PACKAGE_FILENAME} ${OUTPUT_PATH}

echo "---"
echo "Built Container Environment Hook package: ${OUTPUT_PATH}/${CONTAINER_HOOK_PACKAGE_FILENAME}"
tar tf "${OUTPUT_PATH}/${CONTAINER_HOOK_PACKAGE_FILENAME}"
echo "---"
