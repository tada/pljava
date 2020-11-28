\echo Use "CREATE EXTENSION pljava FROM UNPACKAGED" to load this file. \quit

/*
 This script can "update" from any unpackaged PL/Java version supported by
 the automigration code within PL/Java itself. The schema migration is first
 touched off by the LOAD command, and then the ALTER EXTENSION commands gather
 up the member objects according to the current schema version.
 */

DROP TABLE IF EXISTS
@extschema@."see doc: do CREATE EXTENSION PLJAVA in new session";
CREATE TABLE
@extschema@."see doc: do CREATE EXTENSION PLJAVA in new session"
(path, exnihilo) AS
SELECT CAST('${module.pathname}' AS text), false;
LOAD '${module.pathname}';

/*
 Why the CREATE / DROP?  When faced with a LOAD command, PostgreSQL only does it
 if the library has not been loaded already in the session (as could have
 happened if, for example, a PL/Java function has already been called). If the
 LOAD was skipped, there could still be an old-layout schema, because the
 migration only happens in an actual LOAD.  To avoid confusion later, it's
 helpful to fail fast in that case. The loadpath table should have been dropped
 by the LOAD actions, so the re-CREATE/DROP here will incur a (cryptic, but
 dependable) error if those actions didn't happen. The error message will
 include the table name, which is why the table name is phrased as an error
 message.
 
 The solution to a problem detected here is simply to exit the
 session and repeat the CREATE EXTENSION in a new session where PL/Java has not
 been loaded yet.
 */
CREATE TABLE
@extschema@."see doc: do CREATE EXTENSION PLJAVA in new session"();
DROP TABLE
@extschema@."see doc: do CREATE EXTENSION PLJAVA in new session";

/*
 The language-hander functions do not need to be explicitly added, because the
 LOAD actions always CREATE OR REPLACE them, which makes them extension members.
 Since the validators were added for 1.6.0, the language entries are also always
 CREATE OR REPLACEd, so they don't have to be mentioned here either.
 */

ALTER EXTENSION pljava ADD
 FUNCTION sqlj.add_type_mapping(character varying,character varying);
ALTER EXTENSION pljava ADD
 FUNCTION sqlj.alias_java_language(
  character varying,boolean,boolean,character varying);
ALTER EXTENSION pljava ADD
 FUNCTION sqlj.drop_type_mapping(character varying);
ALTER EXTENSION pljava ADD
 FUNCTION sqlj.get_classpath(character varying);
ALTER EXTENSION pljava ADD
 FUNCTION sqlj.install_jar(bytea,character varying,boolean);
ALTER EXTENSION pljava ADD
 FUNCTION sqlj.install_jar(character varying,character varying,boolean);
ALTER EXTENSION pljava ADD
 FUNCTION sqlj.remove_jar(character varying,boolean);
ALTER EXTENSION pljava ADD
 FUNCTION sqlj.replace_jar(bytea,character varying,boolean);
ALTER EXTENSION pljava ADD
 FUNCTION sqlj.replace_jar(character varying,character varying,boolean);
ALTER EXTENSION pljava ADD
 FUNCTION sqlj.set_classpath(character varying,character varying);

ALTER EXTENSION pljava ADD TABLE sqlj.classpath_entry;
ALTER EXTENSION pljava ADD TABLE sqlj.jar_descriptor;
ALTER EXTENSION pljava ADD TABLE sqlj.jar_entry;
ALTER EXTENSION pljava ADD TABLE sqlj.jar_repository;
ALTER EXTENSION pljava ADD TABLE sqlj.typemap_entry;

ALTER EXTENSION pljava ADD SEQUENCE sqlj.jar_entry_entryid_seq;
ALTER EXTENSION pljava ADD SEQUENCE sqlj.jar_repository_jarid_seq;
ALTER EXTENSION pljava ADD SEQUENCE sqlj.typemap_entry_mapid_seq;

SELECT pg_catalog.pg_extension_config_dump('@extschema@.jar_repository', '');
SELECT pg_catalog.pg_extension_config_dump('@extschema@.jar_entry', '');
SELECT pg_catalog.pg_extension_config_dump('@extschema@.jar_descriptor', '');
SELECT pg_catalog.pg_extension_config_dump('@extschema@.classpath_entry', '');
SELECT pg_catalog.pg_extension_config_dump('@extschema@.typemap_entry', '');
