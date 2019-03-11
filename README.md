# DC/OS Apache Kafka Service

This is the source repository for the [DC/OS Apache Kafka](https://mesosphere.com/service-catalog/kafka) package.

## Building the package



### Pre-requisites

- Install Docker, preferentially >18.09

- Setup AWS credentials. The build uses the `AWS_PROFILE` environment variable to know which AWS profile to use. Make sure to export the env variable `export AWS_PROFILE=YOUR_AWS_PROFILE` or add it to `.bashrc` or `zshrc` 

### Build and upload the stub to S3

Make sure you have the pre-requisites installed and AWS credentials in place.

```bash
make build
```

That would launch a container, run the build and the outcome will be the stub universe url that will be saved in the file `.stub_universe_url`. That file path can be a customized running `UNIVERSE_URL_PATH=/your/custom/path make build` 	



## Install the package

Installing the package in a DC/OS cluster would require a DC/OS cluster being setup. As this require some set of binaries in place. The ideal way is to install the package from the container.

The easiest way is entering inside the container:

```
export CLUSTER_URL=DCOS_CLUSTER_URL
make enter-container
make install
```

This will launch the container from where we can already install the package. The `make install` will take the stub present in `${UNIVERSE_URL_PATH}` that defaults to `.stub_universe_url`

If ones wants to install a custom stub, writing the stub URL to the `.stub_universe_url` would work.

`make install` takes care of a bunch of things:

- detect the security mode for the DC/OS cluster, and create the service account and secret if needed.
- generate package options to install the kafka framework.



## Attach to a DC/OS cluster

```
export CLUSTER_URL=DCOS_CLUSTER_URL
make enter-container
make attach-dcos
```

The attach-dcos target assumes few default values like for username and password. But you can provide with different username and password

```
export CLUSTER_URL=DCOS_CLUSTER_URL
make enter-container
export DCOS_USERNAME=CUSTOM_USER_NAME
export DCOS_PASSWORD=CUSTOM_PASSWORD
make attach-dcos
```



## Running the tests

In order to run the tests, we must have a DC/OS cluster available.

```
export CLUSTER_URL=DCOS_CLUSTER_URL
make enter-container
make test
```

When running the tests the stub present in the file `.stub_universe_url` is used.

### Running tests from one file

```
make test-${test-file-name}
```

Example:

```
make test-test_sanity.py
```

### Running a single test

```
make test-${test-file-name}::${test-name}
```

Example:

```
make test-test_sanity.py::test_pod_cli
```


Disabling the log collection when running tests

```
export INTEGRATION_TEST_LOG_COLLECTION=false
make test-test_sanity.py::test_pod_cli
```



Read more [here](/make/)

