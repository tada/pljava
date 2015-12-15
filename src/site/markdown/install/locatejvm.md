# Finding the Java Runtime Environment `libjvm` library

PL/Java [installation][inst] requires knowing the filesystem paths
to some items produced by the [build][], and also to the `libjvm`
shared object for the Java Runtime Environment that is to be used.

[build]: ../build/build.html
[inst]: install.html

To find that object can be a bit of an exercise, because of
the variety of locations and naming schemes for the Java runtime on
different platforms. It may be simplest to do a filesystem search for
the name `libjvm.*` under the Java home directory (which is reported
by `mvn -v` assuming the JRE that you used for running Maven is the
one you intend to use at run time).

The filename extension may be `.so` on many systems, `.dll` on Windows,
etc.

_Todo: add details for Mac, which may be different enough to be confusing._

_Todo: make this whole part easier._

If a tool such as Linux `strace` is available to see what files are opened
by a process, this works:

```
strace -e open java 2>&1 | grep libjvm
```

## Using a less-specific path

The methods above may find the `libjvm` object on a very specific path
(for example, including the JRE vendor, major, minor, version, and patch
numbers). If that exact path is used to `SET pljava.libjvm_location`
in PostgreSQL, PL/Java will stop working as soon as the next Java patch
is applied.

Typical OS distributions will also provide some less specific, alias
paths to the Java runtime environment. On Linux, for example, there
may be a `/usr/lib/jvm` directory with entries using only the major
and minor Java version that are symbolic links to the fully specified
JRE. By using such an alias instead of the absolutely specific Java
path, you can avoid headaches when Java is updated.

By the same token, the directory of aliases may contain a completely
generic one like `jre`, linked to whichever Java version is considered
current. Using an alias that is too generic could possibly invite headaches
if the default Java version is ever changed to one your PL/Java modules
were not written for (or PL/Java itself was not built for).
