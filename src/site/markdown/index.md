# PL/Java: stored procedures, triggers, and functions for PostgreSQL™

PL/Java is a free open-source extension for [PostgreSQL™][pgsql] that allows
stored procedures, triggers, and functions to be written in the
[Java™][java] language and executed in the backend. More about the features
and benefits of PL/Java can be read on the [wiki][].

[pgsql]: http://www.postgresql.org/
[java]: https://www.oracle.com/java/
[wiki]: https://github.com/tada/pljava/wiki

## About this site

This site includes reference
information on PL/Java, covering how to [build][] it, [install][] it,
and [use][] it. There is also a [wiki][] with more information and examples,
though in some cases dated. While information from the wiki is gradually
being migrated to this site and brought up to date, you should still check
the wiki for information you do not find here.

The following sections offer very brief summaries.

[build]: build/build.html
[install]: install/install.html
[use]: use/use.html

## Use of PL/Java, in a nutshell

Backend functions and triggers are written in Java using a directly-connected,
efficient version of the standard Java [JDBC][] API that PL/Java transparently
provides, with enhanced capabilities found in the [PL/Java API][pljapi].

PL/Java source files can use Java [annotations][] from the
[org.postgresql.pljava.annotation package][oppa] to identify the methods and
types that should be seen in PostgreSQL, as in this [example code][trgann].
For a step-by-step example, there is always [Hello, world][helwo].

When the sources are compiled, the Java
compiler will also write an [SQLJ deployment descriptor][depdesc] containing
the SQL statments that must be executed when installing and uninstalling the
compiled Java code in the PostgreSQL backend.

When the compiled Java code and the deployment descriptor file are stored
together in a [JAR file][jar], PL/Java\'s [install_jar][] function will both
load the code into PostgreSQL and execute the necessary SQL commands in the
deployment descriptor, making the new types/functions/triggers available for
use.

[JDBC]: https://docs.oracle.com/javase/tutorial/jdbc/
[pljapi]: pljava-api/apidocs/org.postgresql.pljava/module-summary.html
[annotations]: https://docs.oracle.com/javase/tutorial/java/annotations/
[oppa]: pljava-api/apidocs/org.postgresql.pljava/org/postgresql/pljava/annotation/package-summary.html#package-description
[trgann]: https://github.com/tada/pljava/blob/master/pljava-examples/src/main/java/org/postgresql/pljava/example/annotation/Triggers.java
[depdesc]: https://github.com/tada/pljava/wiki/Sql-deployment-descriptor
[jar]: https://docs.oracle.com/javase/tutorial/deployment/jar/index.html
[install_jar]: https://github.com/tada/pljava/wiki/SQL%20Functions#wiki-install_jar
[helwo]: use/hello.html

## Installation, in a nutshell

PL/Java can be downloaded, then [built using Maven][build]. The build produces
a native code library (file with name ending in .so, .dll, etc., depending on
the plaform) and a JAR file. PostgreSQL must be configured to know where these
are, in addition to the native library for the Java runtime itself. The
[installation guide][install] has details.

### Installation from a prebuilt package

There may be a prebuilt distribution available for your platform.
You can check for that on the [wiki prebuilt packages page][wpbpp],
which can be kept up to date with known available packages.

[wpbpp]: https://github.com/tada/pljava/wiki/Prebuilt-packages

## Moving PL/Java forward

The [Contribution Guide][cguide] describes how to contribute to PL/Java\'s
development. While only the [PL/Java API][pljapi] module must be understood
to _use_ PL/Java, contributing may require getting familiar with PL/Java\'s
other [modules](modules.html). Most have JavaDoc available, which will be
found under each module\'s Project Reports menu.

[cguide]: https://github.com/tada/pljava/wiki/Contribution-guide
