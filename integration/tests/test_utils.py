import json
import os
import time
import sys
from functools import wraps

import dcos
import shakedown


PACKAGE_NAME = 'kafka'
WAIT_TIME_IN_SECONDS = 300

DCOS_URL = shakedown.run_dcos_command('config show core.dcos_url')[0].strip()

# expected SECURITY values: 'permissive', 'strict', 'disabled'
if os.environ.get('SECURITY', '') == 'strict':
    print('Using strict mode test configuration')
    PRINCIPAL = 'service-acct'
    DEFAULT_OPTIONS_DICT = {
        "service": {
            "user": "nobody",
            "principal": 'service-acct',
            "secret_name": "secret"
        }
    }
else:
    print('Using default test configuration')
    PRINCIPAL = 'kafka-principal'
    DEFAULT_OPTIONS_DICT = {}

STATIC_PORT_OPTIONS_DICT = { "brokers": { "port": 9092 } }
DYNAMIC_PORT_OPTIONS_DICT = { "brokers": { "port": 0 } }

DEFAULT_PARTITION_COUNT = 1
DEFAULT_REPLICATION_FACTOR = 1
DEFAULT_BROKER_COUNT = 3

TASK_RUNNING_STATE = 'TASK_RUNNING'


def as_json(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            return json.loads(fn(*args, **kwargs))
        except ValueError as e:
            print("ValueError: {}".format(e))
            return None

    return wrapper


def check_health():
    def fn():
        try:
            return shakedown.get_service_tasks(PACKAGE_NAME)
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(tasks):
        running_tasks = [t for t in tasks if t['state'] == TASK_RUNNING_STATE]
        print('Waiting for {} healthy tasks, got {}/{}'.format(
            DEFAULT_BROKER_COUNT, len(running_tasks), len(tasks)))
        return (
            len(running_tasks) == DEFAULT_BROKER_COUNT,
            'Service did not become healthy'
        )

    return spin(fn, success_predicate)


def get_broker_list():
    def fn():
        return get_kafka_command('broker list')

    def success_predicate(result):
        return (
            len(result) == DEFAULT_BROKER_COUNT, 'Not all brokers were revived'
        )

    return spin(fn, success_predicate)


@as_json
def get_dcos_command(command):
    stdout, stderr, error = shakedown.run_dcos_command(command)
    if error:
        raise RuntimeError(
            'command dcos {} {} failed: {} {}'.format(command, PACKAGE_NAME, stdout, stderr)
        )

    return stdout


@as_json
def get_kafka_command(command):
    stdout, stderr, error = shakedown.run_dcos_command(
        '{} {}'.format(PACKAGE_NAME, command)
    )
    if error:
        raise RuntimeError(
            'command dcos {} {} failed: {} {}'.format(command, PACKAGE_NAME, stdout, stderr)
        )

    return stdout


def get_kafka_config():
    response = dcos.http.get(marathon_api_url('apps/{}/versions'.format(PACKAGE_NAME)))
    assert response.status_code == 200, 'Marathon versions request failed'

    version = response.json()['versions'][0]

    response = dcos.http.get(marathon_api_url('apps/{}/versions/{}'.format(PACKAGE_NAME, version)))
    assert response.status_code == 200

    config = response.json()
    del config['uris']
    del config['version']

    return config


def update_kafka_config(config):
    url = marathon_api_url('apps/' + PACKAGE_NAME)
    try:
        request(dcos.http.put, url, json=config)
    except dcos.errors.DCOSHTTPException as e:
        if e.status() == 409:
            # '409 Conflict': Force deployment
            print("Forcing config update after marathon 409 error.")
            request(dcos.http.put, url + '?force=true', json=config)
        else:
            raise


def kafka_api_url(basename):
    return '{}/v1/{}'.format(shakedown.dcos_service_url(PACKAGE_NAME), basename)


def marathon_api_url(basename):
    return '{}/v2/{}'.format(shakedown.dcos_service_url('marathon'), basename)


def marathon_api_url_with_param(basename, path_param):
    return '{}/{}'.format(marathon_api_url(basename), path_param)


def request(request_fn, *args, **kwargs):
    def success_predicate(response):
        return (
            response.status_code == 200,
            'Request failed: %s' % response.content,
        )

    return spin(request_fn, success_predicate, *args, **kwargs)


def spin(fn, success_predicate, *args, **kwargs):
    now = time.time()
    end_time = now + WAIT_TIME_IN_SECONDS
    while now < end_time:
        print("%s: %.01fs left" % (time.strftime("%H:%M:%S %Z", time.localtime(now)), end_time - now))
        result = fn(*args, **kwargs)
        is_successful, error_message = success_predicate(result)
        if is_successful:
            print('Success state reached, exiting spin.')
            break
        print('Waiting for success state... err={}'.format(error_message))
        time.sleep(1)
        now = time.time()

    assert is_successful, error_message

    return result


def install(additional_options = {}):
    merged_options = _nested_dict_merge(DEFAULT_OPTIONS_DICT, additional_options)
    print('Installing {} with options: {}'.format(PACKAGE_NAME, merged_options))
    shakedown.install_package_and_wait(PACKAGE_NAME, options_json=merged_options)


def uninstall():
    print('Uninstalling/janitoring {}'.format(PACKAGE_NAME))
    try:
        shakedown.uninstall_package_and_wait(PACKAGE_NAME, service_name=PACKAGE_NAME)
    except (dcos.errors.DCOSException, ValueError) as e:
        print('Got exception when uninstalling package, continuing with janitor anyway: {}'.format(e))

    shakedown.run_command_on_master(
        'docker run mesosphere/janitor /janitor.py '
        '-r kafka-role -p {} -z dcos-service-kafka '
        '--auth_token={}'.format(
            PRINCIPAL,
            shakedown.run_dcos_command(
                'config show core.dcos_acs_token'
            )[0].strip()
        )
    )


def _nested_dict_merge(a, b, path=None):
    "ripped from http://stackoverflow.com/questions/7204805/dictionaries-of-dictionaries-merge"
    if path is None: path = []
    a = a.copy()
    for key in b:
        if key in a:
            if isinstance(a[key], dict) and isinstance(b[key], dict):
                _nested_dict_merge(a[key], b[key], path + [str(key)])
            elif a[key] == b[key]:
                pass # same leaf value
            else:
                raise Exception('Conflict at %s' % '.'.join(path + [str(key)]))
        else:
            a[key] = b[key]
    return a
