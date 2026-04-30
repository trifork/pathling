# Pathling repo Makefile.
#
# Convenience targets for building container images. Use buildx so you get
# the same multi-arch behaviour locally and in CI.

# ---------------------------------------------------------------------------
# Pathling FHIR server image
# ---------------------------------------------------------------------------
IMAGE_REPO ?= ghcr.io/trifork/pathling
IMAGE_TAG  ?= azure-mi-v2
PLATFORMS  ?= linux/amd64,linux/arm64
DOCKERFILE := server/Dockerfile
CONTEXT    := .
BUILDER    ?= pathling-builder

# Pulled from server/pom.xml (first <version> element), with a `git` fallback
# for the SHA. Override on the command line if needed.
PATHLING_VERSION ?= $(shell grep -m1 '<version>' server/pom.xml | sed -n 's|.*<version>\(.*\)</version>.*|\1|p')
GIT_SHA          ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo unknown)

BUILD_ARGS := \
  --build-arg PATHLING_VERSION=$(PATHLING_VERSION) \
  --build-arg GIT_SHA=$(GIT_SHA)

# ---------------------------------------------------------------------------
# Custom Superset image (with SQLAlchemy SQL-on-FHIR dialect)
# ---------------------------------------------------------------------------
SUPERSET_IMAGE_REPO ?= ghcr.io/trifork/superset
SUPERSET_IMAGE_TAG  ?= sql-on-fhir-v1
SUPERSET_VERSION    ?= latest
SUPERSET_DOCKERFILE := superset/Dockerfile
SUPERSET_CONTEXT    := superset

SUPERSET_BUILD_ARGS := \
  --build-arg SUPERSET_VERSION=$(SUPERSET_VERSION)

.PHONY: help \
        docker-builder docker-build docker-buildx-push docker-push \
        superset-build superset-buildx-push superset-push

help:
	@echo "Pathling server image:"
	@echo "  docker-build         Build the server image for the host arch and"
	@echo "                       load it into the local Docker daemon."
	@echo "  docker-buildx-push   Build a multi-arch image ($(PLATFORMS)) and"
	@echo "                       push it to \$$(IMAGE_REPO):\$$(IMAGE_TAG)."
	@echo "  docker-push          Alias for docker-buildx-push."
	@echo ""
	@echo "Custom Superset image:"
	@echo "  superset-build       Build the Superset image for the host arch and"
	@echo "                       load it into the local Docker daemon."
	@echo "  superset-buildx-push Build a multi-arch Superset image and push it"
	@echo "                       to \$$(SUPERSET_IMAGE_REPO):\$$(SUPERSET_IMAGE_TAG)."
	@echo "  superset-push        Alias for superset-buildx-push."
	@echo ""
	@echo "Variables (override on the command line):"
	@echo "  IMAGE_REPO=$(IMAGE_REPO)"
	@echo "  IMAGE_TAG=$(IMAGE_TAG)"
	@echo "  SUPERSET_IMAGE_REPO=$(SUPERSET_IMAGE_REPO)"
	@echo "  SUPERSET_IMAGE_TAG=$(SUPERSET_IMAGE_TAG)"
	@echo "  SUPERSET_VERSION=$(SUPERSET_VERSION)"
	@echo "  PLATFORMS=$(PLATFORMS)"

docker-builder:
	@if ! docker buildx inspect $(BUILDER) >/dev/null 2>&1; then \
	  docker buildx create --name $(BUILDER) --driver docker-container --use; \
	else \
	  docker buildx use $(BUILDER); \
	fi
	docker buildx inspect --bootstrap >/dev/null

# Single-arch build for the host, loaded into the local Docker daemon for
# quick `docker run` testing.
docker-build: docker-builder
	docker buildx build \
	  -f $(DOCKERFILE) \
	  -t $(IMAGE_REPO):$(IMAGE_TAG) \
	  $(BUILD_ARGS) \
	  --load \
	  $(CONTEXT)

# Multi-arch build + push. Requires `docker login $(IMAGE_REPO)` first.
docker-buildx-push: docker-builder
	docker buildx build \
	  -f $(DOCKERFILE) \
	  -t $(IMAGE_REPO):$(IMAGE_TAG) \
	  --platform $(PLATFORMS) \
	  $(BUILD_ARGS) \
	  --push \
	  $(CONTEXT)

docker-push: docker-buildx-push

# ---------------------------------------------------------------------------
# Superset targets
# ---------------------------------------------------------------------------

superset-build: docker-builder
	docker buildx build \
	  -f $(SUPERSET_DOCKERFILE) \
	  -t $(SUPERSET_IMAGE_REPO):$(SUPERSET_IMAGE_TAG) \
	  $(SUPERSET_BUILD_ARGS) \
	  --load \
	  $(SUPERSET_CONTEXT)

superset-buildx-push: docker-builder
	docker buildx build \
	  -f $(SUPERSET_DOCKERFILE) \
	  -t $(SUPERSET_IMAGE_REPO):$(SUPERSET_IMAGE_TAG) \
	  --platform $(PLATFORMS) \
	  $(SUPERSET_BUILD_ARGS) \
	  --push \
	  $(SUPERSET_CONTEXT)

superset-push: superset-buildx-push
