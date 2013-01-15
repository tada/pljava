##Rationale behind using JNI as opposed to threads in a remote JVM process.##
###Reasons to use a high level language like Java™ in the backend###
A large part of the reason why JNI was chosen in favor of an RPC based, single JVM solution was due to the expected use-cases. Enterprise systems today are almost always 3-tier or n-tier. Database functions, triggers, and stored procedures are mechanisms that extend the functionality of the backend tier. They typically rely on a tight integration with the database due to a very high rate of interactions and execute inside of the database largely to limit the number of interactions between the middle tier and the backend tier. Some typical use-cases:

* Referential integrity enforcement. Using Java, referential integrity can be implemented that goes beyond what can be done using the standard SQL semantics. It may involve checking XML documents, enforcing some meta-driven rule system, or other complex tasks that put high demands on the implementation language.
* Advanced pattern recognition. Soundex, image comparison, etc.
* XML support functions. Java comes with a lot of XML support. Parsers etc. are readily available.
* Support functions for O/R mappers. A variety of support can be implemented depending on design. One example is an O/R mapper that allows methods on persistent objects. A lot can be gained if such methods are pushed down and executed within the database. Consider the following (OQL):
```sql
SELECT AVG(x.salary - x.computeTax()) FROM Employee x WHERE x.salary > 120000;
```
Pushing the computeTax logic down to the database instead of computing it in the middle tier (where much or the O/R logic resides) is a huge gain from a performance standpoint. The statement could be transformed into SQL as:
```sql
SELECT AVG(x.salary - computeTax(x.salary)) FROM Employee x WHERE x.salary > 120000;
```
As a result, very few interactions (typically only one) need to be made between the middle and the backend tier.
* Views and indexes making use of computed values. In the above example and index could be created on computeTax(x.salary) and a view could express that as net_income.
* Message queue management. Delivering or fetching things using message queues or other delivery mechanisms. As with most interactions with other processes, this requires transaction coordination of some kind.

One might argue that since a JVM often is present running an app-server in the middle tier, would it not be more efficient if that JVM also executed the database functions and triggers? In my opinion, this would be very bad. One major reason for moving execution down to the database is performance (by minimizing the number of roundtrips between the app-server and the database) another is separation of concern. Referential data integrity and other ways to extend the functionality of the database should not be the app-servers concern, it belongs in the backend tier. Other aspects like database versus app-server administration, replication of code and permission changes for functions, and running different tiers on different servers, makes it even worse.

###Resource consumption###
Having one JVM per connection instead of one thread per connection running in the same JVM will undoubtedly consume more resources. There are however a couple of facts that must be remembered:
* The overhead of multiple processes is already present due to the fact that each connection is a process in a PostgreSQL system.
* In order to keep connections separated in case they run in the same JVM, some kind of "compartments" must be created. Either you create them using parallel class loader chains (similar to how EAR files are managed in an EJB server) or you use a less protective model similar to a servlet engine. In order to get a separation that is comparable to what you get using separate JVM's, you must chose the former. That consumes some resources.
* The JVM has undergone a series of improvements in order to reduce footprint and startup time. Some significant improvements where made in Java 1.4 and Java 1.5 introduces Java Heap Self Tuning, Class Data Sharing, and Garbage Collector Ergonomics (read more here), technologies that will minimize the startup time and make the JVM adopt its resource consumption in a much improved way.
* PL/Java can make use of the GCJ. Using this technology, all core classes will be compiled into binaries and optionally pre-loaded by the postmaster. It also means that all modules that are loaded using the install_jar/replace_jar can be compiled into real shared objects. Finally, it means that the footprint for each "JVM" will be significantly decreased.

###Connection pooling###
In the Java community you are very likely to use a connection pool. The pool will ensure that the number of connections stays as low as possible and that connections are reused (instead of closed and reestablished). New JVMs are started rarely.

###Connection isolation###
Separate JVMs gives you a much higher degree of isolation. This brings a number of advantages:
* There's no problem attaching a debugger to one connection (one JVM) while the others run unaffected.
* There's no chance that one connection manages to accidentally (or maliciously) exchange dirty data with another connection.
* A process that performs tasks that consume a lot of CPU under a long period of time can be scheduled with a lower priority using a simple OS command.
* The JVMs can be brought down and restarted individually.
* Security policies are much easier to enforce.

###Transaction visibility###
In order to maintain the correct visibility, the transaction must somehow be propagated to the Java layer. I can see two solutions for this using RPC. Either an XA aware JDBC-driver is used (requires XA support from PostgreSQL) or a JDBC driver is written so that it calls back to the SPI functions in the invoking process. Both choices results in an increased number of RPC calls and a negative performance impact.

The PL/Java approach is to use the underlying SPI interfaces directly through JNI by providing a "pseudo connection" that implements the JDBC interfaces. The mapping is thus very direct. Data need never be serialized nor duplicated.

###RPC performance###
Remote procedure calls are extremely expensive compared to in-process calls. Relying on an RPC mechanism for Java calls will cripple the usefulness of such an implementation a great deal. Here are two examples:
* In order for an update trigger to function using RPC, you can choose one of two approaches. Either you limit the number of RPC calls and send two full Tuples (old and new) and a Tuple Descriptor to the remote JVM, and then pass a third Tuple (the modified new) back to the original, or you pass those structures by reference (as CORBA remote objects) and perform one RPC call each time you access them. You have a tradeoff between on one hand, limited functionality and poor performance, and on the other, good functionality and really bad performance.
* When one or several Java functions are used in the projection or filter of a SELECT statement on a query processing several thousand rows, each row will cause at least one call to Java. In case of RPC, this implies that the OS needs to do at least two context switches (back and forth) for each row in the query.

Using JNI to directly access structures like TriggerData, Relation, TupleDesc, and HeapTuple minimizes the amount of data that needs to be copied. Parameters and return values that are primitives need not even become Java objects. A 32-bit int4 Datum can be directly passed as a Java int (jint in JNI).

###Simplicity###
I've have some experience of work involving CORBA and other RPCs. They add a fair amount of complexity to the process. JNI however, is invisible to the user.