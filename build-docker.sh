#!/bin/sh

# This script runs './build.sh' in the same image as used by CI.

DKR_IMAGE=mesosphere/dcos-kafka-service

if [ -z "$AWS_ACCESS_KEY_ID" -o -z "$AWS_SECRET_ACCESS_KEY" ]; then
    echo "Missing required AWS access info: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY"
    exit 1
fi

# always rebuild the docker image. otherwise, recent changes to kafka-private/ aren't picked up.
docker rmi -f $DKR_IMAGE
# create temp Dockerfile with configured AWS creds configured
cat >"Dockerfile" <<EOF
FROM mesosphere/ci-image:infinity
ADD . kafka-private/
WORKDIR kafka-private/

ENV AWS_ACCESS_KEY_ID $AWS_ACCESS_KEY_ID
ENV AWS_SECRET_ACCESS_KEY $AWS_SECRET_ACCESS_KEY

ENTRYPOINT []
CMD []
EOF
docker build -t $DKR_IMAGE .
rm -v Dockerfile

# docker run $DKR_IMAGE pwd && ls -l && env | sort
docker run $DKR_IMAGE ./build.sh
