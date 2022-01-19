# Mapping between PostgreSQL and Java date/time types

## Legacy JDBC mappings

The first mappings to be specified in JDBC used the JDBC-specific classes
`java.sql.Date`, `java.sql.Time`, and `java.sql.Timestamp`, all of which
are based on `java.util.Date` (but only as an implementation detail; they
should always be treated as their own types and not as instances of
`java.util.Date`).

PL/Java function parameters and returns can be declared in Java to have those
types, objects of those types can be passed to `PreparedStatement.setObject`,
`ResultSet.updateObject`, and `SQLOutput.writeObject` methods, as well as to
the methods that are specific to those types. The JDBC `getObject` and
`readObject` methods that do not take a `Class<?>` parameter will return
objects of those types when retrieving PostgreSQL date or time values.

### Shortcomings

Those classes have never been a good representation for PostgreSQL date/time
values, because they are based on `java.util.Date`, which implies knowledge of
a time zone, even when they are used to represent PostgreSQL values with no time
zone at all. For all of these conversions but one, PL/Java must do time zone
computations, with the one exception being, unintuitively, `timestamp with time
zone`. The conversions of non-zoned values involve a hidden dependency on the
PostgreSQL session's current setting of `TimeZone`, which can vary from session
to session at the connecting client's preference.

There are known issues of long standing in PL/Java's conversions to and from
these types, detailed in [issue #200][issue200]. While these particular issues
are expected to be fixed in a future PL/Java release, the Java 8 / JDBC 4.2
mappings described next are the strongly-recommended alternative to the legacy
mappings, avoiding these issues entirely.

[issue200]: https://github.com/tada/pljava/issues/200

## Java 8 / JDBC 4.2 date/time mappings

Java 8 introduced the much improved set of date/time classes in the `java.time`
package specified by [JSR 310][jsr310]. JDBC 4.2 (the version in Java 8)
allows those as alternate Java class mappings of the SQL types `date`,
`time` (with and without timezone), and `timestamp` (with/without timezone).
These new types are a much better fit to the corresponding PostgreSQL types than
the original JDBC `java.sql` `Date`/`Time`/`Timestamp` classes.

To avoid a breaking change, JDBC 4.2 does not modify what any of the
pre-existing JDBC API does by default. The `getDate`, `getTime`, and
`getTimestamp` methods on a `ResultSet` still return the same `java.sql` types,
and so does `getObject` in the form that does not specify a class. Instead, the
update takes advantage of the general purpose `ResultSet.getObject` methods that
take a `Class<?>` parameter (added in JDBC 4.1), and likewise the
`SQLInput.readObject` method with a `Class<?>` parameter (overlooked in 4.1 but
added in 4.2), so a caller can request a `java.time` class by passing the right
`Class`:

| PostgreSQL type | Pass to `getObject`/`readObject` |
|--:|:--|
|`date`|`java.time.LocalDate.class`|
|`time without time zone`|`java.time.LocalTime.class`|
|`time with time zone`|`java.time.OffsetTime.class`|
|`timestamp without time zone`|`java.time.LocalDateTime.class`|
|`timestamp with time zone`|`java.time.OffsetDateTime.class`|
[Correspondence of PostgreSQL date/time types and Java 8 `java.time` classes]

The `java.time` types can also be used as parameter and return types of PL/Java
functions without special effort (the generated function declarations will make
the right conversions happen), and passed to the setter methods of prepared
statements, writable result sets (for triggers or composite-returning
functions), and `SQLOutput` for UDTs.

Conversions to and from these types never involve the PostgreSQL session time
zone, which can vary from session to session. Any code developed for PL/Java
and Java 8 or newer is strongly encouraged to use these types for date/time
manipulations, for their much better fit to the PostgreSQL types.

PostgreSQL accepts 24:00:00.000000 as a valid time, while a day for
`LocalTime` or `OffsetTime` maxes out at the preceding nanosecond. That is
still a distinguishable value (as the PostgreSQL resolution is only to
microseconds), so the PostgreSQL 24 value is bidirectionally mapped to that.

### Mapping of time and timestamp with time zone

When a `time with time zone` is mapped to a `java.time.OffsetTime`, the Java
value will have a zone offset equal to the one assigned to the value in
PostgreSQL, and so in the reverse direction.

When a `timestamp with time zone` is mapped to a `java.time.OffsetDateTime`,
the Java value will always have a zone offset of zero (UTC). When an
`OffsetDateTime` created in Java is mapped to a PostgreSQL
`timestamp with time zone`, if its offset is not zero, the value adjusted to UTC
is used.

These different behaviors accurately reflect how PostgreSQL treats
the two types differently.

### Infinite dates and timestamps

PostgreSQL allows `date` and `timestamp` (with or without time zone) values of
`infinity` and `-infinity`.

There is no such notion in the corresponding Java classes (the original JDBC
ones or the JDBC 4.2 / JSR 310 ones), but PL/Java will map those PostgreSQL
values repeatably to certain values of the Java classes, and will map Java
objects with those exact values back to PostgreSQL `infinity` or `-infinity`
on the return trip. Java code that needs to recognize those values could do
an initial query returning `infinity` and `-infinity` and save the resulting
Java values to compare others against. It must compare with `equals()`; it
cannot assume that the mapping will produce the very same Java objects
repeatedly, but only objects with equal values.

When dates and timestamps are mapped to the `java.time` classes,
the mapping will have
the useful property that `-infinity` really is earlier than other
PostgreSQL-representable values, and `infinity` really is later. That does not
hold under the old `java.sql.Timestamp` mapping, where both values will be
distant from the present but not further specified.

#### Infinite timestamps without `integer_datetimes`

In PostgreSQL builds with `integer_datetimes` as `off` (a configuration that is
non-default since PostgreSQL 8.4, and impossible since PG 10), an error results
if a timestamp being converted to Java has either infinite value. As uses of
infinite timestamps are probably rare and the configuration is long out of use,
there is no plan to lift this limitation unless an issue is opened to address a
practical need.

[jsr310]: https://www.threeten.org/
