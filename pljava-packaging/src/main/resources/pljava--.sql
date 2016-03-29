\echo Use "CREATE EXTENSION pljava" to load this file. \quit

/*
 Note: most of the work of setting up PL/Java is done within PL/Java itself,
 touched off by the LOAD command, making possible a decent installation
 experience even on pre-9.1, pre-extension PostgreSQL versions. This script
 simply wraps that.
 
 However, in this case, the native library has no easy way to find the
 pathname it has just been loaded from (it looks for the path given to its
 LOAD command, but finds the CREATE EXTENSION command instead). So, temporarily
 save the path in a table.

 The table's existence also helps PL/Java distinguish the case where it is
 being loaded as an extension itself (via this script), and the case where
 it is simply being awakened during the creation of some other extension
 (CREATE EXTENSION foo where foo is something implemented using PL/Java).

 This script template can be used for any pljava--foo--bar.sql upgrade script
 where there has been no schema change between foo and bar, or even if there
 has but it requires no fiddling with pg_extension_config_dump.
 */

DROP TABLE IF EXISTS
@extschema@."see doc: do CREATE EXTENSION PLJAVA in new session";
CREATE TABLE
@extschema@."see doc: do CREATE EXTENSION PLJAVA in new session"
(path, exnihilo) AS
SELECT CAST('${module.pathname}' AS text), false;
LOAD '${module.pathname}';

/*
 Ok, the LOAD succeeded, so everything happened ... unless ... the same
 PL/Java library had already been loaded earlier in this same session.
 That would be an unusual case, but confusing if it happened, because
 PostgreSQL turns LOAD into a (successful) no-op in that case, meaning
 CREATE EXTENSION might appear to succeed without really completing.
 To fail fast in that case, expect that the LOAD actions should have
 dropped the 'loadpath' table already, and just re-create and re-drop it here,
 to incur a (cryptic, but dependable) error if it is still around because the
 work didn't happen. The error message will include the table name, which is
 why the table name is phrased as an error message.

 The solution to a problem detected here is simply to close the session,
 and be sure to execute 'CREATE EXTENSION pljava' in a new session (new
 at least in the sense that Java hasn't been used in it yet).
 */

CREATE TABLE
@extschema@."see doc: do CREATE EXTENSION PLJAVA in new session"();
DROP TABLE
@extschema@."see doc: do CREATE EXTENSION PLJAVA in new session";
