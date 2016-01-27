# kafka-private

Kafka Framework

## Build
```
$ ./gradlew shadowjar
$ ./build-container-hook.sh
```

## Deploy
```
$ dcos config prepend package.sources https://github.com/mesosphere/universe/archive/kafka.zip
$ dcos config unset --index=1
$ dcos config show
core.dcos_url=http://pool-f663-elasticl-1qy8c6vsnk1h5-1954355182.us-west-2.elb.amazonaws.com/
core.email=gabriel@mesosphere.io
core.reporting=True
core.timeout=5
core.token=fa1146fc5ca5a01e88a1a4ceac4ebe5306526b508a8e3d00ebfa0ba6a7d346ef
package.cache=/Users/gabriel/.dcos/cache
package.sources=['https://github.com/mesosphere/universe/archive/kafka.zip']
$ dcos package update
$ dcos package install --yes kafka
```

## Operations
### Connection Information
Kafka comes with many useful tools.  Often they require either Zookeeper connection information, or the list of Broker endpoints.  This information can be retrieved in an easily consumable formation from the /connection endpoint as below.

```
$ http $DCOS_URI/service/kafka0/connection -p hbHB
GET //service/kafka0/connection HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
Connection: keep-alive
Host: gabriel-4-elasticl-zltw2lt3kmwl-647734499.us-west-2.elb.amazonaws.com
User-Agent: HTTPie/0.9.2

HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 196
Content-Type: application/json
Date: Wed, 27 Jan 2016 18:01:22 GMT
Server: openresty/1.7.10.2

{
    "brokers": [
        "ip-10-0-0-233.us-west-2.compute.internal:9092",
        "ip-10-0-0-233.us-west-2.compute.internal:9093",
        "ip-10-0-0-233.us-west-2.compute.internal:9094"
    ],
    "zookeeper": "master.mesos:2181/kafka0"
}
```
### Brokers
By default 3 Brokers are deployed.  Alternatively an override can be specified for the desired number of brokers like this:
```
$ dcos package install --yes kafka --options=kafka0.json
$ cat kafka0.json
{
  "kafka": {
    "broker-count": 5
  }
}
```

To update the number of Brokers at runtime, changing the environment variable BROKER_COUNT in Marathon and deploying the config change will produce the indicated number of brokers.

**Placement Strategy**
By default installing Kafka will place Brokers on whatever nodes have enough Resources.  To restrict deployment to one Broker per node you can deploy with either at deployment time or at runtime.  At runtime one can indicate the placement strategy like this:
```
$ cat kafka0.json
{
  "kafka": {
    "placement-strategy": "NODE"
  }
}
```

To update this value at runtime change the environment variable PLACEMENT_STRATEGY to NODE in Marathon.  Then restart each Broker in a safe rolling manner.

**Persistent Volumes**
By default Kafka will use the sandbox available to Mesos Tasks for storing data.  This storage goes away on Task failure.  So if a Broker crashes the data on it is lost forever.  This is fine for dev environments.  In production environments Kafka should be deployed with the following option enabled:
```
$ cat kafka0.json
{
  "kafka": {
    "pv": true
  }
}
```

To update this value at runtime change the environment variable BROKER_PV to true in Marathon.  Then restart each Broker in a safe rolling manner.

**List**
```
$ http $DCOS_URI/service/kafka0/brokers -p hbHB
GET //service/kafka0/brokers HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
Connection: keep-alive
Host: pool-a210-elasticl-cm28sy3wpqzb-1312862935.us-west-2.elb.amazonaws.com
User-Agent: HTTPie/0.9.2

HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 13
Content-Type: application/json
Date: Tue, 26 Jan 2016 23:26:32 GMT
Server: openresty/1.7.10.2

[
    "0",
    "1",
    "2"
]
```

**Restart**

To restart all brokers:
```
$ http PUT $DCOS_URI/service/kafka0/brokers -p hbHB
PUT //service/kafka0/brokers HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 0
Host: pool-a210-elasticl-cm28sy3wpqzb-1312862935.us-west-2.elb.amazonaws.com
User-Agent: HTTPie/0.9.2

HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 148
Content-Type: application/json
Date: Tue, 26 Jan 2016 23:28:48 GMT
Server: openresty/1.7.10.2

[
    "broker-1__759c9fc2-3890-4921-8db8-c87532b1a033",
    "broker-2__0b444104-e210-4b78-8d19-f8938b8761fd",
    "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
]
```

To restart a single broker:
```
$ http PUT $DCOS_URI/service/kafka0/brokers/0 -p hbHB
PUT //service/kafka0/brokers HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 0
Host: pool-a210-elasticl-cm28sy3wpqzb-1312862935.us-west-2.elb.amazonaws.com
User-Agent: HTTPie/0.9.2

HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 148
Content-Type: application/json
Date: Tue, 26 Jan 2016 23:28:48 GMT
Server: openresty/1.7.10.2

[
    "broker-0__9c426c50-1087-475c-aa36-cd00d24ccebb"
]
```

### Topics
**Create**
```
$ http POST $DCOS_URI/service/kafka0/topics name==topic1 partitions==3 replication==3 -phbHB
POST //service/kafka0/topics?replication=3&name=topic1&partitions=3 HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 0
Host: pool-a210-elasticl-cm28sy3wpqzb-1312862935.us-west-2.elb.amazonaws.com
User-Agent: HTTPie/0.9.2

HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 66
Content-Type: application/json
Date: Tue, 26 Jan 2016 23:29:45 GMT
Server: openresty/1.7.10.2

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Created topic \"topic1\".\n"
}
```
**Alter**

Alter partition count:
```
$ http PUT $DCOS_URI/service/kafka0/topics/topic1 operation==partitions partitions==4 -phbHB
PUT //service/kafka0/topics/topic1?operation=partitions&partitions=4 HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 0
Host: pool-a210-elasticl-cm28sy3wpqzb-1312862935.us-west-2.elb.amazonaws.com
User-Agent: HTTPie/0.9.2

HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 200
Content-Type: application/json
Date: Tue, 26 Jan 2016 23:31:12 GMT
Server: openresty/1.7.10.2

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affected\nAdding partitions succeeded!\n"
}
```

Alter config value:
```
http PUT $DCOS_URI/service/kafka0/topics/topic0 operation==config key==foo value==bar
```
Delete config value:
```
http PUT $DCOS_URI/service/kafka0/topics/topic0 operation==deleteConfig key==foo
```

**Delete**
```
$ http PUT $DCOS_URI/service/kafka0/topics/topic0 operation==delete -p hbHB
PUT //service/kafka0/topics/topic0?operation=delete HTTP/1.1
Accept: */*
Accept-Encoding: gzip, deflate
Connection: keep-alive
Content-Length: 0
Host: pool-a210-elasticl-cm28sy3wpqzb-1312862935.us-west-2.elb.amazonaws.com
User-Agent: HTTPie/0.9.2

HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 152
Content-Type: application/json
Date: Tue, 26 Jan 2016 23:32:59 GMT
Server: openresty/1.7.10.2

{
    "exit_code": 0,
    "stderr": "",
    "stdout": "Topic topic0 is marked for deletion.\nNote: This will have no impact if delete.topic.enable is not set to true.\n"
}
```
