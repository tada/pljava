# PL/Java: stored procedures, triggers, and functions for PostgreSQL™

PL/Java is a free open-source extension for [PostgreSQL™][pgsql] that allows
stored procedures, triggers, and functions to be written in the
[Java™][java] language and executed in the backend. More about the features
and benefits of PL/Java can be read on the [wiki][].

[pgsql]: http://www.postgresql.org/
[java]: https://www.oracle.com/java/
[wiki]: https://github.com/tada/pljava/wiki

## About this site

You have reached the web site for comprehensive (largely machine generated)
information on PL/Java, suitable for advanced use or for those interested in
contributing to the project. More basic information on how to [set up][iguide]
or [use][uguide] PL/Java can be found on the [wiki][]. The following sections
offer very brief summaries.

[iguide]: https://github.com/tada/pljava/wiki/Installation-guide
[uguide]: https://github.com/tada/pljava/wiki/User-guide

## Use of PL/Java, in a nutshell

Backend functions and triggers are written in Java using a directly-connected,
efficient version of the standard Java [JDBC][] API that PL/Java transparently
provides, with enhanced capabilities found in the [PL/Java API][pljapi].

PL/Java source files can use Java [annotations][] from the
[org.postgresql.pljava.annotation package][oppa] to identify the methods and
types that should be seen in PostgreSQL, as in this [example code][trgann].

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
[pljapi]: pljava-api/apidocs/index.html?org/postgresql/pljava/package-summary.html#package_description
[annotations]: https://docs.oracle.com/javase/tutorial/java/annotations/
[oppa]: pljava-api/apidocs/index.html?org/postgresql/pljava/annotation/package-summary.html#package_description
[trgann]: https://github.com/tada/pljava/blob/master/pljava-examples/src/main/java/org/postgresql/pljava/example/annotation/Triggers.java
[depdesc]: https://github.com/tada/pljava/wiki/Sql-deployment-descriptor
[jar]: https://docs.oracle.com/javase/tutorial/deployment/jar/index.html
[install_jar]: https://github.com/tada/pljava/wiki/SQL%20Functions#wiki-install_jar

## Installation, in a nutshell

PL/Java can be downloaded, then [built using Maven][build]. The build produces
a native code library (file with name ending in .so, .dll, etc., depending on
the plaform) and a JAR file. PostgreSQL must be configured to know where these
are, in addition to the native library for the Java runtime itself. The
[installation guide][iguide] has details.

[build]: https://github.com/tada/pljava/wiki/Building-pl-java

## Moving PL/Java forward

The [Contribution Guide][cguide] describes how to contribute to PL/Java\'s
development. While only the [PL/Java API][pljapi] module must be understood
to _use_ PL/Java, contributing may require getting familiar with PL/Java\'s
other [modules](modules.html). Most have JavaDoc available, which will be
found under each module\'s Project Reports menu.

[cguide]: https://github.com/tada/pljava/wiki/Contribution-guide
