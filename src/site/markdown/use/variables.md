# PL/Java configuration variable reference

These PostgreSQL configuration variables can influence PL/Java's operation:

`dynamic_library_path`
: Although strictly not a PL/Java variable, `dynamic_library_path` influences
    where the PL/Java native code object (`.so`, `.dll`, `.bundle`, etc.) can
    be found, if the full path is not given to the `LOAD` command.

`server_encoding`
: Another non-PL/Java variable, this affects all text/character strings
    exchanged between PostgreSQL and Java. `UTF8` as the database and server
    encoding is _strongly_ recommended. If a different encoding is used, it
    should be any of the available _fully defined_ character encodings. In
    particular, the PostgreSQL pseudo-encoding `SQL_ASCII` (which means
    "characters within ASCII are ASCII, others are no-one-knows-what") will
    not work well with PL/Java, raising exceptions whenever strings contain
    non-ASCII characters.

`pljava.classpath`
: The class path to be passed to the Java application class loader. There
    must be at least one (and usually only one) entry, the PL/Java jar file
    itself. To determine the proper setting, see
    [finding the files produced by a PL/Java build](../install/locate.html).

`pljava.debug`
: A boolean variable that, if set `on`, stops the process on first entry to
    PL/Java before the Java virtual machine is started. The process cannot
    proceed until a debugger is attached and used to set the static
    `Backend.c` variable `pljavaDebug` to zero. This may be useful for debugging
    PL/Java problems only seen in the context of some larger application
    that can't be stepped through.

`pljava.enable`
: Setting this variable `off` prevents PL/Java startup from completing, until
    the variable is later set `on`. It can be useful when
    [installing PL/Java on PostgreSQL versions before 9.2][pre92].

`pljava.implementors`
: A list of "implementor names" that PL/Java will recognize when processing
    [deployment descriptors][depdesc] inside a jar file being installed or
    removed. Deployment descriptors can contain commands with no implementor
    name, which will be executed always, or with an implementor name, executed
    only on a system recognizing that name. By default, this list contains only
    the entry `PostgreSQL`. A deployment descriptor that contains commands with
    other implementor names can achieve a rudimentary kind of conditional
    execution if earlier commands adjust this list of names.

`pljava.libjvm_location`
: Used by PL/Java to load the Java runtime. The full path to a `libjvm` shared
    object (filename typically ending with `.so`, `.dll`, or `.dylib`).
    To determine the proper setting, see [finding the `libjvm` library][fljvm].

`pljava.release_lingering_savepoints`
: How the return from a PL/Java function will treat any savepoints created
    within it that have not been explicitly either released (the savepoint
    analog of "committed") or rolled back.
    If `off` (the default), they will be rolled back. If `on`, they will be
    released/committed. If possible, rather than setting this variable `on`,
    it would be better to fix the function to release its own savepoints when
    appropriate.

`pljava.statement_cache_size`
: The number of most-recently-prepared statements PL/Java will keep open.

`pljava.vmoptions`
: Any options to be passed to the Java runtime, in the same form as the
    documented options for the `java` command ([windows][jow],
    [Unix family][jou]). The string is split on whitespace unless found
    between single or double quotes. A backslash treats the following
    character literally, but the backslash itself remains in the string,
    so not all values can be expressed with these rules. If the server
    encoding is not `UTF8`, only ASCII characters should be used in
    `pljava.vmoptions`. The exact quoting and encoding rules for this variable
    may be adjusted in a future PL/Java version.

    The default value for this variable is `-XX:+DisableAttachMechanism`
    so that [Java Management Extensions][jmx] tools such as
    [`jvisualvm`][jvvm] are prevented by default from attaching to the
    process. If you set any explicit value for `pljava.vmoptions`, you should
    include `-XX:+DisableAttachMechanism` if you wish to prevent such tools
    from attaching. If you intend to use the advanced features of such tools
    (such as code profiling in VisualVM), then simply set any value for
    `pljava.vmoptions` that does not contain `-XX:+DisableAttachMechanism`.
    You may also need to do so if you are using a non-Oracle Java runtime
    and it rejects that option as unknown.

    Even with attachment disabled, a tool such as VisualVM can display some
    basic information about PL/Java processes (as long as it runs as the same
    operating system user as the `postmaster`), including the arguments,
    system properties, class and thread counts, and overall heap usage.

[pre92]: ../install/prepg92.html
[depdesc]: https://github.com/tada/pljava/wiki/Sql-deployment-descriptor
[fljvm]: ../install/locatejvm.html
[jmx]: http://www.oracle.com/technetwork/articles/java/javamanagement-140525.html
[jvvm]: http://docs.oracle.com/javase/8/docs/technotes/guides/visualvm/
[jow]: https://docs.oracle.com/javase/8/docs/technotes/tools/windows/java.html
[jou]: https://docs.oracle.com/javase/8/docs/technotes/tools/unix/java.html
