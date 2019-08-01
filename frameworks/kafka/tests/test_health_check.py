import logging
import re
import pytest

import sdk_auth
import sdk_install
import sdk_utils

from tests import auth
from tests import config
from security import transport_encryption

from tests.health_check import check_health_check_logs

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        name = config.SERVICE_NAME
        service_account_info = transport_encryption.setup_service_account(name)

        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(config.SERVICE_NAME, service_account_info)


@pytest.fixture(scope="module", autouse=True)
def kerberos(configure_security):
    try:
        kerberos_env = sdk_auth.KerberosEnvironment()

        principals = auth.get_service_principals(config.SERVICE_NAME, kerberos_env.get_realm(), health_check=True)
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope="module", params=["PORT", "FUNCTIONAL"])
def health_check_method(request):
    return request.param


@pytest.fixture(scope="module", params=["DEFAULT", "SASL_PLAINTEXT", "SASL_SSL", "SSL"])
def kafka_server_with_health_check(request, health_check_method, service_account, kerberos):
    """
    A pytest fixture that installs a kafka service.

    On teardown, the service is uninstalled.
    """
    service_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "health_check": {
                "enabled": True,
                "method": health_check_method,
                "interval": 60,
                "delay": 20,
                "timeout": 60,
                "grace-period": 30,
                "max-consecutive-failures": 3,
                "health-check-topic-prefix": "KafkaHealthCheck"
            }
        }
    }

    if re.search("SASL", request.param):
        service_options = sdk_utils.merge_dictionaries({
            "service": {
                "security": {
                    "kerberos": {
                        "enabled": True,
                        "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                        "realm": kerberos.get_realm(),
                        "keytab_secret": kerberos.get_keytab_path(),
                    }
                }
            }
        }, service_options)

    if re.search("SSL", request.param):
        service_options = sdk_utils.merge_dictionaries({
            "service": {
                "service_account": service_account["name"],
                "service_account_secret": service_account["secret"],
                "security": {
                    "transport_encryption": {"enabled": True}
                }
            }
        }, service_options)

    if re.search("^SSL$", request.param):
        service_options = sdk_utils.merge_dictionaries({
            "service": {
                "security": {
                    "ssl_authentication": {"enabled": True}
                }
            }
        }, service_options)

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
        )
        yield {**{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_health_check(kafka_server_with_health_check):
    check_health_check_logs()
