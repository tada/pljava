# PL/Java VM option recommendations for the OpenJ9 JVM

The OpenJ9 JVM accepts a number of standard options that are the same as
those accepted by Hotspot, but also many nonstandardized ones that are not.
A complete list of options it accepts can be found [here][oj9opts].

There is one option that should be considered for any PL/Java configuration:

* [`-Xquickstart`][xqs]

It can reduce the JVM startup time by doing less JIT compilation and at lower
optimization levels. On the other hand, if the work to be done in PL/Java is
substantial enough, the increased run time of the less-optimized code can make
the overall performance effect net negative. It should be measured under
expected conditions.

[xqs]: https://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.vm.80.doc/docs/xquickstart.html

Beyond that, and the usual opportunities to adjust memory allocations and
garbage-collector settings, anyone setting up PL/Java with OpenJ9 should
seriously consider setting up class sharing, which is much simpler in
OpenJ9 than in Hotspot, and is the subject of the rest of this page.

## How to set up class sharing in OpenJ9

OpenJ9 is an [alternative to the Hotspot JVM][hsj9] that is available in
[OpenJDK][] (which can be downloaded with the choice of either JVM).

OpenJ9 includes a _dynamically managed_ class data sharing feature: it is
able to cache ahead-of-time compiled versions of classes in a file to be
sharably memory-mapped by all backends running PL/Java. The shared cache
significantly reduces both the aggregate memory footprint of multiple
backend JVMs and the per-JVM startup time. It is [described here][ej9cds].

The OpenJ9 class-sharing feature is similar to Hotspot's
[application class data sharing][iads], but with a major advantage in the
context of PL/Java: it is able to share not only classes of the Java runtime
itself and those on `pljava.module_path` (PL/Java's own internals), but also
classes from application jars loaded with `sqlj.install_jar`. The Hotspot
counterpart can share only the first two of those categories.

OpenJ9 sharing is also free of the commercial-license encumbrance on the
Hotspot feature in Oracle Java 8 and later (OpenJDK with Hotspot also includes
the feature, without the encumbrance, but only from Java 10 on).
OpenJ9 sharing is also much less fuss to set up.

To see how much less, the Hotspot setup is a manual, three-step affair
to be done in advance of production use. You choose some code to run that you
hope will exercise all the classes you would like in the shared
archive and dump the loaded-class list, then generate the shared archive
from that list, and finally save the right option in `pljava.vmoptions` to have
the shared archive used at run time.

By contrast, you set up OpenJ9 to share classes with the following step:

1. Add an `-Xshareclasses` option to `pljava.vmoptions` to tell OpenJ9 to
    share classes.

OpenJ9 will then, if the first time, create a shared archive and dynamically
manage it, adding ahead-of-time-compiled versions of classes as they are
used in your application.

[oj9opts]: https://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.vm.80.doc/docs/x_jvm_commands.html
[ej9cds]: https://www.ibm.com/developerworks/library/j-class-sharing-openj9/
[iads]: appcds.html
[vmop]: vmoptions.html
[OpenJDK]: https://adoptopenjdk.net/
[hsj9]: https://www.eclipse.org/openj9/oj9_faq.html
[shclutil]: https://www.ibm.com/developerworks/library/j-class-sharing-openj9/#sharedclassesutilities

### Setup

Arrange `pljava.vmoptions` to contain an option `-Xshareclasses`.

The option can take various suboptions. Two interesting ones are:

    -Xshareclasses:name=/path/to/some/file
    -Xshareclasses:cacheDir=/path/to/some/dir

to control where PL/Java's shared class versions get cached. The first variant
specifies the exact file that will be memory mapped, while the second specifies
what directory will contain the (automatically named) file.

Using either suboption (or both; suboptions are separated by commas), you can
arrange for PL/Java's shared classes to be cached separately from other uses
of Java on the same system. You could even, by saving different
`pljava.vmoptions` settings per database or per user, arrange separate class
caches for distinct applications using PL/Java.

All of the suboptions accepted by `-Xshareclasses` are listed [here][xsc].

[xsc]: https://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.vm.80.doc/docs/xshareclasses.html

#### Hotspot-like loaded-once-and-frozen class share, or dynamic one

If you wish to emulate the Hotspot class sharing feature where a shared class
archive is created ahead of time and then frozen, you can let the application
run for a while with the `-Xshareclasses` option not containing `readonly`,
until the shared cache has been well warmed, and then add `readonly` to the
`-Xshareclasses` option as saved in `pljava.vmoptions`.

It will then be necessary (as it is with Hotspot) to expressly repeat the
process when new versions of the JRE or PL/Java are installed, or (unlike
Hotspot, which does not share them) application jars are updated. This is
not because OpenJ9 would continue loading the wrong versions from cache,
but because it would necessarily bypass the cache to load the current ones.

If the `readonly` option is not used, the OpenJ9 shared cache will dynamically
cache new versions of classes as they are loaded. It does not, however, purge
older versions automatically. There are [shared classes utilities][shclutil]
available to monitor utilization of the cache space, and to reset caches if
needed.

With a dynamic shared cache, OpenJ9 may also continue to refine the shared
data even for unchanged classes that have already been cached. It does not
replace the originally cached representations, but over time can add JIT hints
based on profile data collected in longer-running processes, which can help
new, shorter-lived processes more quickly reach the same level of optimization
as key methods are just-in-time recompiled.

### Effect of `sqlj.replace_jar`

When PL/Java replaces a jar, the class loaders and cached function mappings
are reset in the backend that replaced the jar, so subsequent PL/Java function
calls in that backend will use the new classes.

In other sessions active at the time the jar is replaced, without OpenJ9 class
sharing, execution will continue with the already-loaded classes, unless/until
another class needs to be loaded from the old jar, which will fail with a
`ClassNotFoundException`.

With OpenJ9 class sharing, other sessions may continue executing even as they
load classes, as long as the old class versions are found in the shared cache.

### Java libraries

If your own PL/Java code depends on other Java libraries distributed as
jars, the usual recommendation would be to install those as well into the
database with `sqlj.install_jar`, and use `sqlj.set_classpath` to make them
available. That keeps everything handled uniformly within the database.
With OpenJ9 sharing, there is no downside to this approach, as classes
installed in the database are shared, just as those on the system classpath.

### Thorough class verification

When using class sharing, consider adding `-Xverify:all` to
the other VM options, perhaps once while warming a cache that you will then
treat as `readonly`. Java sometimes applies more relaxed verification to
classes it loads from the system classpath. With class sharing in use, classes
may be loaded and verified early, then saved in the shared archive for quick
loading later. In those circumstances, the cost of requesting verification for
all classes may not be prohibitive, while increasing robustness against damaged
class files.

### Cache invalidation if database or PL/Java reinitialized

The way that PL/Java's class loading currently integrates with OpenJ9 class
sharing relies on a PostgreSQL `SERIAL` column to distinguish updated versions
of classes loaded with `sqlj.install_jar`/`replace_jar`.

If the database is recreated, PL/Java is deinstalled and reinstalled, or
anything else happens to restart the `SERIAL` sequence, it may be wise to
destroy any existing OpenJ9 class share, to avoid incorrectly matching
older cached versions of classes.

### Performance tuning tips on the wiki

Between releases of this documentation, breaking news, tips, and metrics
on PL/Java performance tuning may be shared
[on the performance-tuning wiki page][ptwp].

[ptwp]: https://github.com/tada/pljava/wiki/Performance-tuning
