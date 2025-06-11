-- PL/Java psql installation script
-- See https://tada.github.io/pljava/install/install.html

-- this lets us verify successful installation
-- we should see 'NOTICE: PL/Java loaded'
SET client_min_messages TO NOTICE;

CREATE EXTENSION pljava;
GRANT USAGE ON LANGUAGE java TO public;

