## About PL/Java packaging

The `pljava-packaging` subproject builds a single `jar` file that contains
the files (including the API, implementation, and examples `jar` files,
native code shared object, and PostgreSQL extension control files) that must
be unpacked into a PostgreSQL installation so PL/Java can be used. These files
could have been wrapped in a `tar` or `zip` format instead, but any site where
PL/Java will be used necessarily has Java installed, and therefore support for
the `jar` format, so it is an obvious choice.

The resulting `jar` can be simply extracted using the `jar` tool, and the files
moved to the proper locations, or it can be run with `java -jar`. It contains
two extra `.class` files to give it a very simple self-extracting behavior:
it will run `pg_config` to learn where PostgreSQL is installed, and extract
PL/Java's files into the correct locations. See [Installing PL/Java][install]
for the details.

If the file is simply extracted using the `jar` tool, those two added class
files will also be extracted, and can be deleted; they are not needed for
PL/Java's operation.

### Use with `jshell` as a testing environment

The added classes supply some additional methods, unused during a simple
installation with `java -jar`, but accessible from Java's [JShell][]
scripting tool if it is launched with this `jar` on its classpath.
That allows `jshell` to serve as an environment for scripting tests
of PL/Java in a running PostgreSQL instance, with capabilities similar to
(and modeled on) the [PostgresNode][] Perl module distributed with PostgreSQL.

See [this introduction][nodetut] and the javadoc for [the Node class][node]
for details.

[install]: ../install/install.html
[JShell]: https://docs.oracle.com/javase/9/jshell/introduction-jshell.htm
[PostgresNode]: https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/test/perl/PostgresNode.pm;h=aec3b9a;hb=e640093
[node]: apidocs/org/postgresql/pljava/packaging/Node.html
[nodetut]: ../develop/node.html
