*
*                foreign-data wrapper  blackhole
 *
 * Copyright (c) 2013, PostgreSQL Global Development Group
 *
 * This software is released under the PostgreSQL Licence
 *
 * Author:  Andrew Dunstan <andrew@dunslane.net>
 *
 * IDENTIFICATION
 *                blackhole_fdw/=sql/blackhole_fdw.sql
 *
 *-------------------------------------------------------------------------
 */

CREATE FUNCTION blackhole_fdw_handler()
    RETURNS fdw_handler
AS '$libdir/blackhole_fdw'
LANGUAGE C STRICT;

CREATE FUNCTION blackhole_fdw_validator(text[], oid)
    RETURNS void
AS '$libdir/blackhole_fdw'
LANGUAGE C STRICT;

CREATE
FOREIGN DATA WRAPPER blackhole_fdw
  HANDLER blackhole_fdw_handler
  VALIDATOR blackhole_fdw_validator;

-- CREATE EXTENSION blackhole_fdw;
