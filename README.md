DCOS Kafka Service Guide
======================

[Overview](#overview)
- [Benefits](#benefits)
- [Features](#features)
- [Related Services](#related-services)

[Getting Started](#getting-started)
- [Quick Start](#quick-start)
- [Install and Customize](#install-and-customize)
  - [Default install configuration](#default-install-configuration)
  - [Custom install configuration](#custom-install-configuration)
  - [Connecting Clients](#connecting-clients)

[Configuring](#configuring)
- [Configuration Options](#configuration-options)
  - [Framework Name](#framework-name)
  - [Broker Count](#broker-count)
  - [Enable Persistent Volumes](#enable-persistent-volumes)
  - [Configure Broker Placement Strategy](#configure-broker-placement-strategy)

[Managing](#managing)
- [Changing configuration at runtime](#changing-configuration-at-runtime)
- [Upgrading Software](#upgrading-software)
- [Uninstall](#uninstall)
- [Troubleshooting](#troubleshooting)
  - [Configuration Update Errors](#configuration-update-errors)
  - [Software Maintenance Errors](#software-maintenance-errors)
  - [Status Errors](#status-errors)
  - [Replacing a Permanently Failed Server](#replacing-a-permanently-failed-server)

[API Reference](#api-reference)
- [Connection Information](#connection-information)
- [Broker Operations](#broker-operations)
  - [Add Broker](#add-broker)
  - [Remove Broker](#remove-broker)
  - [List All Brokers](#list-all-brokers)
  - [View Broker Details](#view-broker-details)
  - [Restart Single Broker](#restart-single-broker)
  - [Restart All Brokers](#restart-all-brokers)
  - [Topic Operations](#topic-operations)
    - [List Topics](#list-topics)
    - [Create Topic](#create-topic)
    - [View Topic Details](#view-topic-details)
    - [View Topic Offsets](#view-topic-offsets)
    - [Alter Topic Partition Count](#alter-topic-partition-count)
    - [Alter Topic Config Value](#alter-topic-config-value)
    - [Delete/Unset Topic Config Value](#deleteunset-topic-config-value)
    - [Run Producer Test on Topic](#run-producer-test-on-topic)
    - [Delete Topic](#delete-topic)
    - [List Under Replicated Partitions](#list-under-replicated-partitions)
    - [List Unavailable Partitions](#list-unavailable-partitions)

[Limits](#limits)
- [Configurations](#configurations)
- [Brokers](#brokers)
- [Security](#security)

[Development](#development)

## Overview

DCOS Kafka Service is an automated service that makes it easy to deploy and manage Apache Kafka on Mesosphere DCOS, eliminating nearly all of the complexity traditionally associated with managing a Kafka cluster. Apache Kafka is a distributed high-throughput publish-subscribe messaging system with strong ordering guarantees. Kafka clusters are highly available, fault tolerant, and very durable. For more information on Apache Kafka, see the Apache Kafka [documentation](http://kafka.apache.org/documentation.html). DCOS Kafka Service gives you direct access to the Kafka API so that existing producers and consumers can interoperate. You can configure and install DCOS Kafka Service in moments. Multiple Kafka clusters can be installed on DCOS and managed independently, so you can offer Kafka as a managed service to your organization.

### Benefits

DCOS Kafka Service offers the following benefits of a semi-managed service:

- Easy installation
- Multiple Kafka clusters
- Elastic scaling of brokers
- Replication for high availability
- Kafka cluster and broker monitoring

### Features

DCOS Kafka Service provides the following features:

- Single-command installation for rapid provisioning
- Multiple clusters for multiple tenancy with DCOS
- Runtime configuration and software updates for high availability
- Storage volumes for enhanced data durability, known as Mesos Dynamic Reservations and Persistent Volumes
- Integration with syslog-compatible logging services for diagnostics and troubleshooting
- Integration with statsd-compatible metrics services for capacity and performance monitoring

### Related Services

- [DCOS Spark](https://docs.mesosphere.com/manage-service/spark)

## Getting Started

### Quick Start

- Step 1. Install the [dcos-cli](https://github.com/mesosphere/dcos-cli).

- Step 2. Install a Kafka cluster.

``` bash
$ dcos package install kafka # framework name defaults to 'kafka0'
```

- Step 3. Create a new topic.

``` bash
$ dcos kafka topic create topic1 --partitions 3 --replication 3
```

- Step 4. Read and write data to a topic.

``` bash
$ dcos kafka connection
{
    "broker_list_convenience": "--broker-list ip-10-0-3-230.us-west-2.compute.internal:9092, ip-10-0-3-231.us-west-2.compute.internal:9093",
    "brokers": [
        "ip-10-0-3-230.us-west-2.compute.internal:9092",
        "ip-10-0-3-231.us-west-2.compute.internal:9093"
    ],
    "zookeeper": "master.mesos:2181/kafka0",
    "zookeeper_convenience": "--zookeeper master.mesos:2181/kafka0"
}
$ dcos node ssh --master-proxy --master
core@ip-10-0-6-153 ~ $ docker run -it mesosphere/kafka-client
root@7bc0e88cfa52:/kafka_2.10-0.8.2.2/bin# ./kafka-console-producer.sh --broker-list ip-10-0-3-230.us-west-2.compute.internal:9092 --topic test
This is a message
This is another message

root@7bc0e88cfa52:/kafka_2.10-0.8.2.2/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/kafka0 --topic test --from-beginning
This is a message
This is another message
```

- Step 5. Uninstall the cluster.

``` bash
$ dcos package uninstall --app-id=kafka0 kafka
```

### Install and Customize

#### Default install configuration

To start a basic test cluster with three brokers, run the following command with dcos-cli:

``` bash
$ dcos package install kafka
```

This command creates a new Kafka cluster with the default name `kafka0`. Two clusters cannot share the same name, so installing additional clusters beyond the default cluster requires [customizing the `framework-name` at install time](#custom-install-configuration) for each additional instance.

All `dcos kafka` CLI commands have a `--framework-name` argument allowing the user to specify which Kafka instance to query. If you do not specify a framework name, the CLI assumes the default value, `kafka0`. The default value for `--framework-name` can be customized via the DCOS CLI configuration:

``` bash
$ dcos config set kafka.framework_name new_default_name
```

The default cluster, `kafka0`, is intended for testing and development. It is not suitable for production use without additional customization.

#### Custom install configuration

Customize the defauls by creating a JSON file. Then pass it to `dcos package install` using the `--options parameter`.

Sample JSON options file named `sample-kafka.json`:
``` json
{
  "kafka": {
    "broker-count": 10,
    "framework-name": "sample-kafka",
    "pv": true,
    "placement-strategy": "NODE"
  }
}
```

The command below creates a cluster using `sample-kafka.json`:
``` bash
$ dcos package install --options=sample-kafka.json kafka
```

See [Configuration Options](#configuration-options) for a list of fields that can be customized via an options JSON file when the Kafka cluster is created.

### Multiple Kafka cluster installation

Installing multiple Kafka clusters is identical to installing Kafka clusters with custom configurations as described above.  The only requirement on the operator is that a unique `framework-name` is specified for each installation. For example:

```
$ cat kafka1.json
{
  "kafka": {
    "framework-name": "kafka1"
  }
}

$ dcos package install kafka --options=kafka1.json
This will install Apache Kafka DCOS Service.
Continue installing? [yes/no] yes
Installing Marathon app for package [kafka] version [0.1.0]
Installing CLI subcommand for package [kafka] version [0.1.0]
New command available: dcos kafka
The Apache Kafka DCOS Service is installed:
  docs   - https://github.com/mesos/kafka
  issues - https://github.com/mesos/kafka/issues
```

### Uninstall

Uninstalling a cluster is also straightforward. Replace `kafka0` with the name of the kafka instance to be uninstalled.

``` bash
$ dcos package uninstall --app-id=kafka0 kafka
```

The instance will still be present in zookeeper at `/[framework_name]`, e.g., `/kafka0`. To completely clear the configuration, the zookeeper node must be removed.

### Changing configuration in flight

Once the cluster is already up and running, it may be customized in-place. The Kafka Scheduler will be running as a Marathon process, and can be reconfigured by changing values within Marathon.

1. View your Marathon dashboard at `http://$DCOS_URI/marathon`
2. In the list of `Applications`, click the name of the Kafka framework to be updated.
3. Within the Kafka instance details view, click the `Configuration` tab, then click the `Edit` button.
4. In the dialog that appears, expand the `Environment Variables` section and update any field(s) to their desired value(s). For example, to [increase the number of Brokers](#broker-count), edit the value for `BROKER_COUNT`. Do not edit the value for `FRAMEWORK_NAME`.
5. Click `Change and deploy configuration` to apply any changes and cleanly reload the Kafka Framework scheduler. The Kafka cluster itself will persist across the change.
 
#### Configuration Deployment Strategy

Configuration updates are rolled out through execution of Update Plans.  The way these plans are executed is configurable.

##### Configuration Update Plans

In brief, Plans are composed of Phases, which are in turn composed of Blocks.  Two possible configuration update strategies specify how the Blocks are executed.  These strategies are specified by setting the `PLAN_STRATEGY` environment variable on the scheduler.  By default, the strategy is `INSTALL` which rolls changes out one Broker at a time with no pauses.

The alternative is the `STAGE` strategy.  This strategy will cause two mandatory human decision points to be injected into the configuration update process.  Initially, no configuration update will take place, and the Service will be waiting on a human to confirm the update plan is what was expected.  The operator may then decide to continue the configuration update through a REST API call or rollback the configuration update by replacing the original configuration through Marathon in exactly the same way as a configuration update is specified above.  After specifying that an update should continue, one Block representing one Broker will be updated and the configuration update will again pause.  An operator now has a second opportunity to rollback or continue.  If continuation is again the operator's decision, the rest of the Brokers will be updated one at a time until all Brokers are using the new configuration without further required human interaction.  Finally, an operator may indicate that an update should be interrupted at any point and is then able to make the same continue or rollback decisions.

### Connecting Clients

The following code connects to a DCOS-hosted Kafka instance using `bin/kafka-console-producer.sh` and `bin/kafka-console-consumer.sh` as an example: <!-- what role are these scripts playing here? -->

``` bash
 $ dcos kafka connection
 {
     "broker_list_convenience": "--broker-list ip-10-0-3-230.us-west-2.compute.internal:9092, ip-10-0-3-231.us-west-2.compute.internal:9093",
     "brokers": [
         "ip-10-0-3-230.us-west-2.compute.internal:9092",
         "ip-10-0-3-231.us-west-2.compute.internal:9093"
     ],
     "zookeeper": "master.mesos:2181/kafka0",
     "zookeeper_convenience": "--zookeeper master.mesos:2181/kafka0"
 }
 $ dcos node ssh --master-proxy --master
 core@ip-10-0-6-153 ~ $ docker run -it mesosphere/kafka-client
 root@7bc0e88cfa52:/kafka_2.10-0.8.2.2/bin# ./kafka-console-producer.sh --broker-list ip-10-0-3-230.us-west-2.compute.internal:9092 --topic test
 This is a message
 This is another message
 
 root@7bc0e88cfa52:/kafka_2.10-0.8.2.2/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/kafka0 --topic test --from-beginning
 This is a message
 This is another message
 ```

```bash
$ dcos kafka connection
```

## Configuring

##### Configuration Update REST API

There are two Phases in the Update Plans for Kafka.  The first Phase is for Mesos Task reconciliation and is always executed without need for human interaction.  The more interesting phase is the Update phase.
```bash
GET $DCOS_URI/service/kafka0/v1/plan/phases HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
[...]

{
    "phases": [
        {
            "0": "Reconciliation"
        },
        {
            "1": "Update to: c3c7fd10-9697-454a-9360-595376169d1f"
        }
    ]
}
```

The update phase can be viewed by making the REST request below.
```bash 
GET $DCOS_URI/service/kafka0/v1/plan/phases/1 HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
[...]

{
    "blocks": [
        {
            "0": {
                "name": "broker-0",
                "status": "Complete"
            }
        },
        {
            "1": {
                "name": "broker-1",
                "status": "Complete"
            }
        },
        {
            "2": {
                "name": "broker-2",
                "status": "Complete"
            }
        }
    ]
}
```

When using the `STAGE` deployment strategy, an update plan will initially pause without doing any update to ensure the plan is as expected.  It will look like this:

```bash
GET $DCOS_URI/service/kafka0/v1/plan/phases/1 HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
[...]

{
    "blocks": [
        {
            "0": {
                "name": "broker-0",
                "status": "Pending"
            }
        },
        {
            "1": {
                "name": "broker-1",
                "status": "Pending"
            }
        },
        {
            "2": {
                "name": "broker-2",
                "status": "Pending"
            }
        }
    ]
}
```

In order to execute the first block a continue command is required and can be executed in the following way:

```bash
PUT $DCOS_URI/service/kafka0/v1/plan?cmd=continue HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
[...]

{
    "Result": "Received cmd: continue"
}
```

After executing the continue operation the plan will look like this:

```bash
GET $DCOS_URI/service/kafka0/v1/plan/phases/1 HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
[...]

{
    "blocks": [
        {
            "0": {
                "name": "broker-0",
                "status": "Complete"
            }
        },
        {
            "1": {
                "name": "broker-1",
                "status": "Pending"
            }
        },
        {
            "2": {
                "name": "broker-2",
                "status": "Pending"
            }
        }
    ]
}
```

An additional continue command will cause the rest of the plan to be executed without further interruption.  If at some point an operator would like to interrupt an in progress configuration update, this can be accomplished with the following command:

```bash
PUT $DCOS_URI/service/kafka0/v1/plan?cmd=interrupt HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
[...]

{
    "Result": "Received cmd: interrupt"
}
```

## Configuration Options

The following describes the most commonly used features of the Kafka framework and how to configure them via dcos-cli and in Marathon. View the [default `config.json` in DCOS Universe](https://github.com/mesosphere/universe/tree/kafka_0_9_0_0/repo/packages/K/kafka/3) to see all possible configuration options.

### Framework Name

The name of this Kafka instance in DCOS. This is the only option that cannot be changed once the Kafka cluster is started: it can only be configured via the `dcos-cli --options` flag the Kafka instance is created.

- **In dcos-cli options.json**: `framework-name` = string (default: `kafka0`)
- **In Marathon**: The framework name cannot be changed after the cluster has started.

### Broker Count

Configure the number of brokers running in a given Kafka cluster. The default count at installation is three brokers.

- **In dcos-cli options.json**: `broker-count` = integer (default: `3`)
- **In Marathon**: `BROKER_COUNT` = integer

### Enable Persistent Volumes

Kafka brokers can be configured to use the sandbox available to Mesos Tasks for storing data. This storage goes away when a task fails. If a Broker crashes, the data on it is lost forever. While this may be acceptable in development environments, in production environments Kafka should be deployed with the persistent volume option enabled:

- **In dcos-cli options.json**: `pv` = boolean (default: `false`)
- **In Marathon**: `BROKER_PV` = `TRUE` or `FALSE`

The disk configuration option only has an effect when persistent volumes are enabled.  It may only be set at the time that persistent volumes are enabled.  It may not be changed later. 
- **In dcos-cli options.json**: `disk` = integer (default: 5000)
- **In Marathon**: `BROKER_DISK` = 5000

An example options file for a production deployment with persistent volumes enabled looks like this:

```
{
  "kafka": {
    "framework-name": "kafkaprod",
    "pv": true,
    "disk": 10000,
    "placement-strategy": "NODE",
    "broker-count": 5
  }
}
```

Enabling persistent volumes has two additional consequences.

1. The placement strategy specified at the time when persistent volumes are enabled is the last opportunity to affect Broker placement.  Further changes to placement strategy will be ignored as Brokers are now associated with their persistent volumes.
2. Uninstalling the service after turning on persistent volumes will leak reserved resources.  A garbage collection scheme is under development.  Currently use of persistent volumes should be restricted to Kafka clusters which are intended for long sustained use.

Please see Mesos documentation for further information regarding manual removal of [reserved resources](http://mesos.apache.org/documentation/latest/reservation/) and [persistent volumes](http://mesos.apache.org/documentation/latest/persistent-volume/).

### Configure Broker Placement Strategy

`ANY` allows brokers to be placed on any node with sufficient resources, while `NODE` ensures that all brokers within a given Kafka cluster are never colocated on the same node.

- **In dcos-cli options.json**: `placement-strategy` = `ANY` or `NODE` (default: `ANY`)
- **In Marathon**: `PLACEMENT_STRATEGY` = `ANY` or `NODE`

### Configure Kafka Broker Properties

Kafka Brokers are configured through settings in a server.properties file deployed with each Broker.  The settings here can be specified at installation time or during a post-deployment configuration update.  They are set in the DCOS Universe's config.json as options such as:

```
"kafka_override_log_retention_hours": {
  "description": "Kafka broker log retention hours.",
  "type": "integer",
  "default": 168
},
```

The defaults can be overriden at install time by specifying an options.json file with a format like this:
```
{
  "kafka": {
    "kafka_override_log_retention_hours": 100
  }
}
```

These same values are also represented as environment variables for the Scheduler in the form `KAFKA_OVERRIDE_LOG_RETENTION_HOURS` and may be modified through Marathon and deployed during a rolling upgrade as [described here](#changing-configuration-in-flight).

## Managing

### Changing configuration at runtime

Once the cluster is already up and running, you can customize it in-place. The Kafka scheduler runs as a Marathon process and can be reconfigured by changing values within Marathon.

1. View your Marathon dashboard at `http://$DCOS_URI/marathon`
2. In the list of `Applications`, click the name of the Kafka framework to be updated.
3. Within the Kafka instance details view, click the `Configuration` tab, then click `Edit`.
4. In the dialog box that appears, expand the `Environment Variables` section and update any field(s) to their desired value(s). For example, to [increase the number of Brokers](#broker-count), edit the value for `BROKER_COUNT`. Do not edit the value for `FRAMEWORK_NAME`. <!-- what happens if you do that? -->
5. Click `Change and deploy configuration` to apply any changes and cleanly reload the Kafka framework scheduler. The Kafka cluster itself will persist across the change.

See [Configuration Options](#configuration-options) for a list of fields that can be customized via Marathon while the Kafka cluster is running.

#### Add a Broker

Increase the `BROKER_COUNT` value via Marathon. New brokers should start automatically. <!-- so you wouldn't use the API to do this? If so, I will move this to Management -->

#### Remove a Broker

Broker removal is currently a manual process. Set the `BROKER_COUNT` environment variable to the desired target count. All Remove all replicas off the brokers that will be removed as a consequence of shrinking the cluster. Then, remove each broker by performing the reschedule operation. For example, if you are resizing a cluster from 3 brokers to 2, set the `BROKER_COUNT` to 2 and move replicas off the broker with id 2 (ids start at 0). Then, remove the broker with the reschedule command:

 ``` bash
 $ dcos kafka broker reschedule 2
 [
     "broker-2__b44b8d32-69e9-420b-aae2-59952dfaa8c2"
 ]
 ```

<!-- ### Upgrading Software

**TODO** once implemented: guide for updating kafka process and/or framework itself -->

### Uninstall

Uninstalling a cluster is straightforward. Replace `kafka0` with the name of the kafka instance to be uninstalled.

``` bash
$ dcos package uninstall --app-id=kafka0 kafka
```

The instance will still be present in zookeeper at `/[framework_name]`, e.g., `/kafka0`. To completely clear the configuration, remove the zookeeper node.

<!--  ## Troubleshooting

### Configuration Update Errors

**TODO** How to handle configuration input validation errors

**TODO** How to handle errors when applying configuration to a running service

### Software Maintenance Errors

### Replacing a Permanently Failed Server

If a machine has permanently failed it is possible some manual intervention will be required to replace the Broker or Brokers which were resident on that machine.  The details are dependent upon whether Persistent Volumes were enabled.  If Persistent Volumes were not enabled and there are sufficient resources in the cluster to place Brokers according to the requirements of the current placement strategy, then the cluster will automatically heal itself.

When Persistent Volumes are enabled the service continuously attempts to replace Brokers where their data has been persisted.  In the case where a machine has permanently failed, the Kafka CLI can be used to replace the Brokers.

In the example below the Broker with id `0` will be rescheduled on new machine as long as cluster resources are sufficient to satisfy the services placement constraints.

```bash
$ dcos kafka broker reschedule 0
```

## Limitations

### Configurations

#### Persistent Volumes
By default Kafka does not use the Persitent Volume feature of Mesos.  Once this feature is enabled (by setting the "BROKER_PV" environment variabel to "true") Brokers are tied to the node on which their persistent volumes lie so changes to the "placement-strategy" configuration option will no longer have an effect.  Furthermore once a persistent volume is created for a Broker its disks size is no longer runtime configurable.

#### Pitfalls of managing configurations outside of the framework
The Kafka framework's core responsibility is to deploy and maintain the deployment of a Kafka cluster whose configuration has been specified.  In order to do this the framework makes the assumption that it has ownership of Broker configuration.  If an end-user makes modificiations to individual Brokers through out-of-band configuration operations the Framework will almost certainly override those modifications at a later time.  If a Broker crashes it will be restarted with the configuration known to the Scheduler, not one modified out-of-band.  If a configuration update is initiated, then during the process of the rolling update, all out-of-band modifications will be overwritten.

### Brokers

The number of deployable Brokers is constrained by two factors.  First, Brokers have specified required resources, so Brokers may not be placed if the Mesos cluster lacks the requisite resources.  Second, the specified "PLACEMENT_STRATEGY" environment variable may affect how many Brokers may be created in a Kafka cluster.  By default the value is "ANY" so Brokers are placed anywhere and are only constrained by the resources of the cluster.  A second option is "NODE".  In this case only one Broker may be placed on a given Mesos Agent.

### Security

**TODO** describe how someone could configure Kafka 0.9's beta security features (or specify that they're not supported?): data encryption, client authentication over SSL/SASL, broker authentication with ZK...

## API Reference

For ongoing maintenance of the Kafka cluster itself, the Kafka framework exposes an HTTP API whose structure is designed to roughly match the tools provided by the Kafka distribution, such as `bin/kafka-topics.sh`.

The examples here provide equivalent commands using both `[dcos-cli](https://github.com/mesosphere/dcos-cli)` (with the `kafka` CLI module installed) and `curl`. These examples assume a service named `kafka0` (the default), and the `curl` examples assume a DCOS host of `$DCOS_URI`. Replace these with appropriate values as needed.

The `dcos kafka` CLI commands have a `--framework-name` argument, allowing the user to specify which Kafka instance to query. The value defaults to `kafka0`, so it's technically redundant to specify `--framework-name=kafka0` in these examples. The default value for `--framework-name` can be customized via the DCOS CLI configuration:

``` bash
$ dcos config set kafka.framework_name new_default_name
```

### Connection Information

Kafka comes with many useful tools of its own that often require either Zookeeper connection information or the list of broker endpoints. This information can be retrieved in an easily consumable format from the `/connection` endpoint:

``` bash
$ curl -X GET "$DCOS_URI/service/kafka0/v1/connection"
GET /service/kafka0/v1/connection HTTP/1.1
[...]

{
    "brokers": [
        "10.0.0.1:9092",
        "10.0.0.2:9093",
        "10.0.0.3:9094"
    ],
    "zookeeper": "master.mesos:2181/kafka0"
}
```

The same information can be retrieved through the CLI:

``` bash
$ dcos kafka connection
{
    "broker_list_convenience": "--broker-list ip-10-0-3-230.us-west-2.compute.internal:9092, ip-10-0-3-231.us-west-2.compute.internal:9093",
    "brokers": [
        "ip-10-0-3-230.us-west-2.compute.internal:9092",
        "ip-10-0-3-231.us-west-2.compute.internal:9093"
    ],
    "zookeeper": "master.mesos:2181/kafka0",
    "zookeeper_convenience": "--zookeeper master.mesos:2181/kafka0"
}
$ dcos node ssh --master-proxy --master
core@ip-10-0-6-153 ~ $ docker run -it mesosphere/kafka-client
root@7bc0e88cfa52:/kafka_2.10-0.8.2.2/bin# ./kafka-console-producer.sh --broker-list ip-10-0-3-230.us-west-2.compute.internal:9092 --topic test
This is a message
This is another message

root@7bc0e88cfa52:/kafka_2.10-0.8.2.2/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/kafka0 --topic test --from-beginning
This is a message
This is another message
```

### Broker Operations

#### Add Broker

Increase the `BROKER_COUNT` value via Marathon. New brokers should start automatically. <!-- so you wouldn't use the API to do this? If so, I will move this to Management -->

#### Remove Broker

Broker removal is currently a manual process.  The "BROKER_COUNT" environment variable should be set to the desired target count.  All Replicas should be moved off the Brokers which will be removed as a consequence of shrinking the cluster.  Then each Broker can be removed by performing the reschedule operation.  For example if resizing a cluster from 3 brokers to 2, the "BROKER_COUNT" should be set to 2 and then replicas should be moved off of the Broker with id 2 (ids start at 0).  Then the Broker should be removed with the reschedule command.

``` bash
$ dcos kafka broker reschedule 2
[
    "broker-2__b44b8d32-69e9-420b-aae2-59952dfaa8c2"
]
```

#### List All Brokers

``` bash
$ dcos kafka --framework-name=kafka0 broker list
{
    "brokers": [
        "0",
        "1",
        "2"
    ]
}
```

``` bash
$ curl -X GET "$DCOS_URI/service/kafka0/v1/brokers"
GET /service/kafka0/v1/brokers HTTP/1.1
[...]

{
    "brokers": [
        "0",
        "1",
        "2"
    ]
}
```
#### View Broker Details

``` bash
$ dcos kafka --framework-name=kafka0 broker describe 0
{
    "endpoints": [
        "PLAINTEXT://w1.dcos:9092"
    ],
    "host": "w1.dcos",
    "jmx_port": -1,
    "port": 9092,
    "timestamp": "1454462821420",
    "version": 2
}

```

``` bash
$ curl -X GET "$DCOS_URI/service/kafka0/v1/brokers/0"
GET /service/kafka0/v1/brokers/0 HTTP/1.1
[...]

{
    "endpoints": [
        "PLAINTEXT://worker12398:9092"
    ],
    "host": "worker12398",
    "jmx_port": -1,
    "port": 9092,
    "timestamp": "1453854226816",
    "version": 2
}
```

#### Restart Single Broker

``` bash
$ dcos kafka --framework-name=kafka0 broker restart 0
[
    "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
]
```

``` bash
$ curl -X PUT "$DCOS_URI/service/kafka0/v1/brokers/0"
PUT /service/kafka0/v1/brokers/0 HTTP/1.1
[...]

[
    "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
]
```

#### Restart All Brokers

``` bash
$ curl -X PUT $DCOS_URI/service/kafka0/v1/brokers
PUT /service/kafka0/v1/brokers HTTP/1.1
[...]

[
    "broker-1__759c9fc2-3890-4921-8db8-c87532b1a033",
    "broker-2__0b444104-e210-4b78-8d19-f8938b8761fd",
    "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
]
```

### Topic Operations

These operations mirror what is available with `bin/kafka-topics.sh`.

#### List Topics

``` bash
$ dcos kafka --framework-name=kafka0 topic list
[
    "topic1",
    "topic0"
]
```

``` bash
$ curl -X GET "$DCOS_URI/service/kafka0/v1/topics"
GET /service/kafka0/v1/topics HTTP/1.1
[...]

[
    "topic1",
    "topic0"
]
```

#### Create Topic

``` bash
$ dcos kafka --framework-name=kafka0 topic create topic1 --partitions=3 --replication=3
{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Created topic \"topic1\".\n"
}
```

``` bash
$ curl -X POST "$DCOS_URI/service/kafka0/v1/topics?name=topic1&partitions=3&replication=3"
POST /service/kafka0/v1/topics?replication=3&name=topic1&partitions=3 HTTP/1.1
[...]

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Created topic \"topic1\".\n"
}
```

#### View Topic Details

``` bash
$ dcos kafka --framework-name=kafka0 topic describe topic1
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
```

``` bash
$ curl -X GET "$DCOS_URI/service/kafka0/v1/topics/topic1"
GET /service/kafka0/v1/topics/topic1 HTTP/1.1
[...]

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
```

#### View Topic Offsets

``` bash
$ dcos kafka --framework-name=kafka0 topic offsets topic1
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
```

``` bash
$ curl -X "$DCOS_URI/service/kafka0/v1/topics/topic1/offsets"
GET /service/kafka0/v1/topics/topic1/offsets HTTP/1.1
[...]

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
```

#### Alter Topic Partition Count

``` bash
$ dcos kafka --framework-name=kafka0 topic partitions topic1 2

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affected\nAdding partitions succeeded!\n"
}
```

``` bash
$ curl -X PUT "$DCOS_URI/service/kafka0/v1/topics/topic1?operation=partitions&partitions=2"
PUT /service/kafka0/v1/topics/topic1?operation=partitions&partitions=2 HTTP/1.1
[...]

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affected\nAdding partitions succeeded!\n"
}
```

#### Alter Topic Config Value

``` bash
$ dcos kafka --framework-name=kafka0 topic config topic1 cleanup.policy compact
```

``` bash
$ curl -vX PUT "$DCOS_URI/service/kafka0/v1/topics/topic1?operation=config&key=cleanup.policy&value=compact"
PUT /service/kafka0/v1/topics/topic1?operation=config&key=cleanup.policy&value=compact HTTP/1.1
[...]

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Updated config for topic \"topic0\".\n"
}
```

#### Delete/Unset Topic Config Value

``` bash
$ dcos kafka --framework-name=kafka0 topic delete_config topic1 cleanup.policy
```

``` bash
$ curl -vX PUT "$DCOS_URI/service/kafka0/v1/topics/topic1?operation=deleteConfig&key=cleanup.policy"
```

#### Run Producer Test on Topic

``` bash
$ dcos kafka --framework-name=kafka0 topic producer_test topic1 10

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.\n"
}
```

``` bash
$ curl -X PUT "$DCOS_URI/service/kafka0/v1/topics/topic1?operation=producer-test&messages=10"
PUT /service/kafka0/v1/topics/topic1?operation=producer-test&messages=10 HTTP/1.1
[...]

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.\n"
}
```

#### Delete Topic

``` bash
$ dcos kafka --framework-name=kafka0 topic delete topic1

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Topic topic1 is marked for deletion.\nNote: This will have no impact if delete.topic.enable is not set to true.\n"
}
```

``` bash
$ curl -X DELETE "$DCOS_URI/service/kafka0/v1/topics/topic1"
DELETE /service/kafka0/v1/topics/topic1?operation=delete HTTP/1.1
[...]

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Topic topic1 is marked for deletion.\nNote: This will have no impact if delete.topic.enable is not set to true.\n"
}
```

#### List Under Replicated Partitions

``` bash
$ dcos kafka --framework-name=kafka0 topic under_replicated_partitions

{
    "exit_code": 0,
    "stderr": "",
    "stdout": ""
}
```

``` bash
$ curl -X "$DCOS_URI/service/kafka0/v1/topics/under_replicated_partitions"
GET /service/kafka0/v1/topics/under_replicated_partitions HTTP/1.1
[...]

{
    "exit_code": 0,
    "stderr": "",
    "stdout": ""
}
```

#### List Unavailable Partitions

``` bash
$ dcos kafka --framework-name=kafka0 topic unavailable_partitions

{
    "exit_code": 0,
    "stderr": "",
    "stdout": ""
}
```

``` bash
$ curl -X "$DCOS_URI/service/kafka0/v1/topics/unavailable_partitions"
GET /service/kafka0/v1/topics/unavailable_partitions HTTP/1.1
[...]

{
    "exit_code": 0,
    "stderr": "",
    "stdout": ""
}
```

### Config Updates

#### View Plan Status

View phases
``` bash
$ http $DCOS_URI/service/kafka0/v1/plan
HTTP/1.1 200 OK
[...]

{
    "phases": [
        {
            "0": "Update to: 91e74d8e-f50b-49ca-a7cb-7d4aff463b99"
        }
    ]
}
```

View blocks
``` bash
$ http $DCOS_URI/service/kafka0/v1/plan/0
HTTP/1.1 200 OK
[...]

{
    "blocks": [
        {
            "0": {
                "name": "broker-0",
                "status": "Complete"
            }
        },
        {
            "1": {
                "name": "broker-1",
                "status": "Complete"
            }
        },
        {
            "2": {
                "name": "broker-2",
                "status": "Complete"
            }
        }
    ]
}
```

## Limits

### Configurations

#### Persistent Volumes
Kafka does not use the persistent volume feature of Mesos by default. Once this feature is enabled, brokers are tied to the node on which their persistent volumes lie, so changes to the "placement-strategy" configuration option will no longer have an effect. Furthermore, once a persistent volume is created for a broker, its disk size is no longer runtime configurable.

#### Pitfalls of managing configurations outside of the framework
The Kafka framework's core responsibility is to deploy and maintain the deployment of a Kafka cluster whose configuration has been specified. In order to do this, the framework assumes that it owns the broker configuration. If an end-user makes modifications to individual brokers through out-of-band configuration operations, the framework may later override those modifications. If a broker crashes <!-- for example? -->, it will restart with the configuration known to the scheduler, not one modified out-of-band. In addition, if a configuration update is initiated, all out-of-band modifications will be overwritten during the rolling update process.

### Brokers
The number of deployable Brokers is constrained by two factors.  First, Brokers have specified required resources, so Brokers may not be placed if the Mesos cluster lacks the requisite resources.  Second, the specified "PLACEMENT_STRATEGY" environment variable may affect how many Brokers may be created in a Kafka cluster.  By default the value is "ANY" so Brokers are placed anywhere and are only constrained by the resources of the cluster.  A second option is "NODE".  In this case only one Broker may be placed on a given Mesos Agent.

## TODO [API for ACL changes](https://kafka.apache.org/documentation.html#security_authz_examples)? (kafka-acls.sh)

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development guide.
