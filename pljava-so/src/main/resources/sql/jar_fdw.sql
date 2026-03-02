/*-------------------------------------------------------------------------
 *
 *                foreign-data wrapper  jar
 *
 * IDENTIFICATION
 *                jar/=sql/jar_fdw.sql
 *
 *-------------------------------------------------------------------------
 *
 *
 * Copyright (c) 2013, PostgreSQL Global Development Group
 *
 * This software is released under the PostgreSQL Licence
 *
 * Author:  Andrew Dunstan <andrew@dunslane.net>
 *
 *-------------------------------------------------------------------------
 */

CREATE FUNCTION jar_fdw_handler()
RETURNS fdw_handler
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT;

CREATE FUNCTION jar_fdw_validator(text[], oid)
RETURNS void
AS 'MODULE_PATHNAME'
LANGUAGE C STRICT;

CREATE FOREIGN DATA WRAPPER jar_fdw
  HANDLER jar_fdw_handler
  VALIDATOR jar_fdw_validator;
