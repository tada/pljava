#!/usr/bin/env bash

saxon_jar_name=$(find "${HOME}/.m2/repository/net/sf/saxon/Saxon-HE/" -name "Saxon-HE-*.jar" | head -n 1)
saxon_jar="file:${saxon_jar_name}"
examples_jar_name=$(find pljava-examples -name "pljava-examples*.jar")
examples_jar="file:${PWD}/${examples_jar_name}"

if [ "$TRAVIS_OS_NAME" = "osx" ]; then
    printf '%s\n' "SELECT sqlj.install_jar(:'path','saxon',true);" | psql -v path="$saxon_jar" -U postgres
    psql -c "SELECT sqlj.set_classpath('public', 'saxon');" -U postgres
    printf '%s\n' "SELECT sqlj.install_jar(:'path','examples',true);" | psql -v path="$examples_jar" -U postgres
    psql -c "SELECT sqlj.get_classpath('javatest');" -U postgres
    psql -c "SELECT sqlj.set_classpath('javatest', 'examples');" -U postgres
    psql -c "SELECT javatest.java_addone(3);" -U postgres
elif [ "$POSTGRESQL_VERSION" = "SOURCE" ]; then
    sudo setfacl -m u:postgres:rwx /home/travis/.m2/
    printf '%s\n' "SELECT sqlj.install_jar(:'path','saxon',true);" | sudo -u postgres /usr/local/pgsql/bin/psql -v path="$saxon_jar" -U postgres
    sudo -u postgres /usr/local/pgsql/bin/psql -c "SELECT sqlj.set_classpath('public', 'saxon');" -U postgres
    printf '%s\n' "SELECT sqlj.install_jar(:'path','examples',true);" | sudo -u postgres /usr/local/pgsql/bin/psql -v path="$examples_jar" -U postgres
    sudo -u postgres /usr/local/pgsql/bin/psql -c "SELECT sqlj.get_classpath('javatest');" -U postgres
    sudo -u postgres /usr/local/pgsql/bin/psql -c "SELECT sqlj.set_classpath('javatest', 'examples');" -U postgres
    sudo -u postgres /usr/local/pgsql/bin/psql -c "SELECT javatest.java_addone(3);" -U postgres
else
    sudo setfacl -m u:postgres:rwx /home/travis/.m2/
    printf '%s\n' "SELECT sqlj.install_jar(:'path','saxon',true);" | sudo -u postgres psql -v path="$saxon_jar" -U postgres
    sudo -u postgres psql -c "SELECT sqlj.set_classpath('public', 'saxon');" -U postgres
    printf '%s\n' "SELECT sqlj.install_jar(:'path','examples',true);" | sudo -u postgres psql -v path="$examples_jar" -U postgres 2> test.log
    grep -w "WARNING" test.log
    grep -w "ERROR" test.log
    sudo -u postgres psql -c "SELECT sqlj.get_classpath('javatest');" -U postgres
    sudo -u postgres psql -c "SELECT sqlj.set_classpath('javatest', 'examples');" -U postgres
    sudo -u postgres psql -c "SELECT javatest.java_addone(3);" -U postgres
fi
