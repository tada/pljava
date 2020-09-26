if [ "$TRAVIS_OS_NAME" = "osx" ]; then
    HOMEBREW_NO_AUTO_UPDATE=1
    export HOMEBREW_NO_AUTO_UPDATE

    brew uninstall postgis postgresql

    if [ "$POSTGRESQL_VERSION" = "12" ]; then
        unset POSTGRESQL_VERSION
    else
        POSTGRESQL_VERSION="@$POSTGRESQL_VERSION"
    fi
    brew install "postgresql${POSTGRESQL_VERSION}"

    pgConfig="/usr/local/opt/postgresql${POSTGRESQL_VERSION}/bin/pg_config"
else
    sudo sh -c 'service postgresql stop || true'
    sudo apt-get -qq remove postgresql libpq-dev libpq5 postgresql-client-common postgresql-common --purge
    if [ "$POSTGRESQL_VERSION" = "SOURCE" ]; then
        sudo apt-get -qq install build-essential libreadline-dev zlib1g-dev flex bison libxml2-dev libxslt-dev libssl-dev libxml2-utils xsltproc

        git clone git://git.postgresql.org/git/postgresql.git ../postgresql
        cd ../postgresql
        git checkout REL_12_STABLE

        ./configure --with-libxml --enable-cassert --enable-debug CFLAGS='-ggdb -Og -g3 -fno-omit-frame-pointer' --quiet
        make --silent && sudo make install

        cd contrib
        make --silent && sudo make install

        pgConfig="/usr/local/pgsql/bin/pg_config"

        cd ../../pljava
    else
        . /etc/lsb-release
        echo "deb http://apt.postgresql.org/pub/repos/apt/ $DISTRIB_CODENAME-pgdg main ${POSTGRESQL_VERSION}" > ../pgdg.list
        sudo mv ../pgdg.list /etc/apt/sources.list.d/
        wget --quiet -O - https://apt.postgresql.org/pub/repos/apt/ACCC4CF8.asc | sudo apt-key add -
        sudo apt-get -qq update
        sudo apt-get -qq install "postgresql-${POSTGRESQL_VERSION}" "postgresql-server-dev-${POSTGRESQL_VERSION}" libecpg-dev libkrb5-dev
        pgConfig=pg_config
    fi
fi
