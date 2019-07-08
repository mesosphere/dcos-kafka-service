#!/usr/bin/env bash

set -e

netcat -z -n -v 127.0.0.1 ${KAFKA_BROKER_PORT} 2>&1 | grep -q 'open'
