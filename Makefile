
# framework name of the package. The variable is also used to install package.
FRAMEWORK_NAME ?= kafka

# PATH to framework directory.
FRAMEWORK_PATH ?= frameworks/$(FRAMEWORK_NAME)

# default UPLOAD method.
UPLOAD_METHOD ?= aws

# docker image that is used to build/test/install the package.
DOCKER_IMAGE ?= mesosphere/dcos-commons

# default S3_BUCKET where the stub is uploaded.
S3_BUCKET ?= infinity-artifacts

# default path where the stub url will be stored.
UNIVERSE_URL_PATH ?= .stub_universe_url

# default path for the package options
PACKAGE_OPTIONS ?= .package-options.json

# default DC/OS user name
DCOS_USERNAME ?= bootstrapuser

# default DC/OS password
DCOS_PASSWORD ?= deleteme

# pytest version
PY_TEST_VERSION ?= 3.10.0

# max failures, used when running a single test
PY_SINGLE_TEST_MAX_FAILURE ?= 0

# default service name where the package is installed
SERVICE_NAME ?= $(FRAMEWORK_NAME)

# default service account name
SERVICE_ACCOUNT_NAME ?= $(SERVICE_NAME)-sa

# default service account secret name
SERVICE_ACCOUNT_SECRET_NAME ?= $(SERVICE_NAME)-sa-secret

include ./make/make.mk
include ./make/docker.mk
