# DCOS Kafka test client

This package comes in two parts:
* Java code which links against the stock Java Kafka client libraries to produce and consume data against a Kafka cluster, while simultaneously printing throughput stats to `stdout`.
* A python helper script named `launcher.py` which launches the Java test clients in a DCOS cluster as Marathon tasks.

Basic usage looks like this:

```
$ pip install -r requirements.txt
$ python launcher.py http://your-dcos-cluster.com
or...
$ export DCOS_URI=http://your-dcos-cluster.com
$ python launcher.py
```

On DC/OS, a raw auth token should be provided, or else 401 errors will result:

```
$ python launcher.py --auth_token=osnethuoesnud http://your-dcos-cluster.com
or...
$ export DCOS_URI=http://your-dcos-cluster.com
$ export AUTH_TOKEN=osnethuoesnud
$ python launcher.py
```

On DC/OS EE, a username and password can instead be provided:

```
$ python launcher.py --username=foo --password=bar http://your-dcos-cluster.com
or...
$ export DCOS_URI=http://your-dcos-cluster.com
$ export DCOS_USERNAME=foo
$ export DCOS_PASSWORD=bar
$ python launcher.py
```

See `python launcher.py --help` for a list of available options, but keep in mind that this is not an exhaustive list of all possible configuration:
* All test client parameters are exposed as environment variables. The fields exposed by `launcher.py` are just the subset that are expected to be commonly changed. See [ClientConfigs.java](src/main/java/org/apache/mesos/kafka/testclient/ClientConfigs.java) for a full listing.
* Any environment variables prefixed with `KAFKA_OVERRIDE_` when running the clients will be translated and forwarded to the underlying Kafka client library as parameters. For example, `KAFKA_OVERRIDE_GROUP_ID` is forwarded as `group.id` when constructing the underlying Kafka client. This behavior mirrors the behavior of the DCOS Kafka Scheduler.
