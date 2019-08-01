import logging
import re
import tempfile

import sdk_cmd
import sdk_tasks
import sdk_diag

from tests import config


log = logging.getLogger(__name__)


def check_health_check_logs():
    broker = None
    executor_path = None
    health_check = {"success": 0, "faliure": 0}

    # Get list of all tasks for kafka service
    tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME)

    # Get any 0th broker
    for t in tasks:
        if t.state == "TASK_RUNNING" and t.name == "kafka-0-broker":
            broker = t
            break
    if not broker:
        raise Exception("Broker 0 is not in status: TASK_RUNNING!")

    # Get Mesos Executor path for Broker 0
    cluster_tasks = sdk_cmd.cluster_request("GET", "/mesos/tasks").json()
    agent_executor_paths = sdk_cmd.cluster_request(
        "GET", "/slave/{}/files/debug".format(broker.agent_id)
    ).json()
    for cluster_task in cluster_tasks["tasks"]:
        if cluster_task["slave_id"] == broker.agent_id:
            executor_path = sdk_diag._find_matching_executor_path(
                agent_executor_paths, sdk_diag._TaskEntry(cluster_task)
            )
            break
    if not executor_path:
        raise Exception("Executor path not found!")

    # Get path of executor's stderr file
    file_infos = sdk_cmd.cluster_request(
        "GET", "/slave/{}/files/browse?path={}".format(broker.agent_id, executor_path)
    ).json()
    file_info = None
    for _t in file_infos:
        if _t["path"].endswith("/stderr"):
            file_info = _t
            break
    if not file_info:
        raise Exception("Executor stderr file not found!")

    # Download stderr file
    stderr_log = None
    try:
        stderr_log = tempfile.NamedTemporaryFile(mode="wb", delete=False)
        stream = sdk_cmd.cluster_request(
            "GET",
            "/slave/{}/files/download?path={}".format(broker.agent_id, file_info["path"])
        )
        with stderr_log as f:
            for chunk in stream.iter_content(chunk_size=8192):
                f.write(chunk)
    except Exception:
        log.exception(
            "Failed to get file: {} from agent: {}".format(file_info["path"], broker.agent_id))

    # Check stderr for health check output
    try:
        with open(stderr_log.name) as f:
            for line in f:
                if re.match("^(Health check passed)", line):
                    health_check["success"] += 1
                if re.match("^(Health check failed)", line):
                    health_check["faliure"] += 1
    except Exception:
        log.exception("Failed to read downloaded executor stderr file: {} from agent: {}".format(file_info["path"], broker.agent_id))

    log.info("HEALTH CHECK success:{} faliure:{}".format(health_check["success"], health_check["faliure"]))
    assert health_check["success"] > health_check["faliure"]
