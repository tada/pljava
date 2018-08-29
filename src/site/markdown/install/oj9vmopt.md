# PL/Java VM option recommendations for the OpenJ9 JVM

The OpenJ9 JVM accepts a number of standard options that are the same as
those accepted by Hotspot, but also many nonstandardized ones that are not.
A complete list of options it accepts can be found [here][oj9opts].

There is one option likely to benefit _every_ PL/Java configuration:

* [`-Xquickstart`][xqs]

[xqs]: https://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.vm.80.doc/docs/xquickstart.html

Beyond that, and the usual opportunities to adjust memory allocations and
garbage-collector settings, anyone setting up PL/Java with OpenJ9 should
seriously consider setting up class sharing, which is much simpler in
OpenJ9 than in Hotspot, and is the subject of the rest of this page.

## How to set up class sharing in OpenJ9

OpenJ9 is an [alternative to the Hotspot JVM][hsj9] that is available in
[OpenJDK][] (which can be downloaded with the choice of either JVM).

OpenJ9 includes a _dynamically managed_ class data sharing feature
comparable to Hotspot's [application class data sharing][iads], but
much less fuss to set up.

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
used in your application. The details are [described here][ej9cds].

[oj9opts]: https://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.vm.80.doc/docs/x_jvm_commands.html
[ej9cds]: https://www.ibm.com/developerworks/library/j-class-sharing-openj9/
[iads]: appcds.html
[vmop]: vmoptions.html
[OpenJDK]: https://adoptopenjdk.net/
[hsj9]: https://www.eclipse.org/openj9/oj9_faq.html

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

If you wish to emulate the Hotspot class sharing feature where a shared class
archive is created ahead of time and then frozen, you can let the application
run for a while with the `-Xshareclasses` option not containing `readonly`,
until the shared cache has been well warmed, and then add `readonly` to the
`-Xshareclasses` option saved in `pljava.vmoptions`.

All of the suboptions accepted by `-Xshareclasses` are listed [here][xsc].

[xsc]: https://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.vm.80.doc/docs/xshareclasses.html

### Java libraries

If your own PL/Java code depends on other Java libraries distributed as
jars, the usual recommendation would be to install those as well into the
database with `sqlj.install_jar`, and use `sqlj.set_classpath` to make them
available. That keeps everything handled uniformly within the database.

On the other hand, if you are building a shared archive, and some of the
dependency libraries are substantial, you could consider instead storing
those jars on the file system and naming them in `pljava.classpath`. Those
library classes can then also be included in the shared archive.

Not everything from the original jar file can go into the shared archive.
After the archive has been built, the original jars still must be on the
file system and named in `pljava.classpath`.

When using class sharing, consider adding `-Xverify:all` to
the other VM options. Java sometimes applies more relaxed verification to
classes it loads from the system classpath. With class sharing in use, classes
may be loaded and verified early, then saved in the shared archive for quick
loading later. In those circumstances, the cost of requesting verification for
all classes may not be prohibitive.
