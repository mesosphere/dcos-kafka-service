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
