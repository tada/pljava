# Versions of external packages needed to build and use PL/Java

As of November 2015, the following version constraints are known.

## Java

No version of Java before 1.6 ("Java 6") is supported. The PL/Java code
makes use of Java features first appearing in Java 6.

As for later versions of Java, backward compatibility in the language is
generally good. The most likely problem areas with a new Java version will
be additions to the JDBC API that PL/Java has not yet implemented.

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

PL/Java does not currently support PostgreSQL releases before 8.1.
Recent work is known to have introduced dependencies on 8.1 features.

The current aim is to avoid deliberately breaking compatibility back
to 8.2. (A former commercial fork of PostgreSQL 8.2 recently returned
to the open-source fold with a *really* old version of PL/Java, so
the aim is that the current PL/Java should be a possible upgrade there.)
