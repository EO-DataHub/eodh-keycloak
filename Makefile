# Default input parameters
docker ?= docker
image ?= eodh-keycloak
version ?= dev

.SILENT:
MAKEFLAGS += --no-print-directory

# Local smoke build only — publishing to ECR is done by CI (see README).
.PHONY: container-build
container-build:
	$(docker) build -t $(image):$(version) .

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
