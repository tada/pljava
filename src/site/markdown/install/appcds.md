# How to set up application class data sharing in Hotspot

For the Hotspot JVM, [Application class data sharing][appcds] is a feature,
first released in the Oracle JVM (8u40 and later) that extends the ordinary
Java class data sharing feature to also include selected classes from the
application class path. In PL/Java terms, that means that not only Java's own
internal classes, but PL/Java's also, can be saved in a preprocessed shared
archive and quickly mapped when any backend starts PL/Java. For an overview, see
the [PL/Java VM options page][vmop].

Starting with Java 10, the feature is also available in
[OpenJDK with Hotspot][OpenJDK]. From Java 8 onward, a different feature
with the same effect is available in [OpenJDK with OpenJ9][OpenJDK]; that
feature is covered [on its own page][cdsJ9].

[appcds]: http://docs.oracle.com/javase/8/docs/technotes/tools/unix/java.html#app_class_data_sharing
[vmop]: vmoptions.html
[bcl]: http://www.oracle.com/technetwork/java/javase/terms/license/index.html
[OpenJDK]: https://adoptopenjdk.net/
[cdsJ9]: oj9vmopt.html#How_to_set_up_class_sharing_in_OpenJ9

## License considerations

In Oracle Java, application class data sharing is a "commercial feature" first
released in Java 8, and will not work unless `pljava.vmoptions` also contain
`-XX:+UnlockCommercialFeatures` , with implications described in the
"supplemental license terms" of the Oracle
[binary code license for Java SE][bcl]. The license seems
to impose no burden on use for internal development and testing, but requires
negotiating an additional agreement with Oracle if the feature will be used
"in your internal business operations or for any commercial or production
purpose." It is available to consider for any application where the
additional performance margin can be given a price.

The same feature in OpenJDK with Hotspot is available from Java 10 onward,
and does not require any additional license or `-XX:+UnlockCommercialFeatures`
option. The equivalent feature in OpenJDK with OpenJ9,
[described separately][cdsJ9] is available from Java 8 onward, also with no
additional license or setup needed.

## Setup

The setup instructions on this page are for Hotspot, whether in Oracle Java
or OpenJDK with Hotspot. The two differ only in that, wherever an
`-XX:+UnlockCommercialFeatures` option is shown in the steps below,
**it is needed in Oracle Java but not in OpenJDK/Hotspot**.

Setting up PL/Java to use application class data sharing is a three-step
process. Each step is done by setting a different combination of options
in `pljava.vmoptions`. These are the three steps in overview:

1. Make a list of classes to be preloaded, by saving the names of classes
    that are loaded while executing some desired code in PL/Java.

2. In a new session, trigger the loading of PL/Java in a dump mode, where
    the JVM loads the classes from the list in step 1, and writes a
    shared archive.

3. Move the shared archive to a final, readable-to-postgres location,
    and save (with `ALTER DATABASE ... SET` or `ALTER SYSTEM`) a version
    of `pljava.vmoptions` with the final options to use the generated archive.

Before beginning, any [`pljava.*` variable settings][gucs] necessary should
already have been made and saved, and basic PL/Java operation confirmed.

[gucs]: ../use/variables.html

The steps that follow involve setting the `pljava.vmoptions` variable to
contain various options. If other options have been set in
`pljava.vmoptions` already, be sure to include those also when saving the
final `pljava.vmoptions` setting at the end.

### Generate the list of needed classes

Classes eligible to go in the shared archive are the Java system classes
(including anything in the deprecated `java.ext.dirs` or `java.endorsed.dirs`
directories), classes in the PL/Java jar itself, and any others in jars named in
`pljava.classpath`. Classes from PL/Java application jars loaded into the
database normally with `sqlj.install_jar` are not candidates for the shared
archive. The feature will speed the startup of PL/Java itself, but application
classes are still loaded from the database in PL/Java's usual way.

The generated list will contain any such classes that Java needed to load
while starting up and running some sample PL/Java code. That can be anything
that exercises most features of PL/Java; the supplied [examples jar][exj]
will do nicely. There is no great benefit to running your specific application
code, as those classes won't end up in the archive anyway. (But see *Java
libraries* below.)

Here, `/tmp/pljava.classlist` may be any file name you choose, in a location
the PostgreSQL backend will be able to write and read. The URL for the
`pljava-examples` jar is abbreviated here, standing in for its
[real installed location on your system][exj]. At the end of this step,
use `\c` in psql to start a fresh new connection.

[exj]: ../examples/examples.html

```
% psql
=# SET pljava.vmoptions TO
-#  '-XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:off '
-#  '-XX:DumpLoadedClassList=/tmp/pljava.classlist';
SET
=# SELECT sqlj.install_jar('file:/.../pljava-examples...jar', 'ex', true);
... lots of output from the examples tests ...
 install_jar 
-------------
 
(1 row)

=# SELECT sqlj.remove_jar('ex', true);
...
 remove_jar 
------------
 
(1 row)

=# \c
You are now connected to database "..." as ...
```

### Process the list of classes into a shared archive

The last step ended with `\c` to start a new session. In this step,
`/tmp/pljava.classlist` still represents the chosen file name that was
just written, and `/tmp/pljava.jsa` is another chosen name where the
backend will be able to write the Java shared archive.

```
=# SET pljava.vmoptions TO
-#  '-XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:dump '
-#  '-XX:SharedArchiveFile=/tmp/pljava.jsa '
-#  '-XX:SharedClassListFile=/tmp/pljava.classlist';
SET
=# SELECT sqlj.get_classpath('public'); -- any PL/Java function will do
INFO:  Allocated shared space: ... bytes at 0x...
INFO:  Loading classes to share ...
INFO:  Loading classes to share: done.
INFO:  Rewriting and linking classes ...
INFO:  Rewriting and linking classes: done
...
The connection to the server was lost. Attempting reset: Succeeded.
=# 
```

Java (and the postgres backend with it) exit when finished writing the
shared archive; psql notices and starts a new connection, ready for
the final step.

### Final `pljava.vmoptions` settings to use the new archive

The archive just written (`/tmp/pljava.jsa` as illustrated) may be moved
to a more permanent place, such as a system location where postgres will
be able to read it, but it is protected from modification; in this
example, `/usr/pgsql/lib/pljava.jsa`.

Be sure the final `pljava.vmoptions` setting also includes any *other*
VM options you may have chosen to set.

```
=# SET pljava.vmoptions TO
-#  '-XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:on '
-#  '-XX:SharedArchiveFile=/usr/pgsql/lib/pljava.jsa';
SET
=# SELECT sqlj.get_classpath('public'); -- just checking it works
 get_classpath 
---------------
 
(1 row)

=# ALTER DATABASE ... SET pljava.vmoptions FROM CURRENT; -- save it!
```

Alternatively, use `ALTER SYSTEM` (or edit the `postgresql.conf` file)
to save the setting for all databases in the cluster.

## Java libraries

If your own PL/Java code depends on other Java libraries distributed as
jars, the usual recommendation would be to install those as well into the
database with `sqlj.install_jar`, and use `sqlj.set_classpath` to make them
available. That keeps everything handled uniformly within the database.

On the other hand, if you are building a shared archive, and some of the
dependency libraries are substantial, you could consider instead storing
those jars on the file system and naming them in `pljava.classpath`. Those
library classes can then also be included in the shared archive.

In that case, when generating the needed class list, you should run some
representative sample of your own application code, not just the PL/Java
examples, so that the necessary library classes will have been exercised.

Not everything from the original jar file can go into the shared archive.
After the archive has been built, the original jars still must be on the
file system and named in `pljava.classpath`.

When generating the needed classes list, consider adding `-Xverify:all` to
the other VM options. Java sometimes applies more relaxed verification to
classes it loads from the system classpath. As you will only need to do this
once and they will later be loaded quickly from the shared archive, they
might as well be checked thoroughly at the start.
