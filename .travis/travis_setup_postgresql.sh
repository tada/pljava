#!/usr/bin/env bash
if [ "$TRAVIS_OS_NAME" = "osx" ]; then
    export PGDATA="${HOME}/postgres"

    initdb -D "$PGDATA"
    pg_ctl -w -t 300 -c -o '-p 5432' -l /tmp/postgres.log start
    wait
    sleep 5 && createuser -s postgres
else
    if [ "$POSTGRESQL_VERSION" = "SOURCE" ]; then
        export PGDATA="/home/travis/postgres"
        /usr/local/pgsql/bin/pg_ctl -D ${PGDATA} -U postgres initdb
        /usr/local/pgsql/bin/pg_ctl -D ${PGDATA} -w -t 300 -c -o '-p 5432' -l /tmp/postgres.log start
        wait
        sleep 5 && createuser -s postgres
    else
        sudo service postgresql start ${POSTGRESQL_VERSION}
    fi
fi
