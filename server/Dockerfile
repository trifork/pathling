# Multi-arch image for the Pathling FHIR server.
#
# Expects the Maven build to have been run already, producing:
#   target/classes/        compiled classes + bundled UI + resources
#   target/dependency/     all runtime dependency JARs (via
#                          dependency:copy-dependencies)
#
# Build via buildx; the base image is multi-arch so no QEMU is required for
# the Java app itself.

FROM amazoncorretto:21

WORKDIR /app

# Dependencies first so this layer is cached when application code changes.
COPY target/dependency/ /app/libs/
COPY target/classes/    /app/classes/

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
