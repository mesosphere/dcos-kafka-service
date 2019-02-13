# DC/OS Apache Kafka Service

This is the source repository for the [DC/OS Apache Kafka](https://mesosphere.com/service-catalog/kafka) package.

## Building the package

### Requirements

* AWS access
    * Suggestion, use [`maws`](https://github.com/mesosphere/maws)
* Go
    * On macOS, use ``brew install golang``
* JDK 8

## Build step

In order to build a stub-universe hosted on an S3 bucket run:

```bash
./frameworks/kafka/build.sh aws
```

This outputs

## Running tests


###

## How to update the Kafka base tech

This is not necessarily a comprehensive list of changes.

* Choose a version of Kafka to update to, from XXXCHANGELOG
* Go to XXXJENKINSLINK and something something Jenkins
* In XXXFILE, change the version to point the version used
* Create a PR which runs CI
* Fix all issues shown in CI
* Read the CHANGELOGs between the current base tech and the desired base tech
* Note any new configuration variables, and changes to default configurations
* For each new configuration variable, choose whether to expose this configuration variable to DC/OS users
* XXX
