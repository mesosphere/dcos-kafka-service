#
#    Copyright (C) 2015 Mesosphere, Inc.
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
from dcos_kafka import broker_api, cluster_api, plan_api, topic_api


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
    print("Usage: dcos help kafka")


@kafka.command()
@click.option('--address', is_flag=True)
@click.option('--dns', is_flag=True)
def connection(address, dns):
    """Show Kafka Broker connection info"""
    address = bool(address)
    dns = bool(dns)

    if not xor(address, dns):
        cluster_api.connection()
    elif address:
        cluster_api.connection_address()
    elif dns:
        cluster_api.connection_dns()


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
def list_configurations():
    """Lists all available configurations"""
    cluster_api.list_configurations()


@config.command('describe')
@click.argument("config_id")
def describe_configuration(config_id):
    """Describes a single configuration"""
    cluster_api.describe_configuration(config_id)


@config.command()
def target():
    """Describes the target configuration"""
    cluster_api.describe_target_configuration()


@kafka.group()
@click.option('--name', help='Name of the Kafka instance to query')
def plan(name):
    """Rollout plan maintenance"""
    if name:
        ku.set_fwk_name(name)


@plan.command()
def show():
    """Display the full plan"""
    plan_api.plan()


@plan.command()
def active():
    """Display the active operation chain, if any"""
    plan_api.active_operation()


# python dislikes functions named 'continue'
@plan.command('continue')
def continue_():
    """Continue the current Waiting operation"""
    plan_api.continue_cmd()


@plan.command()
def interrupt():
    """Interrupt the current InProgress operation"""
    plan_api.interrupt_cmd()


@plan.command()
def force():
    """Force the current operation to complete"""
    plan_api.force_complete_cmd()


@plan.command('restart')
def restart_plan():
    """Restart the current operation"""
    plan_api.restart_cmd()


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
