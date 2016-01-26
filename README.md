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
By default 3 Brokers are deployed.  Alternatively an ovverride can be specified for the desired number of brokers like this:
```
$ dcos package install --yes kafka --options=kafka0.json
$ cat kafka0.json
{
  "kafka": {
    "broker-count": 5
  }
}
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
