# Installing PL/Java

The PL/Java [build process][bld] using `mvn clean install` produces files
needed to install the language in PostgreSQL, but does not place those
files in their final locations or configure PostgreSQL to use them.
Once the build is done, these instructions cover how to make PL/Java available
in your database. 

[bld]: ../build/build.html

The successful build will have produced two important files:

* The PL/Java `jar` file, which contains Java classes, so it is
    architecture-independent.

* The PL/Java native library, which is architecture-specific. Its filename
    extension can depend on the operating system: `.so` on many systems,
    `.dll` on Windows, `.bundle` on Mac OS X / Darwin.

For a final installation, you will want to copy these files from the build tree
to more permanent locations, though a quick test that PL/Java works can be made
with the files right where they are. See [locating the built files][locate] for
how to find their exact names in your build tree.

A third path name you will need to know is the one to your Java Runtime
Environment's `libjvm` shared object (`.so`, `.dll`, etc.), usually a few
levels deep under the directory where Java is installed on your platform.
See [locating libjvm][jvmloc] for help finding it.

[locate]: locate.html
[jvmloc]: locatejvm.html

## Special topics

Be sure to read these additional sections if:

* You are installing to [a PostgreSQL release earlier than 9.2][pre92]
* You are installing on [a system using SELinux][selinux]
* You are installing on [Mac OS X][osx]

[pre92]: prepg92.html
[selinux]: selinux.html
[osx]: ../build/macosx.html

## Install, configure, check

Because PL/Java, by design, runs entirely in the backend process created
for each connection to PostgreSQL, to configure it does not require any
cluster-wide actions such as stopping or restarting the server, or editing
the configuration file; any necessary settings can be made in SQL over
an ordinary connection.

_Caution: if you are installing a new, little-tested PL/Java build, be aware
that in the unexpected case of a crash, the `postmaster` will kick other
sessions out of the database for a moment to verify integrity, and then let
them reconnect. If that would be disruptive, it may be best to `initdb` a
new cluster in some temporary location and test PL/Java there, installing to
a production server only when satisfied._

After connecting to the desired database (the connection must be as a
PostgreSQL superuser), the commands for first-time PL/Java setup are:

```
SET client_min_messages TO NOTICE; -- or anything finer (INFO, DEBUG, ...)
SET pljava.libjvm_location TO ' use the libjvm path here ';
SET pljava.classpath TO ' use the pljava.jar path here ';
LOAD ' use the PL/Java native library path here ';
```
(The `client_min_messages` setting is only to ensure you do not miss
the `NOTICE` message in case of success.) If you see

    NOTICE: PL/Java loaded

then you are ready to test some PL/Java functions, such as the ones
in the [`examples.jar` supplied in the build][examples].

[examples]: ../examples/examples.html

Although typically only `pljava.libjvm_location` and `pljava.classpath` need
to be set, there is a [reference to PL/Java configuration variables][varref]
if you need it.

[varref]: ../use/variables.html

### Choosing where to place the files

Exactly where you place the files, and what pathnames you use in the
above commands, can depend on your situation:

* Are you a superuser on the OS where PostgreSQL is installed (or are you
    a distribution maintainer building a PL/Java package for that platform)?
* Are you not an OS superuser, but you have PostgreSQL superuser rights and
    OS permissions as the user that runs `postgres`?
* Do you have only PostgreSQL superuser rights, no ability to write locations
    owned by the user `postgres` runs as, but the ability to write some
    locations that user can read?
* Do you have PostgreSQL superuser rights and want to quickly test that you have
    built a working PL/Java?

The rest of this page will cover those cases. First, the quick check.

### A quick install check

For a quick sanity test, there is no need to move the built files to more
permanent locations, as long as the build tree location and permissions are
such that the PostgreSQL backend can read them where they are. Use those
pathnames directly in the `SET` and `LOAD` commands.

For the lowest-impact quick test, begin a transaction first, load PL/Java
and run [any tests you like][examples], then roll the transaction back.

### OS superuser or distribution maintainer

If you fall in this category, you can minimize configuration within
PostgreSQL by placing the built files into standard locations,
so `SET` commands are not needed for PostgreSQL to find them. For example,
if the PL/Java native library is copied into the PostgreSQL `$libdir`
(shown by `pg_config` as `PKGLIBDIR`), then the `LOAD` command can be
given just the basename of the file instead of a full path. Or, if
`dynamic_library_path` is already set, the file can be placed in any
directory on that list for the same effect.

_Todo: proclaim and implement default location for pljava.classpath_

**If you are a distribution maintainer** packaging PL/Java for a certain
platform, and you know or control that platform's conventions for where
the Java `libjvm` should be found, or where PostgreSQL extension files
(architecture-dependent and -independent) should go, please build your
PL/Java package with those locations as the defaults for the corresponding
PL/Java variables, and with the built files in those locations.

_Todo: add maven build options usable by distro spinners to set those defaults._

### PostgreSQL superuser with access as user running postgres

If you are not a superuser on the OS, you may not be able to place the
PL/Java files in the default locations PostgreSQL was built with.
If you have permissions as the user running `postgres`, you might choose
locations in a directory associated with that user, such as the `DATADIR`,
and set the `pljava.*` variables to point to them. Use a `LOAD` command
with the full path of the native library, or set `dynamic_library_path` to
include its location, and give only the basename to `LOAD`.

If you would rather ensure that the user running `postgres`, if compromised,
could not modify these files, then the next case will be more appropriate.

### PostgreSQL superuser, OS user distinct from the user running postgres

In this case, simply place the files in any location where you can make them
readable by the user running `postgres`, and set the `pljava.*` variables
accordingly.

## Making the configuration settings persistent

If you have loaded PL/Java successfully after making necessary changes to
some `pljava.*` variables, you will want those settings to stick. The simplest
way is to reissue the same `SET` commands as `ALTER DATABASE ` *databasename*`
SET ...` commands, which will be effective when any user connects to the same
database.

Another approach is to save them to the server's configuration file.
If you wish PL/Java to be available for all databases in a cluster, it may
be more convenient to put the settings in the file than to issue
`ALTER DATABASE` for several databases, but `pg_ctl reload` will be needed
to make changed settings effective. Starting with PostgreSQL 9.4,
`ALTER SYSTEM` may be used as an alternative to editing the file.

For PostgreSQL releases [earlier than 9.2][pre92], the configuration file is
the _only_ way to make your settings persistent.
