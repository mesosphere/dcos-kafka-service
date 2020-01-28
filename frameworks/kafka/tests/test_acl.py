import logging
import pytest

import sdk_cmd
import sdk_install

from tests import config
from tests import test_utils


LOG = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def kafka_server(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(config.PACKAGE_NAME, config.SERVICE_NAME, config.DEFAULT_BROKER_COUNT)

        yield {"package_name": config.PACKAGE_NAME, "service": {"name": config.SERVICE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope="module", autouse=True)
def kafka_topic(kafka_server: dict):
    test_utils.create_topic(config.EPHEMERAL_TOPIC_NAME, kafka_server["service"]["name"])
    return {"name": config.EPHEMERAL_TOPIC_NAME, "service_name": kafka_server["service"]["name"]}


@pytest.mark.smoke
@pytest.mark.sanity
def test_add_topic_acl(kafka_topic: dict):
    _, acl_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME,
        kafka_topic["service_name"],
        "acl add --principalAction allow --principal User:DCOS --operation All --topic {} --group '*' --hostAction allow --host 192.168.1.2".format(kafka_topic["name"]),
        parse_json=True,
    )

    assert "Output: Adding ACLs for resource `ResourcePattern(resourceType=TOPIC, name={}, patternType=LITERAL)".format(kafka_topic["name"]) in acl_info["message"]
    assert "User:DCOS has Allow permission for operations: All from hosts: 192.168.1.2" in acl_info["message"]


@pytest.mark.smoke
@pytest.mark.sanity
def test_list_topic_acl(kafka_topic: dict):
    _, acl_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME,
        kafka_topic["service_name"],
        "acl add --principalAction allow --principal User:DCOS --operation All --topic {} --group '*' --hostAction allow --host 192.168.1.2".format(kafka_topic["name"]),
        parse_json=True,
    )

    _, acl_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME,
        kafka_topic["service_name"],
        "acl list --topic {}".format(kafka_topic["name"]),
        parse_json=True,
    )

    assert "Output: Current ACLs for resource `Topic:LITERAL:{}`".format(kafka_topic["name"]) in acl_info["message"]
    assert "User:DCOS has Allow permission for operations: All from hosts: 192.168.1.2" in acl_info["message"]


@pytest.mark.smoke
@pytest.mark.sanity
def test_remove_topic_acl(kafka_topic: dict):
    _, acl_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME,
        kafka_topic["service_name"],
        "acl add --principalAction allow --principal User:DCOS --operation All --topic {} --group '*' --hostAction allow --host 192.168.1.2".format(kafka_topic["name"]),
        parse_json=True,
    )

    _, acl_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME,
        kafka_topic["service_name"],
        "acl remove --principalAction allow --principal User:DCOS --operation All --topic {} --group '*' --hostAction allow --host 192.168.1.2".format(kafka_topic["name"]),
        parse_json=True,
    )

    assert not acl_info["message"]

    _, acl_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME,
        kafka_topic["service_name"],
        "acl list --topic {}".format(kafka_topic["name"]),
        parse_json=True,
    )

    assert not acl_info["message"]
