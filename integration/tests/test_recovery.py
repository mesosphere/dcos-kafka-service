import json
import pytest

import dcos
import shakedown

from tests.test_utils import (
    DEFAULT_BROKER_COUNT,
    STATIC_PORT_OPTIONS_DICT,
    PACKAGE_NAME,
    check_health,
    get_dcos_command,
    get_kafka_config,
    install,
    kafka_api_url,
    marathon_api_url,
    request,
    spin,
    uninstall,
)


def get_broker_host():
    return shakedown.get_service_ips(PACKAGE_NAME).pop()


def get_scheduler_host():
    return shakedown.get_service_ips('marathon').pop()


def increment_broker_port_config():
    config = get_kafka_config()
    config['env']['BROKER_PORT'] = str(int(config['env']['BROKER_PORT']) + 1)
    r = request(
        dcos.http.put,
        marathon_api_url('apps/kafka'),
        json=config,
    )


def get_and_verify_plan(predicate=lambda r: True):
    def fn():
        return dcos.http.get(kafka_api_url('plan'))

    def success_predicate(result):
        message = 'Request to /plan failed'

        try:
            body = result.json()
        except:
            return False, message

        return predicate(body), message

    return spin(fn, success_predicate).json()


def kill_task_with_pattern(pattern, host=None):
    command = (
        "sudo kill -9 "
        "$(ps ax | grep {} | grep -v grep | tr -s ' ' | sed 's/^ *//g' | "
        "cut -d ' ' -f 1)".format(pattern)
    )
    if host is None:
        result = shakedown.run_command_on_master(command)
    else:
        result = shakedown.run_command_on_agent(host, command)

    if not result:
        raise RuntimeError(
            'Failed to kill task with pattern "{}"'.format(pattern)
        )


def wait_for_deployment_lock_release():
    def fn():
        return dcos.http.get(marathon_api_url('deployments'))

    def pred(result):
        try:
            return (
                result.status_code == 200 and result.json() == [],
                'Deployment was not unlocked'
            )
        except json.decoder.JSONDecodeError as e:
            return False, 'Deployment was not unlocked'

    return spin(fn, pred)


def run_planned_operation(operation, failure=lambda: None):
    wait_for_deployment_lock_release()
    plan = get_and_verify_plan()

    operation()
    next_plan = get_and_verify_plan(
        lambda p: (
            plan['phases'][1]['id'] != p['phases'][1]['id'] or
            len(plan['phases']) < len(p['phases']) or
            p['status'] == 'IN_PROGRESS'
        )
    )

    failure()
    completed_plan = get_and_verify_plan(lambda p: p['status'] == 'COMPLETE')


def setup_module(module):
    uninstall()
    install(STATIC_PORT_OPTIONS_DICT)
    check_health()


def teardown_module(module):
    uninstall()


@pytest.mark.recovery
def test_service_becomes_healthy_after_broker_fails():
    kill_task_with_pattern('kafka.Kafka', get_broker_host())

    check_health()


@pytest.mark.recovery
def test_service_becomes_healthy_after_all_brokers_fail():
    for host in shakedown.get_service_ips(PACKAGE_NAME):
        kill_task_with_pattern('kafka.Kafka', host)

    check_health()


@pytest.mark.recovery
def test_service_becomes_healthy_after_scheduler_fails():
    kill_task_with_pattern('kafka.scheduler.Main', get_scheduler_host())

    check_health()


@pytest.mark.recovery
def test_service_becomes_healthy_after_master_fails():
    kill_task_with_pattern('mesos-master')

    check_health()


@pytest.mark.recovery
def test_service_becomes_healthy_after_zk_fails():
    kill_task_with_pattern('zookeeper')

    check_health()


@pytest.mark.recovery
def test_service_becomes_healthy_after_agent_is_partitioned():
    host = get_broker_host()

    spin(shakedown.partition_agent, lambda x: (True, ''), host)
    shakedown.reconnect_agent(host)

    check_health()


@pytest.mark.recovery
def test_service_becomes_healthy_after_all_agents_are_partitioned():
    hosts = shakedown.get_service_ips(PACKAGE_NAME)

    for host in hosts:
        spin(shakedown.partition_agent, lambda x: (True, ''), host)
    for host in hosts:
        shakedown.reconnect_agent(host)

    check_health()


@pytest.mark.recovery
def test_config_update_eventually_succeeds_after_broker_fails():
    host = get_broker_host()
    run_planned_operation(
        increment_broker_port_config,
        lambda: kill_task_with_pattern('kafka.Kafka', host)
    )

    check_health()


@pytest.mark.recovery
def test_config_update_eventually_succeeds_after_all_brokers_fail():
    hosts = shakedown.get_service_ips(PACKAGE_NAME)
    run_planned_operation(
        increment_broker_port_config,
        lambda: [kill_task_with_pattern('kafka.Kafka', h) for h in hosts]
    )

    check_health()


@pytest.mark.recovery
def test_config_update_eventually_succeeds_after_scheduler_fails():
    host = get_scheduler_host()
    run_planned_operation(
        increment_broker_port_config,
        lambda: kill_task_with_pattern('kafka.scheduler.Main', host)
    )

    check_health()


@pytest.mark.recovery
def test_config_update_eventually_succeeds_after_master_fails():
    run_planned_operation(
        increment_broker_port_config,
        lambda: kill_task_with_pattern('mesos-master')
    )

    check_health()


@pytest.mark.recovery
def test_config_update_eventually_succeeds_after_zk_fails():
    run_planned_operation(
        increment_broker_port_config,
        lambda: kill_task_with_pattern('zookeeper')
    )

    check_health()


@pytest.mark.recovery
def test_config_update_eventually_succeeds_after_agent_is_partitioned():
    host = get_broker_host()

    def partition():
        spin(shakedown.partition_agent, lambda x: (True, ''), host)
        shakedown.reconnect_agent(host)

    run_planned_operation(increment_broker_port_config, partition)

    check_health()


@pytest.mark.recovery
def test_config_update_eventually_succeeds_after_all_agents_are_partitioned():
    hosts = shakedown.get_service_ips(PACKAGE_NAME)

    def partition():
        for host in hosts:
            spin(shakedown.partition_agent, lambda x: (True, ''), host)
        for host in hosts:
            shakedown.reconnect_agent(host)

    run_planned_operation(increment_broker_port_config, partition)

    check_health()
