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


def plan():
    # This RPC is expected to return a 503 code depending on plan state
    ku.http_get_json("/plan", ignored_errors=[503])


def active_operation():
    ku.http_get_json("/plan/status")


def continue_cmd():
    ku.http_get_json("/plan/continue")


def interrupt_cmd():
    ku.http_post_json("/plan/interrupt")


def force_complete_cmd():
    ku.http_post_json("/plan/forceComplete")


def restart_cmd():
    ku.http_post_json("/plan/restart")
