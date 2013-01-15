#Welcome to PL/Java#
If you have comments or ideas regarding this wiki, please convey them on the [Mailing List](http://lists.pgfoundry.org/mailman/listinfo/pljava-dev).

##Overview##
PL/Java is a free add-on module that brings Java™ Stored Procedures, Triggers, and Functions to the [PostgreSQL™](http://www.postgresql.org/) backend. The development started late 2003 and the first release of PL/Java arrived in January 2005. The project is released under the [[PLJava License]] license.

##Features##
* Ability to write both functions and triggers using Java 1.4 or higher.
* Standardized utilities (modeled after the SQL 2003 proposal) to install and maintain Java code in the database.
* Standardized mappings of parameters and result. Supports scalar and composite UDT's, pseudo types, arrays, and sets.
* An embedded, high performance, JDBC driver utilizing the internal PostgreSQL SPI routines.
* Metadata support for the JDBC driver. Both DatabaseMetaData and ResultSetMetaData are included.
* Full support for PostgreSQL 8.0 savepoints and exception handling.
* Ability to use IN, INOUT, and OUT parameters when used with PostgreSQL 8.1 or higher
* Two language handlers, one TRUSTED (the default) and one that is not TRUSTED (language tag is javaU to conform with the defacto standard)
* Transaction and Savepoint listeners enabling code execution when a transaction or savepoint is commited or rolled back.
* Integration with GNU GCJ on selected platforms.

##Documentation##
[[Installation Guide]]  
[[User Guide]]  
[[Migration Guide]]  
[[Contribution Guide]]  

##Resources##
[Downloads](http://pgfoundry.org/frs/?group_id=1000038)  
[Mailing List](http://lists.pgfoundry.org/mailman/listinfo/pljava-dev)    
[Bug Tracking](/tada/pljava/issues)  
[Older bug tracker at PgFoundry](http://pgfoundry.org/tracker/?group_id=1000038)

##Technology##
[[Technoloy in Brief]]  
[[The choice of JNI]]