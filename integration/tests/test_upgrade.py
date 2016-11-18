import dcos.http
import pytest
import shakedown

from tests.test_utils import (
    DEFAULT_BROKER_COUNT,
    PACKAGE_NAME,
    check_health,
    get_dcos_command,
    get_kafka_command,
    install,
    marathon_api_url_with_param,
    request,
    spin,
    uninstall,
)


TOPIC_NAME = 'topic1'
NUM_TEST_MSGS = 24


def setup_module(module):
    uninstall()


def teardown_module(module):
    uninstall()


@pytest.mark.sanity
def test_upgrade():
    test_repo_name, test_repo_url = get_test_repo_info()
    test_version = get_pkg_version()
    print('Found test version: {}'.format(test_version))
    remove_repo(test_repo_name, test_version)
    master_version = get_pkg_version()
    print('Found master version: {}'.format(master_version))

    print('Installing master version')
    install({'package_version': master_version})
    check_health()
    write_messages()

    print('Upgrading to test version')
    destroy_service()
    add_repo(test_repo_name, test_repo_url, master_version)
    install({'package_version': test_version})
    check_post_upgrade_health()


def get_test_repo_info():
    repos = shakedown.get_package_repos()
    test_repo = repos['repositories'][0]
    return test_repo['name'], test_repo['uri']


def get_pkg_version():
    cmd = 'package describe {}'.format(PACKAGE_NAME)
    pkg_description = get_dcos_command(cmd)
    return pkg_description['version']


def remove_repo(repo_name, prev_version):
    assert shakedown.remove_package_repo(repo_name)
    new_default_version_available(prev_version)


def add_repo(repo_name, repo_url, prev_version):
    assert shakedown.add_package_repo(
        repo_name,
        repo_url,
        0)
    # Make sure the new repo packages are available
    new_default_version_available(prev_version)


def new_default_version_available(prev_version):
    def fn():
        get_pkg_version()
    def success_predicate(pkg_version):
        return (pkg_version != prev_version, 'Package version has not changed')
    spin(fn, success_predicate)


def write_messages():
    get_kafka_command(
        'topic producer_test {} {}'.format(TOPIC_NAME, NUM_TEST_MSGS)
    )


def destroy_service():
    destroy_endpoint = marathon_api_url_with_param('apps', PACKAGE_NAME)
    request(dcos.http.delete, destroy_endpoint)
    # Make sure the scheduler has been destroyed
    def fn():
        shakedown.get_service(PACKAGE_NAME)

    def success_predicate(service):
        return (service == None, 'Service not destroyed')

    spin(fn, success_predicate)


def check_post_upgrade_health():
    check_health()
    check_scheduler_health()
    check_offsets()


def check_scheduler_health():
    # Make sure scheduler endpoint is responding and all brokers are available
    def fn():
        try:
            return get_kafka_command('broker list')
        except RuntimeError:
            return []

    def success_predicate(brokers):
        return (len(brokers) == DEFAULT_BROKER_COUNT,
                'Scheduler and all brokers not available')

    spin(fn, success_predicate)


def check_offsets():
    offset_info = get_kafka_command(
        'topic offsets {}'.format(TOPIC_NAME)
    )
    offset = int(offset_info[0]['0'])
    assert offset == NUM_TEST_MSGS
