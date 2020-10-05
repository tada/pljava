# Versions of external packages needed to build and use PL/Java

As of mid-2020, the following version constraints are known.

## Java

No version of Java before 9 is supported. The PL/Java code
makes use of Java features first appearing in Java 9.

As for later versions of Java, backward compatibility in the language is
generally good. Before Java 8, most likely problem areas with a new Java
version tended to be additions to the JDBC API that PL/Java had not yet
implemented. Since Java 8, even JDBC additions have not caused problems for
existing PL/Java code, as they have taken advantage of the default-methods
feature introduced in that release.

In the PL/Java 1.6.x series, the build can be done with Java 9 or newer.
Once built, PL/Java is able to use another Java 9 or later JVM at run time,
simply by setting
[the `pljava.libjvm_location` variable][jvml] to the desired version's library
(but see the exceptions described next).

### Exceptions to build-version / runtime-version compatibility

Because of compiler bugs in Java 10 and 11, builds done with those versions
will not run on earlier Java releases. A build on 10 requires 10 or later at
run time; a build on 11 requires 11 or later at run time. To ensure that the
built extension can use any Java 9 or later at run time, it must be built on
Java 9, or on Java 12 or later.

PL/Java can run application code written for a later Java version than PL/Java
itself was built with, as long as that later JRE version is used at run time.
That also allows PL/Java to take advantage of recent Java implementation
advances such as [class data sharing][cds].

PL/Java has been successfully used with [Oracle Java][orj] and with
[OpenJDK][], which is available with
[either the Hotspot or the OpenJ9 JVM][hsj9]. It can also be built and used
with [GraalVM][].

[jvml]: ../use/variables.html
[cds]:  ../install/vmoptions.html#Class_data_sharing
[orj]: https://www.oracle.com/technetwork/java/javase/downloads/index.html
[OpenJDK]: https://adoptopenjdk.net/
[hsj9]: https://www.eclipse.org/openj9/oj9_faq.html
[GraalVM]: https://www.graalvm.org/

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

PL/Java does not currently support PostgreSQL releases before 8.2.
Recent work is known to have introduced dependencies on 8.2 features.

The current aim is to avoid deliberately breaking compatibility back
to 8.2. (A former commercial fork of PostgreSQL 8.2 recently returned
to the open-source fold with a *really* old version of PL/Java, so
the aim is that the current PL/Java should be a possible upgrade there.)

More current PostgreSQL versions, naturally, are the focus of development
and receive more attention in testing.

PL/Java 1.5.1 has been successfully built and run on at least one platform
with PostgreSQL versions from 11 to 8.2, the latest maintenance
release for each.
