# Default input parameters
docker ?= docker
image ?= public.ecr.aws/n1b3o1k2/eodh-keycloak
version ?= 26.0.4-0.1.0

.SILENT:
MAKEFLAGS += --no-print-directory

.PHONY: build
build:
	$(docker) build -t $(image):$(version) .

.PHONY: publish
publish:
	$(docker) push $(image):$(version)