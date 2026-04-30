# Custom Apache Superset image with the SQLAlchemy SQL-on-FHIR dialect
# pre-installed, so Superset can use sql-on-fhir:// connection strings.
#
# Built and pushed via the repo Makefile (`make superset-buildx-push`).

ARG SUPERSET_VERSION=latest
FROM apache/superset:${SUPERSET_VERSION}

USER root

# Install git (needed to pip-install from a git URL), then add the extra
# Python packages into Superset's venv site-packages directory.
RUN apt-get update \
 && apt-get install -y --no-install-recommends git \
 && rm -rf /var/lib/apt/lists/* \
 && pip install --no-cache-dir \
        --target=/app/.venv/lib/python3.10/site-packages \
        psycopg2-binary \
        "cachelib[redis]" \
        "git+https://github.com/trifork/sqlalchemy-sql-on-fhir.git"

USER superset
