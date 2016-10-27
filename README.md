DC/OS Apache Kafka Service Guide
======================

[![Build Status](http://jenkins.mesosphere.com/service/jenkins/buildStatus/icon?job=kafka/0-trigger-master)](http://jenkins.mesosphere.com/service/jenkins/job/kafka/job/0-trigger-master/)

[Overview](#overview)
- [Benefits](#benefits)
- [Features](#features)
- [Related Services](#related-services)

[Getting Started](#getting-started)
- [Quick Start](#quick-start)

[Install and Customize](#install-and-customize)
- [Default Installation](#default-installation)
- [Minimal Installation](#minimal-installation)
- [Custom Installation](#custom-installation)
- [Multiple Kafka cluster installation](#multiple-kafka-cluster-installation)
- [Uninstall](#uninstall)

[Configuring](#configuring)
- [Changing Configuration at Runtime](#changing-configuration-at-runtime)
- [Configuration Options](#configuration-options)

[Connecting Clients](#connecting-clients)
- [Using the DC/OS CLI](#using-the-dcos-cli)
- [Using the REST API](#using-the-rest-api)
- [REST API Authentication](#rest-api-authentication)
- [Connection Info Response](#connection-info-response)
- [Configuring the Kafka Client Library](#configuring-the-kafka-client-library)
- [Configuring the Kafka Test Scripts](#configuring-the-kafka-test-scripts)

[Managing](#managing)
- [Add a Broker](#add-a-broker)
- [Upgrade Software](#upgrade-software)

[Troubleshooting](#troubleshooting)
- [Configuration Update Errors](#configuration-update-errors)
- [Replacing a Permanently Failed Server](#replacing-a-permanently-failed-server)

[REST API Reference](#api-reference)
- [Connection Information](#connection-information)
- [Broker Operations](#broker-operations)
- [Topic Operations](#topic-operations)
- [Config History](#config-history)
- [Config Updates](#config-updates)

[Limitations](#limitations)
- [Configurations](#configurations)
- [Brokers](#brokers)

# Overview

DC/OS Apache Kafka is an automated service that makes it easy to deploy and manage Apache Kafka on Mesosphere DC/OS, eliminating nearly all of the complexity traditionally associated with managing a Kafka cluster. Apache Kafka is a distributed high-throughput publish-subscribe messaging system with strong ordering guarantees. Kafka clusters are highly available, fault tolerant, and very durable. For more information on Apache Kafka, see the Apache Kafka [documentation][1]. DC/OS Kafka gives you direct access to the Kafka API so that existing producers and consumers can interoperate. You can configure and install DC/OS Kafka in moments. Multiple Kafka clusters can be installed on DC/OS and managed independently, so you can offer Kafka as a managed service to your organization.

## Benefits

DC/OS Kafka offers the following benefits of a semi-managed service:

*   Easy installation
*   Multiple Kafka clusters
*   Elastic scaling of brokers
*   Replication for high availability
*   Kafka cluster and broker monitoring

## Features

DC/OS Kafka provides the following features:

*   Single-command installation for rapid provisioning
*   Multiple clusters for multiple tenancy with DC/OS
*   High availability runtime configuration and software updates
*   Storage volumes for enhanced data durability, known as Mesos Dynamic Reservations and Persistent Volumes
*   Integration with syslog-compatible logging services for diagnostics and troubleshooting
*   Integration with statsd-compatible metrics services for capacity and performance monitoring

## Related Services

*   [DC/OS Spark][2]

<a name="getting-started"></a>
# Getting Started

## Quick Start

*   Step 1. Install a Kafka cluster.

        $ dcos package install kafka


*   Step 2. Create a new topic.

        $ dcos kafka topic create topic1


*   Step 3. Find connection information.

        $ dcos kafka connection
        {
            "address": [
                "10.0.0.211:9843",
                "10.0.0.217:10056",
                "10.0.0.214:9689"
            ],
            "dns": [
                "broker-0.kafka.mesos:9843",
                "broker-1.kafka.mesos:10056",
                "broker-2.kafka.mesos:9689"
            ],
            "vip": "broker.kafka.l4lb.thisdcos.directory:9092",
            "zookeeper": "master.mesos:2181/dcos-service-kafka"
        }


*   Step 4. Produce and consume data.

        $ dcos node ssh --master-proxy --leader
    
        core@ip-10-0-6-153 ~ $ docker run -it mesosphere/kafka-client
    
        root@7d0aed75e582:/bin# echo "Hello, World." | ./kafka-console-producer.sh --broker-list broker.kafka.l4lb.thisdcos.directory:9092 --topic topic1
    
        root@7d0aed75e582:/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/dcos-service-kafka --topic topic1 --from-beginning
        Hello, World.


See also [Connecting clients][3].

<a name="install-and-customize"></a>
# Install and Customize

## About installing Kafka on Enterprise DC/OS

In Enterprise DC/OS `strict` [security mode](/1.8/administration/installing/custom/configuration-parameters/#security), Kafka requires a service account. In `permissive`, a service account is
optional. Only someone with `superuser` permission can create the service account. Refer to [Provisioning
Kafka](http://docs.mesosphere.com/1.8/administration/id-and-access-mgt/service-auth/kafka-auth/) for instructions.

## Default Installation

To start a basic test cluster with three brokers, run the following command on the DC/OS CLI:

    $ dcos package install kafka


This command creates a new Kafka cluster with the default name `kafka`. Two clusters cannot share the same name, so installing additional clusters beyond the default cluster requires [customizing the `name` at install time][4] for each additional instance.

All `dcos kafka` CLI commands have a `--name` argument allowing the user to specify which Kafka instance to query. If you do not specify a service name, the CLI assumes the default value, `kafka`. The default value for `--name` can be customized via the DC/OS CLI configuration:

    $ dcos kafka --name kafka-dev <cmd>


## Minimal Installation

For development purposes, you may wish to install Kafka on a local DC/OS cluster. For this, you can use [dcos-vagrant][5].

To start a minimal cluster with a single broker, create a JSON options file named `sample-kafka-minimal.json`:

    {
        "brokers": {
            "count": 1,
            "mem": 512,
            "heap": {
                "size": 500
            },                                                                                  
            "disk": 1000
        }
    }


The command below creates a cluster using `sample-kafka-minimal.json`:

    $ dcos package install --options=sample-kafka-minimal.json kafka


## Custom Installation

Customize the defaults by creating a JSON file. Then, pass it to `dcos package install` using the `--options` parameter.

Sample JSON options file named `sample-kafka-custom.json`:

    {
        "service": {
            "name": "sample-kafka-custom",
            "placement_strategy": "NODE"
        },
        "brokers": {
            "count": 10
        },
        "kafka": {
            "delete_topic_enable": true,
            "log_retention_hours": 128
        }
    }


The command below creates a cluster using `sample-kafka.json`:

    $ dcos package install --options=sample-kafka-custom.json kafka


See [Configuration Options][6] for a list of fields that can be customized via an options JSON file when the Kafka cluster is created.

## Multiple Kafka cluster installation

Installing multiple Kafka clusters is identical to installing Kafka clusters with custom configurations as described above. The only requirement on the operator is that a unique `name` is specified for each installation. For example:

    $ cat kafka1.json
    {
        "service": {
            "name": "kafka1"
        }
    }

    $ dcos package install kafka --options=kafka1.json

## Uninstall

Uninstalling a cluster is straightforward. Replace `name` with the name of the kafka instance to be uninstalled.

    $ dcos package uninstall --app-id=<name> kafka

Then, use the [framework cleaner script][7] to remove your Kafka instance from Zookeeper and to destroy all data associated with it. The script requires several arguments, the default values to be used are:

- `framework_role` is `kafka-role`.
- `framework_principal` is `kafka-principal`.
- `zk_path` is `dcos-service-<service-name>`.

These values may vary if you had customized them during installation.

<a name="configuring"></a>
# Configuring

## Changing Configuration at Runtime

You can customize your cluster in-place when it is up and running.

The Kafka scheduler runs as a Marathon process and can be reconfigured by changing values from the DC/OS web interface. These are the general steps to follow:

1.  Go to the `Services` tab of the DC/OS web interface.
2.  Click the name of the Kafka service to be updated.
3.  Within the Kafka instance details view, click the `Edit` button.
4.  In the dialog that appears, expand the `Environment Variables` section and update any field(s) to their desired value(s). For example, to [increase the number of Brokers][8], edit the value for `BROKER_COUNT`. Do not edit the value for `FRAMEWORK_NAME` or `BROKER_DISK` or `PLACEMENT_STRATEGY`.
5.  A `PHASE_STRATEGY` of `STAGE` should also be set. See "Configuration Deployment Strategy" below for more details.
6.  Click `Change and deploy configuration` to apply any changes and cleanly reload the Kafka service scheduler. The Kafka cluster itself will persist across the change.

### Configuration Deployment Strategy

Configuration updates are rolled out through execution of Update Plans. You can configure the way these plans are executed.

### Configuration Update Plans

In brief, "plans" are composed of "phases," which are in turn composed of "blocks." Two possible configuration update strategies specify how the blocks are executed. These strategies are specified by setting the `PHASE_STRATEGY` environment variable on the scheduler. By default, the strategy is `INSTALL`, which rolls changes out to one broker at a time with no pauses.

The alternative is the `STAGE` strategy. This strategy injects two mandatory human decision points into the configuration update process. Initially, no configuration update will take place: the service waits for a human to confirm the update plan is correct. You may then decide to either continue the configuration update through a [REST API][9] call, or roll back the configuration update by replacing the original configuration through the DC/OS web interface in exactly the same way as a configuration update is specified above.

After specifying that an update should continue, one block representing one broker will be updated and the configuration update will again pause. At this point, you have a second opportunity to roll back or continue. If you decide to continue a second time, the rest of the brokers will be updated one at a time until all the brokers are using the new configuration. You may interrupt an update at any point. After interrupting, you can choose to continue or roll back. Consult the "Configuration Update REST API" for these operations.

### Configuration Update REST API

There are two phases in the update plans for Kafka: Mesos task reconciliation and update. Mesos task reconciliation is always executed without need for human interaction.

Make the REST request below to view the current plan. See [REST API authentication][10] for information on how this request must be authenticated.

    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan"
    GET <dcos_url>/service/kafka/v1/plan HTTP/1.1

    {
        "errors": [],
        "phases": [
            {
                "blocks": [
                    {
                        "has_decision_point": false,
                        "id": "7752c4fe-e998-4f30-bfd3-9748fc8c8354",
                        "message": "Reconciliation complete",
                        "name": "Reconciliation",
                        "status": "Complete"
                    }
                ],
                "id": "60e9359e-6c6a-4da2-a06f-e21ee2ea9c77",
                "name": "Reconciliation",
                "status": "Complete"
            },
            {
                "blocks": [
                    {
                        "has_decision_point": false,
                        "id": "918c6019-09af-476d-b0f6-a26f59526bd7",
                        "message": "Broker-0 is Complete",
                        "name": "broker-0",
                        "status": "Complete"
                    },
                    {
                        "has_decision_point": false,
                        "id": "883945bc-87e7-4156-bda9-fca249aef828",
                        "message": "Broker-1 is Complete",
                        "name": "broker-1",
                        "status": "Complete"
                    },
                    {
                        "has_decision_point": false,
                        "id": "17e70549-6401-4128-80ee-a9a5f89e0ff7",
                        "message": "Broker-2 is Complete",
                        "name": "broker-2",
                        "status": "Complete"
                    }
                ],
                "id": "c633dd86-8011-466d-81e6-38560e55dc90",
                "name": "Update to: 85c7946b-6456-463a-85aa-89b05f947a6e",
                "status": "Complete"
            }
        ],
        "status": "Complete"
    }



When using the `STAGE` deployment strategy, an update plan will initially pause without doing any update to ensure the plan is correct. It will look like this:

    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan"
    GET <dcos_url>/service/kafka/v1/plan HTTP/1.1

    {
        "errors": [],
        "phases": [
            {
                "blocks": [
                    {
                        "has_decision_point": false,
                        "id": "8139b860-2011-45fd-acda-738d8b915f30",
                        "message": "Reconciliation complete",
                        "name": "Reconciliation",
                        "status": "Complete"
                    }
                ],
                "id": "7137bceb-67d0-45ef-8fd8-00646967f762",
                "name": "Reconciliation",
                "status": "Complete"
            },
            {
                "blocks": [
                    {
                        "has_decision_point": true,
                        "id": "926fb980-8942-48fb-8eb6-1b63fad4e7e3",
                        "message": "Broker-0 is Pending",
                        "name": "broker-0",
                        "status": "Pending"
                    },
                    {
                        "has_decision_point": true,
                        "id": "60f6dade-bff8-42b5-b4ac-aa6aa6b705a4",
                        "message": "Broker-1 is Pending",
                        "name": "broker-1",
                        "status": "Pending"
                    },
                    {
                        "has_decision_point": false,
                        "id": "c98b9e72-b12b-4b47-9193-776a2cb9ed53",
                        "message": "Broker-2 is Pending",
                        "name": "broker-2",
                        "status": "Pending"
                    }
                ],
                "id": "863ab024-ccb2-4fd8-b182-1ce18858b8e8",
                "name": "Update to: 8902f778-eb2a-4347-94cd-18fcdb557063",
                "status": "Waiting"
            }
        ],
        "status": "Waiting"
    }



**Note:** After a configuration update, you may see an error from Mesos-DNS; this will go away 10 seconds after the update.

Enter the `continue` command to execute the first block:

    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan?cmd=continue"
    PUT <dcos_url>/service/kafka/v1/plan?cmd=continue HTTP/1.1

    {
        "Result": "Received cmd: continue"
    }


After you execute the continue operation, the plan will look like this:

    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan"
    GET <dcos_url>/service/kafka/v1/plan HTTP/1.1

    {
        "errors": [],
        "phases": [
            {
                "blocks": [
                    {
                        "has_decision_point": false,
                        "id": "8139b860-2011-45fd-acda-738d8b915f30",
                        "message": "Reconciliation complete",
                        "name": "Reconciliation",
                        "status": "Complete"
                    }
                ],
                "id": "7137bceb-67d0-45ef-8fd8-00646967f762",
                "name": "Reconciliation",
                "status": "Complete"
            },
            {
                "blocks": [
                    {
                        "has_decision_point": false,
                        "id": "926fb980-8942-48fb-8eb6-1b63fad4e7e3",
                        "message": "Broker-0 is InProgress",
                        "name": "broker-0",
                        "status": "InProgress"
                    },
                    {
                        "has_decision_point": true,
                        "id": "60f6dade-bff8-42b5-b4ac-aa6aa6b705a4",
                        "message": "Broker-1 is Pending",
                        "name": "broker-1",
                        "status": "Pending"
                    },
                    {
                        "has_decision_point": false,
                        "id": "c98b9e72-b12b-4b47-9193-776a2cb9ed53",
                        "message": "Broker-2 is Pending",
                        "name": "broker-2",
                        "status": "Pending"
                    }
                ],
                "id": "863ab024-ccb2-4fd8-b182-1ce18858b8e8",
                "name": "Update to: 8902f778-eb2a-4347-94cd-18fcdb557063",
                "status": "InProgress"
            }
        ],
        "status": "InProgress"
    }



If you enter `continue` a second time, the rest of the plan will be executed without further interruption. If you want to interrupt a configuration update that is in progress, enter the `interrupt` command:

    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN"  "<dcos_url>/service/kafka/v1/plan?cmd=interrupt"
    PUT <dcos_url>/service/kafka/v1/plan?cmd=interrupt HTTP/1.1

    {
        "Result": "Received cmd: interrupt"
    }

**Note:** The interrupt command can’t stop a block that is `InProgress`, but it will stop the change on the subsequent blocks.

## Configuration Options

The following describes the most commonly used features of the Kafka service and how to configure them via the DC/OS CLI and from the DC/OS web interface. View the [default `config.json` in DC/OS Universe][11] to see all possible configuration options.

### Service Name

The name of this Kafka instance in DC/OS. This is an option that cannot be changed once the Kafka cluster is started: it can only be configured via the DC/OS CLI `--options` flag when the Kafka instance is created.

*   **In DC/OS CLI options.json**: `name` = string (default: `kafka`)
*   **DC/OS web interface**: The service name cannot be changed after the cluster has started.

### Broker Count

Configure the number of brokers running in a given Kafka cluster. The default count at installation is three brokers. This number may be increased, but not decreased, after installation.

*   **In DC/OS CLI options.json**: `broker-count` = integer (default: `3`)
*   **DC/OS web interface**: `BROKER_COUNT` = `integer`

### Broker Port

Configure the port number that the brokers listen on. If the port is set to a particular value, this will be the port used by all brokers. The default port is 9092.  Note that this requires that `placement-strategy` be set to `NODE` to take effect, since having every broker listening on the same port requires that they be placed on different hosts. Setting the port to 0 indicates that each Broker should have a random port in the 9092-10092 range. 

*   **In DC/OS CLI options.json**: `broker-port` = integer (default: `9092`)
*   **DC/OS web interface**: `BROKER_PORT` = `integer`

### Configure Broker Placement Strategy

`ANY` allows brokers to be placed on any node with sufficient resources, while `NODE` ensures that all brokers within a given Kafka cluster are never colocated on the same node. This is an option that cannot be changed once the Kafka cluster is started: it can only be configured via the DC/OS CLI `--options` flag when the Kafka instance is created.

*   **In DC/OS CLI options.json**: `placement-strategy` = `ANY` or `NODE` (default: `ANY`)
*   **DC/OS web interface**: `PLACEMENT_STRATEGY` = `ANY` or `NODE`

### Configure Kafka Broker Properties

Kafka Brokers are configured through settings in a server.properties file deployed with each Broker. The settings here can be specified at installation time or during a post-deployment configuration update. They are set in the DC/OS Universe's config.json as options such as:

    "log_retention_hours": {
        "title": "log.retention.hours",
        "description": "Override log.retention.hours: The number of hours to keep a log file before deleting it (in hours), tertiary to log.retention.ms property",
        "type": "integer",
        "default": 168
    },

The defaults can be overridden at install time by specifying an options.json file with a format like this:

    {
        "kafka": {
            "log_retention_hours": 100
        }
    }

These same values are also represented as environment variables for the scheduler in the form `KAFKA_OVERRIDE_LOG_RETENTION_HOURS` and may be modified through the DC/OS web interface and deployed during a rolling upgrade as [described here][12].

<a name="disk-type"></a>
### Disk Type 

The type of disks that can be used for storing broker data are: `ROOT` (default) and `MOUNT`.  The type of disk may only be specified at install time.

* `ROOT`: Broker data is stored on the same volume as the agent work directory. Broker tasks will use the configured amount of disk space.
* `MOUNT`: Broker data will be stored on a dedicated volume attached to the agent. Dedicated MOUNT volumes have performance advantages and a disk error on these MOUNT volumes will be correctly reported to Kafka.

Configure Kafka service to use dedicated disk volumes:
* **DC/OS cli options.json**: 
    
```json
    {
        "brokers": {
            "disk_type": "MOUNT"
        }
    }
```

* **DC/OS web interface**: Set the environment variable `DISK_TYPE` = `MOUNT`

When configured to `MOUNT` disk type, the scheduler selects a disk on an agent whose capacity is equal to or greater than the configured `disk` value.

### JVM Heap Size

Kafka service allows configuration of JVM Heap Size for the broker JVM process. To configure it:
* **DC/OS cli options.json**:

```json
    {
        "brokers": {
            "heap": {
                "size": 2000
            }
        }
    }
```

* **DC/OS web interface**: Set the environment variable `BROKER_HEAP_MB` = `2000`

**Note**: The total memory allocated for the Mesos task is specified by the `BROKER_MEM` configuration parameter. The value for `BROKER_HEAP_MB` should not be greater than `BROKER_MEM` value. Also, if `BROKER_MEM` is greater than `BROKER_HEAP_MB`, then the Linux operating system will use `BROKER_MEM` - `BROKER_HEAP_MB` for [PageCache](https://en.wikipedia.org/wiki/Page_cache).

### Alternate Zookeeper 

By default the Kafka framework uses the Zookeeper ensemble made available on the Mesos masters of a DC/OS cluster. You can configure an alternate Zookeeper installation at install time.

To configure it:
* **DC/OS CLI options.json**:

```json
    {
        "kafka": {
            "kafka_zookeeper_uri": "zookeeper.marathon.mesos:2181"
        }
    }
```

This configuration option may not be changed after installation.

### Recovery and Health Checks

You can enable automated replacement of brokers and configure the circumstances under which they are replaced.

#### Enable Broker Replacement

To enable automated replacement:

* **DC/OS CLI options.json**:

```json
    {
        "enable_replacement":{
            "description":"Enable automated replacement of Brokers. WARNING: May cause data loss. See documentation.",
            "type":"boolean",
            "default":false
        }
    }
```

* **DC/OS web interface**: Set the environment variable `ENABLE_REPLACEMENT` = `true` to enable replacement.

**Warning:** The replacement mechanism has no way of knowing if the broker it is destructively replacing had the last copy of a given item of data. So depending on a user's specified replication policy and the degree and duration of the permanent failures the system has encountered, data loss is a possibility.

The following configuration options control the circumastances under which a broker is replaced.

#### Minumum Grace Period

Configure the minimum amount of time before a broker should be replaced:

* **DC/OS CLI options.json**:

```json
    {   
        "recover_in_place_grace_period_secs":{
            "description":"The minimum amount of time (in minutes) which must pass before a Broker may be destructively replaced.",
            "type":"number",
            "default":1200
        }
    }
```

* **DC/OS web interface**: Set the environment variable `RECOVERY_GRACE_PERIOD_SEC` = `1200`

#### Minumum Delay Between Replacements

Configure the minimum amount of time between broker replacements.

```json
    {
        "min_delay_between_recovers_secs":{
            "description":"The minimum amount of time (in seconds) which must pass between destructive replacements of Brokers.",
            "type":"number",
            "default":600
        }
    }
```

* **DC/OS web interface**: Set the environment variable `REPLACE_DELAY_SEC` = `600`

The following configurations control the health checks that determine when a broker has failed:

#### Enable Health Check

Enable health checks on brokers:

```json
    {
        "enable_health_check":{
            "description":"Enable automated detection of Broker failures which did not result in a Broker process exit.",
            "type":"boolean",
            "default":true
        }
    }
```

* **DC/OS web interface**: Set the environment variable `ENABLE_BROKER_HEALTH_CHECK` = `true`

#### Health Check Delay

Set the amount of time before the health check begins:

```json
    {
        "health_check_delay_sec":{
            "description":"The period of time (in seconds) waited before the health-check begins execution.",
            "type":"number",
            "default":15
        }
    }
```

* **DC/OS web interface**: Set the environment variable `BROKER_HEALTH_CHECK_DELAY_SEC` = `15`

#### Health Check Interval

Set the interval between health checks:

```json
    {
        "health_check_interval_sec":{
            "description":"The period of time (in seconds) between health-check executions.",
            "type":"number",
            "default":10
        }
    }
```

* **DC/OS web interface**: Set the environment variable `BROKER_HEALTH_CHECK_INTERVAL_SEC` = `10`

#### Health Check Timeout

Set the time a health check can take to complete before it is considered a failed check:
```json
    {
        "health_check_timeout_sec":{
            "description":"The duration (in seconds) allowed for a health-check to complete before it is considered a failure.",
            "type":"number",
            "default":20
        }
    }
```

* **DC/OS web interface**: Set the environment variable `BROKER_HEALTH_CHECK_TIMEOUT_SEC` = `20`

#### Health Check Grace Period

Set the amount of time after the delay before health check failures count toward the maximum number of consecutive failures:

```json
    {
        "health_check_grace_period_sec":{
            "description":"The period of time after the delay (in seconds) before health-check failures count towards the maximum consecutive failures.",
            "type":"number",
            "default":10
        }
    }
```

* **DC/OS web interface**: Set the environment variable `BROKER_HEALTH_CHECK_GRACE_SEC` = `10`

#### Maximum Consecutive Health Check Failures

```json
    {
        "health_check_max_consecutive_failures":{
            "description":"The the number of consecutive failures which cause a Broker process to exit.",
            "type":"number",
            "default":3
        }
    }
```

* **DC/OS web interface**: Set the environment variable `BROKER_HEALTH_CHECK_MAX_FAILURES` = `3`

<a name="connecting-clients"></a>
# Connecting Clients

The only supported client library is the official Kafka Java library, ie `org.apache.kafka.clients.consumer.KafkaConsumer` and `org.apache.kafka.clients.producer.KafkaProducer`. Other clients are at the user's risk.

## Using the DC/OS CLI

The following command can be executed from the CLI in order to retrieve a set of brokers to connect to.

    dcos kafka --name=<name> connection

## Using the REST API

The following `curl` example demonstrates how to retrive connection a set of brokers to connect to using the REST API. See [REST API authentication][10] for information on how this request must be authenticated.

    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/connection"

## Using the REST API

The following `curl` example demonstrates how to retrieve a set of brokers to connect to.
 

    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/connection"

### Connection Info Response

The response, for both the CLI and the REST API is as below.

    {
            "address": [
                "10.0.0.211:9843",
                "10.0.0.217:10056",
                "10.0.0.214:9689"
            ],
            "dns": [
                "broker-0.kafka.mesos:9843",
                "broker-1.kafka.mesos:10056",
                "broker-2.kafka.mesos:9689"
            ],
            "vip": "broker.kafka.l4lb.thisdcos.directory:9092",
            "zookeeper": "master.mesos:2181/kafka"
        }


This JSON array contains a list of valid brokers that the client can use to connect to the Kafka cluster. For availability reasons, it is best to specify multiple brokers in configuration of the client. Use the VIP to address any one of the Kafka brokers in the cluster. [Learn more about load balancing and VIPs in DC/OS](https://dcos.io/docs/1.8/usage/service-discovery/load-balancing-vips/).

## Configuring the Kafka Client Library

### Adding the Kafka Client Library to Your Application

    <dependency>
      <groupId>org.apache.kafka</groupId>
      <artifactId>kafka-clients</artifactId>
      <version>0.9.0.1</version>
    </dependency>


The above is the correct dependency for the Kafka Client Library to use with the DC/OS Cassandra service. After adding this dependency to your project, you should have access to the correct binary dependencies to interface with the Kafka Cluster.

### Connecting the Kafka Client Library

The code snippet below demonstrates how to connect a Kafka Producer to the cluster and perform a loop of simple insertions.

    import org.apache.kafka.clients.producer.KafkaProducer;
    import org.apache.kafka.clients.producer.ProducerRecord;
    import org.apache.kafka.common.serialization.ByteArraySerializer;

    Map<String, Object> producerConfig = new HashMap<>();
    producerConfig.put("bootstrap.servers", "10.0.0.211:9843,10.0.0.217:10056,10.0.0.214:9689");
    // optional:
    producerConfig.put("metadata.fetch.timeout.ms": "3000");
    producerConfig.put("request.timeout.ms", "3000");
    // ... other options: http://kafka.apache.org/documentation.html#producerconfigs
    ByteArraySerializer serializer = new ByteArraySerializer();
    KafkaProducer<byte[], byte[]> kafkaProducer = new KafkaProducer<>(producerConfig, serializer, serializer);

    byte[] message = new byte[1024];
    for (int i = 0; i < message.length; ++i) {
      if (i % 2 == 0) {
        message[i] = 'x';
      } else {
        message[i] = 'o';
      }
    }
    ProducerRecord<byte[], byte[]> record = new ProducerRecord<>("test-topic", message);
    while (true) {
      kafkaProducer.send(record).get();
      Thread.sleep(1000);
    }


The code snippet below demonstrates how to connect a Kafka Consumer to the cluster and perform a simple retrievals.

    import org.apache.kafka.clients.consumer.ConsumerRecord;
    import org.apache.kafka.clients.consumer.ConsumerRecords;
    import org.apache.kafka.clients.consumer.KafkaConsumer;
    import org.apache.kafka.common.serialization.ByteArrayDeserializer;

    Map<String, Object> consumerConfig = new HashMap<>();
    consumerConfig.put("bootstrap.servers", "10.0.0.211:9843,10.0.0.217:10056,10.0.0.214:9689");
    // optional:
    consumerConfig.put("group.id", "test-client-consumer")
    // ... other options: http://kafka.apache.org/documentation.html#consumerconfigs
    ByteArrayDeserializer deserializer = new ByteArrayDeserializer();
    KafkaConsumer<byte[], byte[]> kafkaConsumer = new KafkaConsumer<>(consumerConfig, deserializer, deserializer);

    List<String> topics = new ArrayList<>();
    topics.add("test-topic");
    kafkaConsumer.subscribe(topics);
    while (true) {
      ConsumerRecords<byte[], byte[]> records = kafkaConsumer.poll(1000);
      int bytes = 0;
      for (ConsumerRecord<byte[], byte[]> record : records) {
        if (record.key() != null) {
          bytes += record.key().length;
        }
        bytes += record.value().length;
      }
      System.out.println(String.format("Got %d messages (%d bytes)", records.count(), bytes));
    }
    kafkaConsumer.close();


## Configuring the Kafka Test Scripts

The following code connects to a DC/OS-hosted Kafka instance using `bin/kafka-console-producer.sh` and `bin/kafka-console-consumer.sh` as an example:

    $ dcos kafka connection
    {
        "address": [
            "10.0.0.211:9843",
            "10.0.0.217:10056",
            "10.0.0.214:9689"
        ],
        "dns": [
            "broker-0.kafka.mesos:9843",
            "broker-1.kafka.mesos:10056",
            "broker-2.kafka.mesos:9689"
        ],
        "vip": "broker.kafka.l4lb.thisdcos.directory:9092",
        "zookeeper": "master.mesos:2181/kafka"
    }

    $ dcos node ssh --master-proxy --leader

    core@ip-10-0-6-153 ~ $ docker run -it mesosphere/kafka-client

    root@7d0aed75e582:/bin# echo "Hello, World." | ./kafka-console-producer.sh --broker-list 10.0.0.211:9843, 10.0.0.217:10056, 10.0.0.214:9689 --topic topic1

    root@7d0aed75e582:/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/kafka --topic topic1 --from-beginning
    Hello, World.

<a name="managing"></a>
# Managing

## Add a Broker

Increase the `BROKER_COUNT` value via the DC/OS web interface as in any other configuration update.

## Upgrade Software

1.  In the DC/OS web interface, destroy the Kafka scheduler to be updated.

2.  Verify that you no longer see it in the DC/OS web interface.

3.  If you are using the enterprise edition, create an JSON options file with your latest configuration and set your plan strategy to "STAGE"

        {
            "service": {
                "phase_strategy": "STAGE"
            }
        }


1.  Install the latest version of Kafka:

        $ dcos package install kafka -—options=options.json


1.  Roll out the new version of Kafka in the same way as a configuration update is rolled out. See Configuration Update Plans.

<a name="troubleshooting"></a>
# Troubleshooting

The Kafka service will be listed as "Unhealthy" when it detects any underreplicated partitions. This error condition usually indicates a malfunctioning broker. Use the `dcos kafka topic under_replicated_partitions` and `dcos kafka topic describe <topic-name>` commands to find the problem broker and determine what actions are required.

Possible repair actions include `dcos kafka broker restart <broker-id>` and `dcos kafka broker replace <broker-id>`. The replace operation is destructive and will irrevocably lose all data associated with the broker. The restart operation is not destructive and indicates an attempt to restart a broker process.

## Configuration Update Errors

The `errors` field below indicates the changes needed to create a valid configuration:

```
$ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan"
GET /service/kafka/v1/plan HTTP/1.1

{
    "errors": [
        "Validation error on field \"BROKER_COUNT\": Decreasing this value (from 3 to 2) is not supported."
    ],
    "phases": [
        {
            "blocks": [
                {
                    "has_decision_point": false,
                    "id": "e56d2e4a-e05b-42ad-b4a0-d74b68d206af",
                    "message": "Reconciliation complete",
                    "name": "Reconciliation",
                    "status": "Complete"
                }
            ],
            "id": "c26bec40-3290-4501-b3da-945d0abef55f",
            "name": "Reconciliation",
            "status": "Complete"
        },
        {
            "blocks": [
                {
                    "has_decision_point": false,
                    "id": "d4e72ee8-4608-423a-9566-1632ff0ab211",
                    "message": "Broker-0 is Complete",
                    "name": "broker-0",
                    "status": "Complete"
                },
                {
                    "has_decision_point": false,
                    "id": "3ea30deb-9660-42f1-ad23-bd418d718999",
                    "message": "Broker-1 is Complete",
                    "name": "broker-1",
                    "status": "Complete"
                },
                {
                    "has_decision_point": false,
                    "id": "4da21440-de73-4772-9c85-877f2677e62a",
                    "message": "Broker-2 is Complete",
                    "name": "broker-2",
                    "status": "Complete"
                }
            ],
            "id": "226a780e-132f-4fea-b584-7712b07cf357",
            "name": "Update to: 72cecf77-dbc5-4ae6-8f91-c88702b9a6a8",
            "status": "Complete"
        }
    ],
    "status": "Error"
}
```

## Replacing a Permanently Failed Server

If a machine has permanently failed, manual intervention is required to replace the broker or brokers that resided on that machine. Because DC/OS Kafka uses persistent volumes, the service continuously attempts to replace brokers where their data has been persisted. In the case where a machine has permanently failed, use the Kafka CLI to replace the brokers.

In the example below, the broker with id `0` will be replaced on new machine as long as cluster resources are sufficient to satisfy the service’s placement constraints and resource requirements.

    $ dcos kafka broker replace 0

<a name="api-reference"></a>
# REST API Reference

For ongoing maintenance of the Kafka cluster itself, the Kafka service exposes an HTTP API whose structure is designed to roughly match the tools provided by the Kafka distribution, such as `bin/kafka-topics.sh`.

The examples here provide equivalent commands using both the [DC/OS CLI](https://github.com/mesosphere/dcos-cli) (with the `kafka` CLI module installed) and `curl`. These examples assume a service named `kafka` (the default), and the `curl` examples assume a DC/OS cluster path of `<dcos_url>`. Replace these with appropriate values as needed.

The `dcos kafka` CLI commands have a `--name` argument, allowing the user to specify which Kafka instance to query. The value defaults to `kafka`, so it's technically redundant to specify `--name=kafka` in these examples.

Depending on your version of DC/OS and the configuration of your cluster, you may need to authenticate your REST calls. See [REST API Authentication](#rest-api-authentication) for more information.

<a name="rest-api-authentication"></a>
## REST API Authentication

Depending on how the cluster is configured, commands using the REST API may need to be authenticated. These instructions only apply to interacting with the Kafka REST API directly. Access the underlying Kafka Brokers themselves with the standard Kafka APIs.

If your DC/OS Enterprise installation requires encryption, you must also use the `ca-cert` flag when making REST calls. Refer to [Obtaining and passing the DC/OS
certificate in cURL requests](https://docs.mesosphere.com/1.8/administration/tls-ssl/#get-dcos-cert) for information on how to use the `--cacert` flag. [If encryption is not
required](https://docs.mesosphere.com/1.8/administration/tls-ssl/), you can omit the --cacert flags.

All `curl` examples in this document assume that an auth token has already been retrieved using one of the following methods and stored in an environment variable named `AUTH_TOKEN`. See the following documentation for how to retrieve this token from the authentication service.

#### User token authentication

DC/OS Enterprise Edition comes with support for [user ACLs][13]. To interact with the Kafka REST API you must first retrieve an auth token from the [auth HTTP endpoint][14], then provide this token in following requests.

First, we retrieve `uSeR_t0k3n` with our user credentials and store the token as an environment variable:

    $ curl --data '{"uid":"username", "password":"password"}' -H "Content-Type:application/json" "$DCOS_URI/acs/api/v1/auth/login"
    POST /acs/api/v1/auth/login HTTP/1.1

    {
      "token": "uSeR_t0k3n"
    }

    $ export AUTH_TOKEN=uSeR_t0k3n


Then, use this token to authenticate requests to the Kafka Service:

    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/connection"
    GET /service/kafka/v1/connection HTTP/1.1

    {
        "address": [
            "10.0.0.211:9843",
            "10.0.0.217:10056",
            "10.0.0.214:9689"
        ],
        "dns": [
            "broker-0.kafka.mesos:9843",
            "broker-1.kafka.mesos:10056",
            "broker-2.kafka.mesos:9689"
        ],
        "vip": "broker.kafka.l4lb.thisdcos.directory:9092",
        "zookeeper": "master.mesos:2181/kafka"
    }


## Connection Information

Kafka comes with many useful tools of its own that often require either Zookeeper connection information or the list of broker endpoints. This information can be retrieved in an easily consumable format from the `/connection` endpoint:

    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/connection"
    GET /service/kafka/v1/connection HTTP/1.1

    {
        "address": [
            "10.0.0.211:9843",
            "10.0.0.217:10056",
            "10.0.0.214:9689"
        ],
        "dns": [
            "broker-0.kafka.mesos:9843",
            "broker-1.kafka.mesos:10056",
            "broker-2.kafka.mesos:9689"
        ],
        "vip": "broker.kafka.l4lb.thisdcos.directory:9092",
        "zookeeper": "master.mesos:2181/kafka"
    }


The same information can be retrieved through the DC/OS CLI:

    $ dcos kafka connection
    {
        "address": [
            "10.0.0.211:9843",
            "10.0.0.217:10056",
            "10.0.0.214:9689"
        ],
        "dns": [
            "broker-0.kafka.mesos:9843",
            "broker-1.kafka.mesos:10056",
            "broker-2.kafka.mesos:9689"
        ],
        "vip": "broker.kafka.l4lb.thisdcos.directory:9092",
        "zookeeper": "master.mesos:2181/kafka"
    }


## Broker Operations

### Add Broker

Increase the `BROKER_COUNT` value via the DC/OS web interface. This should be rolled as in any other configuration update.

### List All Brokers

    $ dcos kafka --name=kafka broker list
    {
        "brokers": [
            "0",
            "1",
            "2"
        ]
    }


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/brokers"
    GET /service/kafka/v1/brokers HTTP/1.1

    {
        "brokers": [
            "0",
            "1",
            "2"
        ]
    }


### Restart Single Broker

Restarts the broker in-place.

    $ dcos kafka --name=kafka broker restart 0
    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]


    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/brokers/0"
    PUT /service/kafka/v1/brokers/0 HTTP/1.1

    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]


### Replace Single Broker

Restarts the broker and replaces its existing resource/volume allocations. The new broker instance may also be placed on a different machine.

    $ dcos kafka --name=kafka broker replace 0
    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]


    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/brokers/0?replace=true"
    PUT /service/kafka/v1/brokers/0 HTTP/1.1

    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]


## Topic Operations

These operations mirror what is available with `bin/kafka-topics.sh`.

### List Topics

    $ dcos kafka --name=kafka topic list
    [
        "topic1",
        "topic0"
    ]


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/topics"
    GET /service/kafka/v1/topics HTTP/1.1

    [
        "topic1",
        "topic0"
    ]

### Describe Topic

    $ dcos kafka --name=kafka topic describe topic1
    {
        "partitions": [
            {
                "0": {
                    "controller_epoch": 1,
                    "isr": [
                        0,
                        1,
                        2
                    ],
                    "leader": 0,
                    "leader_epoch": 0,
                    "version": 1
                }
            },
            {
                "1": {
                    "controller_epoch": 1,
                    "isr": [
                        1,
                        2,
                        0
                    ],
                    "leader": 1,
                    "leader_epoch": 0,
                    "version": 1
                }
            },
            {
                "2": {
                    "controller_epoch": 1,
                    "isr": [
                        2,
                        0,
                        1
                    ],
                    "leader": 2,
                    "leader_epoch": 0,
                    "version": 1
                }
            }
        ]
    }


    $ curl -X POST -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/topics/topic1"
    GET /service/kafka/v1/topics/topic1 HTTP/1.1

    {
        "partitions": [
            {
                "0": {
                    "controller_epoch": 1,
                    "isr": [
                        0,
                        1,
                        2
                    ],
                    "leader": 0,
                    "leader_epoch": 0,
                    "version": 1
                }
            },
            {
                "1": {
                    "controller_epoch": 1,
                    "isr": [
                        1,
                        2,
                        0
                    ],
                    "leader": 1,
                    "leader_epoch": 0,
                    "version": 1
                }
            },
            {
                "2": {
                    "controller_epoch": 1,
                    "isr": [
                        2,
                        0,
                        1
                    ],
                    "leader": 2,
                    "leader_epoch": 0,
                    "version": 1
                }
            }
        ]
    }


### Create Topic

    $ dcos kafka --name=kafka topic create topic1 --partitions=3 --replication=3
    {
        "message": "Output: Created topic \"topic1\".\n"
    }


    $ curl -X POST -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/topics?name=topic1&partitions=3&replication=3"
    POST /service/kafka/v1/topics?replication=3&name=topic1&partitions=3 HTTP/1.1

    {
        "message": "Output: Created topic \"topic1\".\n"
    }


### View Topic Offsets

There is an optional `--time` parameter which may be set to either "first", "last", or a timestamp in milliseconds as [described in the Kafka documentation][15].

    $ dcos kafka --name=kafka topic offsets topic1 --time=last
    [
        {
            "2": "334"
        },
        {
            "1": "333"
        },
        {
            "0": "333"
        }
    ]


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/topics/topic1/offsets?time=last"
    GET /service/kafka/v1/topics/topic1/offsets?time=last HTTP/1.1

    [
        {
            "2": "334"
        },
        {
            "1": "333"
        },
        {
            "0": "333"
        }
    ]


### Alter Topic Partition Count

    $ dcos kafka --name=kafka topic partitions topic1 2
    {
        "message": "Output: WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affected\nAdding partitions succeeded!\n"
    }


    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/topics/topic1?operation=partitions&partitions=2"
    PUT /service/kafka/v1/topics/topic1?operation=partitions&partitions=2 HTTP/1.1

    {
        "message": "Output: WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affected\nAdding partitions succeeded!\n"
    }


### Run Producer Test on Topic

    $ dcos kafka --name=kafka topic producer_test topic1 10

    {
        "message": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.\n"
    }


    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/topics/topic1?operation=producer-test&messages=10"
    PUT /service/kafka/v1/topics/topic1?operation=producer-test&messages=10 HTTP/1.1

    {
        "message": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.\n"
    }

Runs the equivalent of the following command from the machine running the Kafka Scheduler:

    kafka-producer-perf-test.sh \
        --topic <topic> \
        --num-records <messages> \
        --throughput 100000 \
        --record-size 1024 \
        --producer-props bootstrap.servers=<current broker endpoints>

### Delete Topic

    $ dcos kafka --name=kafka topic delete topic1

    {
        "message": "Topic topic1 is marked for deletion.\nNote: This will have no impact if delete.topic.enable is not set to true.\n"
    }


    $ curl -X DELETE -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/topics/topic1"
    DELETE /service/kafka/v1/topics/topic1 HTTP/1.1

    {
        "message": "Topic topic1 is marked for deletion.\nNote: This will have no impact if delete.topic.enable is not set to true.\n"
    }


Note the warning in the output from the commands above. You can change the indicated "delete.topic.enable" configuration value as a configuration change.

### List Under Replicated Partitions

    $ dcos kafka --name=kafka topic under_replicated_partitions

    {
        "message": ""
    }


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/topics/under_replicated_partitions"
    GET /service/kafka/v1/topics/under_replicated_partitions HTTP/1.1

    {
        "message": ""
    }


### List Unavailable Partitions

    $ dcos kafka --name=kafka topic unavailable_partitions

    {
        "message": ""
    }


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/topics/unavailable_partitions"
    GET /service/kafka/v1/topics/unavailable_partitions HTTP/1.1

    {
        "message": ""
    }


## Config History

These operations relate to viewing the service's configuration history.

### List Configuration IDs

    $ dcos kafka --name=kafka config list

    [
        "319ebe89-42e2-40e2-9169-8568e2421023",
        "294235f2-8504-4194-b43d-664443f2132b"
    ]


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/configurations"
    GET /service/kafka/v1/configurations HTTP/1.1

    [
        "319ebe89-42e2-40e2-9169-8568e2421023",
        "294235f2-8504-4194-b43d-664443f2132b"
    ]

### Describe Configuration

This configuration shows a default per-broker memory allocation of 2048 (configured via the `BROKER_MEM` parameter):

    $ dcos kafka --name=kafka config show 319ebe89-42e2-40e2-9169-8568e2421023

    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "disk_type": "ROOT",
            "java_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafka_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 2048, // <<--
            "overrider_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phase_strategy": "INSTALL",
            "placement_strategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/configurations/319ebe89-42e2-40e2-9169-8568e2421023"
    GET /service/kafka/v1/configurations/319ebe89-42e2-40e2-9169-8568e2421023 HTTP/1.1

    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "disk_type": "ROOT",
            "java_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafka_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 2048, // <<--
            "overrider_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phase_strategy": "INSTALL",
            "placement_strategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }

### Describe Target Configuration

The target configuration, meanwhile, shows an increase of configured per-broker memory from 2048 to 4096 (again, configured as `BROKER_MEM`):

    $ dcos kafka --name=kafka config target

    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "disk_type": "ROOT",
            "java_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafka_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 4096, // <<--
            "overrider_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phase_strategy": "INSTALL",
            "placement_strategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/configurations/target"
    GET /service/kafka/v1/configurations/target HTTP/1.1

    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "disk_type": "ROOT",
            "java_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafka_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 4096, // <<--
            "overrider_uri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phase_strategy": "INSTALL",
            "placement_strategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }

## Config Updates

These options relate to viewing and controlling rollouts and configuration updates.

### View Plan Status

Displays all Phases and Blocks in the service Plan. If a rollout is currently in progress, this returns a 503 HTTP code with response content otherwise unchanged.

    $ dcos kafka --name=kafka plan show
    GET /service/kafka/v1/plan HTTP/1.1

    {
        "errors": [],
        "phases": [
            {
                "blocks": [
                    {
                        "has_decision_point": false,
                        "id": "019eaab4-4082-4e38-ab01-a5d2a825cf8d",
                        "message": "Reconciliation complete",
                        "name": "Reconciliation",
                        "status": "Complete"
                    }
                ],
                "id": "a58d1e15-15b8-47a3-84d7-ae36b13a5ba8",
                "name": "Reconciliation",
                "status": "Complete"
            },
            {
                "blocks": [
                    {
                        "has_decision_point": false,
                        "id": "8a727290-062a-44bf-87ba-3ebfe005aa18",
                        "message": "Broker-0 is Complete",
                        "name": "broker-0",
                        "status": "Complete"
                    },
                    {
                        "has_decision_point": false,
                        "id": "5d2aa9d9-5eda-4a13-aea1-9d98e4cf7ea7",
                        "message": "Broker-1 is Complete",
                        "name": "broker-1",
                        "status": "Complete"
                    },
                    {
                        "has_decision_point": false,
                        "id": "7f6762b6-7a51-4df2-bb4d-b82b75623938",
                        "message": "Broker-2 is Complete",
                        "name": "broker-2",
                        "status": "Complete"
                    }
                ],
                "id": "e442fd2e-8f6b-4ddb-ac9f-78cd1da2c422",
                "name": "Update to: d5c33781-a2b8-4426-86d7-3c6e23d89633",
                "status": "Complete"
            }
        ],
        "status": "Complete"
    }


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan"
    GET /service/kafka/v1/plan HTTP/1.1

    {
        "errors": [],
        "phases": [
            {
                "blocks": [
                    {
                        "has_decision_point": false,
                        "id": "019eaab4-4082-4e38-ab01-a5d2a825cf8d",
                        "message": "Reconciliation complete",
                        "name": "Reconciliation",
                        "status": "Complete"
                    }
                ],
                "id": "a58d1e15-15b8-47a3-84d7-ae36b13a5ba8",
                "name": "Reconciliation",
                "status": "Complete"
            },
            {
                "blocks": [
                    {
                        "has_decision_point": false,
                        "id": "8a727290-062a-44bf-87ba-3ebfe005aa18",
                        "message": "Broker-0 is Complete",
                        "name": "broker-0",
                        "status": "Complete"
                    },
                    {
                        "has_decision_point": false,
                        "id": "5d2aa9d9-5eda-4a13-aea1-9d98e4cf7ea7",
                        "message": "Broker-1 is Complete",
                        "name": "broker-1",
                        "status": "Complete"
                    },
                    {
                        "has_decision_point": false,
                        "id": "7f6762b6-7a51-4df2-bb4d-b82b75623938",
                        "message": "Broker-2 is Complete",
                        "name": "broker-2",
                        "status": "Complete"
                    }
                ],
                "id": "e442fd2e-8f6b-4ddb-ac9f-78cd1da2c422",
                "name": "Update to: d5c33781-a2b8-4426-86d7-3c6e23d89633",
                "status": "Complete"
            }
        ],
        "status": "Complete"
    }

### View Active Plan Entries

When a configuration change is in progresss, this command shows the Block/Phase/Stage which are currently active.

    $ dcos kafka --name=kafka plan active

    {
        "block": {
            "has_decision_point": false,
            "id": "498d30f5-248d-4e8d-a857-305d3d709c83",
            "message": "Broker-0 is InProgress",
            "name": "broker-0",
            "status": "InProgress"
        },
        "phase": {
            "block_count": 3,
            "id": "0d3cb059-d94b-4d73-8c8a-586ba3f4c75f",
            "name": "Update to: 5e56ff27-94c3-415d-aae8-a24f6ed5b42d",
            "status": "InProgress"
        },
        "stage": {
            "errors": [],
            "phase_count": 2,
            "status": "InProgress"
        }
    }


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan/status"
    GET /service/kafka/v1/plan/status HTTP/1.1

    {
        "block": {
            "has_decision_point": false,
            "id": "498d30f5-248d-4e8d-a857-305d3d709c83",
            "message": "Broker-0 is InProgress",
            "name": "broker-0",
            "status": "InProgress"
        },
        "phase": {
            "block_count": 3,
            "id": "0d3cb059-d94b-4d73-8c8a-586ba3f4c75f",
            "name": "Update to: 5e56ff27-94c3-415d-aae8-a24f6ed5b42d",
            "status": "InProgress"
        },
        "stage": {
            "errors": [],
            "phase_count": 2,
            "status": "InProgress"
        }
    }

If no upgrade is in progress, then the `block` and `phase` entries are omitted and the `stage` is shown as `Complete`.

    $ dcos kafka --name=kafka plan active

    {
        "stage": {
            "errors": [],
            "phase_count": 2,
            "status": "Complete"
        }
    }


    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan/status"
    GET /service/kafka/v1/plan/status HTTP/1.1

    {
        "stage": {
            "errors": [],
            "phase_count": 2,
            "status": "Complete"
        }
    }

### Upgrade Interaction

These operations are only applicable when `PHASE_STRATEGY` is set to `STAGE`, they have no effect when it is set to `INSTALL`. See [Changing Configuration at Runtime](#changing-configuration-at-runtime) for more information.

#### Continue

    $ dcos kafka --name=kafka plan continue
    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan/continue"

#### Interrupt

    $ dcos kafka --name=kafka plan interrupt
    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan/interrupt"

#### Force Complete

    $ dcos kafka --name=kafka plan force
    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan/forceComplete"

#### Restart

    $ dcos kafka --name=kafka plan restart
    $ curl -H "Authorization: token=$AUTH_TOKEN" "<dcos_url>/service/kafka/v1/plan/restart"

<a name="limitations"></a>
# Limitations

## Configurations

The "disk" configuration value is denominated in MB. We recommend you set the configuration value `log_retention_bytes` to a value smaller than the indicated "disk" configuration. See [instructions for customizing these values][16].

## Managing Configurations Outside of the Service

The Kafka service's core responsibility is to deploy and maintain the deployment of a Kafka cluster whose configuration has been specified. In order to do this the service makes the assumption that it has ownership of broker configuration. If an end-user makes modifications to individual brokers through out-of-band configuration operations, the service will almost certainly override those modifications at a later time. If a broker crashes, it will be restarted with the configuration known to the scheduler, not one modified out-of-band. If a configuration update is initiated, all out-of-band modifications will be overwritten during the rolling update.

## Brokers

The number of deployable brokers is constrained by two factors. First, brokers have specified required resources, so brokers may not be placed if the DC/OS cluster lacks the requisite resources. Second, the specified "PLACEMENT_STRATEGY" environment variable may affect how many brokers can be created in a Kafka cluster. By default the value is "ANY," so brokers are placed anywhere and are only constrained by the resources of the cluster. A second option is "NODE." In this case only one broker may be placed on a given DC/OS agent.

## Security

The security features introduced in Apache Kafka 0.9 are not supported at this time.

 [1]: http://kafka.apache.org/documentation.html
 [2]: https://docs.mesosphere.com/manage-service/spark
 [3]: #connecting-clients
 [4]: #custom-install-configuration
 [5]: https://github.com/mesosphere/dcos-vagrant
 [6]: #configuration-options
 [7]: https://docs.mesosphere.com/1.8/usage/managing-services/uninstall/#framework-cleaner 
 [8]: #broker-count
 [9]: #using-the-rest-api
 [10]: #rest-auth
 [11]: https://github.com/mesosphere/universe/tree/1-7ea/repo/packages/K/kafka/6
 [12]: #changing-configuration-in-flight
 [13]: https://docs.mesosphere.com/administration/security-and-authentication/
 [14]: https://docs.mesosphere.com/administration/security-and-authentication/auth-api/
 [15]: https://cwiki.apache.org/confluence/display/KAFKA/System+Tools#SystemTools-GetOffsetShell
 [16]: #configure-kafka-broker-properties
