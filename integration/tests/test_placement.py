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
    get_plan,
)


TOPIC_NAME = 'topic1'
NUM_TEST_MSGS = 24


def setup_module(module):
    uninstall()


def teardown_module(module):
    uninstall()


# using the functions @nick implemented for cassandra


@pytest.mark.placement
@pytest.mark.sanity
def test_marathon_unique_hostname():
    install(additional_options = {'service':{'placement_constraint':'hostname:UNIQUE'}})
    check_health()
    plan = get_plan(lambda p: p['status'] == 'COMPLETE')
    assert plan['status'] == 'COMPLETE'
    uninstall()


# effectively same as 'hostname:UNIQUE':
@pytest.mark.placement
@pytest.mark.sanity
def test_marathon_max_one_per_hostname():
    install(additional_options = {'service':{'placement_constraint':'hostname:MAX_PER:1'}})
    check_health()
    plan = get_plan(lambda p: p['status'] == 'COMPLETE')
    assert plan['status'] == 'COMPLETE'
    uninstall()


@pytest.mark.placement
@pytest.mark.sanity
@pytest.mark.timeout(120)
def test_marathon_rack_not_found():
    # install without waiting, since the install should never succeed and a timeout would result in an
    # assertion failure
    install(additional_options = {'service':{'placement_constraint':'rack_id:LIKE:rack-foo-.*'}}, wait=False)
    try:
        check_health()
        assert False, "Should have failed healthcheck"
    except:
        pass # expected to fail, just wanting to wait

    plan = get_plan()

    # check that first node is still (unsuccessfully) looking for a match:
    # reconciliation complete
    assert plan['status'] == 'IN_PROGRESS'
    # phase is pending
    assert plan['phases'][1]['status'] == 'PENDING'
    # step is pending
    assert plan['phases'][1]['steps'][0]['status'] == 'PENDING'
    uninstall()
