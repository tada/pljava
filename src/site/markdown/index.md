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
types that should be seen in PostgreSQL. When they are compiled, the Java
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
[depdesc]: https://github.com/tada/pljava/wiki/Sql-deployment-descriptor
[jar]: https://docs.oracle.com/javase/tutorial/deployment/jar/index.html
[install_jar]: https://github.com/tada/pljava/wiki/SQL%20Functions#wiki-install_jar
