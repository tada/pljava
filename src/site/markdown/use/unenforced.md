# PL/Java with no policy enforcement

This page describes how PL/Java operates when it is not enforcing any security
policy, as when running on stock Java 24 or later.

When the newest Java language features are not needed, it may be preferable to
use a Java 23 or earlier JVM to retain PL/Java's historically fine-grained and
configurable limits on what the Java code can do. For that case, please see
instead the [configuring permissions in PL/Java][policy] page.

## History: policy enforcement pre-Java 24

PL/Java has historically been able to enforce configurable limits on the
behavior of Java code, and to offer more than one "procedural language" with
distinct names, such as `java` and `javau`, for declaring functions with
different limits on what they can do. In PostgreSQL parlance, the language named
without 'u' would be described as 'trusted', meaning any functions created in
that language would run with strict limits. Such functions could be created by
any PostgreSQL user granted `USAGE` permission on that language. The language
named with 'u' would be described as 'untrusted' and impose fewer limits on what
functions can do; accordingly, only PostgreSQL superusers would be allowed to
create functions in such a language.

PL/Java, going further than many PLs, allowed tailoring of the exact policies
imposed for both `java` and `javau`, and also allowed creation of additional
language aliases beyond those two, with different tailored policies for each.

Those capabilities remain available when PL/Java is used with Java versions
up through Java 23, and are described more fully in
[configuring permissions in PL/Java][policy].

## The present: Java 24 and later, no policy enforcement in PL/Java 1.6

The Java language features necessary for policy enforcement in the PL/Java 1.6
series have been removed from the language as of Java 24. It is possible to
use Java 24 or later with an up-to-date 1.6-series PL/Java, but only by running
with no policy enforcement at all.

That does not mean only that PL/Java's 'trusted' and 'untrusted' languages are
no longer different: it means that even the 'untrusted' language's more-relaxed
former limits can no longer be enforced. When run with enforcement disabled,
PL/Java is better described as a wholly-'untrusted' PL with nearly no limits on
what the Java code can do.

The only limits a Java 24 or later runtime can impose on what the Java code can
do are those imposed by the isolation of modules in the
[Java Platform Module System][jpms] and by a small number of VM options, which
will be discussed further below.

This picture is radically different from the historical one with enforcement. To
run PL/Java in this mode may be a reasonable choice if Java 24 or later language
features are wanted and if all of the Java code to be used is considered well
vetted, thoroughly trusted, and defensively written.

For news of possible directions for policy enforcement in future PL/Java
versions, please bookmark [this wiki page][jep411].

## Opting in to PL/Java with no enforcement

For PL/Java to run with no policy enforcement (and, therefore, for it to run
at all on Java 24 or later), specific configuration settings must be made to opt
in.

### In `pljava.vmoptions`

The string `-Djava.security.manager=disallow` must appear in the setting of
[`pljava.vmoptions`][vmoptions] or PL/Java will be unable to start on Java 24
or later.

For details on what `java.security.manager` settings to use on other Java
versions, see [Available policy-enforcement settings by Java version][smprop].

### in `pljava.allow_unenforced`

Typically, a PL extension that provides only 'untrusted' execution will define
only a single, untrusted, PL name: `plpython3u` would be an example.

PL/Java, however:

* Has historically offered both a `javau` and a trusted `java` PL
* Still can offer both, when run on a Java 23 or older JVM
* May have been installed in a database with functions already created of both
    types, and then switched to running on Java 24 and without enforcement
* Can also be switched back to a Java 23 or older JVM and provide enforcement
    again

Therefore, a PL/Java installation still normally provides two (or more) named
PLs, each being declared to PostgreSQL as either 'trusted' or not.

When running with no enforcement, however:

* Only PostgreSQL superusers can create functions, even using PL names shown as
    'trusted', and without regard to any grants of `USAGE` on those PLs.

    There may, however, be functions already defined in 'trusted' PLs that were
    created by non-superusers with `USAGE` granted, at some earlier time when
    PL/Java was running with enforcement. It may be important to audit those
    functions' code before allowing them to run.

* No PL/Java function at all will be allowed to run unless the name of its PL is
    included in the `pljava.allow_unenforced` [configuration variable][vbls].

* When there are existing PL/Java functions declared in more than one named PL,
    they can be audited in separate batches, with the name of each PL added
    to the `pljava.allow_unenforced` setting after the functions declared
    in that PL have been approved. Or, individual functions, once approved, can
    be redeclared with the PL name changed to one already listed in
    `pljava.allow_unenforced`.

* Creation of a new function, even by a superuser, with a PL name not listed in
    `pljava.allow_unenforced` will normally raise an error when PL/Java is
    running without enforcement. This will not be detected, however, at times
    when `check_function_bodies` is `off`, so is better seen as a reminder than
    as a form of security. The more-important check is the one made when
    the function executes.

### in `pljava.allow_unenforced_udt`

Java methods for input and output conversion of PL/Java
[mapped user-defined types][mappedudt], which are executed directly by PL/Java
and have no SQL declarations to carry a PL name, are allowed to execute only if
`pljava.allow_unenforced_udt` is `on`. The table `sqlj.typemap_entry` can be
queried for a list of mapped UDT Java classes to audit before changing this
setting to `on`.

## Hardening for PL/Java with no policy enforcement

### External hardening measures

Developers of the Java language, in their rationale for removing the
Java features needed for policy enforcement, have placed strong emphasis on
available protections at the OS or container level, external to the process
running Java. For the case of PL/Java, that would mean typical hardening
measures such as running PostgreSQL in a container, using [SELinux][selinux],
perhaps in conjunction with [sepgsql][], and so on.

Those external measures, however, generally confine what the process can do as a
whole. Because PL/Java executes within a PostgreSQL backend process, which must
still be allowed to do everything PostgreSQL itself does, it is difficult for an
external measure to restrict what Java code can do any more narrowly than that.

### Java hardening measures

Java features do remain that can be used to put some outer guardrails on what
the Java code can do. They include some specific settings that can be made in
`pljava.vmoptions`, and the module-isolation features of the
[Java Platform Module System][jpms] generally. These should be conscientiously
used:

#### `--sun-misc-unsafe-memory-access=deny`

This setting is first available in Java 23. It should be used whenever
available, and especially in Java 24 or later with no policy enforcement.
Without this setting, and in the absence of policy enforcement, any Java code
can access memory in ways that break the Java object model.

The only reason not to set this option would be when knowingly using a Java
library that requires the access, if there is no update or alternative to using
that library. More modern code would use later APIs for which access can be
selectively granted to specific modules.

#### `--illegal-native-access=deny`

This setting is first available in Java 24 and should be used whenever
available. Without this setting, in the absence of policy enforcement,
any Java code can execute native code. There is arguably no good reason to
relax this setting, as options already exist to selectively grant such access
to specific modules that need it, if any.

#### Module system protections

Java's module system is one of the most important remaining mechanisms for
limiting what Java code may be able to do. Keeping unneeded modules out of the
module graph, advantageous already for startup speed and memory footprint,
also means whatever those modules do won't be available to Java code.

The supplied [examples jar][examples] provides a function, [java_modules][],
that can be used to see what modules have been resolved into Java's boot module
layer.

The `--limit-modules` VM option can be effectively used to resolve fewer modules
when PL/Java loads. As of this writing, in early 2025, starting PL/Java with no
`--add-modules` or `--limit-modules` options results in 48 modules in the graph,
while a simple `--limit-modules=org.postgresql.pljava.internal` added to
`pljava.vmoptions` reduces the graph to nine modules---all the transitive
requirements of PL/Java itself---and all of PL/Java's supplied examples
successfully run. Any additional modules needed for user code can be added back
with `--add-modules`. More details at [Limiting the module graph][limiting].

The `--sun-misc-unsafe-memory-access=deny` option mentioned above denies access
to certain methods of the `sun.misc.Unsafe` class, which is supplied by
the `jdk.unsupported` module. It may be preferable, when there is no other need
for it, to also make sure `jdk.unsupported` is not present in the module graph
at all.

##### Modularize code needing special access

It is currently less convenient in PL/Java 1.6 to provide user code in modular
form: the `sqlj.install_jar` and `sqlj.set_classpath` functions manage a class
path, not a module path. Supplying a module requires placing it on the file
system and adding it to `pljava.module_path`.

The extra inconvenience may be worthwhile in some cases where there is a subset
of code that requires special treatment, such as an exception to the native
access restriction. Placing just that code into a named module on the module
path allows the exception to be made just for that module by name. With the
removal of Java's former fine-grained policy permissions, such module-level
exceptions are the finest-grained controls remaining in stock Java.

For news of possible directions for policy enforcement in future PL/Java
versions, please bookmark [this wiki page][jep411].

### Defensive coding

#### Java system properties

It can be laborious to audit a code base for assumptions that a given Java
system property has a value that is reliable. In the case of no policy
enforcement, when any system property can be changed by any code at any time,
best practice is to rely on defensive copies taken early, before arbitrary
user code can have run.

For example, `PrintWriter.println` uses a copy of the `line.separator` property
taken early in the JVM's own initialization, so code that relies on `println` to
write a newline will be more dependable than code using `line.separator`
directly.

PL/Java itself takes a defensive copy of all system properties early in its own
startup, immediately after adding the properties that PL/Java sets. The
`frozenSystemProperties` method of the `org.postgresql.pljava.Session` object
returns this defensive copy, as a subclass of `java.util.Properties` that is
unmodifiable (throwing `UnsupportedOperationException` from methods where a
modification would otherwise result).

[policy]: policy.html
[jpms]: jpms.html
[vmoptions]: ../install/vmoptions.html
[vbls]: variables.html
[jep411]: https://github.com/tada/pljava/wiki/JEP-411
[selinux]: ../install/selinux.html
[sepgsql]: https://www.postgresql.org/docs/17/sepgsql.html
[limiting]: jpms.html#Limiting_the_module_graph
[mappedudt]: ../pljava-api/apidocs/org.postgresql.pljava/org/postgresql/pljava/annotation/MappedUDT.html
[examples]: ../examples/examples.html
[java_modules]: ../pljava-examples/apidocs/org/postgresql/pljava/example/annotation/Modules.html#method-detail
[smprop]: ../install/smproperty.html
