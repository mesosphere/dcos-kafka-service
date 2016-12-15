import json
import os
import time

import dcos
import pytest
import shakedown

from tests.test_utils import (
    DYNAMIC_PORT_OPTIONS_DICT,
    STATIC_PORT_OPTIONS_DICT,
    check_health,
    get_kafka_config,
    install,
    marathon_api_url,
    spin,
    uninstall,
    update_kafka_config
)


def get_connection_info():
    def fn():
        return shakedown.run_dcos_command('kafka connection')

    def success_predicate(result):
        deployments = dcos.http.get(marathon_api_url('deployments')).json()
        if deployments:
            return False, 'Deployment is ongoing'

        stdout, stderr, error = result
        try:
            stdout = json.loads(stdout)
        except Exception as e:
            return False, 'Command did not return JSON: {}'.format(e)
        else:
            return (
                not error and len(stdout['address']) == 3,
                'Command errored or expected number of brokers are not up',
            )

    return json.loads(spin(fn, success_predicate)[0])


def setup_module(module):
    uninstall()


def teardown_module(module):
    uninstall()


@pytest.yield_fixture
def dynamic_port_config():
    install(DYNAMIC_PORT_OPTIONS_DICT)
    yield
    uninstall()


@pytest.fixture
def static_port_config():
    install(STATIC_PORT_OPTIONS_DICT)


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
    update_kafka_config(config)

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
    update_kafka_config(config)

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
    update_kafka_config(config)

    check_health()


@pytest.mark.sanity
def test_can_adjust_config_from_dynamic_to_static_port():
    check_health()

    config = get_kafka_config()
    config['env']['BROKER_PORT'] = '9092'
    update_kafka_config(config)

    check_health()

    result = get_connection_info()
    assert len(result['address']) == 3

    for hostport in result['address']:
        assert hostport.split(':')[-1] == '9092'
