FROM library/postgres:17.3-bookworm

LABEL maintainer="Bear Giles <bgiles@coyotesong.com>"
LABEL org.opencontainers.image.authors="bgiles@coyotesong.com"
LABEL org.opencontainers.image.description="PostgreSQL with pl/java build environment preinstalled (Debian)"
LABEL org.opencontainers.image.source="https://github.com/tada/pljava"

RUN apt-get update && apt-get install -y postgresql-17-pljava

# we normally copy files to /usr/share/java - that's where '-java' packages go.
# but for now we're reusing exsting jar

COPY 1-install-pljava.sql /docker-entrypoint-initdb.d/
COPY 2-install-backend.sql /docker-entrypoint-initdb.d/

