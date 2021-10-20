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

By contrast, PL/Java (through and including 1.6) *does not* include the
jar name in `AS` clauses, and provides an [`SQLJ.SET_CLASSPATH`][scp] function
that can set a distinct class path for any schema in the database. The
schema `public` can also have a class path, which becomes the fallback for
any search that is not resolved on another schema's class path.

[scp]: ../pljava/apidocs/org.postgresql.pljava.internal/org/postgresql/pljava/management/Commands.html#set_classpath

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

[basetype]: http://www.postgresql.org/docs/9.5/static/sql-createtype.html#AEN81321
[baseudt]: ../pljava-api/apidocs/org.postgresql.pljava/org/postgresql/pljava/annotation/BaseUDT.html

For the other flavors of user-defined type (described below),
[`SQLJ.ADD_TYPE_MAPPING`][atm] (a PL/Java function, not in the standard) must
be called to record the connection between the new type's SQL name and the
Java class that implements it. The [@MappedUDT annotation][mappedudt] generates
a call to this function along with any other SQL commands declaring the type.

[atm]: ../pljava/apidocs/org.postgresql.pljava.internal/org/postgresql/pljava/management/Commands.html#add_type_mapping
[mappedudt]: ../pljava-api/apidocs/org.postgresql.pljava/org/postgresql/pljava/annotation/MappedUDT.html

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

Starting in PL/Java 1.6.3, a PL/Java function is entered with the current
thread's [context class loader][ccl] set according to the schema where the
function is declared, and therefore the rules for applying the type map
just described can be simplified: the type map is the one maintained by
the current context class loader, provided Java code has not changed the
context loader from the initial setting. To date, the code actually obtaining
the type map has not been changed to get it _from_ the context class loader,
so the type map would not be affected by Java code changing the context loader.

There are [more details](contextloader.html) on the management of
the context class loader.

[ccl]: https://docs.oracle.com/javase/9/docs/api/java/lang/Thread.html#getContextClassLoader--

### PL/Java's object system implemented in C

In PL/Java, some behavior is implemented in Java using familiar Java
objects, and some is implemented in C with an object-oriented approach
using C `struct`s that include and extend each other for 'classes' and
their instances, forming a C object hierarchy that inherits ultimately
from `PgObject`. Often there is a close relationship between a C 'class'
and a Java class of the same name, with instances of one holding references
to the other.

#### Types

The `type` subdirectory in `pljava-so` contains
the C sources for a class `Type`, which extends a `TypeClass`, which inherits
from `PgObject`. A `TypeClass` is associated with a single Java (primitive
or reference) type, and might have only a single `Type` that extends it,
associated with a single PostgreSQL type. In that simple case, the singleton
`Type` instance can be directly "registered" in the caches that are keyed
by PostgreSQL type oid or by Java type, respectively, by the function
`Type_registerType`.

#### Type obtainers

It is also possible that a single `TypeClass` can be extended by more than
one `Type`, one for each of multiple PostgreSQL types. In that case, an
alternate function `Type_registerType2` will cache, not a single already-created
`Type` instance, but a `TypeObtainer` function, which can be used to obtain
a `Type` extending its associated `TypeClass` and bound to a specific PostgreSQL
type.

An obtainer function should not allocate a brand new `Type` on every call, but
return an existing `Type` if there already is one for the requested PostgreSQL
type. If a `TypeClass` and its associated Java type can only sensibly map a
small few PostgreSQL types, it could even be overkill for the obtainer to use a
hash map or the like to remember the instances it has returned; it could simply
have a few static variables to cache the few instances it will need, and return
the right one after comparing its oid argument to a few constants.

The `TypeClass` for `SQLXML` works that way, with an obtainer that will only
return a `Type` instance for PostgreSQL `xml`, or for PostgreSQL `text` (in case
the Java caller wants to process a text value known to contain XML, or is being
used in a PostgreSQL server that was built without the `xml` type).

An alternative to using an obtainer in that case would be for the initialization
method of the `TypeClass` to simply create more than one `Type` right away, and
register them all directly with `Type_registerType`, needing no obtainer
function. An example is the `TypeClass` representing `java.sql.Timestamp`, which
creates two `Type` instances and registers them immediately, one each for the
PostgreSQL `timestamp` and `timestamptz` types, as both are mapped to this
Java class by default.

#### Exceptional behavior of `String`

At the other extreme, the `TypeClass` for `String` provides an obtainer that
will supply a `Type` for any PostgreSQL type it is asked to, and will rely on
the PostgreSQL text input and output methods for that type to handle the
conversion. This is how it is possible in PL/Java to request or supply a
`String` whatever the underlying PostgreSQL type.

The obtainer for `String`, at present, does not do any bookkeeping to return
one `Type` per PostgreSQL type oid it is called for. It simply allocates a new
one on every call. That makes it an exception to the [comment in `Type.h`][thsc]
specifying singleton behavior, but the exception is as ancient as the comment.

[thsc]: https://github.com/tada/pljava/blob/d2285d74/src/C/include/pljava/type/Type.h#L105

#### Obtainer vs. direct registration

In the more common case where a `TypeClass` will only sensibly have a few `Type`
children, the choice to simply create and register those directly or to use a
`TypeObtainer` can be influenced by a few considerations.

The `TypeClass` for `java.sql.Timestamp` directly registers its two children
because it is the default mapping according to JDBC for both PostgreSQL types
`timestamp` and `timestamptz`. The two `Type`s are directly registered, keyed
by those two type oids, and directly retrieved from the cache when a PostgreSQL
value of either type has to be mapped.

In contrast, JDBC 4.2 introduced non-default mappings for both SQL types:
a `timestamp` can map to a `java.time.LocalDateTime`, and a `timestamptz` can
map to a `java.time.OffsetDateTime`, but only when the Java code explicitly
requests. So, the `TypeClass` for `LocalDateTime` does not directly register
a `Type` corresponding to SQL `timestamp`. It registers a type obtainer, which
can only return a singleton `Type` for that exact SQL type, and does so when
asked.

For the same reason, the `TypeClass` for `SQLXML` relies on an obtainer.
Although an alternate mapping for the `text` type, it would normally be
the default mapping for type `xml` according to JDBC 4, and would simply
register that `Type` directly. However, PL/Java has long mapped the `xml`
type to `String` by default, so for now (until a later, major release),
it treats `SQLXML` as an alternative mapping Java code may explicitly use.

#### Lazy initialization

In the case of the new JDBC 4.2 date/time optional mappings, there is another
reason for each new `TypeClass` to provide a `TypeObtainer`, even though each
`TypeObtainer` will only support exactly one PostgreSQL type. The corresponding
Java classes do not exist before Java 8, and PL/Java supports earlier releases,
so it cannot unconditionally load those classes at initialization time. Each
corresponding `TypeClass` defers that part of its initialization to the first
call of its obtainer, which only happens if the Java code has referred to the
class and therefore it's known to exist.

A side benefit of this approach is laziness in its own right: less class loading
done at initialization before even knowing whether the classes will be needed.
In future work, it may be possible to further reduce PL/Java's
time-to-first-result by applying the technique more widely to
types that use direct registration now.

#### `Type_canReplaceType`

When there is a registered default mapping from a PostgreSQL type to
a `Type` _a_, and the Java type associated with that `TypeClass` is not the one
used in the Java code, the Java type expected by the code will be
looked up and resolved to a `TypeClass`, and from there by its
type obtainer to a second `Type` _b_. The `Type_canReplaceType` method of _b_
will be called, passing _a_. If it returns `true`, the `Type` _b_ and its
methods will be used instead of _a_ to handle the coercions from
PostgreSQL `Datum` to Java type and vice versa. Otherwise, PL/Java will
seek a chain of PostgreSQL type coercions to bridge the gap.

The design is slightly awkward at present, because `Type_canReplaceType`
is applied to two `Type`s (or has one as receiver and one as argument, in the
"C objects" view), so it has to be applied to the result, _b_, of the type
obtainer, essentially to find out whether calling that obtainer was worth
doing. A simpler design might result by changing its argument to a `TypeClass`.

In the current design, redundant checks are largely avoided by not expecting
the type obtainer to do error reporting. If it supports more than one PostgreSQL
type, it should use the PostgreSQL type oid that is passed to determine which
`Type` instance to return. If the PostgreSQL oid is not one of those, it should
simply return whichever `Type` instance represents its primary or most
natural mapping. It does not need to report that the PostgreSQL oid is
unsupported; it can leave that to its can-replace method. A corollary is that
a type obtainer supporting exactly one PostgreSQL type may return its
singleton `Type` instance unconditionally, ignoring its argument.

#### Coercions

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

### A general rule, with one present exception

As the steps above reveal, for both directions of conversion, it is the
_PostgreSQL_ type that starts the algorithm off. The known mappings are
used to find a prospective Java type from it, and then if the actual Java
type appearing in the code is not the expected one, plans are adjusted
accordingly.

This pattern is seen elsewhere in the ISO SQL standard, in Part 14 on
XML-related specifications, which include how to convert values of SQL types
to XML Schema data types and the reverse. Again, for both conversion directions,
the algorithms begin with the SQL type, then adjust if the prospective mapped
type is not the one expected.

#### Parameters supplied to a JDBC `PreparedStatement` from Java

The sole exception in PL/Java is the JDBC `PreparedStatement`, and only for
the _parameters supplied to_ the statement. _Results from it_ are handled
consistently with the general rule.

Ordinarily, when preparing a query that contains parameters, PostgreSQL's
parsing and analysis will reach conclusions about what SQL types the parameters
will need to have so that the query makes sense. JDBC presents those conclusions
to the Java code through the `getParameterMetaData` method once the query has
been prepared, so that the Java code can supply values of appropriate types,
or necessary coercions can be done. The (client side) pgJDBC driver is able
to implement `getParameterMetaData` because the PostgreSQL frontend-backend
protocol allows for sending a query to prepare and having the server send back
a `ParameterDescription` message with the needed type information.

For curious historical reasons, PostgreSQL has been able to supply remote
clients with that `ParameterDescription` information since PG 7.4 came out
in 2003, but a module _loaded right inside the backend_ like PL/Java could
not request the same information using SPI until PG 9.0 in 2010, and
[still not easily][sne]. By then, PL/Java had long been 'faking'
`ParameterMetaData` in a way that reverses the usual type mapping pattern.

#### How `ParameterMetaData` gets faked

PL/Java, when creating a `PreparedStatement`, does not submit the query
immediately to PostgreSQL for analysis. Instead, it initializes all of
the parameter types to unknown, and allows the Java code to go ahead and
call the `set...()` methods to supply values. Using the supplied _Java_ types
as starting points, it fills in the parameter types by following the usual
mappings backward. If the Java code does, in fact, call `getParameterMetaData`,
PL/Java returns the types determined that way for any parameters that have
already been set, and (arbitrarily) `VARCHAR` for any that have not. Only
when the Java code executes the statement the first time does PL/Java submit
the query to PostgreSQL to prepare, passing along the type mappings assumed
so far, and hoping PostgreSQL can make sense of it.

While getting the general rule wrong and differing from client-side pgJDBC,
this is not completely unworkable, and has been PL/Java's behavior
[since 2004][fpm]. Any resulting surprises can generally be resolved by
some rewriting of the query or use of other PL/Java JDBC methods that more
directly indicate the intended PostgreSQL types.
[Some small changes in PL/Java 1.5.1][tpps] may help in some cases. 1.5.1 also
introduces `TypeBridge`s, described later on this page.

A future major release of PL/Java should use the additions to PostgreSQL SPI
and bring the treatment of `PreparedStatement` parameters into conformance
with the general rule. (That release, therefore, will have to support
PostgreSQL versions no earlier than 9.0.)

[sne]: https://www.postgresql.org/message-id/874liv1auh.fsf%40news-spur.riddles.org.uk
[fpm]: https://github.com/tada/pljava/blob/86793a2f/src/java/org/postgresql/pljava/jdbc/SPIPreparedStatement.java#L425
[tpps]: ../releasenotes.html#Typing_of_parameters_in_prepared_statements

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
    will be presented as an instance of the associated Java class---a new
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

The JDBC standard facilities for managing a type map are not implemented
or used, and `getTypeMap` will always return `null`. All of PL/Java's uses
of the type map managed with `SQLJ.ADD_TYPE_MAPPING` take place below the
level of the JDBC mechanisms.

When reading or writing values through any of the JDBC interfaces (except
`SQLInput`/`SQLOutput` in raw mode), there is another layer of
type coercion that can be applied, after (when reading) or before (when
writing) the rules presented above, and unrelated to them. These are
implemented entirely in Java, and can be found in
`SPIConnection.java` under the names `basicCoersion`,
`basicNumericCoersion`, and `basicCalendricalCoersion`.

These three, however, are inconsistently applied. They are used on values
written by Java to the single-row writable `ResultSet`s that PL/Java provides
for composite function results, but not those written to the similar
`ResultSet`s provided to triggers, or prepared statement parameters, or
`SQLOutput` in typed tuple mode. They also cannot be assumed to cover all
cases since JDBC 4.1 and 4.2 introduced new type mappings that can be used
in place of the default ones (such as `java.time.OffsetTime` for `timetz`).

Therefore, a future PL/Java release will probably phase out those three methods
in favor of a more general method.

## The `TypeBridge` class

A start on the replacement of those three methods has already been made in the
work to support the `java.time` types and `SQLXML` in PL/Java 1.5.1. The
support of these alternative mappings requires that the Java types be
recognized as alternate mappings known to the native code, and passed intact
to the native layer with no attempt to coerce them to the expected types first.
To do that, a Java value that is of one of the known supported alternate types
is wrapped in a `TypeBridge.Holder` to link the value with explicit information
on the needed type conversion. As the first step in phasing out the
inconsistently-applied `SPIConnection` basic coercions, they are never applied
at all to a `TypeBridge.Holder`. At present, `TypeBridge`s are used only
for the newly-added type mappings, to avoid a behavior change for pre-existing
ones.

The `TypeBridge` class is not intended as a mechanism for user-extensible
type mappings (the existing facilities for user-defined types should be used).
There will be a small, stable number of `TypeBridge`s corresponding to known
type mappings added in the JDBC spec, or otherwise chosen for native support
in PL/Java. For any `TypeBridge` wrapping a Java value there must be a
native-code `TypeClass` registered for the Java class the bridge is meant
to carry. There is one function in `Type.c` to initialize and register all of
the known handful of `TypeBridge`s. When new ones are added, the list must be
kept in an order such that if bridge _a_ is registered before bridge _b_, then
_a_ will not capture the Java type registered to _b_.

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
Responsible code in `type/UDT.c` is commented with "Assumption 2".

A future version could revisit this limitation, and allow PL/Java UDTs to
specify custom binary transfer formats also.

"Assumption 1" in `UDT.c` is that any PostgreSQL type declared with
`internallength=-2` (meaning it is stored as a variable number of nonzero
bytes terminated by a zero byte) must have a human-readable representation
identical to its stored form, and must be converted to and from Java using
the `INPUT` and `OUTPUT` slots. A `MappedUDT` does not have functions in
those slots, and therefore "Assumption 1" rules out any such type as target
of a `MappedUDT`.

A future version could revisit this limitation also.
