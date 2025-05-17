# README for PL/Java server-side extension (C)

This directory contains the information required to create documentation
for the PL/Java server-side extension source code (C).

The documentation is created using the popular [`doxygen`](https://www.doxygen.nl/index.html).
I've been hitting a small glitch since it has a strong bias towards C++ instead of C - I'm
sure there's an easy remedy but nearly all search results include (and are focused on) C++.

Doxygen can produce documentation in multiple formats, not just HTML. The `manpage` format
could be useful if we added proper documentation to the functions.

(Sidenote: clion includes support for Doxygen. I'm not sure about IntelliJ)

## Prerequisites

- doxygen
- graphviz

## Usage

- Perform a regular build in the C directory _or_ create the `target` directory.
- Run `doxygen` in this directory
- Copy the resulting files to final location.

## Pre-release Ideas

Ideas that we may wish to include before the next release.

### Groups

Doxygen can group related information. In this case natural groups
are:

- SQL type implementations (everything under `type`)
- SQL function implementations
  - SQLInputFromTuple.c
  - SQLOutputToTuple.c
  - etc
- User-defined SQL types
  - UDT.c
  - FDW.c (future...)
- PostgreSQL objects
  - ExecutionPlan.c (?)
  - PgObject.c
  - PgSavepoint.c
  - Session.c (?)
  - TypeOid.c
- JNI and Backend-specific implementations
  - Backend.c
  - JNICalls.c
  - SPI.c

I'm not sure where some of the files should go, e.g.,
Function, Invocation, Iterator, etc., and it's possible
that the JNI and backend-specific files should be in
separate groups.

### Documentation pages

We can insert both standalone and group-specific `@page`
and `@subpage`. This allows us to have documentation with
a tighter focus on individual aspects of the overall package
or specific groups or files.

In some cases this may be nothing more than a few links
to external documentation. E.g., `type` should only require
a few external links since the standard types are well-defined.
At most it might include an example of how to add a
PostgreSQL-specific type like `uuid`, `cidr` or `money`.

In other cases the documentation should go into details
into how the information is actually passed between the
database and JNI layers.

### Read-only FDW

This will probably be a stretch but I wanted to point this
out since this is why I've added this functionality.

A Foreign Data Wrapper gives us a lot of options even if
we only implement a small portion of functionality. This
is because you don't access a FDW directly - you must create
one or more SERVERS. This gives us two locations to provide
additional options, limit access via standard PostgreSQL
privileges, etc.

Fully implementing a full
[Foreign Data Wrapper](https://www.postgresql.org/docs/current/fdw-callbacks.html)
is an intimidating task. However we can implement a **read-only**
FDW with relatively few functions plus an iterator.

The required functions are:

```C
Datum
openssl_engine_fdw_handler(PG_FUNCTION_ARGS) {
	FdwRoutine *routine = makeNode(FdwRoutine);

	/* provide size estimates. Can be static. */
	routine->GetForeignRelSize = engineGetForeignRelSize;
	
	/* provide access paths for query planning. Can be static. */
	routine->GetForeignPaths = engineGetForeignPaths;
	
	/************/
	/* Iterator */
	/************/
	routine->GetForeignPlan = engineGetForeignPlan;
	routine->BeginForeignScan = engineBeginForeignScan;
	routine->IterateForeignScan = engineIterateForeignScan;
	routine->ReScanForeignScan = engineReScanForeignScan;
	routine->EndForeignScan = engineEndForeignScan;
	routine->ExplainForeignScan = engineExplainForeignScan;	 /* OPTIONAL */
	
	/* OPTIONAL: provide schema, e.g., any UDF */
	routine->ImportForeignSchema = engineImportForeignSchema;
	
	/* OPTIONAL: support push-down joins */
	routine->ForeignUpperPaths = engineForeignUpperPaths;
```

The additional SPI types directly required are

- `ForeignPath`
- `ForeignScanState`
- `RelOptInfo`
- `PlannerInfo`
- `Plan`

and optionally

- `ExplainState`
- `ImportForeignSchemaStmt`
- `UpperRelationKind`

I don't know how many additional types are indirectly required.

Note: an 'empty' implementation is available:
[blackhole_fdw](https://bitbucket.org/adunstan/blackhole_fdw/src/master/)

### Updatable FDW

For completeness - a large number of functions are required for
INSERT, UPDATE, and DELETE operations. However a careful review
at the documentation shows that much of the functionality is
optional if we're willing to accept modest limitations on SQL
functionality used with this FDW. (e.g., RETURNING clauses).

In addition there are many situations where the user only
requires INSERT and possibly DELETE.

(We don't need DELETE if the FDW simply passes the information
through to an external resource.)


## Remaining work

- Modify `Makefile` to run `doxygen` as part of the build.
- Either get the 'Data Structures' working or find a way to remove it.
- Strip leading path from source file names
