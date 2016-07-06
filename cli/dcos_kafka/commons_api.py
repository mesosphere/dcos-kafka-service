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
from dcos_kafka import kafka_utils as ku


# plan


def plan():
    # This RPC is expected to return a 503 code depending on plan state
    ku.http_get_json("/plan", ignored_errors=[503])


def plan_active_operation():
    ku.http_get_json("/plan/status")


def plan_cmd_continue():
    ku.http_get_json("/plan/continue")


def plan_cmd_interrupt():
    ku.http_post_json("/plan/interrupt")


def plan_cmd_force_complete():
    ku.http_post_json("/plan/forceComplete")


def plan_cmd_restart():
    ku.http_post_json("/plan/restart")


# configurations


def configuration_list_ids():
    ku.http_get_json("/configurations")


def configuration_show(configuration_name):
    ku.http_get_json("/configurations/" + configuration_name)


def configuration_target_id():
    ku.http_get_json("/configurations/targetId")


def configuration_show_target():
    ku.http_get_json("/configurations/target")


# state


def state_framework_id():
    ku.http_get_json("/state/frameworkId")


def state_list_task_names():
    ku.http_get_json("/state/tasks")


def state_task_info(task_name):
    ku.http_get_json("/state/tasks/info/" + task_name)


def state_task_status(task_name):
    ku.http_get_json("/state/tasks/status/" + task_name)
