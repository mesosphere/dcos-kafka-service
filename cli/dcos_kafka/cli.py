#
#    Copyright (C) 2016 Mesosphere, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""DCOS Kafka"""
import os
from operator import xor

import click
import dcos_kafka.kafka_utils as ku
import pkg_resources
from dcos_kafka import broker_api, commons_api, connection_api, topic_api


@click.group()
def cli():
    pass


@cli.group(invoke_without_command=True)
@click.option('--info/--no-info', default=False)
@click.option('--name', help='Name of the Kafka instance to query')
@click.option('--config-schema',
              help='Prints the config schema for kafka.', is_flag=True)
def kafka(info, name, config_schema):
    """CLI Module for interaction with a DCOS Kafka service"""
    if info:
        print("Deploy and manage Kafka clusters")
    if name:
        ku.set_fwk_name(name)
    if config_schema:
        print_schema()


def print_schema():
    schema = pkg_resources.resource_string(
        'dcos_kafka',
        'data/config-schema/kafka.json'
    ).decode('utf-8')
    print(schema)


@kafka.command()
def help():
    print("Usage: dcos kafka --help")


@kafka.command()
@click.option('--address', is_flag=True)
@click.option('--dns', is_flag=True)
def connection(address, dns):
    """Show Kafka Broker connection info"""
    address = bool(address)
    dns = bool(dns)

    if not xor(address, dns):
        connection_api.connection()
    elif address:
        connection_api.connection_address()
    elif dns:
        connection_api.connection_dns()


@kafka.group()
@click.option('--name', help='Name of the Kafka instance to query')
def broker(name):
    """Kafka Broker maintenance"""
    if name:
        ku.set_fwk_name(name)


@broker.command('list')
def list_brokers():
    """Lists all running brokers in the service"""
    broker_api.list()


@broker.command('restart')
@click.argument("broker_id")
def restart_broker(broker_id):
    """Restarts a single broker job"""
    broker_api.restart(broker_id)


@broker.command()
@click.argument("broker_id")
def replace(broker_id):
    """Replaces a single broker job"""
    broker_api.replace(broker_id)


@kafka.group()
@click.option('--name', help='Name of the Kafka instance to query')
def config(name):
    """Service configuration maintenance"""
    if name:
        ku.set_fwk_name(name)


@config.command('list')
def list_configuration_ids():
    """List IDs of all available configurations"""
    commons_api.configuration_list_ids()


@config.command('show')
@click.argument("config_id")
def show_configuration(config_id):
    """Show a specified configuration"""
    commons_api.configuration_show(config_id)


@config.command()
def target_id():
    """List ID of the target configuration"""
    commons_api.configuration_target_id()


@config.command()
def target():
    """Show the target configuration"""
    commons_api.configuration_show_target()


@kafka.group()
@click.option('--name', help='Name of the Kafka instance to query')
def plan(name):
    """Rollout plan maintenance"""
    if name:
        ku.set_fwk_name(name)


@plan.command()
def show():
    """Display the full plan"""
    commons_api.plan()


@plan.command()
def active():
    """Display the active operation chain, if any"""
    commons_api.plan_active_operation()


# python dislikes functions named 'continue'
@plan.command('continue')
def continue_():
    """Continue the current Waiting operation"""
    commons_api.plan_cmd_continue()


@plan.command()
def interrupt():
    """Interrupt the current InProgress operation"""
    commons_api.plan_cmd_interrupt()


@plan.command()
def force():
    """Force the current operation to complete"""
    commons_api.plan_cmd_force_complete()


@plan.command('restart')
def restart_plan():
    """Restart the current operation"""
    commons_api.plan_cmd_restart()


@kafka.group()
@click.option('--name', help='Name of the Kafka instance to query')
def state(name):
    """Framework persisted state maintenance"""
    if name:
        ku.set_fwk_name(name)


@state.command()
def framework_id():
    """Display the framework ID"""
    commons_api.state_framework_id()


@state.command()
def tasks():
    """Display the list of persisted task names"""
    commons_api.state_list_task_names()


@state.command()
@click.argument('name')
def task(name):
    """Display the TaskInfo for a specific task"""
    commons_api.state_task_info(name)


@state.command()
@click.argument('name')
def status(name):
    """Display the TaskStatus for a specific task"""
    commons_api.state_task_status(name)


@kafka.group()
@click.option('--name', help='Name of the Kafka instance to query')
def topic(name):
    """Kafka Topic maintenance"""
    if name:
        ku.set_fwk_name(name)


@topic.command('list')
def list_topic():
    """Lists all available topics in a framework"""
    topic_api.list()


@topic.command('describe')
@click.argument('name')
def describe_topic(name):
    """Describes a single existing topic"""
    topic_api.describe(name)


@topic.command()
@click.argument('name')
@click.option(
    '--time', default='last',
    help='Offset for the Topic: [first|last|<timestamp in milliseconds>]')
def offsets(name, time):
    """Returns the current offset counts for a topic"""
    if (time == 'first'):
        time = -2
    elif (time == 'last'):
        time = -1

    topic_api.offsets(name, time)


@topic.command()
def unavailable_partitions():
    """Gets info for any unavailable partitions"""
    topic_api.unavailable_partitions()


@topic.command()
def under_replicated_partitions():
    """Gets info for any under-replicated partitions"""
    topic_api.under_replicated_partitions()


@topic.command()
@click.argument("name")
@click.option('--partitions', default=1, help='Number of partitions')
@click.option('--replication', default=3, help='Replication factor')
def create(name, partitions, replication):
    """Creates a new topic"""
    part = os.environ.get('KAFKA_DEFAULT_PARTITION_COUNT', partitions)
    repl = os.environ.get('KAFKA_DEFAULT_REPLICATION_FACTOR', replication)
    topic_api.create(name, part, repl)


@topic.command()
@click.argument("name")
def delete(name):
    """Deletes an existing topic"""
    topic_api.delete(name)


@topic.command()
@click.argument("name")
@click.argument("count")
def partitions(name, count):
    """Alters partition count for an existing topic"""
    topic_api.alter_partition_count(name, count)


@topic.command()
@click.argument("name")
@click.argument("messages")
def producer_test(name, messages):
    """Produce some test messages against a topic"""
    topic_api.producer_test(name, messages)


def main():
    cli(obj={})


if __name__ == "__main__":
    main()
