# Build situations that may need a linker `runpath`

In older PL/Java versions, it was common to build PL/Java with a `RUNPATH`
embedded by the linker, so that it could find the Java library at run time.
The configuration variable `pljava.libjvm_location` now takes care of locating
Java, and in *most* cases, there is no longer any need to pass the linker
a run path.

There are some exceptions, however, where it may still be useful to build
PL/Java with a `RUNPATH` that matches the `pg_config --libdir` output.

## EnterpriseDB PostgreSQL distributions that bundle system libraries

EnterpriseDB's pre-built distributions of PostgreSQL may include not only
PostgreSQL's own libraries in the `pg_config --libdir` directory, but also
specific versions of common system libraries. You are in this situation if
you run `ldd postgres` and the list includes common system libraries like
`libxml2`, `libssl`, `libcrypto`, `libcom_err`, `libldap`, etc., but shows
them found in PostgreSQL's `libdir` instead of the standard system location,
as in this example:

    $ ldd /u/pgsql/95/bin/postgres
    ...
    libldap-2.4.so.2 => /u/pgsql/95/bin/../lib/libldap-2.4.so.2
    ...

In this case, if PL/Java is not built with a `RUNPATH`, it might
find the system-provided versions of these standard libraries at run time,
instead of the ones that came from EnterpriseDB. Signs of a problem include
failure to load PL/Java, reporting `undefined symbol` errors
*against standard system libraries*:

    =# CREATE EXTENSION pljava;
    ERROR: could not load library ...:
    /lib64/libldap_r-2.4.so.2: undefined symbol: ber_sockbuf_io_udp

If this is your situation, you can find the PostgreSQL `libdir` by running
`pg_config --libdir`, and build PL/Java with a `RUNPATH` naming the same
directory, as described below. *Note that if you do this, you lose the ability
to install your built PL/Java on several systems with different PostgreSQL
installed locations*.

## Building PL/Java with a `RUNPATH`

You can specify a `RUNPATH` by defining the `pgsql.runpath` property on the
Maven command line. For example, if `pg_config --libdir` outputs
`/usr/lib/postgresql`, add this to the `mvn` command:

    -Dpgsql.runpath=/usr/lib/postgresql

By default, this produces a linker option `-Wl,-rpath=/usr/lib/postgresql`
which is the expected form for GNU linkers. If a different prefix is needed
for your linker, it can be given as the `pgsql.runpathpfx` property:

    -Dpgsql.runpath=/usr/lib/postgresql -Dpgsql.runpathpfx=-Xfoo=

would place `-Xfoo=/usr/lib/postgresql` on the linker command line.

If these two properties are not flexible enough to produce the right options
for your linker, you can directly add whatever options are necessary by
editing `pljava-so/pom.xml`.

*Note that building PL/Java with a `RUNPATH` forfeits the ability to use the
the built PL/Java on several systems with different PostgreSQL installed
locations*.

## Solving the same problem without rebuilding anything

If you have a situation where PL/Java is finding different libraries than
came with PostgreSQL, but practical considerations weigh against rebuilding
PL/Java (or you prefer to be able to use the same built PL/Java on several
systems with different PostgreSQL installed locations), you can edit whatever
script starts your PostgreSQL server to add an environment variable
(`LD_LIBRARY_PATH` on Linux and typical Unices) to the server's environment,
which all backend sessions will inherit. The variable should include the
value shown by `pg_config --libdir`.

A restart of the server is needed for such a change to take effect.

Other platform-specific options for solving the same problem, such as
`ldconfig` on Linux, may be available. Before there was
`pljava.libjvm_location`, it used to be common to have to know these tricks.
Now it should be uncommon, but in rare cases can still be useful.
