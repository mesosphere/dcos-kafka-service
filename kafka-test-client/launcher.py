#!/usr/bin/python

'''Launches kafka-test-client consumers and producers in Marathon.'''

import json
import logging
import pprint
import random
import string
import sys
import urllib
import urlparse

# non-stdlib libs:
try:
    import click
    import requests
    from requests.exceptions import HTTPError
except ImportError:
    print("Failed to load third-party libraries.")
    print("Please run: $ pip install -r requirements.txt")
    sys.exit(1)

def __urljoin(*elements):
    return "/".join(elem.strip("/") for elem in elements)

def __post(url, json=None):
    pprint.pprint(json)
    if auth_token:
        headers = {"Authorization": "token={}".format(auth_token)}
    else:
        headers = {}
    response = requests.post(url, json=json, headers=headers)
    return __handle_response("POST", url, response)

def __handle_response(httpcmd, url, response):
    # http code 200-299 => success!
    if response.status_code < 200 or response.status_code >= 300:
        errmsg = "Error code in response to %s %s: %s/%s" % (
            httpcmd, url, response.status_code, response.content)
        print(errmsg)
        raise HTTPError(errmsg)
    json = response.json()
    print("Got response for %s %s:\n%s" % (httpcmd, url, json))
    return json

def marathon_launch_app(cluster_url, app_id, cmd, instances=1, packages=[], env={}, auth_token=""):
    formatted_packages = []
    for package in packages:
        formatted_packages.append({"uri": package})
    formatted_env = {}
    for k,v in env.iteritems():
        formatted_env[str(k)] = str(v)
    post_json = {
        "id": app_id,
        "container": {
            "type": "MESOS",
        },
        "cmd": cmd,
        "cpus": 1,
        "mem": 512.0, # 512m apparently required: 128m and 256m results in FAILEDs.
        "disk": 1,
        "instances": instances,
        "fetch": formatted_packages,
        "env": formatted_env,
    }

    json = __post(__urljoin(cluster_url, "marathon/v2/apps"), json=post_json)
    return json["deployments"]

def get_random_id(length=8):
    return ''.join([random.choice(string.ascii_lowercase) for _ in range(length)])

CONSUMER_CLASS = "org.apache.mesos.kafka.testclient.ConsumerMain"
PRODUCER_CLASS = "org.apache.mesos.kafka.testclient.ProducerMain"
JRE_JAVA_PATH = "jre/bin/java" # relative to MESOS_SANDBOX

@click.command()
@click.argument('cluster_url', envvar='DCOS_URI')
@click.option("--framework-name", show_default=True, default="kafka",
              help="framework's name in DCOS, for auto-detecting brokers")
@click.option("--producer-count", show_default=True, default=5,
              help="number of producers to launch")
@click.option("--consumer-count", show_default=True, default=5,
              help="number of consumers to launch")
@click.option("--thread-count", show_default=True, default=5,
              help="number of threads to launch in each producer and consumer")
@click.option("--producer-qps-limit", show_default=True, default=5,
              help="rate limit for producers, in messages per second")
@click.option("--producer-msg-size", show_default=True, default=1024,
              help="per-message size for producers")
@click.option("--stats-print-period-ms", show_default=True, default=500,
              help="how frequently to print throughput stats to stdout")
@click.option("--username", envvar="DCOS_USERNAME",
              help="username to use when making requests to the DCOS cluster (if the cluster requires auth)")
@click.option("--password", envvar="DCOS_PASSWORD",
              help="password to use when making requests to the DCOS cluster (if the cluster requires auth)")
@click.option("--jar-url", show_default=True, default="https://s3-us-west-2.amazonaws.com/infinity-artifacts/kafka/kafka-test-client-0.2.4-uber.jar",
              help="url of the kafka test client package")
@click.option("--jre-url", show_default=True, default="https://s3-eu-west-1.amazonaws.com/downloads.mesosphere.com/kafka/jre-8u72-linux-x64.tar.gz",
              help="url of the jre package")
@click.option("--topic-override",
              help="topic to use instead of a randomized default")
@click.option("--broker-override",
              help="list of broker endpoints to use instead of what the framework returns")
def main(
        cluster_url,
        framework_name,
        producer_count,
        consumer_count,
        thread_count,
        producer_qps_limit,
        producer_msg_size,
        stats_print_period_ms,
        username,
        password,
        jar_url,
        jre_url,
        topic_override,
        broker_override):
    """Launches zero or more test producer and consumer clients against a Kafka framework.

    The clients are launched as marathon tasks, which may be destroyed using the provided curl commands when testing is complete.

    You must at least provide the URL of the cluster, for example: 'python launcher.py http://your-dcos-cluster.com'"""
    print(cluster_url)

    logging.basicConfig()
    logging.getLogger().setLevel(logging.DEBUG)
    requests_log = logging.getLogger('requests.packages.urllib3')
    requests_log.setLevel(logging.DEBUG)
    requests_log.propagate = True

    topic_rand_id = get_random_id() # reused for topic, unless --topic_override is specified
    producer_app_id = "kafkatest-" + topic_rand_id + "-producer"
    consumer_app_id = "kafkatest-" + topic_rand_id + "-consumer"

    common_env = {
        "THREADS": thread_count,
        "MASTER_HOST": urlparse.urlparse(cluster_url).netloc, # http://example.com/ -> example.com
        "STATS_PRINT_PERIOD_MS": stats_print_period_ms,
    }
    if topic_override:
        common_env["TOPIC"] = topic_override
    else:
        common_env["TOPIC"] = "test-" + topic_rand_id
    if broker_override:
        # use this if running kafka manually (without a scheduler)
        common_env["KAFKA_OVERRIDE_BOOTSTRAP_SERVERS"] = broker_override
    else:
        # default to letting the framework scheduler provide the list of servers
        common_env["FRAMEWORK_NAME"] = framework_name

    producer_env = {
        "KAFKA_OVERRIDE_METADATA_FETCH_TIMEOUT_MS": "3000",
        "KAFKA_OVERRIDE_REQUEST_TIMEOUT_MS": "3000",
        "QPS_LIMIT": producer_qps_limit,
        "MESSAGE_SIZE_BYTES": producer_msg_size,
    }
    producer_env.update(common_env)
    consumer_env = {
        "KAFKA_OVERRIDE_GROUP_ID": consumer_app_id,
    }
    consumer_env.update(common_env)

    package_filename = jar_url.split('/')[-1]

    if username and password:
        post_json = {
            "uid": username,
            "password": password
        }
        tok_response = __post(__urljoin(cluster_url, "acs/api/v1/auth/login"), json=post_json)
        auth_token = tok_response.token
    if not marathon_launch_app(
            cluster_url = cluster_url,
            app_id = consumer_app_id,
            cmd = "env && ${MESOS_SANDBOX}/%s -cp ${MESOS_SANDBOX}/%s %s" % (
                JRE_JAVA_PATH, package_filename, CONSUMER_CLASS),
            instances = consumer_count,
            packages = [jre_url, jar_url],
            env = consumer_env,
            auth_token = auth_token):
        print("Starting consumers failed, skipping launch of producers")
        return 1
    if not marathon_launch_app(
            cluster_url = cluster_url,
            app_id = producer_app_id,
            cmd = "env && ${MESOS_SANDBOX}/%s -cp ${MESOS_SANDBOX}/%s %s" % (
                JRE_JAVA_PATH, package_filename, PRODUCER_CLASS),
            instances = producer_count,
            packages = [jre_url, jar_url],
            env = producer_env,
            auth_token = auth_token):
        print("Starting producers failed")
        return 1

    print('''#################
Consumers/producers have been launched.
When finished, delete them from Marathon with these commands:

curl -X DELETE {}/{}
curl -X DELETE {}/{}'''.format(
    marathon_url, consumer_app_id,
    marathon_url, producer_app_id))
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
