DCOS Kafka Service Guide
======================

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
- [Using the DCOS CLI](#using-the-dcos-cli)
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
- [Security](#security)

[API Reference](#api-reference)
- [Connection Information](#connection-information)
- [Broker Operations](#broker-operations)
- [Topic Operations](#topic-operations)
- [Config Updates](#config-updates)

[Limitations](#limitations)
- [Configurations](#configurations)
- [Brokers](#brokers)

[Development](#development)

## Overview

DCOS Kafka is an automated service that makes it easy to deploy and manage Apache Kafka on Mesosphere DCOS, eliminating nearly all of the complexity traditionally associated with managing a Kafka cluster. Apache Kafka is a distributed high-throughput publish-subscribe messaging system with strong ordering guarantees. Kafka clusters are highly available, fault tolerant, and very durable. For more information on Apache Kafka, see the Apache Kafka [documentation](http://kafka.apache.org/documentation.html). DCOS Kafka gives you direct access to the Kafka API so that existing producers and consumers can interoperate. You can configure and install DCOS Kafka in moments. Multiple Kafka clusters can be installed on DCOS and managed independently, so you can offer Kafka as a managed service to your organization.

### Benefits

DCOS Kafka offers the following benefits of a semi-managed service:

- Easy installation
- Multiple Kafka clusters
- Elastic scaling of brokers
- Replication for high availability
- Kafka cluster and broker monitoring

### Features

DCOS Kafka provides the following features:

- Single-command installation for rapid provisioning
- Multiple clusters for multiple tenancy with DCOS
- High availability runtime configuration and software updates
- Storage volumes for enhanced data durability, known as Mesos Dynamic Reservations and Persistent Volumes
- Integration with syslog-compatible logging services for diagnostics and troubleshooting
- Integration with statsd-compatible metrics services for capacity and performance monitoring

### Related Services

- [DCOS Spark](https://docs.mesosphere.com/manage-service/spark)

## Getting Started

### Quick Start

- Step 1. Install a Kafka cluster.

```bash
$ dcos package install kafka # framework name defaults to 'kafka'
```

- Step 2. Create a new topic.

```bash
$ dcos kafka topic create topic1 --partitions 3 --replication 3
```

- Step 3. Find connection information.

``` bash
$ dcos kafka connection
{
    "broker_list_convenience": "--broker-list ip-10-0-3-230.us-west-2.compute.internal:9092, ip-10-0-3-231.us-west-2.compute.internal:9093",
    "brokers": [
        "ip-10-0-3-230.us-west-2.compute.internal:9092",
        "ip-10-0-3-231.us-west-2.compute.internal:9093"
    ],
    "zookeeper": "master.mesos:2181/kafka",
    "zookeeper_convenience": "--zookeeper master.mesos:2181/kafka"
}
```

- Step 4. Produce and consume data.

``` bash
$ dcos node ssh --master-proxy --leader

core@ip-10-0-6-153 ~ $ docker run -it mesosphere/kafka-client

root@7bc0e88cfa52:/kafka_2.10-0.8.2.2/bin# ./kafka-console-producer.sh --broker-list ip-10-0-3-230.us-west-2.compute.internal:9092 --topic test
This is a message
This is another message

root@7bc0e88cfa52:/kafka_2.10-0.8.2.2/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/kafka --topic test --from-beginning
This is a message
This is another message
```

See also [Connecting clients](#connecting-clients).

## Install and Customize

### Default Installation

To start a basic test cluster with three brokers, run the following command on the DCOS CLI:

``` bash
$ dcos package install kafka
```

This command creates a new Kafka cluster with the default name `kafka`. Two clusters cannot share the same name, so installing additional clusters beyond the default cluster requires [customizing the `framework-name` at install time](#custom-install-configuration) for each additional instance.

All `dcos kafka` CLI commands have a `--framework-name` argument allowing the user to specify which Kafka instance to query. If you do not specify a framework name, the CLI assumes the default value, `kafka`. The default value for `--framework-name` can be customized via the DCOS CLI configuration:

``` bash
$ dcos kafka --framework-name kafka-dev <cmd>
```

### Minimal Installation

For development purposes, you may wish to install Kafka on a local DCOS cluster. For this, you can use [dcos-vagrant](https://github.com/mesosphere/dcos-vagrant).

To start a minimal cluster with a single broker, create a JSON options file named `sample-kafka-minimal.json`:

``` json
{
    "brokers": {
        "count": 1,
        "mem": 512,
        "disk": 1000
    }
}
```

The command below creates a cluster using `sample-kafka-minimal.json`:
``` bash
$ dcos package install --options=sample-kafka-minimal.json kafka
```

### Custom Installation

Customize the defaults by creating a JSON file. Then, pass it to `dcos package install` using the `--options` parameter.

Sample JSON options file named `sample-kafka-custom.json`:
``` json
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
```

The command below creates a cluster using `sample-kafka.json`:
``` bash
$ dcos package install --options=sample-kafka-custom.json kafka
```

See [Configuration Options](#configuration-options) for a list of fields that can be customized via an options JSON file when the Kafka cluster is created.

### Multiple Kafka cluster installation

Installing multiple Kafka clusters is identical to installing Kafka clusters with custom configurations as described above.  The only requirement on the operator is that a unique `framework-name` is specified for each installation. For example:

``` bash
$ cat kafka1.json
{
    "service": {
        "name": "kafka1"
    }
}

$ dcos package install kafka --options=kafka1.json
```

### Uninstall

Uninstalling a cluster is also straightforward. Replace `name` with the name of the kafka instance to be uninstalled.

``` bash
$ dcos package uninstall --app-id=name kafka
```

Then, use the [framework cleaner script](https://github.com/mesosphere/framework-cleaner) to remove your Kafka instance from Zookeeper and to destroy all data associated with it. The script require several arguments, the values for which are derived from your framework name:

- `framework-role` is `<framework-name>-role`.
- `framework-principal` is `<framework-name>-principal`.
- `zk_path` is `<framework-name>`.

## Configuring

### Changing Configuration at Runtime

You can customize your cluster in-place when it is up and running.

The Kafka scheduler runs as a Marathon process and can be reconfigured by changing values within Marathon. These are the general steps to follow:

1. View your Marathon dashboard at `http://$DCOS_URI/marathon`
2. In the list of `Applications`, click the name of the Kafka framework to be updated.
3. Within the Kafka instance details view, click the `Configuration` tab, then click the `Edit` button.
4. In the dialog that appears, expand the `Environment Variables` section and update any field(s) to their desired value(s). For example, to [increase the number of Brokers](#broker-count), edit the value for `BROKER_COUNT`. Do not edit the value for `FRAMEWORK_NAME` or `BROKER_DISK` or  `PLACEMENT_STRATEGY`.
5.  A `PHASE_STRATEGY` of `STAGE` should also be set.  See "Configuration Deployment Strategy" below for more details.
6. Click `Change and deploy configuration` to apply any changes and cleanly reload the Kafka Framework scheduler. The Kafka cluster itself will persist across the change.

#### Configuration Deployment Strategy

Configuration updates are rolled out through execution of Update Plans. You can configure the way these plans are executed.

#### Configuration Update Plans

In brief, "plans" are composed of "phases," which are in turn composed of "blocks." Two possible configuration update strategies specify how the blocks are executed. These strategies are specified by setting the `PHASE_STRATEGY` environment variable on the scheduler.  By default, the strategy is `INSTALL`, which rolls changes out to one broker at a time with no pauses.

The alternative is the `STAGE` strategy. This strategy injects two mandatory human decision points into the configuration update process. Initially, no configuration update will take place: the service waits for a human to confirm the update plan is correct. You may then decide to either continue the configuration update through a [REST API](#using-the-rest-api) call, or roll back the configuration update by replacing the original configuration through Marathon in exactly the same way as a configuration update is specified above.

After specifying that an update should continue, one block representing one broker will be updated and the configuration update will again pause. At this point, you have a second opportunity to roll back or continue. If you decide to continue a second time, the rest of the brokers will be updated one at a time until all the brokers are using the new configuration. You may interrupt an update at any point. After interrupting, you can choose to continue or roll back. Consult the "Configuration Update REST API" for these operations.

#### Configuration Update REST API

There are two phases in the update plans for Kafka: Mesos task reconciliation and update. Mesos task reconciliation is always executed without need for human interaction.

Make the REST request below to view the current plan. See [REST API authentication](#rest-api-authentication) for information on how this request must be authenticated.

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan"
GET $DCOS_URI/service/kafka/v1/plan HTTP/1.1

{
    "errors": [],
    "phases": [
        {
            "blocks": [
                {
                    "hasDecisionPoint": false,
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
                    "hasDecisionPoint": false,
                    "id": "918c6019-09af-476d-b0f6-a26f59526bd7",
                    "message": "Broker-0 is Complete",
                    "name": "broker-0",
                    "status": "Complete"
                },
                {
                    "hasDecisionPoint": false,
                    "id": "883945bc-87e7-4156-bda9-fca249aef828",
                    "message": "Broker-1 is Complete",
                    "name": "broker-1",
                    "status": "Complete"
                },
                {
                    "hasDecisionPoint": false,
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

```

When using the `STAGE` deployment strategy, an update plan will initially pause without doing any update to ensure the plan is correct. It will look like this:

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan"
GET $DCOS_URI/service/kafka/v1/plan HTTP/1.1

{
    "errors": [],
    "phases": [
        {
            "blocks": [
                {
                    "hasDecisionPoint": false,
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
                    "hasDecisionPoint": true,
                    "id": "926fb980-8942-48fb-8eb6-1b63fad4e7e3",
                    "message": "Broker-0 is Pending",
                    "name": "broker-0",
                    "status": "Pending"
                },
                {
                    "hasDecisionPoint": true,
                    "id": "60f6dade-bff8-42b5-b4ac-aa6aa6b705a4",
                    "message": "Broker-1 is Pending",
                    "name": "broker-1",
                    "status": "Pending"
                },
                {
                    "hasDecisionPoint": false,
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

```

**Note:** After a configuration update, you may see an error from Mesos-DNS; this will go away 10 seconds after the update.

Enter the `continue` command to execute the first block:

``` bash
$ curl -X PUT --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan?cmd=continue"
PUT $DCOS_URI/service/kafka/v1/plan?cmd=continue HTTP/1.1

{
    "Result": "Received cmd: continue"
}
```

After you execute the continue operation, the plan will look like this:

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan"
GET $DCOS_URI/service/kafka/v1/plan HTTP/1.1

{
    "errors": [],
    "phases": [
        {
            "blocks": [
                {
                    "hasDecisionPoint": false,
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
                    "hasDecisionPoint": false,
                    "id": "926fb980-8942-48fb-8eb6-1b63fad4e7e3",
                    "message": "Broker-0 is InProgress",
                    "name": "broker-0",
                    "status": "InProgress"
                },
                {
                    "hasDecisionPoint": true,
                    "id": "60f6dade-bff8-42b5-b4ac-aa6aa6b705a4",
                    "message": "Broker-1 is Pending",
                    "name": "broker-1",
                    "status": "Pending"
                },
                {
                    "hasDecisionPoint": false,
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

```

If you enter `continue` a second time, the rest of the plan will be executed without further interruption. If you want to interrupt a configuration update that is in progress, enter the `interrupt` command:

``` bash
$ curl -X PUT --header "Authorization: token=$AUTH_TOKEN"  "$DCOS_URI/service/kafka/v1/plan?cmd=interrupt"
PUT $DCOS_URI/service/kafka/v1/plan?cmd=interrupt HTTP/1.1

{
    "Result": "Received cmd: interrupt"
}
```

**Note:** The interrupt command can’t stop a block that is `InProgress`, but it will stop the change on the subsequent blocks.

### Configuration Options

The following describes the most commonly used features of the Kafka framework and how to configure them via dcos-cli and in Marathon. View the [default `config.json` in DCOS Universe](https://github.com/mesosphere/universe/tree/1-7ea/repo/packages/K/kafka/6) to see all possible configuration options.

**Note:** To get the latest version of `config.json`, make sure that you are accessing the file from the highest number folder in the `https://github.com/mesosphere/universe/tree/kafka_0_9_0_1__0_2_3/repo/packages/K/kafka/` directory.

#### Framework Name

The name of this Kafka instance in DCOS. This is an option that cannot be changed once the Kafka cluster is started: it can only be configured via the `dcos-cli --options` flag when the Kafka instance is created.

- **In dcos-cli options.json**: `framework-name` = string (default: `kafka`)
- **In Marathon**: The framework name cannot be changed after the cluster has started.

#### Broker Count

Configure the number of brokers running in a given Kafka cluster. The default count at installation is three brokers.  This number may be increased, but not decreased, after installation.

- **In dcos-cli options.json**: `broker-count` = integer (default: `3`)
- **In Marathon**: `BROKER_COUNT` = integer

#### Configure Broker Placement Strategy

`ANY` allows brokers to be placed on any node with sufficient resources, while `NODE` ensures that all brokers within a given Kafka cluster are never colocated on the same node. This is an option that cannot be changed once the Kafka cluster is started: it can only be configured via the `dcos-cli --options` flag when the Kafka instance is created.

- **In dcos-cli options.json**: `placement-strategy` = `ANY` or `NODE` (default: `ANY`)
- **In Marathon**: `PLACEMENT_STRATEGY` = `ANY` or `NODE`

#### Configure Kafka Broker Properties

Kafka Brokers are configured through settings in a server.properties file deployed with each Broker.  The settings here can be specified at installation time or during a post-deployment configuration update.  They are set in the DCOS Universe's config.json as options such as:

``` json
"log_retention_hours": {
    "title": "log.retention.hours",
    "description": "Override log.retention.hours: The number of hours to keep a log file before deleting it (in hours), tertiary to log.retention.ms property",
    "type": "integer",
    "default": 168
},


```

The defaults can be overridden at install time by specifying an options.json file with a format like this:
``` json
{
    "kafka": {
        "log_retention_hours": 100
    }
}
```

These same values are also represented as environment variables for the Scheduler in the form `KAFKA_OVERRIDE_LOG_RETENTION_HOURS` and may be modified through Marathon and deployed during a rolling upgrade as [described here](#changing-configuration-in-flight).

## Connecting Clients

The only supported client library is the official Kafka Java library, ie `org.apache.kafka.clients.consumer.KafkaConsumer` and `org.apache.kafka.clients.producer.KafkaProducer`. Other clients are at the user's risk.

### Using the DCOS CLI

The following command can be executed from the cli in order to retrieve a set of brokers to connect to.

``` bash
dcos kafka --framework-name=<framework-name> connection
```

### Using the REST API

The following `curl` example demonstrates how to retrive connection a set of brokers to connect to using the REST API. See [REST API authentication](#rest-api-authentication) for information on how this request must be authenticated.

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/connection"
```

### REST API Authentication

Depending on how the cluster is configured, commands using the REST API must be authenticated using one of the following methods. This authentication is only applicable for interacting with the Framework REST API directly. Access to the underlying Kafka Brokers themselves just uses standard Kafka APIs.

All `curl` examples in this document assume that an auth token has already been retrieved using one of the following methods, and stored in an environment variable named `AUTH_TOKEN`. See the following documentation for how to retrieve this token from the authentication service.

##### User token authentication

DCOS Enterprise Edition comes with support for [user ACLs](https://docs.mesosphere.com/administration/security-and-authentication/). Interacting with the Kafka REST API requires first retrieving an auth token from the [auth HTTP endpoint](https://docs.mesosphere.com/administration/security-and-authentication/auth-api/), and then providing this token in following requests.

First, we retrieve `uSeR_t0k3n` with our user credentials, and store the token as an environment variable:

``` bash
$ curl --data '{"uid":"username", "password":"password"}' --header "Content-Type:application/json" "$DCOS_URI/acs/api/v1/auth/login"
POST /acs/api/v1/auth/login HTTP/1.1

{
  "token": "uSeR_t0k3n"
}

$ export AUTH_TOKEN=uSeR_t0k3n
```

This token is then used to authenticate requests to the Kafka Framework:

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/connection"
GET /service/kafka/v1/connection HTTP/1.1

{
    "broker_list_convenience": "--broker-list 10.0.0.26:9318, 10.0.0.23:9505, 10.0.0.24:9989",
    "brokers": [
        "10.0.0.26:9318",
        "10.0.0.23:9505",
        "10.0.0.24:9989"
    ],
    "zookeeper": "hostname:2181/kafka",
    "zookeeper_convenience": "--zookeeper hostname:2181/kafka"
}
```

The token is not needed to access the Kafka brokers themselves.

##### OAuth token authentication

Similar to User token authentication, OAuth token authentication produces a token which must then be included in REST API requests. TODO link to OAuth docs once they exist

``` bash
$ TODO command for getting an OAuth token.. once docs describing OAuth exist
{
  "token": "uSeR_t0k3n"
}

$ export AUTH_TOKEN=uSeR_t0k3n
```

This token is then used to authenticate requests to the Kafka Framework:

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/connection"
GET /service/kafka/v1/connection HTTP/1.1

{
    "broker_list_convenience": "--broker-list 10.0.0.26:9318, 10.0.0.23:9505, 10.0.0.24:9989",
    "brokers": [
        "10.0.0.26:9318",
        "10.0.0.23:9505",
        "10.0.0.24:9989"
    ],
    "zookeeper": "hostname:2181/kafka",
    "zookeeper_convenience": "--zookeeper hostname:2181/kafka"
}
```

### Connection Info Response

The response, for both the CLI and the REST API is as below.

``` json
{
    "broker_list_convenience": "--broker-list 10.0.0.26:9318, 10.0.0.23:9505, 10.0.0.24:9989",
    "brokers": [
        "10.0.0.26:9318",
        "10.0.0.23:9505",
        "10.0.0.24:9989"
    ],
    "zookeeper": "hostname:2181/kafka",
    "zookeeper_convenience": "--zookeeper hostname:2181/kafka"
}
```

This JSON array contains a list of valid brokers that the client can use to connect to the Kafka cluster. For availability reasons, it is best to specify multiple brokers in configuration of the client.

### Configuring the Kafka Client Library

#### Adding the Kafka Client Library to Your Application

``` xml
<dependency>
  <groupId>org.apache.kafka</groupId>
  <artifactId>kafka-clients</artifactId>
  <version>0.9.0.1</version>
</dependency>
```

The above is the correct dependency for the Kafka Client Library to use with the DCOS Cassandra service. After adding this dependency to your project, you should have access to the correct binary dependencies to interface with the Kafka Cluster.

#### Connecting the Kafka Client Library

The code snippet below demonstrates how to connect a Kafka Producer to the cluster and perform a loop of simple insertions.

``` java
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;

Map<String, Object> producerConfig = new HashMap<>();
producerConfig.put("bootstrap.servers", "10.0.0.26:9318,10.0.0.23:9505,10.0.0.24:9989");
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
```

The code snippet below demonstrates how to connect a Kafka Consumer to the cluster and perform a simple retrievals.

``` java
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

Map<String, Object> consumerConfig = new HashMap<>();
consumerConfig.put("bootstrap.servers", "10.0.0.26:9318,10.0.0.23:9505,10.0.0.24:9989");
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
```

### Configuring the Kafka Test Scripts

The following code connects to a DCOS-hosted Kafka instance using `bin/kafka-console-producer.sh` and `bin/kafka-console-consumer.sh` as an example:

``` bash
$ dcos kafka connection
{
    "broker_list_convenience": "--broker-list ip-10-0-3-230.us-west-2.compute.internal:9092, ip-10-0-3-231.us-west-2.compute.internal:9093",
    "brokers": [
        "ip-10-0-3-230.us-west-2.compute.internal:9092",
        "ip-10-0-3-231.us-west-2.compute.internal:9093"
    ],
    "zookeeper": "master.mesos:2181/kafka",
    "zookeeper_convenience": "--zookeeper master.mesos:2181/kafka"
}

$ dcos node ssh --master-proxy --leader
core@ip-10-0-6-153 ~ $ docker run -it mesosphere/kafka-client
root@7bc0e88cfa52:/kafka_2.10-0.8.2.2/bin# ./kafka-console-producer.sh --broker-list ip-10-0-3-230.us-west-2.compute.internal:9092 --topic test
This is a message
This is another message

root@7bc0e88cfa52:/kafka_2.10-0.8.2.2/bin# ./kafka-console-consumer.sh --zookeeper master.mesos:2181/kafka --topic test --from-beginning
This is a message
This is another message
```

## Managing

### Add a Broker

Increase the `BROKER_COUNT` value via Marathon as in any other configuration update.

### Upgrade Software

1. In the Marathon web interface, destroy the Kafka scheduler to be updated.

2. Verify that you no longer see it in the DCOS web interface.

3. If you are using the enterprise edition, create an JSON options file with your latest configuration and set your plan strategy to "STAGE"

``` json
{
    "service": {
        "phase_strategy": "STAGE"
    }
}
```

4. Install the latest version of Kafka:

``` bash
$ dcos package install kafka -—options=options.json
```

5. Rollout the new version of Kafka in the same way as a configuration update is rolled out.  See Configuration Update Plans.

## Troubleshooting

The Kafka service will be listed as "Unhealthy" when it detects any underreplicated partitions. This error condition usually indicates a malfunctioning broker.  Use the `dcos kafka topic under_replicated_partitions` and `dcos kafka topic describe <topic-name>` commands to find the problem broker and determine what actions are required.

Possible repair actions include `dcos kafka broker restart <broker-id>` and `dcos kafka broker replace <broker-id>`.  The replace operation is destructive and will irrevocably lose all data associated with the broker. The restart operation is not destructive and indicates an attempt to restart a broker process.

### Configuration Update Errors

The bolded entries below indicate the necessary changes needed to create a valid configuration:

<pre>
``` json
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan"
GET /service/kafka/v1/plan HTTP/1.1

{
    <b>"errors": [
        "Validation error on field \"BROKER_COUNT\": Decreasing this value (from 3 to 2) is not supported."
    ],</b>
    "phases": [
        {
            "blocks": [
                {
                    "hasDecisionPoint": false,
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
                    "hasDecisionPoint": false,
                    "id": "d4e72ee8-4608-423a-9566-1632ff0ab211",
                    "message": "Broker-0 is Complete",
                    "name": "broker-0",
                    "status": "Complete"
                },
                {
                    "hasDecisionPoint": false,
                    "id": "3ea30deb-9660-42f1-ad23-bd418d718999",
                    "message": "Broker-1 is Complete",
                    "name": "broker-1",
                    "status": "Complete"
                },
                {
                    "hasDecisionPoint": false,
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
    <b>"status": "Error"</b>
}
```
</pre>

### Replacing a Permanently Failed Server

If a machine has permanently failed, manual intervention is required to replace the broker or brokers that resided on that machine. Because DCOS Kafka uses persistent volumes, the service continuously attempts to replace brokers where their data has been persisted. In the case where a machine has permanently failed, use the Kafka CLI to replace the brokers.

In the example below, the broker with id `0` will be replaced on new machine as long as cluster resources are sufficient to satisfy the service’s placement constraints and resource requirements.

```bash
$ dcos kafka broker replace 0
```

### Security

The security features introduced in Apache Kafka 0.9 are not supported at this time.

## API Reference

For ongoing maintenance of the Kafka cluster itself, the Kafka framework exposes an HTTP API whose structure is designed to roughly match the tools provided by the Kafka distribution, such as `bin/kafka-topics.sh`.

The examples here provide equivalent commands using both `[dcos-cli](https://github.com/mesosphere/dcos-cli)` (with the `kafka` CLI module installed) and `curl`. These examples assume a service named `kafka` (the default), and the `curl` examples assume a DCOS cluster path  of `$DCOS_URI`. Replace these with appropriate values as needed.

The `dcos kafka` CLI commands have a `--framework-name` argument, allowing the user to specify which Kafka instance to query. The value defaults to `kafka`, so it's technically redundant to specify `--framework-name=kafka` in these examples. The default value for `--framework-name` can be customized via the DCOS CLI configuration:

``` bash
$ dcos config set kafka.framework_name new_default_name
```

### Connection Information

Kafka comes with many useful tools of its own that often require either Zookeeper connection information or the list of broker endpoints. This information can be retrieved in an easily consumable format from the `/connection` endpoint:

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/connection"
GET /service/kafka/v1/connection HTTP/1.1

{
    "broker_list_convenience": "--broker-list 10.0.0.1:9092, 10.0.0.2:9093, 10.0.0.3:9094",
    "brokers": [
        "10.0.0.1:9092",
        "10.0.0.2:9093",
        "10.0.0.3:9094"
    ],
    "zookeeper": "master.mesos:2181/kafka",
    "zookeeper_convenience": "--zookeeper master.mesos:2181/kafka"
}
```

The same information can be retrieved through the DCOS CLI:

``` bash
$ dcos kafka connection
{
    "broker_list_convenience": "--broker-list ip-10-0-3-230.us-west-2.compute.internal:9092, ip-10-0-3-231.us-west-2.compute.internal:9093",
    "brokers": [
        "ip-10-0-3-230.us-west-2.compute.internal:9092",
        "ip-10-0-3-231.us-west-2.compute.internal:9093"
    ],
    "zookeeper": "master.mesos:2181/kafka",
    "zookeeper_convenience": "--zookeeper master.mesos:2181/kafka"
}
```

### Broker Operations

#### Add Broker

Increase the `BROKER_COUNT` value via Marathon. This should be rolled as in any other configuration update. <!-- so you wouldn't use the API to do this? If so, I will move this to Management -->

#### List All Brokers

``` bash
$ dcos kafka --framework-name=kafka broker list
{
    "brokers": [
        "0",
        "1",
        "2"
    ]
}
```

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/brokers"
GET /service/kafka/v1/brokers HTTP/1.1

{
    "brokers": [
        "0",
        "1",
        "2"
    ]
}
```

#### Restart Single Broker

``` bash
$ dcos kafka --framework-name=kafka broker restart 0
[
    "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
]
```

``` bash
$ curl -X PUT --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/brokers/0"
PUT /service/kafka/v1/brokers/0 HTTP/1.1

[
    "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
]
```

### Topic Operations

These operations mirror what is available with `bin/kafka-topics.sh`.

#### List Topics

``` bash
$ dcos kafka --framework-name=kafka topic list
[
    "topic1",
    "topic0"
]
```

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics"
GET /service/kafka/v1/topics HTTP/1.1

[
    "topic1",
    "topic0"
]
```

#### Create Topic

``` bash
$ dcos kafka --framework-name=kafka topic create topic1 --partitions=3 --replication=3
{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Created topic \"topic1\".\n"
}
```

``` bash
$ curl -X POST --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics?name=topic1&partitions=3&replication=3"
POST /service/kafka/v1/topics?replication=3&name=topic1&partitions=3 HTTP/1.1

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Created topic \"topic1\".\n"
}
```

#### View Topic Offsets

There is an optional --time parameter which may be set to either "first", "last", or a timestamp in milliseconds as [described in the Kafka documentation](https://cwiki.apache.org/confluence/display/KAFKA/System+Tools#SystemTools-GetOffsetShell).

``` bash
$ dcos kafka --framework-name=kafka topic offsets topic1
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
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/topic1/offsets"
GET /service/kafka/v1/topics/topic1/offsets HTTP/1.1

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
$ dcos kafka --framework-name=kafka topic partitions topic1 2

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affected\nAdding partitions succeeded!\n"
}
```

``` bash
$ curl -X PUT --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/topic1?operation=partitions&partitions=2"
PUT /service/kafka/v1/topics/topic1?operation=partitions&partitions=2 HTTP/1.1

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affected\nAdding partitions succeeded!\n"
}
```

#### Run Producer Test on Topic

``` bash
$ dcos kafka --framework-name=kafka topic producer_test topic1 10

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.\n"
}
```

``` bash
$ curl -X PUT --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/topic1?operation=producer-test&messages=10"
PUT /service/kafka/v1/topics/topic1?operation=producer-test&messages=10 HTTP/1.1

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.\n"
}
```

#### Delete Topic

``` bash
$ dcos kafka --framework-name=kafka topic delete topic1

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Topic topic1 is marked for deletion.\nNote: This will have no impact if delete.topic.enable is not set to true.\n"
}
```

``` bash
$ curl -X DELETE --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/topic1"
DELETE /service/kafka/v1/topics/topic1?operation=delete HTTP/1.1

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Topic topic1 is marked for deletion.\nNote: This will have no impact if delete.topic.enable is not set to true.\n"
}
```

Note the warning in the output from the commands above. You can change the indicated "delete.topic.enable" configuration value as a configuration change.

#### List Under Replicated Partitions

``` bash
$ dcos kafka --framework-name=kafka topic under_replicated_partitions

{
    "exit_code": 0,
    "stderr": "",
    "stdout": ""
}
```

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/under_replicated_partitions"
GET /service/kafka/v1/topics/under_replicated_partitions HTTP/1.1

{
    "exit_code": 0,
    "stderr": "",
    "stdout": ""
}
```

#### List Unavailable Partitions

``` bash
$ dcos kafka --framework-name=kafka topic unavailable_partitions

{
    "exit_code": 0,
    "stderr": "",
    "stdout": ""
}
```

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/unavailable_partitions"
GET /service/kafka/v1/topics/unavailable_partitions HTTP/1.1

{
    "exit_code": 0,
    "stderr": "",
    "stdout": ""
}
```

### Config Updates

#### View Plan Status

``` bash
$ curl --header "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan"
GET /service/kafka/v1/plan HTTP/1.1

{
    "errors": [],
    "phases": [
        {
            "blocks": [
                {
                    "hasDecisionPoint": false,
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
                    "hasDecisionPoint": false,
                    "id": "8a727290-062a-44bf-87ba-3ebfe005aa18",
                    "message": "Broker-0 is Complete",
                    "name": "broker-0",
                    "status": "Complete"
                },
                {
                    "hasDecisionPoint": false,
                    "id": "5d2aa9d9-5eda-4a13-aea1-9d98e4cf7ea7",
                    "message": "Broker-1 is Complete",
                    "name": "broker-1",
                    "status": "Complete"
                },
                {
                    "hasDecisionPoint": false,
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

```

## Limitations

### Configurations

The "disk" configuration value is denominated in MB. We recommend you set the configuration value `log_retention_bytes` to a value smaller than the indicated "disk" configuration. See [instructions for customizing these values](#configure-kafka-broker-properties).

#### Pitfalls of Managing Configurations Outside of the Framework

The Kafka framework's core responsibility is to deploy and maintain the deployment of a Kafka cluster whose configuration has been specified. In order to do this the framework makes the assumption that it has ownership of broker configuration. If an end-user makes modifications to individual brokers through out-of-band configuration operations, the framework will almost certainly override those modifications at a later time. If a broker crashes, it will be restarted with the configuration known to the scheduler, not one modified out-of-band. If a configuration update is initiated, all out-of-band modifications will be overwritten during the rolling update.

### Brokers

The number of deployable brokers is constrained by two factors. First, brokers have specified required resources, so brokers may not be placed if the DCOS cluster lacks the requisite resources. Second, the specified "PLACEMENT_STRATEGY" environment variable may affect how many brokers can be created in a Kafka cluster. By default the value is "ANY," so brokers are placed anywhere and are only constrained by the resources of the cluster. A second option is "NODE."  In this case only one broker may be placed on a given DCOS agent.


## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development guide.
