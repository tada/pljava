# Versions of external packages needed to build and use PL/Java

As of fall 2025, the following version constraints are known.

## Java

No version of Java before 9 is supported. The PL/Java code
makes use of Java features first appearing in Java 9.

PL/Java's [security policy enforcement][policy] is available only when the Java
version at run time is 9 through 23. On Java 24 or later runtime, PL/Java 1.6.x
can only run [with no policy enforcement][nopolicy]. This is independed of
the Java version used at build time, and so the availability of enforcement
can be changed at any time after building, by changing the
`pljava.libjvm_location` [configuration variable][jvml] to point to a Java
shared object of a different version.

Other than the loss of policy enforcement in Java 24, backward compatibility
in the language is
generally good. Before Java 8, most likely problem areas with a new Java
version tended to be additions to the JDBC API that PL/Java had not yet
implemented. Since Java 8, even JDBC additions have not caused problems for
existing PL/Java code, as they have taken advantage of the default-methods
feature introduced in that release.

In the PL/Java 1.6.x series, the build can be done with Java 9 or newer.
Once built, PL/Java is able to use another Java 9 or later JVM at run time,
simply by setting
[the `pljava.libjvm_location` variable][jvml] to the desired version's library.

PL/Java can run application code written for a later Java version than PL/Java
itself was built with, as long as that later JRE version is used at run time.
That also allows PL/Java to take advantage of recent Java implementation
advances such as [class data sharing][cds].

Some builds of Java 20 are affected by a bug, [JDK-8309515][]. PL/Java will
report an error if it detects it is affected by that bug, and the solution can
be to use a Java version earlier than 20, or one recent enough to have the bug
fixed. The bug was fixed in Java 21.

PL/Java has been successfully used with [Oracle Java][orj] and with
[OpenJDK][], which is available with
[either the Hotspot or the OpenJ9 JVM][hsj9]. It can also be built and used
with [GraalVM][].

If building with GraalVM, please add `-Dpolyglot.js.nashorn-compat=true` on
the `mvn` command line.

[jvml]: ../use/variables.html
[cds]:  ../install/vmoptions.html#Class_data_sharing
[orj]: https://www.oracle.com/technetwork/java/javase/downloads/index.html
[OpenJDK]: https://adoptopenjdk.net/
[hsj9]: https://www.eclipse.org/openj9/oj9_faq.html
[GraalVM]: https://www.graalvm.org/
[JDK-8309515]: https://bugs.openjdk.org/browse/JDK-8309515

## Maven

PL/Java can be built with Maven versions as far back as 3.5.2.
Maven's requirements can be seen in the [Maven release history][mvnhist].

[mvnhist]: https://maven.apache.org/docs/history.html

## gcc

If you are building on a platform where `gcc` is the compiler,
versions 4.3.0 or later are recommended in order to avoid a
[sea of unhelpful compiler messages][gcc35214].

[gcc35214]: https://gcc.gnu.org/bugzilla/show_bug.cgi?id=35214

## PostgreSQL

The PL/Java 1.6 series does not support PostgreSQL earlier than 9.5.

More current PostgreSQL versions, naturally, are the focus of development
and receive more attention in testing.

PL/Java 1.6.10 has been successfully built and run on at least one platform
with PostgreSQL versions from 18 to 9.5, the latest maintenance
release for each.
