# Versions of external packages needed to build and use PL/Java

As of August 2018, the following version constraints are known.

## Java

No version of Java before 1.6 ("Java 6") is supported. The PL/Java code
makes use of Java features first appearing in Java 6.

As for later versions of Java, backward compatibility in the language is
generally good. The most likely problem areas with a new Java version will
be additions to the JDBC API that PL/Java has not yet implemented.

In the PL/Java 1.5.x series, the build system has not been reworked for
building with Java 9 or newer. However, PL/Java can be built with Java 8
and use a newer JVM at run time, simply by setting
[the `pljava.libjvm_location` variable][jvml] to the newer version's library.

That allows PL/Java to run application code written for the latest Java
versions, and also to take advantage of recent Java implementation advances
such as [class data sharing][cds].

PL/Java has been successfully used with [Oracle Java][orj] and with
[OpenJDK][], which is available with
[either the Hotspot or the OpenJ9 JVM][hsj9].

[jvml]: ../use/variables.html
[cds]:  ../install/vmoptions.html#Class_data_sharing
[orj]: https://www.oracle.com/technetwork/java/javase/downloads/index.html
[OpenJDK]: https://adoptopenjdk.net/
[hsj9]: https://www.eclipse.org/openj9/oj9_faq.html

## Maven

PL/Java can be built with Maven versions at least as far back as 3.0.4.
As shown in the [Maven release history][mvnhist], **Maven releases after
3.2.5 require Java 7 or later**. If you wish to *build* PL/Java using a
Java 6 development kit, you must use a Maven version not newer than 3.2.5.

[mvnhist]: https://maven.apache.org/docs/history.html

## gcc

If you are building on a platform where `gcc` is the compiler,
versions 4.3.0 or later are recommended in order to avoid a
[sea of unhelpful compiler messages][gcc35214].

[gcc35214]: https://gcc.gnu.org/bugzilla/show_bug.cgi?id=35214

## PostgreSQL

PL/Java does not currently support PostgreSQL releases before 8.2.
Recent work is known to have introduced dependencies on 8.2 features.

The current aim is to avoid deliberately breaking compatibility back
to 8.2. (A former commercial fork of PostgreSQL 8.2 recently returned
to the open-source fold with a *really* old version of PL/Java, so
the aim is that the current PL/Java should be a possible upgrade there.)

More current PostgreSQL versions, naturally, are the focus of development
and receive more attention in testing.

PL/Java 1.5.1 has been successfully built and run on at least one platform
with PostgreSQL versions from 11 (beta3) to 8.2, the latest maintenance
release for each.
