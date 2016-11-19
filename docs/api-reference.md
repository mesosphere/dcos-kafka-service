---
post_title: API Reference
menu_order: 60
feature_maturity: preview
enterprise: 'no'
---


<a name="#rest-auth"></a>
# REST API Authentication
REST API requests must be authenticated. This authentication is only applicable for interacting with the Kafka REST API directly. You do not need the token to access the Kafka nodes themselves.
 
If you are using Enterprise DC/OS, follow these instructions to [create a service account and an authentication token](https://docs.mesosphere.com/1.8/administration/id-and-access-mgt/service-auth/custom-service-auth/). You can then configure your service to automatically refresh the authentication token when it expires. To get started more quickly, you can also [get the authentication token without a service account](https://docs.mesosphere.com/1.8/administration/id-and-access-mgt/iam-api/), but you will need to manually refresh the token.

If you are using open source DC/OS, follow these instructions to [pass your HTTP API token to the DC/OS endpoint](https://docs.mesosphere.com/1.8/administration/id-and-access-mgt/auth-api/#passing-your-http-api-token-to-dc-os-endpoints). 

Once you have the authentication token, you can store it in an environment variable and reference it in your REST API calls:

```
$ export AUTH_TOKEN=uSeR_t0k3n
```

The `curl` examples in this document assume that an auth token has been stored in an environment variable named `AUTH_TOKEN`.

If you are using Enterprise DC/OS, the security mode of your installation may also require the `--ca-cert` flag when making REST calls. Refer to [Obtaining and passing the DC/OS certificate in cURL requests](https://docs.mesosphere.com/1.8/administration/tls-ssl/#get-dcos-cert) for information on how to use the `--cacert` flag. [If your security mode is `disabled`](https://docs.mesosphere.com/1.8/administration/tls-ssl/), do not use the `--ca-cert` flag.

For ongoing maintenance of the Kafka cluster itself, the Kafka service exposes an HTTP API whose structure is designed to roughly match the tools provided by the Kafka distribution, such as `bin/kafka-topics.sh`.

The examples here provide equivalent commands using both the [DC/OS CLI](https://github.com/mesosphere/dcos-cli) (with the `kafka` CLI module installed) and `curl`. These examples assume a service named `kafka` (the default), and the `curl` examples assume a DC/OS cluster path of `$DCOS_URI`. Replace these with appropriate values as needed.

The `dcos kafka` CLI commands have a `--name` argument, allowing the user to specify which Kafka instance to query. The value defaults to `kafka`, so it's technically redundant to specify `--name=kafka` in these examples.

# Connection Information

Kafka comes with many useful tools of its own that often require either Zookeeper connection information or the list of broker endpoints. This information can be retrieved in an easily consumable format from the `/connection` endpoint:

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
        "zookeeper": "master.mesos:2181/dcos-service-kafka"
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
        "zookeeper": "master.mesos:2181/dcos-service-kafka"
    }
    

# Broker Operations

## Add Broker

Increase the `BROKER_COUNT` value via Marathon. This should be rolled as in any other configuration update.

## List All Brokers

    $ dcos kafka --name=kafka broker list
    {
        "brokers": [
            "0",
            "1",
            "2"
        ]
    }
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/brokers"
    GET /service/kafka/v1/brokers HTTP/1.1
    
    {
        "brokers": [
            "0",
            "1",
            "2"
        ]
    }
    

## Restart Single Broker

Restarts the broker in-place.

    $ dcos kafka --name=kafka broker restart 0
    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]
    
    
    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/brokers/0"
    PUT /service/kafka/v1/brokers/0 HTTP/1.1
    
    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]
    

## Replace Single Broker

Restarts the broker and replaces its existing resource/volume allocations. The new broker instance may also be placed on a different machine.

    $ dcos kafka --name=kafka broker replace 0
    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]
    
    
    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/brokers/0?replace=true"
    PUT /service/kafka/v1/brokers/0 HTTP/1.1
    
    [
        "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
    ]
    

# Topic Operations

These operations mirror what is available with `bin/kafka-topics.sh`.

## List Topics

    $ dcos kafka --name=kafka topic list
    [
        "topic1",
        "topic0"
    ]
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics"
    GET /service/kafka/v1/topics HTTP/1.1
    
    [
        "topic1",
        "topic0"
    ]
    

## Describe Topic

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
    
    
    $ curl -X POST -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/topic1"
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
    

## Create Topic

    $ dcos kafka --name=kafka topic create topic1 --partitions=3 --replication=3
    {
        "message": "Output: Created topic "topic1".n"
    }
    
    
    $ curl -X POST -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics?name=topic1&partitions=3&replication=3"
    POST /service/kafka/v1/topics?replication=3&name=topic1&partitions=3 HTTP/1.1
    
    {
        "message": "Output: Created topic "topic1".n"
    }
    

## View Topic Offsets

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
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/topic1/offsets?time=last"
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
    

## Alter Topic Partition Count

    $ dcos kafka --name=kafka topic partitions topic1 2
    {
        "message": "Output: WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affectednAdding partitions succeeded!n"
    }
    
    
    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/topic1?operation=partitions&partitions=2"
    PUT /service/kafka/v1/topics/topic1?operation=partitions&partitions=2 HTTP/1.1
    
    {
        "message": "Output: WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affectednAdding partitions succeeded!n"
    }
    

## Run Producer Test on Topic

    $ dcos kafka --name=kafka topic producer_test topic1 10
    
    {
        "message": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.n"
    }
    
    
    $ curl -X PUT -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/topic1?operation=producer-test&messages=10"
    PUT /service/kafka/v1/topics/topic1?operation=producer-test&messages=10 HTTP/1.1
    
    {
        "message": "10 records sent, 70.422535 records/sec (0.07 MB/sec), 24.20 ms avg latency, 133.00 ms max latency, 13 ms 50th, 133 ms 95th, 133 ms 99th, 133 ms 99.9th.n"
    }
    

Runs the equivalent of the following command from the machine running the Kafka Scheduler:

    kafka-producer-perf-test.sh 
        --topic <topic> 
        --num-records <messages> 
        --throughput 100000 
        --record-size 1024 
        --producer-props bootstrap.servers=<current broker endpoints>
    

## Delete Topic

    $ dcos kafka --name=kafka topic delete topic1
    
    {
        "message": "Topic topic1 is marked for deletion.nNote: This will have no impact if delete.topic.enable is not set to true.n"
    }
    
    
    $ curl -X DELETE -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/topic1"
    DELETE /service/kafka/v1/topics/topic1 HTTP/1.1
    
    {
        "message": "Topic topic1 is marked for deletion.nNote: This will have no impact if delete.topic.enable is not set to true.n"
    }
    

Note the warning in the output from the commands above. You can change the indicated "delete.topic.enable" configuration value as a configuration change.

## List Under Replicated Partitions

    $ dcos kafka --name=kafka topic under_replicated_partitions
    
    {
        "message": ""
    }
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/under_replicated_partitions"
    GET /service/kafka/v1/topics/under_replicated_partitions HTTP/1.1
    
    {
        "message": ""
    }
    

## List Unavailable Partitions

    $ dcos kafka --name=kafka topic unavailable_partitions
    
    {
        "message": ""
    }
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/topics/unavailable_partitions"
    GET /service/kafka/v1/topics/unavailable_partitions HTTP/1.1
    
    {
        "message": ""
    }
    

# Config History

These operations relate to viewing the service's configuration history.

## List Configuration IDs

    $ dcos kafka --name=kafka config list
    
    [
        "319ebe89-42e2-40e2-9169-8568e2421023",
        "294235f2-8504-4194-b43d-664443f2132b"
    ]
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/configurations"
    GET /service/kafka/v1/configurations HTTP/1.1
    
    [
        "319ebe89-42e2-40e2-9169-8568e2421023",
        "294235f2-8504-4194-b43d-664443f2132b"
    ]
    

## Describe Configuration

This configuration shows a default per-broker memory allocation of 2048 (configured via the `BROKER_MEM` parameter):

    $ dcos kafka --name=kafka config describe 319ebe89-42e2-40e2-9169-8568e2421023
    
    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "diskType": "ROOT",
            "javaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafkaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 2048, // <<--
            "overriderUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phaseStrategy": "INSTALL",
            "placementStrategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/configurations/319ebe89-42e2-40e2-9169-8568e2421023"
    GET /service/kafka/v1/configurations/319ebe89-42e2-40e2-9169-8568e2421023 HTTP/1.1
    
    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "diskType": "ROOT",
            "javaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafkaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 2048, // <<--
            "overriderUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phaseStrategy": "INSTALL",
            "placementStrategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }
    

## Describe Target Configuration

The target configuration, meanwhile, shows an increase of configured per-broker memory from 2048 to 4096 (again, configured as `BROKER_MEM`):

    $ dcos kafka --name=kafka config target
    
    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "diskType": "ROOT",
            "javaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafkaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 4096, // <<--
            "overriderUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phaseStrategy": "INSTALL",
            "placementStrategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/configurations/target"
    GET /service/kafka/v1/configurations/target HTTP/1.1
    
    {
        "brokerConfiguration": {
            "containerHookUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/container-hook-0.2.5.tgz",
            "cpus": 1,
            "disk": 5000,
            "diskType": "ROOT",
            "javaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/jre-8u72-linux-x64.tar.gz",
            "kafkaUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/kafka_2.10-0.9.0.1.tgz",
            "mem": 4096, // <<--
            "overriderUri": "https://s3.amazonaws.com/downloads.mesosphere.io/kafka/assets/0.2.5-0.9.0.1/overrider.zip"
        },
        "kafkaConfiguration": {
            [...]
        },
        "serviceConfiguration": {
            "count": 3,
            "name": "kafka",
            "phaseStrategy": "INSTALL",
            "placementStrategy": "NODE",
            "principal": "kafka-principal",
            "role": "kafka-role",
            "user": ""
        }
    }
    

# Config Updates

These options relate to viewing and controlling rollouts and configuration updates.

## View Plan Status

Displays all Phases and Blocks in the service Plan. If a rollout is currently in progress, this returns a 503 HTTP code with response content otherwise unchanged.

    $ dcos kafka --name=kafka plan show
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
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan"
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
    

## View Active Plan Entries

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
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan/status"
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
    
    
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan/status"
    GET /service/kafka/v1/plan/status HTTP/1.1
    
    {
        "stage": {
            "errors": [],
            "phase_count": 2,
            "status": "Complete"
        }
    }
    

## Upgrade Interaction

These operations are only applicable when `PHASE_STRATEGY` is set to `STAGE`, they have no effect when it is set to `INSTALL`. See the Changing Configuration at Runtime part of the Configuring section for more information.

### Continue

    $ dcos kafka --name=kafka plan continue
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan/continue"
    

### Interrupt

    $ dcos kafka --name=kafka plan interrupt
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan/interrupt"
    

### Force Complete

    $ dcos kafka --name=kafka plan force
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan/forceComplete"
    

### Restart

    $ dcos kafka --name=kafka plan restart
    $ curl -H "Authorization: token=$AUTH_TOKEN" "$DCOS_URI/service/kafka/v1/plan/restart"

 [15]: https://cwiki.apache.org/confluence/display/KAFKA/System+Tools#SystemTools-GetOffsetShell
