#!/bin/bash

set -e

# see https://tada.github.io/pljava/install/vmoptions.html
# remove first two items if you wish attach java debugger
psql -v ON_ERROR_STOP --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOL
  ALTER DATABASE $POSTGRES_DB SET pljava.vmoptions TO '-Xshare:auto -XX:+DisableAttachMechanism -Xms2m -XX:+UseSerialGC';
EOL

