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
# Brokers
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
http $DCOS_URI/service/kafka0/brokers
```

**Restart**

To restart all brokers:
```
http PUT $DCOS_URI/service/kafka0/brokers
```

To restart a single broker:
```
http PUT $DCOS_URI/service/kafka0/brokers/0
```

# Topics
Create:
```
http POST $DCOS_URI/service/kafka0/topics name==topic0 partitions==3 replication==3
```
Alter:
```
bin/kafka-topics.sh --zookeeper zk_host:port/chroot --alter --topic my_topic_name --partitions 40
http POST $DCOS_URI/service/kafka0/topics/topic0 operation==partitions partitions==4

bin/kafka-topics.sh --zookeeper zk_host:port/chroot --alter --topic my_topic_name --config x=y
http POST $DCOS_URI/service/kafka0/topics/topic0 operation==config key==foo value==bar

bin/kafka-topics.sh --zookeeper zk_host:port/chroot --alter --topic my_topic_name --deleteConfig x
http POST $DCOS_URI/service/kafka0/topics/topic0 operation==deleteConfig key==foo
```
Delete:
```
bin/kafka-topics.sh --zookeeper zk_host:port/chroot --delete --topic my_topic_name
http PUT $DCOS_URI/service/kafka0/topics/topic0 operation==delete
```
