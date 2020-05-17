#!/usr/bin/env bash

if [ "$TRAVIS_OS_NAME" = "osx" ]; then
    libjvm_path=$(find "$JAVA_HOME" -mindepth 2 -name "libjli.dylib" | head -n 1)
else
    libjvm_path=$(find "$JAVA_HOME" -mindepth 2 -name "libjvm.so" | head -n 1)
fi
echo $libjvm_path
vmoptions='-enableassertions:org.postgresql.pljava... -Xcheck:jni'

if [ "$POSTGRESQL_VERSION" = "SOURCE" ]; then
    find pljava-packaging -name "pljava-pg*.jar" | sudo xargs java -jar -Dpgconfig="/usr/local/pgsql/bin/pg_config"
else
    find pljava-packaging -name "pljava-pg*.jar" | sudo xargs java -jar
fi

if [ "$TRAVIS_OS_NAME" = "osx" ]; then
    printf '%s\n' "SET pljava.libjvm_location TO :'path'; ALTER SYSTEM SET pljava.libjvm_location TO :'path'; SELECT pg_reload_conf();" | psql -v path="$libjvm_path" -U postgres
    printf '%s\n' "SET pljava.vmoptions TO :'options'; ALTER SYSTEM SET pljava.vmoptions TO :'options'; SELECT pg_reload_conf();" | psql -v options="$vmoptions" -U postgres
    psql -c "CREATE EXTENSION pljava;" -U postgres
elif [ "$POSTGRESQL_VERSION" = "SOURCE" ]; then
    printf '%s\n' "SET pljava.libjvm_location TO :'path'; ALTER SYSTEM SET pljava.libjvm_location TO :'path'; SELECT pg_reload_conf();" | sudo -u postgres /usr/local/pgsql/bin/psql -v path="$libjvm_path" -U postgres
    printf '%s\n' "SET pljava.vmoptions TO :'options'; ALTER SYSTEM SET pljava.vmoptions TO :'options'; SELECT pg_reload_conf();" | sudo -u postgres /usr/local/pgsql/bin/psql  -v options="$vmoptions" -U postgres
    sudo -u postgres /usr/local/pgsql/bin/psql -c "CREATE EXTENSION pljava;" -U postgres
else
    printf '%s\n' "SET pljava.libjvm_location TO :'path'; ALTER SYSTEM SET pljava.libjvm_location TO :'path'; SELECT pg_reload_conf();" | sudo -u postgres psql -v path="$libjvm_path" -U postgres
    printf '%s\n' "SET pljava.vmoptions TO :'options'; ALTER SYSTEM SET pljava.vmoptions TO :'options'; SELECT pg_reload_conf();" | sudo -u postgres psql -v options="$vmoptions" -U postgres
    sudo -u postgres psql -c "CREATE EXTENSION pljava;" -U postgres
fi
