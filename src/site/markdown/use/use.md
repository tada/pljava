# User guide (non-wiki version)

Content will gradually be migrated here and updated from the
[user guide pages on the wiki][uwik].

For now, this only suggests a few sources:

* The obligatory [Hello world][hello] tutorial example
* The [PL/Java API][pljapi] documentation
* The user guide pages [on the wiki][uwik]
* The many pre-built [examples][]

## How to compile against the PL/Java API

The [Hello world example][hello] demonstrates how to use a Maven POM
that declares `pljava-api` as a dependency. However, as arrangements
are still incomplete for `pljava-api` to be at Maven Central, it has
to be in your local Maven repository. If you have built PL/Java from
source using `mvn clean install`, it will be there already.

If not, an easy way to install the API into your local repository
is to download the PL/Java source, _change into the `pljava-api`
directory_, and run `mvn clean install` there. It will quickly build
and install the API jar, without requiring the various build-time
dependencies needed when all of PL/Java is being built.

If not using Maven, you can simply add the `pljava-api` jar file to the
class path for your Java compiler. Installation normally places the file
in `SHAREDIR/pljava` where `SHAREDIR` is as reported by `pg_config`.

You can also compile successfully by placing the full `pljava` jar
file on the classpath instead of `pljava-api`, but in that case the
compiler will not alert you if your code inadvertently refers to non-API
internal PL/Java classes that may change from release to release.

## Special topics

### Choices when mapping data types

#### Date and time types

PostgreSQL `date`, `time`, and `timestamp` types can still be matched to the
original JDBC `java.sql.Date`, `java.sql.Time`, and `java.sql.Timestamp`,
but application code is encouraged to move to Java 8 or later and use the
[new classes in the `java.time` package in Java 8](datetime.html) instead.

#### XML type

PL/Java can map PostgreSQL `xml` data to `java.lang.String`, but there are
significant advantages to using the
[JDBC 4.0 `java.sql.SQLXML` type](sqlxml.html) for processing XML.

### Parallel query

PostgreSQL 9.3 introduced [background worker processes][bgworker]
(though at least PostgreSQL 9.5 is needed for support in PL/Java),
and PostgreSQL 9.6 introduced [parallel query][parq].

For details on PL/Java in a background worker or parallel query, see
[PL/Java in parallel query](parallel.html).

[bgworker]: https://www.postgresql.org/docs/current/static/bgworker.html
[parq]: https://www.postgresql.org/docs/current/static/parallel-query.html

### Character-set encodings

PL/Java will work most seamlessly when the server encoding in PostgreSQL is
`UTF8`. For other cases, please see the [character encoding notes][charsets].

[hello]: hello.html
[pljapi]: ../pljava-api/apidocs/index.html?org/postgresql/pljava/package-summary.html#package_description
[uwik]: https://github.com/tada/pljava/wiki/User-guide
[examples]: ../examples/examples.html
[charsets]: charsets.html

### Byte-order issues

PL/Java is free of byte-order issues except when using its features for building
user-defined types in Java. At sites with no current or planned use of
those features, this section does not require attention.

The 1.5.0 release of PL/Java begins a transition affecting the byte-order
defaults, which will be completed in a future release. No immediate action is
recommended; there is a [byte-order page](byteorder.html) for more on the topic
and an advance notice of an expected future migration step.
