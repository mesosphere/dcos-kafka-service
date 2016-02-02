#!/bin/sh

DIST_PATH="$(dirname $0)"

# AWS settings:
AWS_REGION="us-west-2"
S3_BUCKET="infinity-artifacts"
DATETIME=$(date +"%Y%m%d-%H%M%S")
UUID=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 16 | head -n 1)
S3_PATH="autodelete7d/kafka/${DATETIME}-${UUID}"
S3_PATH_URL="https://s3-${AWS_REGION}.amazonaws.com/${S3_BUCKET}/${S3_PATH}"

# Generate container hook and stub universe

echo "Using S3 path: ${S3_PATH_URL}"

sh ${DIST_PATH}/build-container-hook.sh
sh ${DIST_PATH}/build-stub-universe.sh ${S3_PATH_URL}

# Upload jar, container hook package, and stub universe

JAR_PATH=$(ls ${DIST_PATH}/../kafka-scheduler/build/libs/kafka-scheduler-*-uber.jar)
if [ "${JAR_PATH}" = "" ]; then
    echo "Scheduler jar not found. Contents of kafka-scheduler/build/:"
    find ${DIST_PATH}/../kafka-scheduler/build
    exit 1
fi

upload_to_aws() {
CMD="aws s3 \
--region=${AWS_REGION} \
cp \
--acl public-read \
$1 \
s3://${S3_BUCKET}/${S3_PATH}/$(basename $1)"
echo "AWS: ${CMD}"
${CMD}
if [ $? -ne 0 ]; then
    echo "Failed to upload $1. AWS settings and contents of project dir:"
    aws configure
    find ${DIST_PATH}/..
    exit 1
fi
}

upload_to_aws ${JAR_PATH}
upload_to_aws $(ls ${DIST_PATH}/build/container-hook-*.tgz)
upload_to_aws ${DIST_PATH}/build/stub-universe.zip

# Output the Universe URL to a file so that it can be used as a build artifact.
UNIVERSE_ZIP_URL="${S3_PATH_URL}/stub-universe.zip"
echo ${UNIVERSE_ZIP_URL} > ${DIST_PATH}/build/universe-url.txt
echo "Stub Universe for this build: ${UNIVERSE_ZIP_URL}"
