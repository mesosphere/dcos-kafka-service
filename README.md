# DC/OS Apache Kafka Service

This is the source repository for the [DC/OS Apache Kafka](https://mesosphere.com/service-catalog/kafka) package.

Service documentation can be found here : [DC/OS Apache Kafka Documentation](https://docs.mesosphere.com/services/kafka/)

## Integration Test Builds Matrix

|  | DC/OS 1.12 | DC/OS 1.13 | DC/OS 2.0 | DC/OS Master |
|------------|------------|------------|------------|------------|
| Permissive | <a  href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_112_Permissive&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_112_Permissive)/statusIcon" /></a > | <a  href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_113_Permissive&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_113_Permissive)/statusIcon" /></a > | <a  href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_20_Permissive&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_20_Permissive)/statusIcon" /></a > | <a  href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_master_Permissive&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_master_Permissive)/statusIcon" /> </a > |
| Strict | <a  href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_112_Strict&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_112_Strict)/statusIcon" /></a > | <a  href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_113_Strict&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_113_Strict)/statusIcon" /></a > | <a   href  ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_20_Strict&guest=1"  ><img   src  ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_20_Strict)/statusIcon"  /></a  > | <a  href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_master_Strict&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_master_Strict)/statusIcon" /></a > |


## Building the package

In order to build a stub-universe hosted on an S3 bucket run:
```bash
./frameworks/kafka/build.sh aws
```
