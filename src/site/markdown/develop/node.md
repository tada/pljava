# The testing harness `Node.class` in PL/Java's self-installer jar

The end product of a PL/Java build is a jar file containing the actual
files (including other jars) that need to be installed on a target system,
plus some logic allowing it to be run with `java -jar` and extract itself,
consulting the target system's `pg_config` to learn where to put the files.
That is unchanged from PL/Java 1.5.

In 1.6, however, the class added in the jar to support the self-extraction
has a number of new methods useful for integration testing.

The new methods are unused in a simple extraction with `java -jar`, but are
available, for example, to Java's [jshell][] scriptable interpreter.
Starting `jshell` with PL/Java's installer jar on its class path creates
a rather versatile environment for scripting tests of PL/Java in one or more
temporary database instances.

This is currently done in the multi-platform CI test configurations in the
project's repository, as a way to keep as much as possible of the testing code
common across platforms.

The overall flavor, and even some of the method names, follow the `PostgresNode`
Perl module that has been part of PostgreSQL's "PGXS" extension-building tools
since 2015, so a quick review of that follows.

## Similarities to PostgreSQL's `PostgresNode` Perl module

When used from a testing script written in Perl, the methods of `PostgresNode`
make it easy to spin up and tear down one or more PostgreSQL instances, running
in temporary directories, listening on temporary ports, non-interfering with
each other or with production instances using the standard locations and ports,
and without needing the permissions that guard those 'real' locations and ports.
A Perl test script might be simply:

```perl
my $n1 = get_new_node("TestNode1");
$n1->init();                # run initdb in n1's temporary location
$n1->start();               # start a server listening on n1's temporary port
$n1->safe_psql("postgres", "select 42");
$n1->stop();                # stop the server
$n1->clean_node();          # recursively delete the temporary location
```

`PostgresNode.pm` illustrates the immense utility of making just a few
well-chosen methods available, when there is already an expressive scripting
language at hand (Perl) for putting those methods to use.

Early Java versions lacked any batteries-included support for scripting, but the
arrival of `jshell` with Java 9 changed that. Start up `jshell` with PL/Java's
installer jar on its classpath, and you have an interactive, scriptable version
of Java, with the methods of `Node.class` available in it.

The ones that correspond to the Perl example above have the same names, for
consistency (right down to the Perlish spelling with underscores rather than
Javaish camelCase):

```java
import org.postgresql.pljava.packaging.Node
Node n1 = Node.get_new_node("TestNode1")
n1.init()
n1.start()
/* ... */
n1.stop()
n1.clean_node()
```

`jshell` has to be run with a rather lengthy command line to get to this point;
more on that later. But once started, it presents a familiar `PostgresNode`-like
environment. As the example shows, `jshell` is lenient about statement-ending
semicolons.

## `Node.class` in detail

A `Node` will register a VM shutdown hook to make sure `stop` and `clean_node`
happen if you forget and exit `jshell`, though forgetting is not recommended.
For using `jshell` interactively, these methods are convenient. If writing a
script, the equivalent `try`-with-resources forms may be tidier:

```java
try (AutoCloseable t1 = n1.initialized_cluster())
{
  try (AutoCloseable t2 = n1.started_server())
  {
    /* ... */
  }
}
```

The server will be stopped when `t2` goes out of scope, and the file tree
created by `initdb` will be removed when `t1` goes out of scope.

The `try` form is less convenient for interactive use, because `jshell` is not
very interactive when gathering a compound statement like a `try`. None of your
actions actually happen until you supply the final closing brace, and then they
all happen at once and the instance is torn down. But for any sort of finished
test script, the `try` form will be natural.

The full set of `Node` methods available can be seen
[in its javadocs][nodeapi].

### Connecting to the server

Running `initdb` and starting a server are all well and good, but sooner or
later a test may need to connect to it. That requires a JDBC driver to be on the
classpath also: specifically `pgjdbc-ng` (at least, that is the one that's been
tested and whose URL syntax is built in to `Node`). The older `pgjdbc` punts on
the correct handling of warning/notice responses from the server, which seems
rather disqualifying for a testing environment. In `pgjdbc-ng`, notices and
warnings (any PostgreSQL severity less than `ERROR` and at or above the
`client_min_messages` setting) are chained together as `SQLWarning` instances,
as JDBC provides.

A new profile has been added to PL/Java's Maven build, and can be activated with
`-Ppgjdbc-ng` on the `mvn` command line. It has no effect but to declare an
extra dependency on the `pgjdbc-ng` dependencies-included jar. It is not used in
the build, but Maven will have downloaded it to the local repository, and that
location can be added to `jshell`'s classpath to make the driver available.

That addition leads to the final long unwieldy command line needed to start
`jshell`, which can be seen in all its glory toward the end of this page.
Once that is copied and pasted into a terminal and any local paths changed, the
rest is easy:

```java
import org.postgresql.pljava.packaging.Node
Node n1 = Node.get_new_node("TestNode1")
n1.init()
n1.start()
import java.sql.Connection
Connection c1 = n1.connect()
```

Once you have an open connection (or several), the convenience methods `Node`
provides for using them are `static`. A connection is already to a specific
`Node`, so there is no need for the convenience methods to be invoked on a
`Node` instance. They are `static`, and simply take a `Connection` as the first
parameter.

```java
import static org.postgresql.pljava.packaging.Node.qp; // query-print
qp(c1, "create table foo (bar int, baz text)")
qp(c1, "insert into foo (values (1, 'Howdy!'))")
qp(c1, "select 1/0")
qp(c1, "select pg_sleep(1.5)")
qp(c1, "select * from foo")
```

This example shows `qp` used several different ways: with a DDL statement that
returns no result, a DML statement that returns an update count, a statement
that returns an error, one that calls a `void`-returning function (and therefore
produces a one-row result with one column typed `void` and always null), and one
that returns a general query result. What it prints:

```
jshell> qp(c1, "create table foo (bar int, baz text)")

jshell> qp(c1, "insert into foo (values (1, 'Howdy!'))")
<success rows='1'/>

jshell> qp(c1, "select 1/0")
<error code='22012' message='division by zero'/>

jshell> qp(c1, "select pg_sleep(1.5)")
<void rows='1' cols='1'/>

jshell> qp(c1, "select * from foo")
      ...
      <column-type-name>text</column-type-name>
    </column-definition>
  </metadata>
  <data>
    <currentRow>
      <columnValue>1</columnValue>
      <columnValue>Howdy!</columnValue>
    </currentRow>
  </data>
</webRowSet>

jshell>
```

The XMLish output style comes from using Java's built-in `WebRowSet.writeXml`
method for dumping general result sets. It is more verbose than one would like,
and easily flummoxed by unusual or PG-specific column types, but it is as useful
a way to readably dump a typical result set as one could hope to write in four
lines of Java. (This is meant as a _small_ class useful for testing, not as a
reimplementation of `psql`!)

The writing of update counts and diagnostics as
`success`/`error`/`warning`/`info` XML elements naturally follows to keep
the output format consistent. The `void` output is special treatment for
the common case of a result set with only the `void` column type, to spare the
effort of generating a whole `WebRowSet` XML that only shows nothing is there.

#### `qp` dissected

`qp` is for interactive, exploratory use, generating printed output. For
scripting purposes, `q` gives direct access to result objects; `qp` is nothing
but a wrapper that calls `q` with the same arguments, and what `q` returns is
passed directly to another method (in fact, another of several overloads of
`qp`) to be printed.

What `q` returns is a `Stream<Object>`. Although declared with element type
`Object`, the stream will only deliver instances of: `ResultSet`, `Long` (an
update count), or throwables (caught exceptions, or `SQLWarning` instances). The
JDBC `Statement` is polled for new `SQLWarning`s before checking for each next
result (`ResultSet` or update count). An error or exception that is thrown and
caught will be placed on the stream when caught (and will be the last thing on
the stream).

All 'notices' from PostgreSQL (severity below `ERROR` but at or above
`client_min_messages`) are turned into `SQLWarning` instances by `pgjdbc-ng`,
which does not provide any API to get the original PostgreSQL severity, or any
of the details other than the message and SQLState code. `Node` classifies them
as `info` if the SQLState 'class' (leftmost two positions) is `00`, otherwise as
`warning`. Exceptions of any other kind are classified as `error`.

As it happens, there is also an overload of `qp` with just one `Stream<Object>`
parameter. If you have already run a query with `q` and have the result stream,
and decide you just want to print that, just pass it to `qp`. There are other
overloads of `qp` for the individual objects you might encounter in a result
stream. One static import of the name `qp` will allow printing many things.

#### More specialized convenience methods

`Node` also supplies several more specialized methods: `setConfig` for
PostgreSQL configuration variables (`qp` is fine for a literal string "set foo
to bar" command, but for computed values, `setConfig` uses a prepared statement
and binding), and wrappers for PL/Java `install_jar`, `remove_jar`, and
`set_classpath`.

The `installExamplesJar` method supplies the correct as-installed path to the
jar file (which we know, because this _is_ the self-installer code, remember?).
The boolean method `examplesNeedSaxon` introspects in the examples jar to see if
it includes the Saxon examples, and therefore needs the Saxon jar in place
before it can be deployed.

As the Saxon jar is probably already in a local Maven repository, `installSaxon`
will install it from there, given a path to the repository root and the desired
version of Saxon-HE. Not to be outdone, `installSaxonAndExamplesAndPath`
combines the steps in correct order to install the Saxon jar, place it on the
classpath, install and deploy the examples jar, and set a final classpath that
installs both.

```java
import static java.nio.file.Paths.get
import java.sql.Connection
import org.postgresql.pljava.packaging.Node
import static org.postgresql.pljava.packaging.Node.qp

Node n1 = Node.get_new_node("TestNode1")

try (
  AutoCloseable t1 = n1.initialized_cluster();
  AutoCloseable t2 = n1.started_server(Map.of(
    "client_min_messages", "info",
    "pljava.vmoptions",
      "-Xcheck:jni -enableassertions:org.postgresql.pljava..."
  ));
)
{
  try ( Connection c = n1.connect() )
  {
    qp(c, "create extension pljava");
  }

  /*
   * Get a new connection; 'create extension' always sets a near-silent logging
   * level, and PL/Java only checks once at VM start time, so in the same
   * session where 'create extension' was done, logging is somewhat suppressed.
   */
  try ( Connection c = n1.connect() )
  {
    qp(Node.installSaxonAndExamplesAndPath(c,
      get(System.getProperty("user.home"), ".m2", "repository").toString(),
      "10.2",
      true))
  }
}
/exit
```

The above example puts together most of the ideas covered here. While it does
demonstrate installing and deploying the examples jar (which runs all of the
tests contained in its deployment code), this example merely prints the output,
rather than examining it programmatically to evaluate success, and it does not
use its exit status to communicate success or failure to its invoker, as one
would expect of a test.

Worked-out examples that do the rest of that can be seen in the project
repository in the configuration files for the CI testing services.

One last `Node` method most useful for checking returned results
programmatically is `stateMachine` (full description in
[the javadocs][nodeapi]). For example, the `installSaxonAndExamplesAndPath` call
above returns a concatenation of four object streams: one from
`sqlj.install_jar` loading the Saxon jar, one from `sqlj.set_classpath` adding
it to the path, one from `sqlj.install_jar` loading the PL/Java examples jar
(which runs the tests in its deployment descriptor), and finally the
`sqlj.set_classpath` placing both jars on the path.

Each of those streams should end with a `void` result set, preceded by zero
or more `info` or `warning` (any `error` should be counted as test failure).
In the third stream, from installing the examples jar, any `warning` should
also be counted as a test failure: the tests in the deployment descriptor
report failures that way to avoid aborting the query, so more results can be
reported.

Using `stateMachine`, that can be expressed as a small set of states and
transitions that match that expected sequence. A state returns a positive
number _n_ to consume the input object it is looking at and transition to
state _n_ for the next item of input, or a negative number _-n_ to go to
state _n_ still with the same input item. The final accepting state returns
`true`; `false` from any state reports a mismatch. A `null` is supplied by
`stateMachine` after the last item on the input `Stream` (which therefore
must not contain nulls):

```java
succeeding &= stateMachine(
  "descriptive string for this state machine",
  null,

  Node.installSaxonAndExamplesAndPath(c,
    System.getProperty("mavenRepo"),
    System.getProperty("saxonVer"),
    true)
  .flatMap(Node::semiFlattenDiagnostics)
  .peek(Node::peek), // so they also appear in the log

  // states 1,2: maybe diagnostics, then a void result set (saxon install)
  (o,p,q) -> isDiagnostic(o, Set.of("error")) ? 1 : -2,
  (o,p,q) -> isVoidResultSet(o, 1, 1) ? 3 : false,

  // states 3,4: maybe diagnostics, then a void result set (set classpath)
  (o,p,q) -> isDiagnostic(o, Set.of("error")) ? 3 : -4,
  (o,p,q) -> isVoidResultSet(o, 1, 1) ? 5 : false,

  // states 5,6: maybe diagnostics, then void result set (example install)
  (o,p,q) -> isDiagnostic(o, Set.of("error", "warning")) ? 5 : -6,
  (o,p,q) -> isVoidResultSet(o, 1, 1) ? 7 : false,

  // states 7,8: maybe diagnostics, then a void result set (set classpath)
  (o,p,q) -> isDiagnostic(o, Set.of("error")) ? 7 : -8,
  (o,p,q) -> isVoidResultSet(o, 1, 1) ? 9 : false,

  // state 9: must be end of input
  (o,p,q) -> null == o
);

```

The `isDiagnostic` method shown above isn't part of the `Node` class; in the
actual test configurations in the repository, it is trivially defined in
`jshell` a few lines earlier. Not everything needs to be built in.

## Invoking `jshell` to use `Node.class`

As hinted above, the command needed to get `jshell` started so all the foregoing
goodness can happen is a bit unwieldy with options. It looks like this:

```sh
jshell \
  --execution local \
  "-J--class-path=$packageJar:$jdbcJar" \
  "--class-path=$packageJar" \
  "-J--add-modules=java.sql.rowset" \
  "-J-Dpgconfig=$pgConfig" \
  "-J-Dcom.impossibl.shadow.io.netty.noUnsafe=true"
```

where _$packageJar_ is a PL/Java self-installer jar, _$jdbcJar_ should point
to a `pgjdbc-ng` "fat jar" (`pgjdbc-ng-all`), and _$pgConfig_ should point to
the `pg_config` executable for the PostgreSQL installation that should be used.
(If there is only one PostgreSQL installation or the right `pg_config` will be
found on the search path, it doesn't have to be specified.)

The `-J--add-modules` is needed because even though `jshell` treats
`java.sql.rowset` as available by default, the local JVM it is running on
(because of `--execution local`) wouldn't know that without being told.

The path given to `jshell` itself (`--class-path` without the `-J`) does not
need to mention the `pgjdbc-ng` jar, because it can be a provider of the
`java.sql.Driver` service without having to be visible. If the script will
want to use `pgjdbc-ng`-specific classes, then the jar does have to be
on `jshell`'s class path too.

The `noUnsafe` setting silences a complaint from the `netty` library
about Java (correctly!) denying it access to private internals.

[jshell]: https://docs.oracle.com/en/java/javase/15/jshell/introduction-jshell.html
[nodeapi]: ../pljava-packaging/apidocs/org/postgresql/pljava/packaging/Node.html#method.summary
