#!/bin/sh

#
# This script is used to build an executor package with the following structure:
#
#   kafka-executor/kafka-executor.sh
#   kafka-executor/libs/kafka-statsd-metrics2-0.4.1.jar
#   kafka-executor/libs/java-dogstatsd-client-2.0.13.jar
#
# The Kafka distribution itself is provided separately from this package.
#

EXECUTOR_VERSION="0.1.0"
EXECUTOR_BASE_DIR="kafka-executor"
EXECUTOR_PACKAGE_FILENAME="${EXECUTOR_BASE_DIR}-${EXECUTOR_VERSION}.tgz"
EXECUTOR_SCRIPT="kafka-executor.sh"

DOGSTATSD_CLIENT_VERSION="2.0.13"
DOGSTATSD_CLIENT_FILENAME="java-dogstatsd-client-${DOGSTATSD_CLIENT_VERSION}.jar"
DOGSTATSD_CLIENT_DOWNLOAD_URL="http://repo1.maven.org/maven2/com/indeed/java-dogstatsd-client/2.0.13/${DOGSTATSD_CLIENT_FILENAME}"

KAFKA_STATSD_VERSION="0.4.1"
KAFKA_STATSD_FILENAME="kafka-statsd-metrics2-${KAFKA_STATSD_VERSION}.jar"
KAFKA_STATSD_DOWNLOAD_URL="https://bintray.com/artifact/download/airbnb/jars/com/airbnb/kafka-statsd-metrics2/0.4.1/${KAFKA_STATSD_FILENAME}"

PROJ_ROOT_PATH="$(dirname $0)"
PACKAGE_PATH="${PROJ_ROOT_PATH}/package"
DOWNLOAD_CACHE_PATH="${PACKAGE_PATH}/download_cache"
STAGING_PATH="${PACKAGE_PATH}/${EXECUTOR_BASE_DIR}"

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
cp ${PROJ_ROOT_PATH}/${EXECUTOR_SCRIPT} ${STAGING_PATH} || exit 1

# build executor package of /*
cd ${PACKAGE_PATH}
tar cf "${EXECUTOR_PACKAGE_FILENAME}" "${EXECUTOR_BASE_DIR}" || exit 1

echo "---"
echo "Built Executor package: ${EXECUTOR_PACKAGE_FILENAME}"
tar tf "${EXECUTOR_PACKAGE_FILENAME}"
echo "---"
