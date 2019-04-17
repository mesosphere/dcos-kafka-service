import pytest
import random
import string

import sdk_cmd
import sdk_install
import sdk_security
import sdk_utils
import subprocess
from tests import config


def install_jmx_configured_kafka():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
    test_run = random_string()
    create_keystore_cmd = ["keytool", "-genkey", "-alias", "self-signed-cert", "-keyalg",
                           "rsa", "-dname", "CN=myhost.example.com,O=Example Company,C=US",
                           "-keystore", "/tmp/{}-self-signed-keystore.ks".format(test_run), "-storepass", "123456",
                           "-keypass", "123456", "-storetype", "JKS"]

    subprocess.check_output(create_keystore_cmd)

    write_to_file("123456", "/tmp/{}-keystorepass".format(test_run))
    write_to_file("admin adminpassword", "/tmp/{}-passwordfile".format(test_run))
    write_to_file("admin readwrite", "/tmp/{}-access".format(test_run))

    sdk_security.install_enterprise_cli(False)

    sdk_cmd.run_cli(
        "security secrets create -f /tmp/{}-self-signed-keystore.ks /test/integration/kafka/keystore-test".format(
            test_run))
    sdk_cmd.run_cli(
        "security secrets create -f /tmp/{}-passwordfile /test/integration/kafka/passwordfile-test".format(test_run))
    sdk_cmd.run_cli("security secrets create -f /tmp/{}-access /test/integration/kafka/access-test".format(test_run))
    sdk_cmd.run_cli(
        "security secrets create -f /tmp/{}-keystorepass /test/integration/kafka/keystorepass-test".format(test_run))

    sdk_install.install(
        package_name=config.PACKAGE_NAME,
        service_name=foldered_name,
        additional_options={"service":
                            {"name": foldered_name, "jmx":
                                {"enabled": True, "port": 31299, "rmi_port": 31298,
                                    "password_file": "/test/integration/kafka/passwordfile-test",
                                    "access_file": "/test/integration/kafka/access-test",
                                    "key_store": "/test/integration/kafka/keystore-test",
                                    "key_store_password_file": "/test/integration/kafka/keystorepass-test"
                                 }
                             }, "brokers": {"cpus": 0.5, "count": 1}},
        expected_running_tasks=1
    )


def uninstall_jmx_secrets():
    sdk_security.delete_secret("/test/integration/kafka/keystore-test")
    sdk_security.delete_secret("/test/integration/kafka/passwordfile-test")
    sdk_security.delete_secret("/test/integration/kafka/access-test")
    sdk_security.delete_secret("/test/integration/kafka/keystorepass-test")


@pytest.mark.dcos_min_version("1.11")
def test_secure_jmx_configuration():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)

    try:
        install_jmx_configured_kafka()
        command = "ps aux | grep 'Dcom.sun.management.jmxremote.port=31299'"
        _, stdout, _ = sdk_cmd.service_task_exec(config.SERVICE_NAME, "test.integration.kafka__kafka-0-broker", command)
        assert "-Dcom.sun.management.jmxremote.registry.ssl=true" in stdout
        assert "-Dcom.sun.management.jmxremote.ssl=true" in stdout
        assert "-Dcom.sun.management.jmxremote=true" in stdout
        assert "-Dcom.sun.management.jmxremote.local.only=false" in stdout
        assert "-Dcom.sun.management.jmxremote.ssl.need.client.auth=true" in stdout

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
        uninstall_jmx_secrets()


# TODO add RMI connect based test once kafka version with SSL on RMI is fixed


def random_string(length=10):
    letters = string.ascii_lowercase
    return ''.join(random.choice(letters) for i in range(length))


def write_to_file(content, file_path):
    text_file = open(file_path, "w+")
    text_file.write(content)
    text_file.close()
