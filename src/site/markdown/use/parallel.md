# PL/Java in parallel query or background worker

With some restrictions, PL/Java can be used in [parallel queries][parq], from
PostgreSQL 9.6, and in some [background worker processes][bgworker] (as
introduced in PostgreSQL 9.3, though 9.5 or later is needed for support
in PL/Java).

[bgworker]: https://www.postgresql.org/docs/current/static/bgworker.html
[parq]: https://www.postgresql.org/docs/current/static/parallel-query.html

## Background worker processes

Because PL/Java requires access to a database containing the `sqlj` schema,
PL/Java is only usable in a worker process that initializes a database
connection, which must happen before the first use of any function that
depends on PL/Java.

## Parallel queries

Like any user-defined function, a PL/Java function can be
[annotated][paranno] with a level of "parallel safety", `UNSAFE` by default.

When a function labeled `UNSAFE` is used in a query, the query cannot be
parallelized at all. If a query contains a function labeled `RESTRICTED`, parts
of the query may execute in parallel, but the part that calls the `RESTRICTED`
function will be executed only in the lead process. A function labeled `SAFE`
may be executed in every process participating in the query.

[paranno]: ../pljava-api/apidocs/org.postgresql.pljava/org/postgresql/pljava/annotation/Function.html#parallel

### Parallel setup cost

PostgreSQL parallel query processing uses multiple operating-system processes,
and these processes are new for each parallel query. If a PL/Java function is
labeled `PARALLEL SAFE` and is pushed by the query planner to run in the
parallel worker processes, each new process will start a Java virtual machine.
The cost of doing so will reduce the expected advantage of parallel execution.

To inform the query planner of this trade-off, the value of the PostgreSQL
configuration variable [`parallel_setup_cost`][parsetcost] should be increased.
The startup cost can be minimized with attention to the
[PL/Java VM option recommendations][vmopt], including class data sharing.

[parsetcost]: https://www.postgresql.org/docs/current/static/runtime-config-query.html#GUC-PARALLEL-SETUP-COST
[vmopt]: ../install/vmoptions.html

### Limits on `RESTRICTED`/`SAFE` function behavior

There are stringent limits on what a function labeled `RESTRICTED` may do,
and even more stringent limits on what may be done in a function labeled `SAFE`.
The PostgreSQL manual describes the limits in the section
[Parallel Labeling for Functions and Aggregates][parlab].

[parlab]: https://www.postgresql.org/docs/current/static/parallel-safety.html#PARALLEL-LABELING

While PostgreSQL does check for some inappropriate operations from a
`PARALLEL SAFE` or `RESTRICTED` function, for the most part it relies on
functions being labeled correctly. When in doubt, the conservative approach
is to label a function `UNSAFE`, which can't go wrong. A function mistakenly
labeled `RESTRICTED` or `SAFE` could produce unpredictable results.

#### Internal workings of PL/Java

While a given PL/Java function itself may clearly qualify as `RESTRICTED` or
`SAFE` by inspection, there may still be cases where a forbidden operation
results from the internal workings of PL/Java itself. This has not been seen
in testing (simple parallel queries with `RESTRICTED` or `SAFE` PL/Java
functions work fine), but to rule out the possibility would require a careful
audit of PL/Java's code. Until then, it would be prudent for any application
involving parallel query with `RESTRICTED` or `SAFE` PL/Java functions
to be first tested in a non-production environment.

### Further reading

A [Parallel query and PL/Java][pqwiki] page on the PL/Java wiki is provided
to collect experience and tips regarding this significant new capability
that may be gathered in between updates to this documentation.

[README.parallel][rmp] in the PostgreSQL source, for more detail on why parallel
query works the way it does.

[rmp]: https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/access/transam/README.parallel
[pqwiki]: https://github.com/tada/pljava/wiki/Parallel-query-and-PLJava
