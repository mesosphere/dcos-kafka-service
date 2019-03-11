.PHONY : install validate-aws-credentials get-security-mode
#This makefile should not contain anything specific for a framework, like the framework name

SDK_VERSION := $$(./gradlew  --project-dir=frameworks/$(FRAMEWORK_NAME) printSDKVersion -q)

RANDOM_ALPHANUMERIC := $(shell cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 16 | head -n 1)

get-container-version: enter-container
	@echo "$(DOCKER_IMAGE):$(SDK_VERSION)"

enter-container:
ifndef INSIDE_CONTAINER
	$(call run_container, /bin/bash, -t -e HOST_USER=$(shell id -un))
endif

build:
ifndef INSIDE_CONTAINER
	$(call run_container, /bin/bash -c "make build")
endif
ifdef INSIDE_CONTAINER
	@$(MAKE) validate-aws-credentials
	$(FRAMEWORK_PATH)/build.sh $(UPLOAD_METHOD)
endif

validate-aws-credentials:
ifdef AWS_PROFILE
	@aws s3api head-bucket --bucket $(S3_BUCKET) \
	|| (echo "Cannot access $(S3_BUCKET). Make sure aws credentials are valid or aren't expired"; exit 1)
endif
ifndef AWS_PROFILE
	$(error AWS_PROFILE is not defined. Consider adding it to ~/.bashrc or ~/.zshrc)
endif

install: export STUB_UNIVERSE_URL := $(shell cat $(UNIVERSE_URL_PATH))
install: attach-dcos create-service-accounts generate-package-options
	@echo "Installing the $(FRAMEWORK_NAME) package $(STUB_UNIVERSE_URL) in $(CLUSTER_URL)" >&2
ifdef STUB_UNIVERSE_URL
	$(MAKE) build
else
	@dcos package repo remove $(FRAMEWORK_NAME)-$(UPLOAD_METHOD) || exit 0
	@dcos package repo add $(FRAMEWORK_NAME)-$(UPLOAD_METHOD) --index=0 $(STUB_UNIVERSE_URL)
	@dcos package install $(FRAMEWORK_NAME) --cli --yes
	@dcos package install $(FRAMEWORK_NAME) --options=$(PACKAGE_OPTIONS) --yes
	@echo "dcos $(FRAMEWORK_NAME) plan show deploy"
endif

create-service-accounts: attach-dcos detect-security-mode
create-service-accounts:
	@locale-gen en_US.UTF-8
	SERVICE_NAME=$(SERVICE_NAME) \
	SECURITY_MODE=$(SECURITY_MODE) \
	SERVICE_ACCOUNT_NAME=$(SERVICE_ACCOUNT_NAME) \
	SERVICE_ACCOUNT_SECRET_NAME=$(SERVICE_ACCOUNT_SECRET_NAME) \
	make/create-service-account.sh

generate-package-options: attach-dcos detect-security-mode
	@PACKAGE_OPTIONS=$(PACKAGE_OPTIONS) \
	SERVICE_NAME=$(SERVICE_NAME) \
	SERVICE_ACCOUNT_NAME=$(SERVICE_ACCOUNT_NAME) \
	SERVICE_ACCOUNT_SECRET_NAME=$(SERVICE_ACCOUNT_SECRET_NAME) \
	SECURITY_MODE=$(SECURITY_MODE) \
	 	make/generate-package-options.sh
	@echo "Package options generated to $(PACKAGE_OPTIONS)" >&2



uninstall: attach-dcos
	@dcos package uninstall $(FRAMEWORK_NAME) --yes

attach-dcos: container-required
ifdef CLUSTER_URL
	@dcos cluster setup --no-check --username=$(DCOS_USERNAME) --password=$(DCOS_PASSWORD) $(CLUSTER_URL) || exit 0
	@dcos task
else
	$(error CLUSTER_URL is not defined)
endif

test: export STUB_UNIVERSE_URL := $(shell cat $(UNIVERSE_URL_PATH))
test: attach-dcos
	@curl -o /tmp/$(RANDOM_ALPHANUMERIC)-requirements.txt https://raw.githubusercontent.com/mesosphere/dcos-commons/$(SDK_VERSION)/test_requirements.txt
	pip3 install pytest==$(PY_TEST_VERSION) && \
		pip3 install -r /tmp/$(RANDOM_ALPHANUMERIC)-requirements.txt
	@py.test -vvv --capture=no $(FRAMEWORK_PATH)/tests --maxfail=$(PY_SINGLE_TEST_MAX_FAILURE)

test-%: export STUB_UNIVERSE_URL := $(shell cat $(UNIVERSE_URL_PATH))
test-%: attach-dcos
	@echo "Starting test $* ..." >&2
	@curl -o /tmp/$(RANDOM_ALPHANUMERIC)-requirements.txt https://raw.githubusercontent.com/mesosphere/dcos-commons/$(SDK_VERSION)/test_requirements.txt
	pip3 install pytest==$(PY_TEST_VERSION) && \
		pip3 install -r /tmp/$(RANDOM_ALPHANUMERIC)-requirements.txt
	@py.test -vvv --capture=no $(FRAMEWORK_PATH)/tests/$* --maxfail=$(PY_SINGLE_TEST_MAX_FAILURE)

container-required:
ifndef INSIDE_CONTAINER
	$(error This target should be called from inside the docker. Run 'make enter-container' first)
endif

# relies on the /dcos-metadata/bootstrap-config.json
# detect the current attached cluster
detect-security-mode:
	$(eval SECURITY_MODE := $(shell curl -X GET -s -k -H "Authorization: token=$(shell dcos config show core.dcos_acs_token)" \
		$(shell dcos config show core.dcos_url)/dcos-metadata/bootstrap-config.json | jq .security))
	@echo "Cluster security mode is $(SECURITY_MODE)" >&2

clean-stub:
	@rm -f $(UNIVERSE_URL_PATH)

clean: clean-stub
	rm -f $(PACKAGE_OPTIONS)
	rm -rf $(FRAMEWORK_PATH)/build
