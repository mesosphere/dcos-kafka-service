#!/usr/bin/python

'''Launches kafka-test-client consumers and producers in Marathon.'''

import getopt
import json
import logging
import pprint
import random
import requests
from requests.exceptions import HTTPError
import string
import sys
import urllib
import urlparse

def __urljoin(*elements):
    return "/".join(elem.strip("/") for elem in elements)

def marathon_apps_url(cluster_uri):
    url = __urljoin(cluster_uri, "marathon", "v2", "apps")
    print("Marathon query: %s" % url)
    return url

def marathon_launch_app(marathon_url, app_id, cmd, instances=1, packages=[], env={}):
    def __post(url, json=None):
        pprint.pprint(json)
        return __handle_response("POST", url, requests.post(url, json=json))

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

    formatted_packages = []
    for package in packages:
        formatted_packages.append({"uri": package})
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
    }

    json = __post(marathon_url, json=post_json)
    return json["deployments"]

def get_random_id(length=8):
    return ''.join([random.choice(string.ascii_lowercase) for _ in range(length)])

CONSUMER_CLASS = "org.apache.mesos.kafka.testclient.ConsumerMain"
PRODUCER_CLASS = "org.apache.mesos.kafka.testclient.ProducerMain"
JRE_URL = "https://s3-eu-west-1.amazonaws.com/downloads.mesosphere.com/kafka/jre-8u72-linux-x64.tar.gz"
JRE_JAVA_PATH = "jre/bin/java"

FRAMEWORK_NAME_DEFAULT='kafka'
CONSUMER_COUNT_DEFAULT = 5
PRODUCER_COUNT_DEFAULT = 5
THREAD_COUNT_DEFAULT = 5
PRODUCER_QPS_LIMIT_DEFAULT = 5
PRODUCER_MSG_SIZE_DEFAULT = 1024
STATS_PRINT_PERIOD_MS_DEFAULT = 500
JAR_URL_DEFAULT = "https://s3-us-west-2.amazonaws.com/infinity-artifacts/kafka/kafka-test-client-0.2.4-uber.jar"


def print_help(argv):
    print('''Flags: {}
  -h/--help
  -c/--cluster_url=<REQUIRED, eg https://cluster-url.com>
  -f/--framework_name={}
  -i/--producer_count={}
  -o/--consumer_count={}
  -t/--thread_count={}
  -q/--producer_qps_limit={}
  -m/--producer_msg_size={}
  -s/--stats_print_period_ms={}
  --jar_url={}
  --topic_override=""
  --broker_override=""
'''.format(
      argv[0],
      FRAMEWORK_NAME_DEFAULT,
      CONSUMER_COUNT_DEFAULT,
      PRODUCER_COUNT_DEFAULT,
      THREAD_COUNT_DEFAULT,
      PRODUCER_QPS_LIMIT_DEFAULT,
      PRODUCER_MSG_SIZE_DEFAULT,
      STATS_PRINT_PERIOD_MS_DEFAULT,
      JAR_URL_DEFAULT))
    print('Example: {} -c http://your-cluster-url.com'.format(argv[0]))


def main(argv):
    # You must initialize logging, otherwise you'll not see debug output.
    logging.basicConfig()
    logging.getLogger().setLevel(logging.DEBUG)
    requests_log = logging.getLogger('requests.packages.urllib3')
    requests_log.setLevel(logging.DEBUG)
    requests_log.propagate = True

    cluster_url = ''
    jar_url = JAR_URL_DEFAULT
    framework_name = FRAMEWORK_NAME_DEFAULT
    consumer_count = CONSUMER_COUNT_DEFAULT
    producer_count = PRODUCER_COUNT_DEFAULT
    thread_count = THREAD_COUNT_DEFAULT
    producer_qps_limit = PRODUCER_QPS_LIMIT_DEFAULT
    producer_msg_size = PRODUCER_MSG_SIZE_DEFAULT
    stats_print_period_ms = STATS_PRINT_PERIOD_MS_DEFAULT
    topic_override = ''
    broker_override = ''

    try:
        opts, args = getopt.getopt(argv[1:], 'hc:f:i:o:t:q:m:s:', [
            'help',
            'cluster_url=',
            'framework_name=',
            'producer_count=',
            'consumer_count=',
            'thread_count=',
            'producer_qps_limit=',
            'producer_msg_size=',
            'stats_print_period_ms=',
            'jar_url=',
            'topic_override=',
            'broker_override='])
    except getopt.GetoptError:
        print_help(argv)
        sys.exit(1)
    for opt, arg in opts:
        if opt in ['-h', '--help']:
            print_help(argv)
            sys.exit(1)
        elif opt in ['-c', '--cluster_url']:
            cluster_url = arg.rstrip('/')
        elif opt in ['-f', '--framework_name']:
            framework_name = arg
        elif opt in ['-i', '--producer_count']:
            producer_count = int(arg)
        elif opt in ['-o', '--consumer_count']:
            consumer_count = int(arg)
        elif opt in ['-t', '--thread_count']:
            thread_count = int(arg)
        elif opt in ['-q', '--producer_qps_limit']:
            producer_qps_limit = int(arg)
        elif opt in ['-m', '--producer_msg_size']:
            producer_msg_size = int(arg)
        elif opt in ['-s', '--stats_print_period_ms']:
            stats_print_period_ms = int(arg)
        elif opt in ['--jar_url']:
            jar_url = arg
        elif opt in ['--topic_override']:
            topic_override = arg
        elif opt in ['--broker_override']:
            broker_override = arg

    if not cluster_url:
        print("-c/--cluster_url is required")
        print_help(argv)
        sys.exit(1)

    topic_rand_id = get_random_id()
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

    marathon_url = marathon_apps_url(cluster_url)
    if not marathon_launch_app(
            marathon_url = marathon_url,
            app_id = consumer_app_id,
            cmd = "env && ${MESOS_SANDBOX}/%s -cp ${MESOS_SANDBOX}/%s %s" % (
                JRE_JAVA_PATH, package_filename, CONSUMER_CLASS),
            instances = consumer_count,
            packages = [JRE_URL, jar_url],
            env = consumer_env):
        print("Starting consumers failed, skipping launch of producers")
        return 1
    if not marathon_launch_app(
            marathon_url = marathon_url,
            app_id = producer_app_id,
            cmd = "env && ${MESOS_SANDBOX}/%s -cp ${MESOS_SANDBOX}/%s %s" % (
                JRE_JAVA_PATH, package_filename, PRODUCER_CLASS),
            instances = producer_count,
            packages = [JRE_URL, jar_url],
            env = producer_env):
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
    sys.exit(main(sys.argv))
