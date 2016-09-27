import dcos.config
import dcos.http
import pytest
import shakedown
import urllib

from tests.test_utils import (
    DEFAULT_PARTITION_COUNT,
    DEFAULT_REPLICATION_FACTOR,
    DEFAULT_BROKER_COUNT,
    DYNAMIC_PORT_OPTIONS_FILE,
    PACKAGE_NAME,
    TASK_RUNNING_STATE,
    check_health,
    get_broker_list,
    get_kafka_command,
    spin,
    uninstall,
)


DEFAULT_TOPIC_NAME = 'topic1'
EPHEMERAL_TOPIC_NAME = 'topic_2'


@pytest.fixture
def default_topic():
    shakedown.run_dcos_command(
        '{} topic create {}'.format(PACKAGE_NAME, DEFAULT_TOPIC_NAME)
    )


def setup_module(module):
    uninstall()
    shakedown.install_package_and_wait(
        PACKAGE_NAME, options_file=DYNAMIC_PORT_OPTIONS_FILE
    )
    check_health()


def teardown_module(module):
    uninstall()


@pytest.mark.sanity
def test_scheduler_connection_setup_is_correct():
    def fn():
        return get_kafka_command('connection')

    def pred(result):
        return (
            len(result['address']) == DEFAULT_BROKER_COUNT,
            'Expected number of brokers never came online'
        )

    connection_info = spin(fn, pred)

    assert len(connection_info) == 4
    assert len(connection_info['dns']) == DEFAULT_BROKER_COUNT
    assert connection_info['zookeeper'] == (
        'master.mesos:2181/dcos-service-{}'.format(PACKAGE_NAME)
    )


@pytest.mark.sanity
def test_expected_brokers_exist():
    broker_info = get_kafka_command('broker list')

    assert set(broker_info) == set([str(i) for i in range(3)])


@pytest.mark.sanity
def test_topic_creation_succeeds():
    create_info = get_kafka_command(
        'topic create {}'.format(EPHEMERAL_TOPIC_NAME)
    )

    assert (
        'Created topic "%s".\n' % EPHEMERAL_TOPIC_NAME in create_info['message']
    )
    assert (
        "topics with a period ('.') or underscore ('_') could collide." in
        create_info['message']
    )

    topic_list_info = get_kafka_command('topic list')
    assert topic_list_info == [EPHEMERAL_TOPIC_NAME]

    topic_info = get_kafka_command(
        'topic describe {}'.format(EPHEMERAL_TOPIC_NAME)
    )
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


@pytest.mark.sanity
def test_topic_deletion_succeeds():
    delete_info = get_kafka_command(
        'topic delete {}'.format(EPHEMERAL_TOPIC_NAME)
    )

    assert len(delete_info) == 1
    assert delete_info['message'].startswith(
        'Output: Topic {} is marked for deletion'.format(EPHEMERAL_TOPIC_NAME)
    )

    # Make sure the topic isn't _actually_ deleted yet.
    topic_info = get_kafka_command(
        'topic describe {}'.format(EPHEMERAL_TOPIC_NAME)
    )
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


@pytest.mark.sanity
def test_topic_partition_count_is_correct(default_topic):
    topic_info = get_kafka_command(
        'topic describe {}'.format(DEFAULT_TOPIC_NAME)
    )
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


@pytest.mark.sanity
def test_topic_offsets_increase_with_writes():
    offset_info = get_kafka_command(
        'topic offsets --time="-1" {}'.format(DEFAULT_TOPIC_NAME)
    )
    assert len(offset_info) == DEFAULT_PARTITION_COUNT

    offsets = {}
    for o in offset_info:
        assert len(o) == DEFAULT_REPLICATION_FACTOR
        offsets.update(o)

    assert len(offsets) == DEFAULT_PARTITION_COUNT

    num_messages = 10
    write_info = get_kafka_command(
        'topic producer_test {} {}'.format(DEFAULT_TOPIC_NAME, num_messages)
    )
    assert len(write_info) == 1
    assert write_info['message'].startswith(
        'Output: {} records sent'.format(num_messages)
    )

    offset_info = get_kafka_command(
        'topic offsets --time="-1" {}'.format(DEFAULT_TOPIC_NAME)
    )
    assert len(offset_info) == DEFAULT_PARTITION_COUNT

    post_write_offsets = {}
    for offsets in offset_info:
        assert len(o) == DEFAULT_REPLICATION_FACTOR
        post_write_offsets.update(o)

    assert not offsets == post_write_offsets


@pytest.mark.sanity
def test_decreasing_topic_partitions_fails():
    partition_info = get_kafka_command(
        'topic partitions {} {}'.format(
            DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_COUNT - 1
        )
    )

    assert len(partition_info) == 1
    assert partition_info['message'].startswith(
        'Output: WARNING: If partitions are increased'
    )
    assert (
        'The number of partitions for a topic can only be increased' in
        partition_info['message']
    )


@pytest.mark.sanity
def test_setting_topic_partitions_to_same_value_fails():
    partition_info = get_kafka_command(
        'topic partitions {} {}'.format(
            DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_COUNT
        )
    )

    assert len(partition_info) == 1
    assert partition_info['message'].startswith(
        'Output: WARNING: If partitions are increased'
    )
    assert (
        'The number of partitions for a topic can only be increased' in
        partition_info['message']
    )


@pytest.mark.sanity
def test_increasing_topic_partitions_succeeds():
    partition_info = get_kafka_command(
        'topic partitions {} {}'.format(
            DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_COUNT + 1
        )
    )

    assert len(partition_info) == 1
    assert partition_info['message'].startswith(
        'Output: WARNING: If partitions are increased'
    )
    assert (
        'The number of partitions for a topic can only be increased' not in
        partition_info['message']
    )


@pytest.mark.sanity
def test_no_under_replicated_topics_exist():
    partition_info = get_kafka_command(
        'topic under_replicated_partitions'
    )

    assert len(partition_info) == 1
    assert partition_info['message'] == ''


@pytest.mark.sanity
def test_no_unavailable_partitions_exist():
    partition_info = get_kafka_command('topic unavailable_partitions')

    assert len(partition_info) == 1
    assert partition_info['message'] == ''


@pytest.mark.sanity
def test_invalid_broker_id_returns_null():
    restart_info = get_kafka_command('broker restart {}'.format(DEFAULT_BROKER_COUNT + 1))

    assert len(restart_info) == 1
    assert restart_info[0] is None


@pytest.mark.special
def test_restart_all_brokers_succeeds():
    for i in range(DEFAULT_BROKER_COUNT):
        broker_task = get_running_broker_task('broker-{}'.format(i))[0]
        broker_id = broker_task['id']
        assert broker_id.startswith('broker-{}__'.format(i))
        restart_info = get_kafka_command('broker restart {}'.format(i))
        task_id_changes('broker-{}'.format(i), broker_id)
        assert len(restart_info) == 1
        assert restart_info[0].startswith('broker-{}__'.format(i))


@pytest.mark.sanity
def test_single_broker_replace_succeeds():
    broker_0_task = get_running_broker_task('broker-0')[0]
    broker_0_id = broker_0_task['id']
    assert broker_0_id.startswith('broker-0__')
    
    replace_info = get_kafka_command('broker replace 0')
    task_id_changes('broker-0', broker_0_id)


@pytest.mark.sanity
def test_is_suppressed():
    dcos_url = dcos.config.get_config_val('core.dcos_url')
    suppressed_url = urllib.parse.urljoin(dcos_url, 'service/kafka/v1/state/properties/suppressed')
    response = dcos.http.get(suppressed_url)
    response.raise_for_status()
    assert response.text == "true"


def get_running_broker_task(broker_name):
    def fn():
        try:
            tasks = shakedown.get_service_tasks(PACKAGE_NAME)
            return [t for t in tasks if t['state'] == TASK_RUNNING_STATE and t['name'] == broker_name]
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(tasks):
        return (len(tasks) == 1, 'Failed to get task')

    return spin(fn, success_predicate)


def task_id_changes(broker_name, task_id):
    def fn():
        try:
            tasks = shakedown.get_service_tasks(PACKAGE_NAME)
            return [t for t in tasks if t['state'] == TASK_RUNNING_STATE and t['name'] == broker_name]
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(tasks):
        return (len(tasks) == 1 and tasks[0]['id'] != task_id, "Task ID didn't change.")

    return spin(fn, success_predicate)
