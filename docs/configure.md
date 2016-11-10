---
post_title: Configure
menu_order: 30
feature_maturity: preview
enterprise: 'no'
---












<a name="changing-configuration-at-runtime"></a>

# Changing Configuration at Runtime

You can customize your cluster in-place when it is up and running.

The Kafka scheduler runs as a Marathon process and can be reconfigured by changing values from the DC/OS web interface. These are the general steps to follow:

1.  Go to the `Services` tab of the DC/OS web interface.
2.  Click the name of the Kafka service to be updated.
3.  Within the Kafka instance details view, click the `Edit` button.
4.  In the dialog that appears, expand the `Environment Variables` section and update any field(s) to their desired value(s). For example, to [increase the number of Brokers][8], edit the value for `BROKER_COUNT`. Do not edit the value for `FRAMEWORK_NAME` or `BROKER_DISK` or `PLACEMENT_STRATEGY`.
5.  A `PHASE_STRATEGY` of `STAGE` should also be set. See "Configuration Deployment Strategy" below for more details.
6.  Click `Change and deploy configuration` to apply any changes and cleanly reload the Kafka service scheduler. The Kafka cluster itself will persist across the change.

## Configuration Deployment Strategy

Configuration updates are rolled out through execution of Update Plans. You can configure the way these plans are executed.

## Configuration Update Plans

In brief, "plans" are composed of "phases," which are in turn composed of "blocks." Two possible configuration update strategies specify how the blocks are executed. These strategies are specified by setting the `PHASE_STRATEGY` environment variable on the scheduler. By default, the strategy is `INSTALL`, which rolls changes out to one broker at a time with no pauses.

The alternative is the `STAGE` strategy. This strategy injects two mandatory human decision points into the configuration update process. Initially, no configuration update will take place: the service waits for a human to confirm the update plan is correct. You may then decide to either continue the configuration update through a REST API call, or roll back the configuration update by replacing the original configuration through the DC/OS web interface in exactly the same way as a configuration update is specified above.

After specifying that an update should continue, one block representing one broker will be updated and the configuration update will again pause. At this point, you have a second opportunity to roll back or continue. If you decide to continue a second time, the rest of the brokers will be updated one at a time until all the brokers are using the new configuration. You may interrupt an update at any point. After interrupting, you can choose to continue or roll back. Consult the "Configuration Update REST API" for these operations.

## Configuration Update REST API

There are two phases in the update plans for Kafka: Mesos task reconciliation and update. Mesos task reconciliation is always executed without need for human interaction.

Make the REST request below to view the current plan. See the REST API Authentication part of the REST API Reference section for information on how this request must be authenticated.

    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan"
    GET $DCOS_URI/service/kafka/v1/plan HTTP/1.1

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

    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan"
    GET $DCOS_URI/service/kafka/v1/plan HTTP/1.1

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

    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan?cmd=continue"
    PUT $DCOS_URI/service/kafka/v1/plan?cmd=continue HTTP/1.1

    {
        "Result": "Received cmd: continue"
    }


After you execute the continue operation, the plan will look like this:

    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan"
    GET $DCOS_URI/service/kafka/v1/plan HTTP/1.1

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

    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN"  "$DCOS_URI/service/kafka/v1/plan?cmd=interrupt"
    PUT $DCOS_URI/service/kafka/v1/plan?cmd=interrupt HTTP/1.1

    {
        "Result": "Received cmd: interrupt"
    }

**Note:** The interrupt command can’t stop a block that is `InProgress`, but it will stop the change on the subsequent blocks.

# Configuration Options

The following describes the most commonly used features of the Kafka service and how to configure them via the DC/OS CLI and from the DC/OS web interface. View the [default `config.json` in DC/OS Universe][11] to see all possible configuration options.

## Service Name

The name of this Kafka instance in DC/OS. This is an option that cannot be changed once the Kafka cluster is started: it can only be configured via the DC/OS CLI `--options` flag when the Kafka instance is created.

*   **In DC/OS CLI options.json**: `name`: string (default: `kafka`)
*   **DC/OS web interface**: The service name cannot be changed after the cluster has started.

## Broker Count

Configure the number of brokers running in a given Kafka cluster. The default count at installation is three brokers. This number may be increased, but not decreased, after installation.

*   **In DC/OS CLI options.json**: `broker-count`: integer (default: `3`)
*   **DC/OS web interface**: `BROKER_COUNT`: `integer`

## Broker Port

Configure the port number that the brokers listen on. If the port is set to a particular value, this will be the port used by all brokers. The default port is 9092.  Note that this requires that `placement-strategy` be set to `NODE` to take effect, since having every broker listening on the same port requires that they be placed on different hosts. Setting the port to 0 indicates that each Broker should have a random port in the 9092-10092 range. 

*   **In DC/OS CLI options.json**: `broker-port`: integer (default: `9092`)
*   **DC/OS web interface**: `BROKER_PORT`: `integer`

## Configure Broker Placement Strategy

`ANY` allows brokers to be placed on any node with sufficient resources, while `NODE` ensures that all brokers within a given Kafka cluster are never colocated on the same node. This is an option that cannot be changed once the Kafka cluster is started: it can only be configured via the DC/OS CLI `--options` flag when the Kafka instance is created.

*   **In DC/OS CLI options.json**: `placement-strategy`: `ANY` or `NODE` (default: `ANY`)
*   **DC/OS web interface**: `PLACEMENT_STRATEGY`: `ANY` or `NODE`

## Configure Kafka Broker Properties

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
## Disk Type 

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

* **DC/OS web interface**: Set the environment variable `DISK_TYPE`: `MOUNT`

When configured to `MOUNT` disk type, the scheduler selects a disk on an agent whose capacity is equal to or greater than the configured `disk` value.

## JVM Heap Size

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

* **DC/OS web interface**: Set the environment variable `BROKER_HEAP_MB`: `2000`

**Note**: The total memory allocated for the Mesos task is specified by the `BROKER_MEM` configuration parameter. The value for `BROKER_HEAP_MB` should not be greater than `BROKER_MEM` value. Also, if `BROKER_MEM` is greater than `BROKER_HEAP_MB` then the Linux operating system will use `BROKER_MEM` - `BROKER_HEAP_MB` for [PageCache](https://en.wikipedia.org/wiki/Page_cache).

## Alternate ZooKeeper 

By default the Kafka framework uses the ZooKeeper ensemble made available on the Mesos masters of a DC/OS cluster. You can configure an alternate ZooKeeper at install time.
To configure it:
* **DC/OS CLI options.json**:

```json
    {
        "kafka": {
            "kafka_zookeeper_uri": "zookeeper.marathon.mesos:2181"
        }
    }
```

This configuration option cannot be changed after installation.

## Recovery and Health Checks

You can enable automated replacement of brokers and configure the circumstances under which they are replaced.

### Enable Broker Replacement

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

* **DC/OS web interface**: Set the environment variable `ENABLE_REPLACEMENT`: `true` to enable replacement.

**Warning:** The replacement mechanism is unaware of whether the broker it is destructively replacing had the latest copy of data. Depending on your replication policy and the degree and duration of the permanent failures, you may lose data.

The following configuration options control the circumstances under which a broker is replaced.

### Minumum Grace Period

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

* **DC/OS web interface**: Set the environment variable `RECOVERY_GRACE_PERIOD_SEC`: `1200`

### Minumum Delay Between Replacements

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

* **DC/OS web interface**: Set the environment variable `REPLACE_DELAY_SEC`: `600`

The following configurations control the health checks that determine when a broker has failed:

### Enable Health Check

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

* **DC/OS web interface**: Set the environment variable `ENABLE_BROKER_HEALTH_CHECK`: `true`

### Health Check Delay

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

* **DC/OS web interface**: Set the environment variable `BROKER_HEALTH_CHECK_DELAY_SEC`: `15`

### Health Check Interval

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

* **DC/OS web interface**: Set the environment variable `BROKER_HEALTH_CHECK_INTERVAL_SEC`: `10`

### Health Check Timeout

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

* **DC/OS web interface**: Set the environment variable `BROKER_HEALTH_CHECK_TIMEOUT_SEC`: `20`

### Health Check Grace Period

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

* **DC/OS web interface**: Set the environment variable `BROKER_HEALTH_CHECK_GRACE_SEC`: `10`

### Maximum Consecutive Health Check Failures

```json
    {
        "health_check_max_consecutive_failures":{
            "description":"The the number of consecutive failures which cause a Broker process to exit.",
            "type":"number",
            "default":3
        }
    }
```

* **DC/OS web interface**: Set the environment variable `BROKER_HEALTH_CHECK_MAX_FAILURES`: `3`

 [8]: #broker-count
 [11]: https://github.com/mesosphere/universe/tree/1-7ea/repo/packages/K/kafka/6
 [12]: #changing-configuration-at-runtime
