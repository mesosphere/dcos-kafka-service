
define run_container
	@echo "Entering container $(DOCKER_IMAGE):$(SDK_VERSION)..."
	docker run -i \
		-e INSIDE_CONTAINER="true" \
		-e S3_BUCKET=$(S3_BUCKET) \
		-e CLUSTER_URL=$(CLUSTER_URL) \
		-e UNIVERSE_URL_PATH=$(UNIVERSE_URL_PATH) \
		-e AWS_PROFILE=$(AWS_PROFILE) \
		-v $(PWD):/build \
		-v $(HOME)/.gradle:/root/.gradle \
		-v $(HOME)/.m2:/root/.m2 \
		-v $(HOME)/.aws/credentials:/root/.aws/credentials \
		-w /build \
		--privileged \
		$(2) $(DOCKER_IMAGE):$(SDK_VERSION) $(1)
endef
