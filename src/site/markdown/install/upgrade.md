# Upgrading

## Upgrading the PL/Java version in a database

PL/Java performs an upgrade installation if there is already an `sqlj` schema
with tables that match a known PL/Java schema from version 1.3.0 or later. It
will convert, preserving data, to the current schema if necessary.

*Remember that PL/Java runs independently
in each database session where it is in use. Older PL/Java versions active in
other sessions can be disrupted by the schema change.*

A trial installation of a PL/Java update can be done in a transaction, and
rolled back if desired, leaving the schema as it was. Any concurrent sessions
with active older PL/Java versions will not be disrupted by the altered schema
as long as the transaction remains open, *but they may block for the duration,
so whatever testing will be done within the transaction should be done quickly
if that could be an issue*.

### Upgrading, outside the extension framework

On PostgreSQL pre-9.1, or whenever PL/Java has not been installed
with `CREATE EXTENSION`, it can be updated with a `LOAD` command just
as in a fresh installation. This must be done in a fresh session (in
which nothing has caused PL/Java to load since establishing the connection).

### Upgrading, within the extension framework

On PostgreSQL 9.1 or later where PL/Java has been installed with
`CREATE EXTENSION`, it can be updated with
[`ALTER EXTENSION pljava UPDATE`][aeu], as long as
`SELECT * FROM pg_extension_update_paths('pljava')` shows a one-step path
from the version currently installed to the version desired.

[aeu]: http://www.postgresql.org/docs/current/static/sql-alterextension.html

As with the `LOAD` method, an `ALTER EXTENSION ... UPDATE` must be done
in a fresh session, before anything has loaded PL/Java; this also precludes
an update with a multi-step path in a single command, but the intent is to
always provide a one-step path between _released_ versions.

If you will be following development (`SNAPSHOT`) versions, the installation
method using `LOAD` may be simpler, as updates between snapshots with the
same version string make no sense to the extension framework.

## Upgrading the PostgreSQL major version with PL/Java in use

### Binary upgrading with `pg_upgrade`

Using the [`pg_upgrade`][pgu] tool [contributed to PostgreSQL in 9.0][pguc],
an entire PostgreSQL cluster can be upgraded to a later major version in a
more direct process than the dump to SQL and reload formerly required.
The binary upgrade is possible as long as the cluster and databases meet
certain requirements, which should be studied in the
[`pg_upgrade` manual page][pgu] version for the PostgreSQL release being
upgraded *to*.

PL/Java adds a few additional considerations:

* `pg_upgrade` will check in advance that every loadable module used in
    the old cluster can be loaded in the new cluster, but the schema and
    data will be copied over by `pg_upgrade` itself. That means that a
    PL/Java build for the new PostgreSQL version must be *installed in
    the directory structure* for the new cluster before running `pg_upgrade`,
    but *not* installed into any databases (the new cluster should not have
    had any non-system objects created yet).

* In the steps of [Installing PL/Java](install.html), that means that the
    self-extracting `java -jar ...` command must have been run (or the
    equivalent package-installation command, if you are getting PL/Java
    through a packaging system for your OS), but no `CREATE EXTENSION` or
    `LOAD` command should have been run to configure it in any database.  If
    using the extracting jar, to be sure of installing it to the right cluster,
    add `-Dpgconfig=`*pgconfigpath* at the end, where *pgconfigpath* is the
    full path to the *new* cluster's `pg_config` executable.

* PL/Java releases before 1.5.1 were not aware of `pg_upgrade` operation.
    To avoid possible errors during the upgrade involving OID or object
    clashes, the PL/Java release installed for the new cluster should be
    1.5.1 or later.

* When `pg_upgrade` tests that all needed modules are present, it expects
    the names to match. The PL/Java module name includes the PL/Java version,
    so the versions installed in the old and new clusters should be the same.
    Given that 1.5.1 or later should be installed in the new cluster,
    if any databases in the old cluster are using an older PL/Java version,
    PL/Java should be upgraded in each (as described at the top of this page)
    before running `pg_upgrade`. To be sure of installing a newer PL/Java
    build into the old cluster, if using the extracting jar, add
    `-Dpgconfig=`*oldpgconfigpath* at the end of the `java -jar ...` command
    line, with *oldpgconfigpath* the full path to the old cluster's `pg_config`
    executable.

[pgu]: https://www.postgresql.org/docs/current/static/pgupgrade.html
[pguc]: https://www.postgresql.org/docs/9.0/static/release-9-0.html#AEN103668
