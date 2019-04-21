import functools
import logging
import operator
import retrying

import sdk_cmd
import sdk_tasks
from tests import config

log = logging.getLogger(__name__)


@retrying.retry(wait_fixed=1000, stop_max_delay=120 * 1000, retry_on_result=lambda res: not res)
def broker_count_check(count, service_name=config.SERVICE_NAME):
    _, brokers, _ = sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, "broker list", parse_json=True)
    return len(brokers) == count


def restart_broker_pods(service_name=config.SERVICE_NAME):
    for i in range(config.DEFAULT_BROKER_COUNT):
        pod_name = "{}-{}".format(config.DEFAULT_POD_TYPE, i)
        task_name = "{}-{}".format(pod_name, config.DEFAULT_TASK_NAME)
        broker_id = sdk_tasks.get_task_ids(service_name, task_name)
        _, restart_info, _ = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, service_name, "pod restart {}".format(pod_name), parse_json=True
        )
        assert len(restart_info) == 2
        assert restart_info["tasks"][0] == task_name
        sdk_tasks.check_tasks_updated(service_name, task_name, broker_id)
        sdk_tasks.check_running(service_name, config.DEFAULT_BROKER_COUNT)


def replace_broker_pod(service_name=config.SERVICE_NAME):
    pod_name = "{}-0".format(config.DEFAULT_POD_TYPE)
    task_name = "{}-{}".format(pod_name, config.DEFAULT_TASK_NAME)
    broker_0_id = sdk_tasks.get_task_ids(service_name, task_name)
    sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, "pod replace {}".format(pod_name))
    sdk_tasks.check_tasks_updated(service_name, task_name, broker_0_id)
    sdk_tasks.check_running(service_name, config.DEFAULT_BROKER_COUNT)
    # wait till all brokers register
    broker_count_check(config.DEFAULT_BROKER_COUNT, service_name=service_name)


def create_topic(topic_name, service_name=config.SERVICE_NAME):
    # Get the list of topics that exist before we create a new topic
    _, topic_list_before, _ = sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, "topic list", parse_json=True)

    _, create_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, service_name, "topic create {}".format(topic_name), parse_json=True
    )
    log.info(create_info)
    assert 'Created topic %s.\n' % topic_name in create_info["message"]

    if "." in topic_name or "_" in topic_name:
        assert (
            "topics with a period ('.') or underscore ('_') could collide."
            in create_info["message"]
        )

    _, topic_list_after, _ = sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, "topic list", parse_json=True)

    new_topics = set(topic_list_after) - set(topic_list_before)
    assert topic_name in new_topics

    _, topic_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, service_name, "topic describe {}".format(topic_name), parse_json=True
    )
    assert len(topic_info) == 1
    assert len(topic_info["partitions"]) == config.DEFAULT_PARTITION_COUNT


def delete_topic(topic_name, service_name=config.SERVICE_NAME):
    _, delete_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, service_name, "topic delete {}".format(topic_name), parse_json=True
    )
    assert len(delete_info) == 1
    assert delete_info["message"].startswith(
        "Output: Topic {} is marked for deletion".format(topic_name)
    )

    _, topic_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, service_name, "topic describe {}".format(topic_name), parse_json=True
    )
    assert len(topic_info) == 1
    assert len(topic_info["partitions"]) == config.DEFAULT_PARTITION_COUNT


def wait_for_topic(package_name: str, service_name: str, topic_name: str):
    """
    Execute `dcos kafka topic describe` to wait for topic creation.
    """

    @retrying.retry(wait_exponential_multiplier=1000, wait_exponential_max=60 * 1000)
    def describe(topic):
        sdk_cmd.svc_cli(package_name, service_name, "topic describe {}".format(topic), parse_json=True)

    describe(topic_name)


def assert_topic_lists_are_equal_without_automatic_topics(expected, actual):
    """Check for equality in topic lists after filtering topics that start with
    an underscore."""
    filtered_actual = list(filter(lambda x: not x.startswith("_"), actual))
    assert expected == filtered_actual


# Pretty much https://github.com/pytoolz/toolz/blob/a8cd0adb5f12ec5b9541d6c2ef5a23072e1b11a3/toolz/dicttoolz.py#L279
def get_in(keys, coll, default=None):
    """ Reaches into nested associative data structures. Returns the value for path ``keys``.

    If the path doesn't exist returns ``default``.

    >>> transaction = {'name': 'Alice',
    ...                'purchase': {'items': ['Apple', 'Orange'],
    ...                             'costs': [0.50, 1.25]},
    ...                'credit card': '5555-1234-1234-1234'}
    >>> get_in(['purchase', 'items', 0], transaction)
    'Apple'
    >>> get_in(['name'], transaction)
    'Alice'
    >>> get_in(['purchase', 'total'], transaction)
    >>> get_in(['purchase', 'items', 'apple'], transaction)
    >>> get_in(['purchase', 'items', 10], transaction)
    >>> get_in(['purchase', 'total'], transaction, 0)
    0
    """
    try:
        return functools.reduce(operator.getitem, keys, coll)
    except (KeyError, IndexError, TypeError):
        return default


def sort(coll):
    """ Sorts a collection and returns it. """
    coll.sort()
    return coll
