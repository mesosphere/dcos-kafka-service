# DC/OS Apache Kafka Service

This is the source repository for the [DC/OS Apache Kafka](https://mesosphere.com/service-catalog/kafka) package.

Service documentation can be found here : [DC/OS Apache Kafka Documentation](https://docs.mesosphere.com/services/kafka/)

## Integration Test Builds Matrix

|  | DC/OS 1.13 | DC/OS 2.0 | DC/OS 2.1 | DC/OS Master |
|------------|------------|------------|------------|------------|
| Permissive | <a href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_113_Permissive&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_113_Permissive)/statusIcon" /></a > | <a href="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_20_Permissive&guest=1" ><img src="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_20_Permissive)/statusIcon" /></a > |  <a href="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_21_Permissive&guest=1" ><img src="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_21_Permissive)/statusIcon" /></a > | <a  href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_master_Permissive&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_master_Permissive)/statusIcon" /> </a > |
| Strict     | <a href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_113_Strict&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_113_Strict)/statusIcon" /></a >         | <a href="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_20_Strict&guest=1" ><img src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_20_Strict)/statusIcon"  /></a  > |  <a href="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_21_Strict&guest=1" ><img src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_21_Strict)/statusIcon"  /></a  > | <a  href ="https://teamcity.mesosphere.io/viewType.html?buildTypeId=DataServices_Kafka_IntegrationTests_DCOS_master_Strict&guest=1" ><img  src ="https://teamcity.mesosphere.io/app/rest/builds/buildType:(id:DataServices_Kafka_IntegrationTests_DCOS_master_Strict)/statusIcon" /></a > |


## Development

Make sure your Docker daemon is [running under a non-root
user](https://docs.docker.com/install/linux/linux-postinstall/).

### Cloning the repository

Using HTTPS
```bash
git clone https://github.com/mesosphere/dcos-kafka-service.git
```
Using SSH
```bash
git clone git@github.com:mesosphere/dcos-kafka-service.git
```

All commands assume that you're in the project root directory.

```bash
cd /dcos-kafka-service
```

### Building the package

First make sure you have a valid AWS session configured either in the form of:
- `~/.aws/credentials` file and exported `AWS_PROFILE` environment variable

or

- exported `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables

If you work for Mesosphere, check out
[maws](https://github.com/mesosphere/maws).

The following command should be run from your host. It will run a Docker
container and build the package there:

```bash
./test.sh -i

# Once you are in the container, run
./frameworks/kafka/build.sh aws
```

### Running tests

First make sure you have a DC/OS cluster where your tests can be run on.

```bash
export CLUSTER_URL="http://your-dcos-cluster.com"
```

Optionally, export a stub Universe URL so that tests run against a particular
stub version of the service.

```bash
export STUB_UNIVERSE_URL='https://universe-converter.mesosphere.com/transform?url=...'
```

Specify the test which you want to run like:

```bash
export PYTEST_ARGS='frameworks/kafka/tests/test_sanity.py -vvv --capture=no --maxfail=1'
```
And run the tests using:
```bash
./test.sh kafka
```
This will run all the tests in the `test_sanity.py` suite.

If you want to relation, the `-k` flag will match tests in all test modules for
tests named `test_service_health`. If you wish to only match tests in a single test
module you'll need to set `PYTEST_ARGS` similar to the example above.

```bash
export PYTEST_ARGS='frameworks/kafka/tests/test_sanity.py -k test_service_health -vvv --capture=no --maxfail=1'
```

### Style guide

#### Opening pull requests

PR titles should be in imperative mood, useful, concise and follow the following
format:

```
[DCOS-xxxxx] Add support for new thing.
```

In the example above a JIRA ticket is referenced with the `[DCOS-xxxxx]` tag. If
for some reason the PR isn't related to a ticket, feel free to use "free-form"
tags, ideally ones that were already used like `[DOCS]`, `[SDK]`, `[MISC]`,
`[TOOLS]` or even `[SDK][TOOLS]` for increased specificity.

PR descriptions should include additional context regarding what is achieved
with the PR, why is it needed, rationale regarding decisions that were made,
possibly with pointers to actual commits.

Example:
```
To make it possible for the new thing we had to:
- Prepare this other thing (2417f95)
- Clean up something else (cq4c78e)

This was required because of this and that.

Example output of thing:

    {
      "a": 2
    }


Please look into http://www.somewebsite.com/details-about-thing
for more context.
```
Or if you are working on a task internally, then below will also work.
```
This PR resolves [D2IQ-XXXX](https://jira.d2iq.com/D2IQ-XXXX)
```

#### Merging pull requests

When all checks are green, a PR should be merged as a squash-commit, with its
message being the PR title followed by the PR number. Example:

```
[DCOS-xxxxx] Add support for new thing. (#42)
```

The description for the squash-commit will ideally be the PR description
verbatim. If the PR description was empty (it probably shouldn't have been!) the
squash-commit description will by default be a list of all the commits in the
PR's branch. That list should be cleaned up to only contain useful entries (no
`fix`, `formatting`, `changed foo`, `refactored bar`), or rewritten so that
additional context is added to the commit, like in the example above for PR
descriptions.

