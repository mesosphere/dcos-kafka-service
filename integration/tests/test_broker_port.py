import json
import os
import time

import dcos
import pytest
import shakedown
import tests.test_utils as test_utils

from tests.test_utils import (
    DEFAULT_PARTITION_COUNT,
    DEFAULT_REPLICATION_FACTOR,
    DEFAULT_BROKER_COUNT,
    DYNAMIC_PORT_OPTIONS_FILE,
    STATIC_PORT_OPTIONS_FILE,
    PACKAGE_NAME,
    check_health,
    get_broker_list,
    get_kafka_command,
    get_kafka_config,
    marathon_api_url,
    request,
    spin,
    uninstall,
)


WAIT_TIME_IN_SECONDS = 300


def get_connection_info():
    def fn():
        return shakedown.run_dcos_command('kafka connection')

    def success_predicate(result):
        deployments = dcos.http.get(marathon_api_url('deployments')).json()
        if deployments:
            return False, 'Deployment is ongoing'

        result, error = result
        try:
            result = json.loads(result)
        except Exception:
            return False, 'Command did not return JSON'
        else:
            return (
                not error and len(result['address']) == 3,
                'Command errored or expected number of brokers are not up',
            )

    return json.loads(test_utils.spin(fn, success_predicate)[0])


@pytest.yield_fixture
def dynamic_port_config():
    shakedown.install_package_and_wait(
        test_utils.PACKAGE_NAME, options_file=DYNAMIC_PORT_OPTIONS_FILE
    )
    yield
    test_utils.uninstall()


@pytest.fixture
def static_port_config():
    shakedown.install_package_and_wait(
        test_utils.PACKAGE_NAME, options_file=STATIC_PORT_OPTIONS_FILE
    )


def teardown_module(module):
    test_utils.uninstall()


@pytest.mark.sanity
def test_dynamic_port_comes_online(dynamic_port_config):
    check_health()


@pytest.mark.sanity
def test_static_port_comes_online(static_port_config):
    check_health()


@pytest.mark.sanity
def test_can_adjust_config_from_static_to_static_port():
    check_health()

    config = get_kafka_config()
    config['env']['BROKER_PORT'] = '9095'
    r = request(
        dcos.http.put,
        marathon_api_url('apps/kafka'),
        json=config
    )

    check_health()

    result = get_connection_info()
    assert len(result['address']) == 3

    for hostport in result['address']:
        assert hostport.split(':')[-1] == '9095'


@pytest.mark.sanity
def test_can_adjust_config_from_static_to_dynamic_port():
    check_health()

    config = get_kafka_config()
    config['env']['BROKER_PORT'] = '0'
    r = request(
        dcos.http.put,
        marathon_api_url('apps/kafka'),
        json=config
    )

    check_health()

    result = get_connection_info()
    assert len(result['address']) == 3

    for hostport in result['address']:
        assert 9092 != int(hostport.split(':')[-1])


@pytest.mark.sanity
def test_can_adjust_config_from_dynamic_to_dynamic_port():
    check_health()

    connections = get_connection_info()['address']
    config = get_kafka_config()
    brokerCpus = int(config['env']['BROKER_CPUS'])
    config['env']['BROKER_CPUS'] = str(brokerCpus + 0.1)
    r = request(
        dcos.http.put,
        marathon_api_url('apps/kafka'),
        json=config
    )

    check_health()


@pytest.mark.sanity
def test_can_adjust_config_from_dynamic_to_static_port():
    check_health()

    config = get_kafka_config()
    config['env']['BROKER_PORT'] = '9092'
    r = request(
        dcos.http.put,
        marathon_api_url('apps/kafka'),
        json=config
    )

    check_health()

    result = get_connection_info()
    assert len(result['address']) == 3

    for hostport in result['address']:
        assert hostport.split(':')[-1] == '9092'
