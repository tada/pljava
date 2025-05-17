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

## Topics - Datum

These files handle the actual conversion between the java class and PostgreSQL
value (Datum). In general they should have minimal dependence on JNI (methods or
structures) but in some cases this is unavoidable, esp. with collections and
coercion.

### Files

- `type/BigDecimal.c`
- `type/Boolean.c`
- `type/Byte.c`
- `type/Date.c`
- `type/Double.c`
- `type/Float.c`
- `type/Integer.c`
- `type/Long.c`
- `type/Short.c`
- `type/String.c`
- `type/Time.c`
- `type/Timestamp.c`
- `type/UDT.c`
- `type/Void.c`

#### Collections

- `type/Array.c`
- `type/byte_array.c`
- `type/Composite.c`

#### Coercerion

- `type/Coerce.c`

#### Other native PostgreSQL types

PostgreSQL provides a large number of additional native types,
in both the core and other extensions. Examples are `money`,
`UUID`, `CIDR` (for IP networks). Unfortunately the code needs
additional information in order to reliably recognize these
types.

This isn't a total loss - any database object can be converted
to and from a string - but it complicates the java code since
not all Classes can be reliably constructed from a String due
to hidden internal state. The classic examples are `Float` and
`Double` since they include hidden resolution. (These values
can be safely converted if you use their methods that convert
their internal bits format to a int or long value.)

### Topics - JNI

These files contain JNIEXPORT functions. They should be
further categories, e.g., files that handle data vs. files
that handle the database connection.

- `Backend.c`
- `DualState.c`
- `ExecutionPlan.c`
- `Function.c`
- `Invocation.c`
- `JNICalls.c`
- `PgSavepoint.c`
- `SPI.c`
- `SQLOutputToChunk.c`
- `Session.c`
- `SubXactListener.c`
- `TypeOid.c`
- `VarlenaWrapper.c`
- `XactListener.c`
- `type/AclId.c`
- `type/ErrorData.c`
- `type/Oid.c`
- `type/Portal.c`
- `type/Relation.c`
- `type/SQLXMLImpl.c`
- `type/SingleRowReader.c`
- `type/TriggerData.c`
- `type/Tuple.c`
- `type/TupleDesc.c`

## Remaining files

About a third of the files don't fit into either category b

## Pre-release Ideas

Ideas that we may wish to include before the next release.

### Have directories match topics

I know there's a major refactoring going on but it might be helpful
for anyone looking at the 'old' version later to move the files into
subdirectories that match the skills required to modify them - even
if that requires adding a bit of glue.

It's basically a "here there be dragons" warning. The "Datum" should
be pretty straightforward given any knowledge of the database's API -
even if that means moving the collections and coercion elsewhere.

In constrast the "JNI" (or "JNIEXPORT") would be a clear warning
that you need to take great care since it's what's directly interacting
with the backend. 

### Documentation pages

We can insert both standalone and group-specific `@page`
and `@subpage`. This allows us to have documentation with
a tighter focus on individual aspects of the overall package
or specific groups or files.

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
