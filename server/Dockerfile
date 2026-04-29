# Multi-stage, multi-arch build for the Pathling FHIR server.
#
# Build context: the repo root (so the builder stage can access ui/ and the
# sql-on-fhir submodule).
#
# The builder stage runs once on the host architecture (Java bytecode and the
# bundled UI are platform-neutral). Only the runtime stage is replicated per
# target architecture by buildx, which keeps multi-arch builds fast.

# ---------------------------------------------------------------------------
# Builder
# ---------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS builder

ARG MAVEN_VERSION=3.9.11
ENV MAVEN_HOME=/opt/maven
ENV BUN_INSTALL=/root/.bun
ENV PATH=${MAVEN_HOME}/bin:${BUN_INSTALL}/bin:$PATH

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip git ca-certificates \
 && curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        -o /tmp/maven.tgz \
 && mkdir -p ${MAVEN_HOME} \
 && tar -xzf /tmp/maven.tgz -C ${MAVEN_HOME} --strip-components=1 \
 && rm /tmp/maven.tgz \
 && curl -fsSL https://bun.sh/install | bash \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .

# .git is excluded from the build context (see .dockerignore), so the
# git-commit-id plugin would fail. Instead we synthesise the properties file
# it normally generates from build args. The file is read at runtime by
# au.csiro.pathling.PathlingServerVersion.
ARG PATHLING_VERSION=unknown
ARG GIT_SHA=unknown
RUN mkdir -p /src/server/src/main/resources \
 && printf 'git.build.version=%s\ngit.commit.id.abbrev=%s\n' \
        "${PATHLING_VERSION}" "${GIT_SHA}" \
      > /src/server/src/main/resources/pathling-server-version.properties

# Build the server module. The UI is bundled into target/classes/static/admin
# by the server pom's resources plugin (after `bun run build`).
WORKDIR /src/server
RUN --mount=type=cache,target=/root/.m2/repository \
    --mount=type=cache,target=/root/.bun/install/cache \
    mvn --batch-mode package \
        -DskipTests -DskipITs \
        -Dmaven.gitcommitid.skip=true

# Collect the runtime classpath into a single directory so the runtime stage
# can simply COPY it.
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn --batch-mode dependency:copy-dependencies \
        -DincludeScope=runtime \
        -DoutputDirectory=/build/dependency \
 && cp -r target/classes /build/classes


# ---------------------------------------------------------------------------
# Runtime (built per target platform).
# ---------------------------------------------------------------------------
FROM amazoncorretto:21 AS runtime

WORKDIR /app

COPY --from=builder /build/dependency/ /app/libs/
COPY --from=builder /build/classes/    /app/classes/

EXPOSE 8080

ENTRYPOINT [ \
  "java", \
  "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED", \
  "--add-opens=java.base/java.lang=ALL-UNNAMED", \
  "--add-opens=java.base/java.net=ALL-UNNAMED", \
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", \
  "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED", \
  "-cp", "/app/classes:/app/libs/*", \
  "au.csiro.pathling.PathlingServer" \
]
