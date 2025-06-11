#
# This Dockerfile defines a multi-stage build that produces the following
# three images:
#
#  - pgxnclient - generic PGXN client (based on PostgreSQL image)
#  - pljava     - PostgreSQL database with PL/JAVA installed
#  - pljavadev  - 'pljava' image with development tools
#
# Build Arguments (must be specified when building images)
#
#  ALPINE_RELEASE
#  PG_VERSION
#  PG_MAJOR
#  PLJAVA_VERSION
#  JAVA_VERSION
#
#  FIXME
#    - use local build artifacts!
#
#  TODO
#    - update maintainer LABEL ?
#
#
FROM postgres:${PG_VERSION}-${ALPINE_RELEASE} AS pgxnclient

LABEL maintainer="Bear Giles <bgiles@coyotesong.com>"
LABEL org.opencontainers.image.authors="bgiles@coyotesong.com"
LABEL org.opencontainers.image.description="PostgreSQL with extension dev packages preinstalled (Debian)"
LABEL org.opencontainers.image.source="https://github.com/tada/pljava"

# the rest is deleted since alpine does not use 'apt'.

#
# Second image - it adds pl/java
#
FROM pgxnclient as pljava

#
# Third image - it creates and builds pljava extension (for local development)
#
FROM pljava as pljavadev