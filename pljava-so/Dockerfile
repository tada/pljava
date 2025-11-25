# TODO: pull various VERS from the environment...

FROM postgres:16-bookworm
LABEL authors="Bear Giles <bgiles@coyotesong.com>"

ENV TARGET=target
ENV RESOURCES=src/main/resources

# can/should be set as build property...
ENV PG_VERS=16
ENV LIBDIR=/usr/lib/postgresql/${PG_VERS}/lib
ENV EXTDIR=/usr/share/postgresql/${PG_VERS}/extension

ENV SO_NAME=pljava

ENV FDW_NAME=blackhole_fdw
ENV FDW_VERS=1.6.9

# this will install the standard version. It can be updated once the docker image is running in a test environment.
RUN apt-get update && apt-get install -y postgresql-${PG_VERS}-pljava postgresql-${PG_VERS}-pljava-dbgsym

COPY ${TARGET}/pljava-pgxs/lib${SO_NAME}-so-2-SNAPSHOT.so ${LIBDIR}/lib${SO_NAME}-so-${FDW_VERS}.so

COPY ${RESOURCES}/fdw/${FDW_NAME}.control ${EXTDIR}/
COPY ${RESOURCES}/fdw/sql/${FDW_NAME}*.sql ${EXTDIR}/

# ENTRYPOINT ["top", "-b"]
