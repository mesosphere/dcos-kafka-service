import os
import pytest

import dcos
import shakedown
import tests.test_utils as test_utils


def setup_module(module):
    test_utils.uninstall()


def teardown_module(module):
    test_utils.uninstall()


@pytest.fixture
def static_port_config():
    test_utils.install(test_utils.STATIC_PORT_OPTIONS_DICT)


@pytest.mark.sanity
@pytest.mark.timeout(600)
def test_failing_health_check(static_port_config):
    broker_id = '0'
    broker_name = 'broker-' + broker_id

    def found_broker(result):
        return result != None, 'Broker not found.'

    def broker_killed_result_checker(result):
        return result, 'Broker not killed.'

    print('Waiting for last Running Broker.')
    test_utils.spin(get_running_broker_task_id, found_broker, 'broker-2')

    # Get broker-0's task ID so we can know when it kills itself after failing
    # the health check.
    task_id = get_running_broker_task_id(broker_name)
    print("{}'s task_id is {}".format(broker_name, task_id))

    # Delete the ZK node which should trigger the health check to kill broker-0
    shakedown.run_command_on_master(
        'wget https://github.com/outbrain/zookeepercli/releases/'
        'download/v1.0.10/zookeepercli'
    )
    shakedown.run_command_on_master('sudo chmod +x zookeepercli')
    shakedown.run_command_on_master(
        './zookeepercli --servers 127.0.0.1 -c delete '
        '/dcos-service-kafka/brokers/ids/' + broker_id
    )

    print('Waiting for Broker to fail.')
    test_utils.spin(broker_killed, broker_killed_result_checker, task_id)

    print('Waiting for Running Broker.')
    test_utils.spin(get_running_broker_task_id, found_broker, broker_name)


def get_running_broker_task_id(broker_name):
    tasks_url = test_utils.DCOS_URL + '/mesos/tasks'
    tasks_json = dcos.http.get(tasks_url).json()

    for task in tasks_json['tasks']:
        task_name = task['name']
        task_state = task['state']

        if broker_name == task_name and task_state == 'TASK_RUNNING':
            print('Broker with name ' + broker_name + ' is running')
            return task['id']

    return None


def broker_killed(task_id):
    tasks_url = test_utils.DCOS_URL + '/mesos/tasks'
    tasks_json = dcos.http.get(tasks_url).json()

    for task in tasks_json['tasks']:
        curr_task_id = task['id']
        task_state = task['state']

        if curr_task_id == task_id and task_state == 'TASK_KILLED':
            print('Broker with id ' + task_id + ' failed');
            return True

    return False
