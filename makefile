# Default input parameters
docker ?= docker
image ?= public.ecr.aws/n1b3o1k2/eodh-keycloak
version ?= 26.0.4-0.1.0

.SILENT:
MAKEFLAGS += --no-print-directory

.PHONY: container-build
container-build:
	$(docker) build -t $(image):$(version) .

.PHONY: container-push
container-push:
	$(docker) push $(image):$(version)