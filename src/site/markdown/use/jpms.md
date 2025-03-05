# PL/Java and the Java Platform Module System

Java 9 introduced the [Java Platform Module System][jpms] (JPMS), allowing
Java code to be strongly encapsulated into modules that declare their
dependency relationships to other modules explicitly.

One consequence is that where a pre-Java-9 Java runtime would have
a "class path", a Java-9-or-later runtime has a "module path" as well as
a legacy class path.

Pre-1.6.0 releases of PL/Java were not modularized, and can be used on
older Java runtimes. They will also run on Java 9 and later, but still only
as non-modular code. The Java runtime is launched with a class path that
includes a PL/Java jar and usually nothing else. That single jar includes
both API and implementation classes.

PL/Java 1.6.0 or later is structured as an API module (named
`org.postgresql.pljava`) and an implementation module (named
`org.postgresql.pljava.internal`) supplied in two distinct jar files.
The Java runtime is launched with a *module path*, not a class path,
that names both the implementation and the API jar, and usually nothing else.
For a class path, the runtime is launched by default with none at all.

User code developed to run in PL/Java is normally installed via SQL
calling the `sqlj.install_jar` and `sqlj.set_classpath` functions, and these
mechanisms are independent of the Java runtime's class path at launch.

In Java 9 and later, both a module path and a class path are supported so that
newer, modularized code and legacy, non-modular code can interoperate, and
legacy code can be migrated over time:

* A jar file that embodies an explicit named module should be placed on
    the module path. It participates fully in the Java module system, and
    its access to other modules will be determined by its explicit
    `requires`/`exports`/`uses`/`provides`/`opens` relationships.

* A jar file containing legacy, non-modular code should be placed on the
    class path, and is treated as part of an unnamed module that has access
    to the exports and opens of all other _readable_ modules. Such code will,
    therefore, continue to work as it did before Java 9, provided the needed
    modules are _readable_, as explained below. (Even a jar containing Java 9+
    modular code will be treated this way, if found on the class path rather
    than the module path.)

* A jar file can be placed on the module path even if it does not contain
    an explicit named module. In that case, it becomes an "automatic" module,
    with a name derived from the jar file name, or a better name can be
    specified in its manifest. It will continue to have access to the
    exports of any other modules, like an unnamed module, but other modules
    will be able to declare dependencies on it by name. This can be a useful
    intermediate step in migrating legacy code to modules.

As of PL/Java 1.6, while PL/Java's own implementation is modularized, it
implements a version of the ISO SQL Java Routines and Types specification
that does not include Java module system concepts. Its `sqlj.set_classpath`
function manipulates an internal class path, not a module path, and a jar
installed with `sqlj.install_jar` behaves as legacy code in an unnamed module.

## Readable versus observable modules

Using Java's terminology, modules that can be found on the module path are
_observable_. Not all of those are automatically _readable_; the _readable_
ones in a JVM instance are initially those encountered, at JVM start-up, in
the "recursive enumeration" step of [module resolution][resolution].

Recursive enumeration begins with some root modules, and proceeds until all of
the modules on which they (transitively) depend have been added to the readable
set. When PL/Java is launched in a session, PL/Java's own module is a root,
and so the readable modules will include those PL/Java itself depends on,
such as `java.base`, `java.sql`, and the other modules `java.sql` names with
`requires transitive` directives.

Those modules may be enough for many uses of PL/Java. However, if code for use
in PL/Java will refer to other modules,
[`--add-modules` in `pljava.vmoptions`][addm] can be used to add more roots.
Because of recursive enumeration, it is enough to add just one module, or a few
modules, whose dependencies recursively cover whatever modules will be needed.

At one extreme for convenience, Java provides a module, `java.se`, that simply
declares dependencies on the other modules that make up the full Java SE API.
Therefore, `--add-modules=java.se` will ensure that any PL/Java code is able to
refer to any of the Java SE API. However, PL/Java instances may use less memory
and start up more quickly if an effort is made to add only modules actually
needed.

### Limiting the module graph

Less conveniently perhaps, but advantageously for memory footprint and quick
startup, the [`--limit-modules`][limitmods] option can be used. As of this
writing in early 2025, starting up a simple PL/Java installation on Java 24
with no `--add-modules` option results in 48 modules resolved, and the 48
include some unlikely choices for PL/Java purposes, such as `java.desktop`,
`jdk.unsupported.desktop`, `jdk.javadoc`, and others.

With the option `--limit-modules=org.postgresql.pljava.internal` added, only
nine modules are resolved---the transitive closure of those required by PL/Java
itself---and all of PL/Java's supplied examples successfully run.

The `--add-modules` option can then be used to make any other actually-needed
modules available again. Those named with `--add-modules` are implicitly added
to those named with `--limit-modules`, so there is no need to change the
`--limit-modules` setting when adding another module. For example,

```
--limit-modules=org.postgresql.pljava.internal --add-modules=java.net.http
```

will allow use of `java.net.http` in addition to the nine modules resolved for
PL/Java itself.

Limiting the module graph can be especially advisable when running PL/Java with
no security policy enforcement, as required on stock Java 24 and later. The page
[PL/Java with no policy enforcement][unenforced] should be carefully reviewed
for other implications of running PL/Java that way.

The supplied [examples jar][examples] provides a function, [java_modules][],
that can be used to see what modules have been resolved into Java's boot module
layer.

For more detail on why the boot layer includes the modules it does,
`-Djdk.module.showModuleResolution=true` can be added temporarily in
`pljava.vmoptions`, and a log of module requirements and bindings will be sent
to the standard output of the backend process when PL/Java starts. PostgreSQL,
however, may normally start backend processes with standard output going
nowhere, so the logged information may be invisible unless running PostgreSQL
in [a test harness][node].

## Configuring the launch-time module path

The configuration variable `pljava.module_path` controls the
module path used to launch the Java runtime. Its default is constructed to
include the expected locations of PL/Java's own implementation and API jars,
and nothing else, so there is typically no need to set it explicitly
unless those jars have been installed at unusual locations.
Its syntax is simply the pathnames of the jar files, separated by the
correct path separator character for the platform (often a colon, or a
semicolon on Windows).

There may at times be a reason to place additional modular jars on this
module path. Whenever it is explicitly set, it must still include the
correct locations of the PL/Java implementation and API jars.

## Configuring the launch-time class path

The launch-time class path has an empty default, which means (because
PL/Java has a main module) that there is no class path. It does *not*
default to finding class files in the backend's current directory (which,
in a pre-Java-9 runtime, is what an empty class path would mean).

There may at times be a reason to place some jar or jars on the launch-time
class path, rather than installing them in SQL with `sqlj.install_jar` in
the usual way. It can be set by adding a `-Djava.class.path=...` in the
`pljava.vmoptions` configuration variable. Like the module path, its syntax
is simply the jar file pathnames, separated by the platform's path separator
character.

[jpms]: https://cr.openjdk.java.net/~mr/jigsaw/spec/
[resolution]: https://docs.oracle.com/javase/9/docs/api/java/lang/module/package-summary.html#resolution
[addm]: ../install/vmoptions.html#Adding_to_the_set_of_readable_modules
[limitmods]: https://openjdk.org/jeps/261#Limiting-the-observable-modules
[unenforced]: unenforced.html
[examples]: ../examples/examples.html
[java_modules]: ../pljava-examples/apidocs/org/postgresql/pljava/example/annotation/Modules.html#method-detail
[node]: ../develop/node.html
