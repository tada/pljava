#!/bin/ksh

export M2_HOME=/usr/local/maven
export PGXS=/Library/PostgreSQL/9.3/lib/postgresql/pgxs/src/makefiles/pgxs.mk
export USE_LD_RPATH=2
export PATH=$PATH:/Library/PostgreSQL/9.3/bin
$M2_HOME/bin/mvn install
