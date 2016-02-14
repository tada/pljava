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

### Character-set encodings

PL/Java will work most seamlessly when the server encoding in PostgreSQL is
`UTF8`. For other cases, please see the [character encoding notes][charsets].

[hello]: hello.html
[pljapi]: ../pljava-api/apidocs/index.html?org/postgresql/pljava/package-summary.html#package_description
[uwik]: https://github.com/tada/pljava/wiki/User-guide
[examples]: ../examples/examples.html
[charsets]: charsets.html
