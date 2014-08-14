#!/bin/ksh

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.7.0_60.jdk/Contents/Home
#export PG_HOME=/Library/PostgreSQL/9.3
export PG_HOME=/usr/local/pgsql
export M2_HOME=/usr/local/maven
export PGXS=$PG_HOME/lib/postgresql/pgxs/src/makefiles/pgxs.mk
export USE_LD_RPATH=2
export PATH=$JAVA_HOME/bin:$PG_HOME/bin:$PATH
$M2_HOME/bin/mvn install -X
