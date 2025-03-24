# The thread context class loader

Starting with PL/Java 1.6.3, within an SQL-declared PL/Java function, the
class loader returned by `Thread.currentThread().getContextClassLoader`
is the one that corresponds to the per-schema classpath that has been set
with [`SQLJ.SET_CLASSPATH`][scp] for the schema where the function is
declared (assuming no Java code uses `setContextClassLoader` to change it).

Many available Java libraries, as well as built-in Java facilities using the
[`ServiceLoader`][slo], refer to the context class loader, so this behavior
ensures they will see the classes that are available on the classpath that was
set up for the PL/Java function. In versions where PL/Java did not set the
context loader, awkward arrangements could be needed in user code for the
desired classes or services to be found.

## Limits on the implementation

To set this loader with minimal overhead on function entry, PL/Java uses native
access to a `Thread` field. It is possible that some Java runtimes can exist
where the expected field is not present, and PL/Java will fall back (with a
warning) to not managing the context loader. The warning can be suppressed
by explicitly configuring PL/Java not to manage the context loader, as described
below.

It is also possible for an application or library to create subclasses
of `Thread` that override the behavior of `getContextClassLoader` so that
the value set by PL/Java will have no effect. PL/Java does not detect such
a case to work around it.

When PL/Java is used [with policy enforcement][policy], a clear sign of code
that does subclass `Thread` in this way is that it will need the
`enableContextClassLoaderOverride` [`RuntimePermission`][runtimeperm] to be
granted in the [policy][]. When PL/Java is used [without enforcement][nopolicy],
there will be no such clear sign, making a problem of this kind harder to trace.

## Effects on application code

With this change as of PL/Java 1.6.3, application or library code that uses
the [`ServiceLoader`][slo], or otherwise refers to the context class loader,
will find services or resources available on the class path that was set up
for the function. Typically, this behavior is wanted. In prior PL/Java versions,
services and resources might be found only if they were available to the
system class loader.

For example, a call like `javax.xml.transform.TransformerFactory.newInstance()`
might return Java's built-in XSLT 1.0 implementation if there is nothing else
on the class path, but return an XSLT 3.0 implementation if the configured
PL/Java class path includes a Saxon jar.

If there are cases where an application intends to use a built-in Java
implementation regardless of the class path, there may be a method available
that specifies that behavior. For example,
[`TransformerFactory.newDefaultInstance()`][tfndi] will always return Java's
own `Transformer` implementation.

If an application misbehaves as a result of finding implementations on the
class path it was not finding before, and cannot be conveniently fixed by
adjusting the class path or changing to `newDefaultInstance`-like methods
in the code, PL/Java can be configured for its old behavior of not setting
the context class loader, as described below.

## Effects on UDT methods

User-defined types implemented in PL/Java have support methods that are
transparently invoked to convert database values to Java values and back.
This can happen within a PL/Java function, when it gets or sets values in
`ResultSet`, `PreparedStatement`, `SQLInput`, or `SQLOutput` objects, and
also conceptually "before" or "after" the function proper, to convert its
incoming parameters and its return value(s). In all such contexts, the UDT
methods are considered to act on behalf of that target PL/Java function,
and the context class loader they see is the one for the schema where the
target function is declared.

A [`BaseUDT`][baseudt] implemented in PL/Java has support methods that are
declared to PostgreSQL as SQL functions in their own right. In addition to being
transparently called on behalf of another PL/Java function, with the behavior
described above, they can be called directly by PostgreSQL like any other
SQL function. When that happens, like any other declared function, they will
have the context class loader set according to the schema containing the
declaration.

## Suppressing context loader management

Some circumstances may call for keeping the pre-1.6.3 behavior
where no management of the context class loader was done. That could be to
avoid unplanned effects on applications as described above, or to suppress
the warning message if running on a JVM where PL/Java's technique doesn't work.

To suppress the loader management, add

```
-Dorg.postgresql.pljava.context.loader=unmanaged
```

in the `pljava.vmoptions` [setting](../use/variables.html).


[scp]: ../pljava/apidocs/org.postgresql.pljava.internal/org/postgresql/pljava/management/Commands.html#set_classpath
[slo]: https://docs.oracle.com/javase/9/docs/api/java/util/ServiceLoader.html
[tfndi]: https://docs.oracle.com/javase/9/docs/api/javax/xml/transform/TransformerFactory.html#newDefaultInstance--
[runtimeperm]: https://docs.oracle.com/en/java/javase/14/docs/api/java.base/java/lang/RuntimePermission.html
[baseudt]: ../pljava-api/apidocs/org.postgresql.pljava/org/postgresql/pljava/annotation/BaseUDT.html
[policy]: ../use/policy.html
[nopolicy]: ../use/unenforced.html
