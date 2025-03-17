# Default input parameters
docker ?= docker
image ?= public.ecr.aws/eodh/eodh-keycloak
version ?= 26.0.4-0.2.5

.SILENT:
MAKEFLAGS += --no-print-directory

.PHONY: container-build
container-build:
	$(docker) build -t $(image):$(version) .

.PHONY: container-push
container-push:
	$(docker) push $(image):$(version)

.PHONY: run-dev
run-dev:
	$(docker) compose up --build -d && \
	$(docker) compose logs -f -t

.PHONY: stop-dev
stop-dev:
	$(docker) compose down

.PHONY: destroy-dev
destroy-dev:
	$(docker) compose rm -fsv