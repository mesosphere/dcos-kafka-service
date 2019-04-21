#!/usr/bin/env bash

# This `TEMPLATE_KAFKA_VERSION` string is composed of:
# <scala-version>-<kafka-version>

# The available versions of Kafka (with the Scala versions used) can be found
# at:
# https://kafka.apache.org/downloads.

# The version here is looked for on the Mesosphere Amazon S3 downloads
# repository.
# To upload a new version of the Kafka package to that repository, follow the
# instructions at:
# https://wiki.mesosphere.com/display/ENGINEERING/Uploading+an+asset+to+production
export TEMPLATE_KAFKA_VERSION="2.12-2.2.0"
export TEMPLATE_DCOS_SDK_VERSION="0.56.0"