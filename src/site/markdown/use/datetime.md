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
default time zone of the Java runtime, which can be set with `-Duser.timezone=`
in `pljava.vmoptions`, or is otherwise typically taken from the time zone of the
OS where the PostgreSQL backend is running.

#### Calendar discrepancies

SQL (and PostgreSQL) rely on a definition of calendar date from the ISO 8601
_Information interchange - representation of dates and times_ standard, based
on the Gregorian calendar, which was adopted at different times in different
parts of the world, from 1582 in European Catholic countries to well into the
twentieth century for late adopters.

The `java.sql` date/time types do a hybrid calendar computation, using the
Gregorian calendar if the date is modern, and the Julian calendar before
15 October 1582, which makes ten days disappear (the next earlier day is 4
October). That behavior is good if you are in a European Catholic country,
or if the date in question is of something that happened in a European
Catholic country.

Because that's a hard call for a DBMS to make, ISO 8601, SQL, PostgreSQL, and
the JSR 310 Java types described below, all use the Gregorian calendar
_proleptically_, that is, for all dates, even before it was historically in use.
Scholars of ancient events will find it produces different dates than they are
used to (which becomes just one more thing such scholars need to know anyway),
but for data interchange purposes it provides a clear, consistent standard.

Because of that difference in calendar interpretation, the legacy `java.sql`
types containing dates will differ by several days from PostgreSQL dates earlier
than 15 October 1582. ("Several" means "ten" at first, following time backward
from 1582, but decreasing by a day in three of every four centuries
starting in February 1500).

#### Time discrepancies

When an SQL `time with time zone` is represented as a `java.sql.Time`, it is
assigned as the time in UTC corresponding to the local time in its associated
zone. Its component fields, if unpacked in Java, will be computed in the default
time zone of the Java runtime. That ordinary Java behavior is not normally
surprising, but can be in one case that may not be obvious:

If the PostgreSQL session time zone and the Java default time zone are the same,
and the `time with time zone` value was computed in the session time zone
(`current_time` is a perfect example), one might not expect the value seen in
Java to differ. But `java.sql.Time` represents the time internally as a time on
1 January 1970, and if the default time zone observes summer time and the
current summer time status differs from that in January 1970, the time
components seen in Java will differ (by the amount of the summer time
adjustment) from those seen in PostgreSQL.

The `java.sql` types can mismatch PostgreSQL's time zone data for values
before 1900, as discussed in [Local Mean Time and pre-1900 behavior][lmp1b]
below.

#### Bottom line

The Java 8 / JDBC 4.2 mappings described next
are the strongly-recommended alternative to the legacy mappings, avoiding
these issues entirely.

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

When timestamps are mapped to the `java.time` classes, the mapping will have
the useful property that `-infinity` really is earlier than other
PostgreSQL-representable values, and `infinity` really is later. That does not
hold under the old `java.sql.Timestamp` mapping, where both values will be
distant from the present but not further specified.

The convenient relation does not hold for dates at all, under the `java.sql` or
`java.time` mappings; `infinity` and `-infinity` just have to be treated as two
special values. They come out as two consecutive days in the late Miocene,
as it happens, in the third week of June.

#### Infinite timestamps without `integer_datetimes`

In PostgreSQL builds with `integer_datetimes` as `off` (a configuration that is
non-default since PostgreSQL 8.4, and impossible since PG 10), an error results
if a timestamp being converted to Java has either infinite value. As uses of
infinite timestamps are probably rare and the configuration is long out of use,
there is no plan to lift this limitation unless an issue is opened to address a
practical need.

## Time zone rules

For both the legacy `java.sql` types and the newer `java.time` types, whether
the Java value (seen as a string or as broken-out date/time fields) appears to
match the PostgreSQL value can depend on the time zone rules in effect when the
value is examined. These are occasionally changed by politicians, creating a
need for a source of up-to-date rules.

The [IANA Time Zone Database][tzdb] is ultimately the source of the rules
for both PostgreSQL and Java, but a Java runtime typically
[embeds its own copy][jtz], while PostgreSQL [can be configured][pgtz] to embed
a copy of its own, or rely on one supplied by the operating system.

Depending on a site's policy for applying updates, it may be possible for
PostgreSQL and Java to be operating, at times, from slightly different versions
of the rules. Any differences would only affect whatever few locations had
changes to their time zone rules in the more recent version.

### Local Mean Time and pre-1900 behavior

Time zones are a fairly modern innovation, and each zone in the IANA database
also includes a _local mean time_ (LMT) for the location, for use with
timestamps earlier than the first official rule for that zone. The LMT is
derived from actual longitude, and not confined to round hours or simple
fractions thereof. For example, in the time zone `America/Indiana/Indianapolis`,
PostgreSQL will produce this output:

    SELECT TIMESTAMP WITH TIME ZONE '1869-05-06 12:00Z';
    ------------------------------
     1869-05-06 06:15:22-05:44:38

A `java.time.OffsetDateTime` will match that:

    jshell> var zoneid = java.time.ZoneId.of("America/Indiana/Indianapolis")
    jshell> var inst = java.time.Instant.parse("1869-05-06T12:00:00Z")
    jshell> java.time.OffsetDateTime.ofInstant(inst, zoneid)
       ==> 1869-05-06T06:15:22-05:44:38

But a `java.sql.Timestamp` will display it differently (for this example,
"America/Indiana/Indianapolis" is the Java default time zone):

    jshell> new java.sql.Timestamp(inst.toEpochMilli())
       ==> 1869-05-06 07:00:00.0

Java uses the IANA rules, and it is easy to confirm that they include the same
LMT that PostgreSQL is using:

    jshell> var rules = zoneid.getRules()
    jshell> var t1 = rules.nextTransition(inst)
    t1 ==> Transition[Overlap at 1883-11-18T12:15:22-05:44:38 to -06:00]

The first time zone for this area was adopted in November 1883, and the rule
shows both the former LMT of -05:44:38, and the new official zone offset of
-06:00.

But the `java.sql.Timestamp` here has used neither the LMT that was in effect
for the date in question (which would produce local time 06:15:22), nor the
-06:00 value that took effect in 1883 (which would produce local time 06:00:00).

    jshell> var t2 = rules.nextTransition(
       ...>     t1.getDateTimeAfter().atOffset(t1.getOffsetAfter()).toInstant())
    t2 ==> Transition[Gap at 1918-03-31T02:00-06:00 to -05:00]

The rules for this zone were next changed in March 1918, from -06 to -05. It has
since been changed several times more, sometimes back to -06, but is currently
again -05, except in summer.

We can isolate the behavior difference to the pre-JSR 310 `java.util.TimeZone`
class, which the `java.sql` types implicitly use:

    jshell> TimeZone.getTimeZone(zoneid).getOffset(
       ...>  inst.toEpochMilli()) / 3600000
       ==> -5

Explaining the behavior requires knowing that the JDK implementation of
`java.util.TimeZone` [follows the IANA rules only between 1900 and 2037][f1900].
_Before_ 1900, it applies a constant offset taken from the non-summer offset
of _the most recent, currently active rule_, therefore -05 in this example,
and _not_ the offset (-06) that was actually in force in 1900.

That offset _will_ be delivered for a time that is between 1900 and the next
rule change in 1918:

    jshell> TimeZone.getTimeZone(zoneid).getOffset(java.time.Instant.parse(
       ...> "1900-01-01T00:00:00Z").toEpochMilli()) / 3600000
       ==> -6

Beyond 2037, `java.util.TimeZone` repeats the last rule in effect for the zone
as of 2037, with transitions to and from summer time if it has them.

These peculiar 'features' of the `java.util` and `java.sql` types provide still
more reason to use the newer `java.time` types, which apply the IANA time zone
rules consistently, as PostgreSQL itself does.

Ironically, there is an open (as of this writing) [bug report for openjdk][blmt]
requesting that the JSR 310 time zone handling be changed to match what the old
`java.util` implementation does, at the same time that old implementation
contains [a FIXME comment][tzfixme] to support LMT.

[jsr310]: https://www.threeten.org/
[tzdb]: https://www.iana.org/time-zones
[jtz]: https://www.oracle.com/technetwork/java/javase/tzdata-versions-138805.html
[pgtz]: https://www.postgresql.org/docs/current/install-procedure.html#CONFIGURE
[f1900]: http://hg.openjdk.java.net/jdk/jdk13/file/57a391a23f7f/src/java.base/share/classes/sun/util/calendar/ZoneInfo.java#l48
[blmt]: https://bugs.openjdk.java.net/browse/JDK-8024267
[tzfixme]: http://hg.openjdk.java.net/jdk/jdk13/file/57a391a23f7f/src/java.base/share/classes/sun/util/calendar/ZoneInfo.java#l253
[lmp1b]: #Local_Mean_Time_and_pre-1900_behavior
