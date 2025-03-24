# PL/Java VM option recommendations

The PostgreSQL configuration variable `pljava.vmoptions` can be used to
supply custom options to the Java VM when it is started. Several of these
options are likely to be worth setting.

If using [the OpenJ9 JVM][hsj9], be sure to look also at the
[VM options specific to OpenJ9][vmoptJ9].

## Selecting operation with or without security policy enforcement

PL/Java can operate [with security policy enforcement][policy], its former
default and only mode, or [with no policy enforcement][unenforced], the only
mode available on stock Java 24 and later.

When `pljava.libjvm_location` points to a Java 17 or earlier JVM, there is
no special VM option needed, and PL/Java will operate with policy enforcement
by default. However, when `pljava.libjvm_location` points to a Java 18 or later
JVM, `pljava.vmoptions` must contain either `-Djava.security.manager=allow` or
`-Djava.security.manager=disallow`, to select operation with or without policy
enforcement, respectively. No setting other than `allow` or `disallow` will
work. Only `disallow` is available for stock Java 24 or later.

For just how to configure specific Java versions, see
[Available policy-enforcement settings by Java version][smprop].

Before operating with `disallow`, the implications detailed in
[PL/Java with no policy enforcement][unenforced] should be carefully reviewed.

[policy]: ../use/policy.html
[unenforced]: ../use/unenforced.html
[smprop]: smproperty.html

## Adding to the set of readable modules

By default, a small set of Java modules (including `java.base`,
`org.postgresql.pljava`, and `java.sql` and its transitive dependencies,
which include `java.xml`) will be readable to any Java code installed with
`install_jar`.

While those modules may be enough for many uses, other modules are easily added
using `--add-modules` within `pljava.vmoptions`. For example,
`--add-modules=java.net.http,java.rmi` would make the HTTP Client and WebSocket
APIs readable, along with the Remote Method Invocation API.

For convenience, the module `java.se` simply transitively requires all the
modules that make up the full Java SE API, so `--add-modules=java.se` will make
that full API available to PL/Java code without further thought. The cost,
however, may be that PL/Java uses more memory and starts more slowly than if
only a few needed modules were named.

For just that reason, there is also a `--limit-modules` option that can be used
to trim the set of readable modules to the minimum genuinely needed. More on the
use of that option [here][limitmods].

[limitmods]: ../use/jpms.html#Limiting_the_module_graph

Third-party modular code can be made available by adding the modular jars
to `pljava.module_path` (see [configuration variables](../use/variables.html))
and naming those modules in `--add-modules`. PL/Java currently treats all jars
loaded with `install_jar` as unnamed-module, legacy classpath code.

For more, see [PL/Java and the Java Platform Module System](../use/jpms.html).

## Byte order for PL/Java-implemented user-defined types

PL/Java is free of byte-order issues except when using its features for building
user-defined types in Java. At sites with no current or planned use of
those features, this section does not require attention.

The 1.5.0 release of PL/Java begins a transition affecting the byte-order
defaults, which will be completed in a future release. No immediate action is
recommended; there is a [byte-order page](../use/byteorder.html) for more on
the topic and an advance notice of an expected future migration step.

## Class data sharing

Class data sharing is a commonly-supported Java virtual machine feature
that reduces both VM startup time and memory footprint by having many of
Java's runtime classes preprocessed into a file that can
be quickly memory-mapped into the process at Java startup, and shared
if there are multiple processes running Java VMs.

How to set it up differs depending on the Java VM in use, Hotspot
(in [Oracle Java][orjava] or [OpenJDK with Hotspot][OpenJDK]), or OpenJ9
(an [alternate JVM][hsj9] also available with [OpenJDK][]). The instructions on
this page are specific to Hotspot. For the corresponding feature in OpenJ9,
which is worth a good look, see the [class sharing in OpenJ9][cdsJ9] page.

For Hotspot, the class data sharing is enabled by including
`-Xshare:on` or `-Xshare:auto`
in the `pljava.vmoptions` string. In rough terms in 64-bit Java 8 it can
reduce the 'Metaspace' size per PL/Java backend by slightly over 4 MB, and
cut about 15 percent from the PL/Java startup time per process.

Sharing may be enabled automatically if the Java VM runs in `client` mode
(described below), but may need to be turned on with `-Xshare:on` or
`-Xshare=auto` if the VM runs in `server` mode.

The `on` setting can be useful for quickly confirming that sharing works,
as it will report a hard failure if anything is amiss. However, `auto` is
recommended in production: on an operating system with address-space layout
randomization, it is possible for some backends to (randomly) fail to map
the share. Under the `auto` setting, they will proceed without sharing (and
with higher resource usage, which may not be ideal), where with the `on`
setting they would simply fail.

*Note: the earliest documentation on class data sharing, dating to Java 1.5,
suggested that the feature was not available at all in server mode. In recent
VMs that is no longer the case, but it does have to be turned on.*

To confirm that sharing is on, look at PL/Java's startup message for a line
similar to:

    Java HotSpot(TM) 64-Bit Server VM (25.74-b02, mixed mode, sharing)

To see the startup message after PL/Java is already installed, use

    SET client_min_messages TO debug1;

in a new session, before executing any PL/Java function;

    SELECT sqlj.get_classpath('public');

for example.

### `classes.jsa` not present

If the option `-Xshare:on` makes PL/Java fail to start, with a message like

    An error has occurred while processing the shared archive file.
    Specified shared archive not found.

then the `classes.jsa` file was not automatically created when Java was
installed. It can be created with the simple command `java -Xshare:dump`
(run with adequate privilege to write in Java's installed location).

### Preloading PL/Java's classes as well as the Java runtime's

In Hotspot, the basic class data sharing feature includes only Java's own
runtime classes in the shared archive. When using Java 8 from Oracle, 8u40 or
later, or OpenJDK with Hotspot starting with Java 10, an expanded feature
called [application class data sharing][appcds]
is available, with which you can build a shared archive that preloads
PL/Java's classes as well as Java's own. In rough terms this doubles the
improvement in startup time seen with basic class data sharing alone,
for a total improvement (compared to no sharing) of 30 to 35 percent.
It also allows the memory per backend to be even further
scaled back, as discussed under "plausible settings" below.

([In OpenJ9][cdsJ9], the basic class sharing feature since Java 8 already
includes this ability, with no additional setup steps needed.)

#### Licensing considerations

Basic class data sharing is widely available with no restriction, but
*application class data sharing* [in Oracle Java][orjava] is a
"commercial feature" that first appeared in
Oracle Java 8. It will not work unless `pljava.vmoptions` also contain
`-XX:+UnlockCommercialFeatures` , with implications described in the
"supplemental license terms" of the Oracle
[binary code license for Java SE][bcl]. The license seems
to impose no burden on use for internal development and testing, but requires
negotiating an additional agreement with Oracle if the feature will be used
"in your internal business operations or for any commercial or production
purpose." It is available to consider for any application where the
additional performance margin can be given a price.

[In OpenJDK (with Hotspot)][OpenJDK], starting in Java 10, the same feature
is available and set up in the same way, but is freely usable; it does not
require any additional license, and does not require any
`-XX:+UnlockCommercialFeatures` to be added to the options.

Starting in Java 11, Oracle offers
[Oracle-branded downloads of both "Oracle JDK" and "Oracle's OpenJDK builds"][o]
that are "functionally identical aside from some cosmetic and packaging
differences". "Oracle's OpenJDK builds" may be used for production or
commercial purposes with no additional licensing, while any such use of
"Oracle JDK" requires a commercial license. The application class data sharing
feature is available in both, and no longer requires the
`-XX:+UnlockCommercialFeatures` in either case (not in "Oracle's OpenJDK builds"
because their use is unrestricted, and not in "Oracle JDK" because the
"commercial feature" is now, effectively, the entire JDK).

[In OpenJDK (with OpenJ9)][OpenJDK], the class-sharing feature present from
Java 8 onward will naturally share PL/Java's classes as well as the Java
runtime's, with no additional setup steps.

Here are the instructions for
[setting up application class data sharing in Hotspot][iads].

Here are instructions for [setting up class sharing (including application
classes!) in OpenJ9][cdsJ9].

[orjava]: http://www.oracle.com/technetwork/java/javase/downloads/index.html
[OpenJDK]: https://adoptopenjdk.net/
[hsj9]: https://www.eclipse.org/openj9/oj9_faq.html
[appcds]: http://docs.oracle.com/javase/8/docs/technotes/tools/unix/java.html#app_class_data_sharing
[bcl]: http://www.oracle.com/technetwork/java/javase/terms/license/index.html
[iads]: appcds.html
[vmoptJ9]: oj9vmopt.html
[cdsJ9]: oj9vmopt.html#How_to_set_up_class_sharing_in_OpenJ9
[o]: https://blogs.oracle.com/java-platform-group/oracle-jdk-releases-for-java-11-and-later
[cdsaot]: http://web.archive.org/web/20191020025455/https://blog.gilliard.lol/2017/10/04/AppCDS-and-Clojure.html

## `-XX:AOTLibrary=`

JDK 9 and later have included a tool, `jaotc`, that does ahead-of-time
compilation of class files to native code, producing a shared-object file
that can be named with the `-XX:AOTLibrary` option. Options to the `jaotc`
command can specify which jars, modules, individual classes, or individual
methods to compile and include. Optionally, `jaotc` can include additional
metadata with the compiled code (at the cost of a slightly larger file),
so that the Java runtime's tiered JIT compiler can still further optimize
the compiled-in-advance methods that turn out to be hot spots.

To make a library of manageable size, a list of touched classes and methods
from a sample run can be made, much as described above for application class
data sharing.

A [blog post by Matthew Gilliard][cdsaot] reports successfully combining `jaotc`
compilation and application class data sharing with good results, and goes into
some detail on the preparation steps.

## `-XX:+DisableAttachMechanism`

Management and monitoring tools like `jvisualvm` (included with the Oracle JDK)
can show basic information about any running Java process, but can show much
more detailed information by 'attaching' to the process.

By default, attachment requires the tool to be run on the same host as the
database server, and under the same identity. Those existing restrictions
should satisfy most security requirements (after all, a local process under
the postgres identity has many other ways to bypass PostgreSQL's assurances),
but there can also be non-security-related reasons to add the
`-XX:+DisableAttachMechanism` option:

Interaction with class data sharing
: Depending on the versions of Java and `jvisualvm`, it is possible for
    `jvisualvm` to hang after selecting a Java process to display, if that VM
    has class data sharing enabled. In such cases, adding
    `-XX:+DisableAttachMechanism` to the VM options will ensure that `jvisualvm`
    can at least display the usual overview information that is available when
    it cannot attach.

Memory footprint
: In Java 8, running the VM with `-XX:+DisableAttachMechanism` seems to
    reduce the necessary `MetaspaceSize` by about 3 megabytes.

## `client` and `server` VM

A Java VM may be able to operate in a `client` mode (optimized for starting
small apps with minimal latency), or a `server` mode, tailored to large,
long-running apps emphasizing throughput. Typically the `server` mode is
selected automatically if a 'server class' (large memory or 64-bit) platform
is detected.

PostgreSQL will often be run on 'server class' platforms, causing the VM to
select `server` mode, when the usage pattern in PL/Java more closely resembles
the `client` scenario.

While there is no one simple option to force `client` mode if the VM has
selected `server`, it is possible to adjust individual settings to more
appropriate values.

The heap size settings are important, because the defaults on a server-class
machine will be to grab a substantial fraction of all memory. In a PL/Java
application where many processes may run, it is more important to choose
smaller heap sizes close to what is actually needed. The size can be set
with `-Xms` to give a starting size, which the VM can expand if needed; this
is a safe option when you have an idea what the usual case memory demand will
be, but are not confident about the maximum that could sometimes be needed.
A hard limit can be set with `-Xmx` if you know a heap size that your code
should never need to exceed.

The [choice of garbage collector][gcchoice] can also affect the memory
footprint, as the different collectors incur different memory overheads.
If it performs adequately for the application, the `Serial` collector
seems to lead the choices in space efficiency, followed by `ConcMarkSweep`.
The `G1` collector, favored as `ConcMarkSweep`'s replacement, uses slightly
more space to work, while `Parallel` and `ParallelOld` will occupy more than
double the space of any of these.

[gcchoice]: https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/collectors.html

### Plausible settings

The optimal memory settings and garbage collector for a specific PL/Java
application are best determined by testing, but it can be helpful to have
an idea what is realistic. Java's default heap and metaspace sizes, especially
on a server-class machine, tend to be far larger than necessary.

As a point of reference, PL/Java can load, and execute the tests in, its
shipped `pljava-examples` jar with the following settings:

    -Xshare:on -XX:+DisableAttachMechanism -Xms2m -XX:+UseSerialGC

and end up with a heap grown to slightly under 4 MB, and a 5 MB metaspace
with less than 2 MB used. (The only way to get a metaspace smaller than 5 MB
is to use the `-XX:MaxMetaspaceSize` option, which is a hard limit, with the
risk of out-of-memory errors if set too low. The test described here can be
completed with the metaspace limited to 3 MB.)

If the Oracle *application class data sharing* feature is enabled, the
metaspace limit can be set at 2 MB, and conclude the test without growing
past 1 MB.

For an application where startup time is most critical (for example, only
trivial work is being done in Java, but needed in many short-lived
backend sessions), a few percent can be saved by setting the initial heap size
slightly larger than the smallest that works, to avoid an initial garbage
collection. In a test using PL/Java to do trivial work (nothing but
`SELECT sqlj.get_classpath('public')`), the sweet spot comes around
`-Xms5m` (which seems to end up allocating 6, but completes
with no GC in my testing).

### Performance tuning tips on the wiki

Between releases of this documentation, breaking news, tips, and metrics
on PL/Java performance tuning may be shared
[on the performance-tuning wiki page][ptwp].

[ptwp]: https://github.com/tada/pljava/wiki/Performance-tuning
