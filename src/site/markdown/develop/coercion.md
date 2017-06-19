# Passing of data types between PostgreSQL and Java

The wiki documentation for [default type mapping][dtm] gives a rather
high-level view. That seems adequate for most uses (the archives are
not full of "why on earth did PL/Java do that to my data type?" questions),
but this page is meant to capture the steps in more detail.

[dtm]: https://github.com/tada/pljava/wiki/Default-type-mapping

## Some preliminaries

### The per-schema class path

One of the most significant differences between the ISO 9075-13 standard
and what PL/Java actually does is in the area of finding and loading classes.
In the standard, when a function is declared in SQL, its
`external Java reference string` (corresponding to the `AS` clause in
PostgreSQL) *names the installed jar* where the search for the class should
begin. The standard also provides an `SQLJ.ALTER_JAVA_PATH` function that
gives complete control, based on the jar where a search begins, of which
other jars should be searched for dependencies.

By contrast, PL/Java (through and including 1.5) *does not* include the
jar name in `AS` clauses, and provides an [`SQLJ.SET_CLASSPATH`][scp] function
that can set a distinct class path for any schema in the database. The
schema `public` can also have a class path, which becomes the fallback for
any search that is not resolved on another schema's class path.

[scp]: ../pljava/apidocs/index.html?org/postgresql/pljava/management/Commands.html#setClassPath(java.lang.String,%20java.lang.String)

The class named in an SQL function declaration's `AS` clause is looked up
on the *class path for the schema in which the function is declared*, with
the `public` schema as fallback.

### The per-schema type map

When PL/Java is used to create user-defined types, there needs to be a way
of associating the type name declared in SQL with the Java class that
implements it. The most transparent case is a [base type][basetype] (written
in PL/Java with the [@BaseUDT annotation][baseudt]), which is completely
integrated into PostgreSQL's type system and is usable from in or out
of Java just like any other PostgreSQL type.

[basetype]: http://www.postgresql.org/docs/current/static/sql-createtype.html#AEN80283
[baseudt]: ../pljava-api/apidocs/index.html?org/postgresql/pljava/annotation/BaseUDT.html

For the other flavors of user-defined type (described below),
[`SQLJ.ADD_TYPE_MAPPING`][atm] (a PL/Java function, not in the standard) must
be called to record the connection between the new type's SQL name and the
Java class that implements it. The [@MappedUDT annotation][mappedudt] generates
a call to this function along with any other SQL commands declaring the type.

[atm]: ../pljava/apidocs/index.html?org/postgresql/pljava/management/Commands.html#addTypeMapping(java.lang.String,%20java.lang.String)
[mappedudt]: ../pljava-api/apidocs/index.html?org/postgresql/pljava/annotation/MappedUDT.html

What it records is simply the SQL type name as a string, and the Java class
name as a string, and these mappings apply database-wide. But internally,
PL/Java maintains a type map per schema. Why? (Hint: while it is true that
the SQL type names can be schema-qualified, that is not the answer.)

The reason is that the database-wide mappings are from SQL type names to
Java class *names*, and the actual Java class found for a given name can
depend on the per-schema class path.

Whenever the rules given below provide for applying the type map, they
mean, for parameter and return value conversions done at the invocation of
a PL/Java function, the type map for the schema in which the target function
is declared and, at other times, the map for the schema in which the
innermost executing PL/Java function on the call stack is declared.

### PL/Java's object system implemented in C

In PL/Java, some behavior is implemented in Java using familiar Java
objects, and some is implemented in C with an object-oriented approach
using C `struct`s that include and extend each other for 'classes' and
their instances, forming a C object hierarchy that inherits ultimately
from `PgObject`. Often there is a close relationship between a C 'class'
and a Java class of the same name, with instances of one holding references
to the other.

The `type` subdirectory in `pljava-so` contains
the C sources for a class `Type`, which inherits directly from `PgObject`,
and many subclasses of `Type` representing different known SQL types
and how they correspond to Java types.

Each C `Type` has a method `coerceDatum` that takes a PostgreSQL `Datum`
and produces the corresponding Java value, and a method `coerceObject` that
does the reverse. There are also `invoke*` methods provided on `Type`. The
convention is that the actual invocation of a function goes through the
subclass of `Type` that represents the function's return type.

## When type coercions can take place

SQL types can be converted to Java objects or the reverse in several different
contexts.

### Parameters and return values when calling a PL/Java function from SQL

These are the usual rules for converting function parameters from the types
used in the function's SQL declaration to the types of the underlying Java
method's signature, and the Java method's return type to the return type
in the SQL declaration. They do not apply to the special cases where a
composite type is passed or returned, which are seen by the Java code
as JDBC result sets.

0. A C `Type` subclass is looked up for the SQL type name declared for the
    parameter or return type, using the fixed mappings registered during
    PL/Java initialization (see calls to `Type_registerType` in the code).

0. If this search has not produced a `Type` (after forming an array type
    where necessary, or replacing a domain with its base type), the type
    map is consulted, which may result in a new `UDT` subclass being
    registered that handles the conversion between a PostgreSQL `Datum` and
    the associated Java class.

0. *If the function's `AS` clause only names a method, without parameter or
    return type signatures,* the Java types that correspond to the `Type`
    objects chosen at this stage are used to construct a signature for the
    Java method. The method to be used must have the given name and exact
    signature, or one replacing a primitive return type with its boxed form,
    or one with `ResultSetProvider` replaced with `ResultSetHandle`.
    Either the matching method is found at this step, or the call fails.

0. *If the function's `AS` clause includes Java types for the parameters
    and/or return type,* they are compared (textually) to the signature that
    would have been generated. If they all match, the method is resolved in
    the same way as if the signature had not been included.

0. For any Java type in the explicit method signature that differs from the
    the one that corresponds to the C `Type` subclass so far chosen, another
    C `Type` is looked up using the explicit Java type as the key. If that
    `Type` is usable in place of the one earlier chosen (as determined by
    the `Type_canReplaceType` method), it will be used. Otherwise, a
    `Coerce` type is generated according to PostgreSQL's
    `find_coercion_pathway` function, which uses all of PostgreSQL's
    configured type-casting rules to find a suitable conversion.

That final step is roughly equivalent to inserting an SQL `CAST`. However,
two of PostgreSQL's possible casting strategies are not currently
handled by PL/Java, namely array coercions, and coercions by going
through the text output/input functions. Also, if the return type in the
SQL declaration is a domain, constraints on the domain are not checked,
allowing the function to return values of the base type that should not
be possible in the domain. This is a bug.

### Parameters supplied to a JDBC `PreparedStatement` from Java

These are passed through the `coerceObject` method of a C `Type` selected
according to the SQL type that the query plan has for the parameter. The
type map for the innermost PL/Java invocation on the call stack is consulted
if necessary, so these rules are equivalent to the first two in the
"parameters and return values" case. However, see "additional JDBC coercions"
below.

JDBC defines some `setObject` and `setNull` methods on `PreparedStatement`
that must be passed a `java.sql.Types` constant. The JDBC constant will be
mapped to a PostgreSQL type OID through a fixed mapping coded in
`Oid_forSqlType`.

### Values read or written through the JDBC `ResultSet` interface

This case includes not only results from SPI queries made in Java, but
also composite function parameters or return values, and *old* and *new* tuples
in triggers.

Although done in different places in the code (`SPIResultSet`, `Tuple`,
`HeapTupleHeader`, `TupleDesc`), these also have the same behavior as the
first two rules in the "parameters and return values" case. Again, see
"additional JDBC coercions" below.

### Values read or written through the JDBC `SQLInput`/`SQLOutput` interfaces

These are used in PL/Java's implementation of user-defined types. There
are three distinct flavors of user-defined type that PL/Java can manage.
[Base types][basetype] and [composite types][comptype] are established terms
in PostgreSQL. *Mirrored type* is a term invented just now to denote that
other thing PL/Java can do.

[comptype]: http://www.postgresql.org/docs/current/static/sql-createtype.html#AEN80249

[Base type][basetype] a/k/a scalar type
: A type declared with the [no-`AS` form of `CREATE TYPE`][basetype],
    integrating into the PostgreSQL type system at the lowest level.
    The implementation controls its stored size and format and its text input
    and output syntax. The fact that the type is implemented in Java is
    transparent and it can be used from SQL like any other type. For access
    to the storage area, Java code is provided `SQLInput` and `SQLOutput`
    implementations that act as raw memory buffer accessors with methods to
    read and write common types of various widths. Created by a Java class
    with the [@BaseUDT][baseudt] annotation.

[Composite type][comptype]
: A type declared with the
    [`AS` (list of named typed attributes) form of `CREATE TYPE`][comptype],
    which can then be associated with a Java class using
    [SQLJ.ADD_TYPE_MAPPING][atm]. From outside of Java code, it can be
    manipulated like any PostgreSQL composite type, while to Java code it
    will be presented as an instance of the associated Java class--a new
    instance at every conversion, however. Java code is provided
    `SQLInput` and `SQLOutput` implementations that retrieve and set the
    typed attributes of the composite. Created by a Java class
    with the [@MappedUDT][mappedudt] annotation having a `structure`
    attribute.

Mirrored type
: An existing PostgreSQL type that is outside of the standard SQL types that
    have pre-registered PL/Java `Type` mappings, but has been associated with
    a Java class through use of [SQLJ.ADD_TYPE_MAPPING][atm]. The Java code
    is provided the same raw-memory-accessing `SQLInput` and `SQLOutput`
    implementations as for a base type, and the developer must understand
    and match the stored form of the existing type. This can be a brittle
    design to maintain. Because PL/Java consults the type map only if the
    first step of `Type` lookup fails, standard SQL types like `integer`
    cannot be mirrored this way. Created by a Java class
    with the [@MappedUDT][mappedudt] annotation having no `structure`
    attribute, and naming an existing PostgreSQL type.

*Note: the presence of absence of a `structure` attribute in a `@MappedUDT`
annotation only determines whether the SQL generator emits a `CREATE TYPE`
declaring the structure, as well as the `SQLJ.ADD_TYPE_MAPPING` call (which is
always emitted) to associate the class. The `@MappedUDT` annotation could also
be used with no `structure` attribute and the name of an existing composite
type, to associate that type with a Java class. This would act as a composite
type (with `SQLInput`/`SQLOutput` working in typed-tuple mode), even though it
could also be described as mirroring an existing type.*

| | [Base type][basetype] | [Composite type][comptype] | Mirrored type |
----|:--:|:--:|:--:|
**Annotate** | [@BaseUDT][baseudt] | [@MappedUDT][mappedudt] ||
**Stored form** | Raw (Java controls) | Tuple | Native (Java must match) |
**SQLInput/SQLOutput mode** | Raw buffer | Typed tuple | Raw buffer |
[Summary]

#### `SQLInput`/`SQLOutput` in typed tuple mode

In this mode, values read or written through this interface get the same
treatment they would from a JDBC `ResultSet`, converted according to the
first two rules set out above.

#### `SQLInput`/`SQLOutput` in raw memory access mode

In this mode, SQL-specific conversions are not performed, and many of
the SQL-specific `read...` and `write...` methods are disabled
(throwing an exception for unsupported operation), leaving mostly those
for common Java types with familiar widths and formats. The `wasNull` method
always returns `false`. The methods for `Byte`, `Short`, `Int`, and `Long`
deal directly in 1, 2, 4, or 8-octet fields, as with the methods for other
fixed-width types. The methods for variable-length fields (`BigDecimal`,
`BinaryStream`, `Bytes`, `CharacterStream`, `String`, and `URL`) all share
a 16-bit-length-prefixed format allowing up to 65535 bytes in the field.

| `read`/`write...` method | acts on | notes |
---:|:--:|:---|
`Array` | unsupported |
`AsciiStream` | unsupported |
`BigDecimal` | pfx | as via \*`String`
`BinaryStream` | pfx |
`Blob` | unsupported |
`Boolean` | 1 octet | `!= 0`, written as `1` or `0`
`Byte` | 1 octet |
`Bytes` | pfx |
`CharacterStream` | pfx | as via \*`String`
`Clob` | unsupported |
`Date` | 8 octets | `getTime()` as `long`
`Double` | 8 octets |
`Float` | 4 octets |
`Int` | 4 octets |
`Long` | 8 octets |
`NClob` | unsupported |
`NString` | unsupported |
`Object` | unsupported |
`Ref` | unsupported |
`RowId` | unsupported |
`Short` | 2 octets |
`SQLXML` | unsupported |
`String` | pfx | always `UTF-8`
`Struct` | unsupported |
`Time` | 8 octets | `getTime()` as `long`
`Timestamp` | 8 octets | `getTime()` as `long`
`URL` | pfx | via \*`String`
[Formats used by `SQLInput`/`SQLOutput` in raw mode. `pfx` is variable length with 2-octet count.]

*Note: in all PL/Java versions to and including `1.5.0-BETA2`, the
`SQLInput`/`SQLOutput` methods for types wider than a byte are bigendian,
regardless of the underlying hardware.*

For mirrored types, this is plainly a bug: when running on little-endian
hardware, PostgreSQL and Java will disagree on what the values are.

For base types there is no disagreement issue (because *only* the Java code
manipulates those), though there may be a slight performance cost. In
release 1.5.0, byte order is selectable. For mirrored types, the default has
changed to `native`. For base types, the default stays `big_endian`, to preserve
the values of any existing user-defined types stored with earlier PL/Java
versions, and to preserve their `COPY` and on-network binary form.

Because of the "UDT function slot switcheroo" (described further below),
PL/Java UDTs implicitly have a binary send/receive/`COPY` form reflecting
their internal stored representation; they cannot, at present, use the
send and receive function slots to define a custom format for binary transfer.
Because the [binary `COPY` format documentation][bincop] specifies network
byte order (that is, big-endian), there are no plans to change the default
stored form from `big_endian` until some future release decouples the stored
representation from that for binary transfer.

[bincop]: http://www.postgresql.org/docs/8.2/static/sql-copy.html#AEN46503

In some future upgrade, it may be appropriate to change the byte order for
base types, which will require a migration step for any stored data of
PL/Java base types. Methods for doing that are covered on the
[byte order page][byto].

[byto]: ../use/byteorder.html

## Additional JDBC coercions

When reading or writing values through any of the JDBC interfaces (except
`SQLInput`/`SQLOutput` in raw mode), there is another layer of
type coercion that can be applied, after (when reading) or before (when
writing) the rules presented above, and unrelated to them. These are
implemented entirely in Java, and can be found in
`SPIConnection.java` under the names `basicCoersion`,
`basicNumericCoersion`, and `basicCalendricalCoersion`.

The JDBC standard facilities for managing a type map are not implemented
or used, and `getTypeMap` will always return `null`. All of PL/Java's uses
of the type map managed with `SQLJ.ADD_TYPE_MAPPING` take place below the
level of the JDBC mechanisms.

## The user-defined-type function slot switcheroo

When a new base type is defined in PostgreSQL, the `CREATE TYPE` command can
specify four functions that
deal directly with the type representation: `INPUT` and `OUTPUT`, which convert
between the internal representation and printable/parsable text form, and
`RECEIVE` and `SEND`, normally used to offer another transfer format, more
efficient than text, to pass over the channel between frontend and backend.

When a new base UDT is defined in PL/Java, the generated `CREATE TYPE` command
fills those four function slots, but with functions whose `AS` strings do not
directly name the Java methods to call. Instead, the strings have a special form
identifying the Java class associated with the type, and the slot type,
`INPUT`/`OUTPUT`/`SEND`/`RECEIVE`. When PostgreSQL calls these "functions",
the PL/Java runtime passes control appropriately; there are fixed names and
signatures for the four methods that the associated class needs to implement.

The semantics of the four slots are slightly reinterpreted.
`INPUT` and `OUTPUT` still implement the type's outward, textual
form, but instead of converting between that form and the form PostgreSQL sees,
they convert between the text form and an instance of the Java class (using
the methods `parse` and `toString`, respectively).

It would be natural to expect that the other two slots, `RECEIVE` and `SEND`,
correspond to the other two required Java methods, `readSQL` and `writeSQL`,
but they do not exactly. The `readSQL` and `writeSQL` are actually called only
from the coercion methods of `UDT` (in PL/Java's C "object" system, a subclass
of `Type`) when PL/Java needs to convert between a Java class instance and the
PostgreSQL stored type, *not* from `Function` when PostgreSQL has called
through the `RECEIVE` or `SEND` slot in order to transport the value
between backend and frontend. This is the type-function-slot switcheroo.

That repurposing of the `RECEIVE` and `SEND` slots does not leave a way to
name special functions for binary transport to the frontend, so whenever
PostgreSQL does call through those slots, PL/Java always does a raw binary
transfer using the `libpq` API directly (for fixed-size representations),
`bytearecv`/`byteasend` for `varlena` representations, or
`unknownrecv`/`unknownsend` for C string representations.

A future version could revisit this limitation, and allow PL/Java UDTs to
specify custom binary transfer formats also.
