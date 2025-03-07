# Default input parameters
docker ?= docker
image ?= public.ecr.aws/eodh/eodh-keycloak
version ?= 26.0.4-0.2.4

.SILENT:
MAKEFLAGS += --no-print-directory

.PHONY: container-build
container-build:
	$(docker) build -t $(image):$(version) .

.PHONY: container-push
container-push:
	$(docker) push $(image):$(version)