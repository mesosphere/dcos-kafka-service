import logging
import pytest

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_security
import sdk_utils

from tests import config
from tests import test_utils
from tests import client


LOG = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def zookeeper_server(configure_security):
    service_options = {
        "service": {"name": config.ZOOKEEPER_SERVICE_NAME, "virtual_network_enabled": True}
    }
    ZOOKEEPER_TASK_COUNT = 2
    zk_account = "test-zookeeper-service-account"
    zk_secret = "test-zookeeper-secret"

    try:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        if sdk_utils.is_strict_mode():
            custom_options = sdk_utils.merge_dictionaries(
                {"service": {"service_account": zk_account, "service_account_secret": zk_secret}},
                service_options,
                {"node": {"count": 1}},
            )

            sdk_security.setup_security(
                config.ZOOKEEPER_SERVICE_NAME,
                service_account=zk_account,
                service_account_secret=zk_secret,
            )

        sdk_install.install(
            config.ZOOKEEPER_PACKAGE_NAME,
            config.ZOOKEEPER_SERVICE_NAME,
            ZOOKEEPER_TASK_COUNT,
            package_version=config.ZOOKEEPER_PACKAGE_VERSION,
            additional_options=custom_options,
            timeout_seconds=30 * 60,
            insert_strict_options=False,
        )

        yield {**service_options, **{"package_name": config.ZOOKEEPER_PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        if sdk_utils.is_strict_mode():
            sdk_security.delete_service_account(
                service_account_name=zk_account, service_account_secret=zk_secret
            )


@pytest.fixture(scope="module", autouse=True)
def kafka_client():
    try:
        kafka_client = client.KafkaClient("kafka-client", config.PACKAGE_NAME, config.SERVICE_NAME)
        kafka_client.install()

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.fixture(scope="module", autouse=True)
def kafka_server(zookeeper_server):
    try:

        # Get the zookeeper DNS values
        zookeeper_dns = sdk_cmd.svc_cli(
            zookeeper_server["package_name"],
            zookeeper_server["service"]["name"],
            "endpoint clientport",
            parse_json=True,
        )[1]["dns"]

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options={"kafka": {"kafka_zookeeper_uri": ",".join(zookeeper_dns)}},
        )

        # wait for brokers to finish registering before starting tests
        test_utils.broker_count_check(config.DEFAULT_BROKER_COUNT, service_name=config.SERVICE_NAME)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def topic_create(kafka_server: dict):
    return test_utils.create_topic(config.EPHEMERAL_TOPIC_NAME, kafka_server["service"]["name"])


def fetch_topic(kafka_server: dict):
    _, topic_list, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, kafka_server["service"]["name"], "topic list", parse_json=True
    )
    return topic_list


def restart_zookeeper_node(id: int):
    sdk_cmd.svc_cli(
        config.ZOOKEEPER_PACKAGE_NAME,
        config.ZOOKEEPER_SERVICE_NAME,
        "pod restart zookeeper-{}".format(id),
    )
    sdk_plan.wait_for_kicked_off_recovery(config.ZOOKEEPER_SERVICE_NAME)
    sdk_plan.wait_for_completed_recovery(config.ZOOKEEPER_SERVICE_NAME)

@pytest.mark.sanity
@pytest.mark.zookeeper
def test_check_topic_list_on_zk_restart(kafka_server):
    ID = 0
    topic_create(kafka_server)
    topic_list_before = fetch_topic(kafka_server)
    restart_zookeeper_node(ID)
    topic_list_after = fetch_topic(kafka_server)
    assert topic_list_before == topic_list_after
