#!/usr/bin/env bash

set -ex

# Grant permissions for the framework installation
grant_framework_permissions () {
    SERVICE_ACCOUNT_NAME=$1
    USER=$2

    FRAMEWORK_ROLE=$(echo $SERVICE_NAME-role | sed 's/\//__/g')

    dcos security org users grant $SERVICE_ACCOUNT_NAME dcos:mesos:master:framework:role:$FRAMEWORK_ROLE create
    dcos security org users grant $SERVICE_ACCOUNT_NAME dcos:mesos:master:task:user:$USER create
    dcos security org users grant $SERVICE_ACCOUNT_NAME dcos:mesos:agent:task:user:$USER create
    dcos security org users grant $SERVICE_ACCOUNT_NAME dcos:mesos:master:reservation:role:$FRAMEWORK_ROLE create
    dcos security org users grant $SERVICE_ACCOUNT_NAME dcos:mesos:master:reservation:principal:$SERVICE_NAME delete
    dcos security org users grant $SERVICE_ACCOUNT_NAME dcos:mesos:master:volume:role:$FRAMEWORK_ROLE create
    dcos security org users grant $SERVICE_ACCOUNT_NAME dcos:mesos:master:volume:principal:$SERVICE_NAME delete
}


if [ "${SECURITY_MODE}" == "strict" ]; then
	./tools/create_service_account.sh "${SERVICE_ACCOUNT_NAME}" "${SERVICE_ACCOUNT_SECRET_NAME}" --strict --force
	grant_framework_permissions  "${SERVICE_ACCOUNT_NAME}" nobody
fi
if [ "${SECURITY_MODE}" == "permissive" ]; then
	./tools/create_service_account.sh "${SERVICE_ACCOUNT_NAME}" "${SERVICE_ACCOUNT_SECRET_NAME}"  --force
	grant_framework_permissions "${SERVICE_ACCOUNT_NAME}" nobody
fi
if [ "${SECURITY_MODE}" == "disabled" ]; then
	echo "Skipping service account creation" >&2
fi
