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
from dcos_kafka import kafka_utils as ku


def list():
    ku.http_get_json("/topics")


def describe(name):
    ku.http_get_json("/topics/" + name)


def create(name, partitions, replication):
    payload = {
        'name': name,
        'partitions': partitions,
        'replication': replication
    }
    ku.http_post_json("/topics", params=payload)


def delete(name):
    ku.http_delete_json("/topics/" + name)


def offsets(name, time):
    payload = {'time': time}
    ku.http_get_json("/topics/" + name + "/offsets", params=payload)


def unavailable_partitions():
    ku.http_get_json("/topics/unavailable_partitions")


def under_replicated_partitions():
    ku.http_get_json("/topics/under_replicated_partitions")


def alter_partition_count(name, partitions):
    payload = {'operation': 'partitions', 'partitions': partitions}
    ku.http_put_json("/topics/" + name, params=payload)


def producer_test(name, message_count):
    payload = {'operation': 'producer-test', 'messages': message_count}
    ku.http_put_json("/topics/" + name, params=payload)
