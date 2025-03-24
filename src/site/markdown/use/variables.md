# PL/Java configuration variable reference

These PostgreSQL configuration variables can influence PL/Java's operation:

`check_function_bodies`
: Although not technically a PL/Java variable, `check_function_bodies` affects
    how strictly PL/Java validates a new function at the time of a
    `CREATE FUNCTION` (or when installing a jar file with `CREATE FUNCTION`
    among its deployment actions). With `check_function_bodies` set to `on`,
    PL/Java will make sure that the referenced class and method can be loaded
    and resolved. If the referenced class depends on classes in other jars,
    those other jars must be already installed and on the class path, so
    loading jars with dependencies in the wrong order can incur validation
    errors. With `check_function_bodies` set to `off`, only basic syntax is
    checked at `CREATE FUNCTION` time, so it is possible to declare functions
    or install jars in any order, postponing any errors about unresolved
    dependencies until later when the functions are used.

`dynamic_library_path`
: Another non-PL/Java variable, `dynamic_library_path` influences
    where the PL/Java native code object (`.so`, `.dll`, `.bundle`, etc.) can
    be found, if the full path is not given to the `LOAD` command.

`server_encoding`
: Another non-PL/Java variable, this affects all text/character strings
    exchanged between PostgreSQL and Java. `UTF8` as the database and server
    encoding is strongly recommended. If a different encoding is used, it
    should be any of the available _fully defined_ character encodings. In
    particular, the PostgreSQL pseudo-encoding `SQL_ASCII` does not fully
    define what any values outside ASCII represent; it is usable, but
    [subject to limitations][sqlascii].

`pljava.allow_unenforced`
: Only used when PL/Java is run with no policy enforcement, this setting is
    a list of language names (such as `javau` and `java`) in which functions
    will be allowed to execute. This setting has an empty default, and should
    only be changed after careful review of the
    [PL/Java with no policy enforcement][unenforced] page.

`pljava.allow_unenforced_udt`
: Only used when PL/Java is run with no policy enforcement, this on/off
    setting controls whether data conversion functions associated with
    PL/Java [mapped user-defined types][mappedudt]
    will be allowed to execute. This setting defaults to off, and should
    only be changed after careful review of the
    [PL/Java with no policy enforcement][unenforced] page.

`pljava.debug`
: A boolean variable that, if set `on`, stops the process on first entry to
    PL/Java before the Java virtual machine is started. The process cannot
    proceed until a debugger is attached and used to set the static
    `Backend.c` variable `pljavaDebug` to zero. This may be useful for debugging
    PL/Java problems only seen in the context of some larger application
    that can't be stepped through.

`pljava.enable`
: Setting this variable `off` prevents PL/Java startup from completing, until
    the variable is later set `on`. It can be useful in some debugging settings.

`pljava.implementors`
: A list of "implementor names" that PL/Java will recognize when processing
    [deployment descriptors][depdesc] inside a jar file being installed or
    removed. Deployment descriptors can contain commands with no implementor
    name, which will be executed always, or with an implementor name, executed
    only on a system recognizing that name. By default, this list contains only
    the entry `postgresql`. A deployment descriptor that contains commands with
    other implementor names can achieve a rudimentary kind of conditional
    execution if earlier commands adjust this list of names, as described
    [here][condex]. _Commas separate
    elements of this list. Elements that are not regular identifiers need to be
    surrounded by double-quotes; prior to PostgreSQL 11, that syntax can be used
    directly in a `SET` command, while in 11 and after, such a value needs to be
    a (single-quoted) string explicitly containing the double quotes._

`pljava.java_thread_pg_entry`
: A choice of `allow`, `error`, `block`, or `throw` controlling PL/Java's thread
    management. Java makes heavy use of threading, while PostgreSQL may not be
    accessed by multiple threads concurrently. PL/Java's historical behavior is
    `allow`, which serializes access by Java threads into PostgreSQL, allowing
    a different Java thread in only when the current one calls or returns into
    Java. PL/Java formerly made some use of Java object finalizers, which
    required this approach, as finalizers run in their own thread.

    PL/Java itself no longer requires the ability for any thread to access
    PostgreSQL other than the original main thread. User code developed for
    PL/Java, however, may still rely on that ability. To test whether it does,
    the `error` or `throw` setting can be used here, and any attempt by a Java
    thread other
    than the main one to enter PostgreSQL will incur an exception (and stack
    trace, written to the server's standard error channel). When confident that
    there is no code that will need to enter PostgreSQL except on the main
    thread, the `block` setting can be used. That will eliminate PL/Java's
    frequent lock acquisitions and releases when the main thread crosses between
    PostgreSQL and Java, and will simply indefinitely block any other Java
    thread that attempts to enter PostgreSQL. This is an efficient setting, but
    can lead to blocked threads or a deadlocked backend if used with code that
    does attempt to access PG from more than one thread. (A JMX client, like
    JConsole, can identify the blocked threads, should that occur.)

    The `throw` setting is like `error` but more efficient: under the `error`
    setting, attempted entry by the wrong thread is detected in the native C
    code, only after a lock operation and call through JNI. Under the `throw`
    setting, the lock operations are elided and an entry attempt by the wrong
    thread results in no JNI call and an exception thrown directly in Java.

`pljava.libjvm_location`
: Used by PL/Java to load the Java runtime. The full path to a `libjvm` shared
    object (filename typically ending with `.so`, `.dll`, or `.dylib`).
    To determine the proper setting, see [finding the `libjvm` library][fljvm].

    The version of the Java library pointed to by this variable will determine
    whether PL/Java can run [with security policy enforcement][policy] or
    [with no policy enforcement][unenforced].

`pljava.module_path`
: The module path to be passed to the Java application class loader. The default
    is computed from the PostgreSQL configuration and is usually correct, unless
    PL/Java's files have been installed in unusual locations. If it must be set
    explicitly, there must be at least two (and usually only two) entries, the
    PL/Java API jar file and the PL/Java internals jar file. To determine the
    proper setting, see
    [finding the files produced by a PL/Java build](../install/locate.html).

    If additional modular jars are added to the module path,
    `--add-modules` in [`pljava.vmoptions`][addm] will make them readable by
    PL/Java code.

    For more on PL/Java's "module path" and "class path", see
    [PL/Java and the Java Platform Module System](jpms.html).

`pljava.policy_urls`
: Only used when PL/Java is running [with security policy enforcement][policy].
    When running [with no policy enforcement][unenforced], this variable is
    ignored. It is a list of URLs to Java security [policy files][policy]
    determining the permissions available to PL/Java functions. Each URL should
    be enclosed in double quotes; any double quote that is literally part of
    the URL may be represented as two double quotes (in SQL style) or as
    `%22` in the URL convention. Between double-quoted URLs, a comma is the
    list delimiter.

    The Java installation's `java.security` file usually defines two policy
    file locations:

    0. A systemwide policy from the Java vendor, sufficient for the Java runtime
        itself to function as expected
    0. A per-user location, where a policy file, if found, can add to the policy
        from the systemwide file.

    The list in `pljava.policy_urls` will modify the list from the Java
    installation, by default after the first entry, keeping the Java-supplied
    systemwide policy but replacing the customary per-user file (there
    probably isn't one in the home of the `postgres` user, and if there is
    it is probably not tailored for PL/Java).

    Any entry in this list can start with _n_`=` (inside the quotes) for a
    positive integer _n_, to specify which entry of Java's policy location list
    it will replace (entry 1 is the systemwide policy, 2 the customary user
    location). URLs not prefixed with _n_`=` will follow consecutively. If the
    first entry is not so prefixed, `2=` is assumed.

    A final entry of `=` (in the required double quotes) will prevent
    use of any remaining entries in the Java site-configured list.

    This setting defaults to
    `"file:${org.postgresql.sysconfdir}/pljava.policy","="`

`pljava.release_lingering_savepoints`
: How the return from a PL/Java function will treat any savepoints created
    within it that have not been explicitly either released (the savepoint
    analog of "committed") or rolled back and released.
    If `off` (the default), they will be rolled back. If `on`, they will be
    released/committed. If possible, rather than setting this variable `on`,
    it would be safer to fix the function to release its own savepoints when
    appropriate.

    A savepoint continues to exist after being used as a rollback target.
    This is JDBC-specified behavior, but was not PL/Java's behavior before
    release 1.5.3, so code may exist that did not explicitly release or roll
    back a savepoint after rolling back to it once. To avoid a behavior change
    for such code, PL/Java will always release a savepoint that is still live
    at function return, regardless of this setting, if the savepoint has already
    been rolled back.

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

    Some important settings can be made here, and are described on the
    [VM options page][vmop]. For Java 18 and later, this variable must include
    a `-Djava.security.manager=allow` or `-Djava.security.manager=disallow]`
    setting, determining whether PL/Java will run
    [with security policy enforcement][policy] or
    [with no policy enforcement][unenforced], and those pages should be reviewed
    for the implications of the choice. Details vary by Java version; see
    [Available policy-enforcement settings by Java version][smprop].

[pre92]: ../install/prepg92.html
[depdesc]: https://github.com/tada/pljava/wiki/Sql-deployment-descriptor
[fljvm]: ../install/locatejvm.html
[jmx]: http://www.oracle.com/technetwork/articles/java/javamanagement-140525.html
[jvvm]: http://docs.oracle.com/javase/8/docs/technotes/guides/visualvm/
[jow]: https://docs.oracle.com/javase/8/docs/technotes/tools/windows/java.html
[jou]: https://docs.oracle.com/javase/8/docs/technotes/tools/unix/java.html
[vmop]: ../install/vmoptions.html
[sqlascii]: charsets.html#Using_PLJava_with_server_encoding_SQL_ASCII
[addm]: ../install/vmoptions.html#Adding_to_the_set_of_readable_modules
[condex]: ../pljava-api/apidocs/org.postgresql.pljava/org/postgresql/pljava/annotation/package-summary.html#conditional-execution-in-the-deployment-descriptor-heading
[policy]: policy.html
[unenforced]: unenforced.html
[mappedudt]: ../pljava-api/apidocs/org.postgresql.pljava/org/postgresql/pljava/annotation/MappedUDT.html
[smprop]: ../install/smproperty.html
