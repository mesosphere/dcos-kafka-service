#!/usr/bin/env bash


if [ "${SECURITY_MODE}" = "disabled" ]; then
	cat > "${PACKAGE_OPTIONS}" <<EOF
{
  "service": {
    "name": "${SERVICE_NAME}"
  }
}
EOF
	exit 0
fi


cat > "${PACKAGE_OPTIONS}" <<EOF
{
  "service": {
    "name": "${SERVICE_NAME}",
    "service_account": "${SERVICE_ACCOUNT_NAME}",
    "service_account_secret": "${SERVICE_ACCOUNT_SECRET_NAME}"
  }
}
EOF

