#
# This Dockerfile defines a multi-stage build that produces the following
# three images:
#
#  - pgxnclient - generic PGXN client (based on PostgreSQL image)
#  - pljava     - PostgreSQL database with PL/JAVA installed
#  - pljavadev  - 'pljava' image with development tools
#
#  FIXME
#    - use local build artifacts!
#
#  TODO
#    - update maintainer LABEL ?
#

# for documentation
ARG BASE_IMAGE_NAME
ARG DEBIAN_RELEASE
ARG JAVA_VERSION
ARG PG_MAJOR
ARG PG_VERSION
ARG PLJAVA_VERSION

#
# First image - only pgxnclient
#
FROM ${BASE_IMAGE_NAME}:${PG_VERSION}-${DEBIAN_RELEASE} AS pgxnclient

ARG BASE_IMAGE_NAME
ARG PG_VERSION
ARG PG_MAJOR
ARG DEBIAN_RELEASE

LABEL maintainer="Bear Giles <bgiles@coyotesong.com>"
LABEL org.opencontainers.image.authors="bgiles@coyotesong.com"
LABEL org.opencontainers.image.description="PostgreSQL with extension dev packages preinstalled (Debian)"
LABEL org.opencontainers.image.source="https://github.com/tada/pljava"

ENV DEBIAN_FRONTEND=noninteractive

EXPOSE 5432

VOLUME /var/log/postgresql/
VOLUME /docker-entrypoint-initdb.d/

# add apt source repository (note: error with /usr/bin/sed)
RUN sed -e 's/^deb /deb-src /' </etc/apt/sources.list.d/pgdg.list >/etc/apt/sources.list.d/pgdg-src.list

# DO NOT do a general update - that would also update the postgresql version
# Unfortunately that also means we can't pull debian pljava source later
# RUN /usr/bin/apt-get upgrade -y

# Update apt database then install pgtap, pgxnclient, plus a few packages
# useful for manual tasks later.
RUN /usr/bin/apt-get update && /usr/bin/apt-get install -y \
       pgxnclient \
       postgresql-${PG_MAJOR}-pgtap \
       apt-utils \
       whiptail

# Remove cached files, unnecessary packages, etc.
RUN /usr/bin/apt-get autoclean && /usr/bin/apt-get autoremove

#
# Second image - it adds pl/java
#
FROM pgxnclient as pljava

ARG PG_MAJOR

LABEL maintainer="Bear Giles <bgiles@coyotesong.com>"
LABEL org.opencontainers.image.authors="bgiles@coyotesong.com"
LABEL org.opencontainers.image.description="PostgreSQL with pl/java preinstalled (Debian)"
LABEL org.opencontainers.image.source="https://github.com/tada/pljava"

ENV DEBIAN_FRONTEND=noninteractive

# Install pljava and a few useful java packages
RUN /usr/bin/apt-get install -y \
    libpostgresql-jdbc-java \
    libsaxon-java \
    libsaxonb-java

# Remove cached files, unnecessary packages, etc.
RUN /usr/bin/apt-get autoclean && apt-get autoremove

#RUN mkdir -p /docker-entrypoint-initdb.d \
#             /usr/lib/postgresql/${PG_MAJOR}/lib \
#             /usr/share/postgresql/${PG_MAJOR}/extension \
#             /usr/share/postgresql/${PG_MAJOR}/pljava \

ADD content/docker-entrypoint-initdb.d/* /docker-entrypoint-initdb.d/
ADD content/*.so                         /usr/lib/postgresql/${PG_MAJOR}/lib/
ADD content/pljava.control               /usr/share/postgresql/${PG_MAJOR}/extension
ADD content/*.jar                        /usr/share/postgresql/${PG_MAJOR}/pljava/

RUN /bin/chmod 0755 /docker-entrypoint-initdb.d/*.sh

#
# Third image - it creates and builds pljava extension (for local development)
#
FROM pljava as pljavadev

ARG PG_MAJOR
ARG PG_VERSION
ARG JAVA_VERSION

LABEL maintainer="Bear Giles <bgiles@coyotesong.com>"
LABEL org.opencontainers.image.authors="bgiles@coyotesong.com"
LABEL org.opencontainers.image.description="PostgreSQL with pl/java build environment preinstalled (Debian)"
LABEL org.opencontainers.image.source="https://github.com/tada/pljava"

ENV DEBIAN_FRONTEND=noninteractive

# install git, java, C compiler, and libraries
RUN /usr/bin/apt-get update && \
    /usr/bin/apt-get install -y \
      debhelper-compat \
      default-jdk-headless \
      dpkg-dev \
      gcc \
      git \
      libecpg-dev \
      libkrb5-dev \
      maven \
      openjdk-${JAVA_VERSION}-jdk \
      postgresql-all \
      postgresql-server-dev-${PG_MAJOR}

# download source for pl/java
# the latter allows us to build a new deb package with the local changes
WORKDIR /usr/local/src

#############################################################
# FIXME - use local build products !
#############################################################
# RUN /usr/bin/apt-get source postgresql-${PG_MAJOR}-pljava
# WORKDIR /usr/local/src/postgresql-pljava-${PLJAVA_VERSION}
# RUN /usr/bin/dpkg-buildpackage --build=binary --no-sign

