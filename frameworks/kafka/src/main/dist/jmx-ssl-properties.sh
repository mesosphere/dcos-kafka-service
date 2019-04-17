#!/usr/bin/env bash

JMX_PROPERTIES_FILE=$MESOS_SANDBOX/.ssl/jmxremote.ssl.properties

cat <<EOF >> $JMX_PROPERTIES_FILE

javax.net.ssl.keyStore=$MESOS_SANDBOX/jmx/key_store
javax.net.ssl.keyStorePassword=$SECURE_JMX_KEY_STORE_PASSWORD_ENV
javax.net.ssl.trustStore=$TRUST_STORE_PATH
javax.net.ssl.trustStorePassword=$SECURE_JMX_TRUST_STORE_PASSWORD_ENV

EOF

chmod 600 $JMX_PROPERTIES_FILE
