if [ "$TRAVIS_OS_NAME" = "osx" ]; then
    brew uninstall postgis postgresql

    if [ "$POSTGRESQL_VERSION" = "12" ]; then
        unset POSTGRESQL_VERSION
    else
        POSTGRESQL_VERSION="@$POSTGRESQL_VERSION"
    fi
    brew install "postgresql${POSTGRESQL_VERSION}"

    export PATH="/usr/local/opt/postgresql${POSTGRESQL_VERSION}/bin:${PATH}"
    export LDFLAGS="-L/usr/local/opt/postgresql${POSTGRESQL_VERSION}/lib"
    export CPPFLAGS="-I/usr/local/opt/postgresql${POSTGRESQL_VERSION}/include"
    export PKG_CONFIG_PATH="/usr/local/opt/postgresql${POSTGRESQL_VERSION}/lib/pkgconfig"
else
    sudo chmod 777 /home/travis
    sudo service postgresql stop
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

        export LD_LIBRARY_PATH="/usr/local/pgsql/lib"
        sudo /sbin/ldconfig /usr/local/pgsql/lib
        export PATH="/usr/local/pgsql/bin:${PATH}"

        cd ../../pljava
    else
        . /etc/lsb-release
        echo "deb http://apt.postgresql.org/pub/repos/apt/ $DISTRIB_CODENAME-pgdg main ${POSTGRESQL_VERSION}" > ../pgdg.list
        sudo mv ../pgdg.list /etc/apt/sources.list.d/
        wget --quiet -O - https://apt.postgresql.org/pub/repos/apt/ACCC4CF8.asc | sudo apt-key add -
        sudo apt-get -qq update
        sudo apt-get -qq install "postgresql-${POSTGRESQL_VERSION}" "postgresql-server-dev-${POSTGRESQL_VERSION}" libecpg-dev libkrb5-dev
    fi
fi