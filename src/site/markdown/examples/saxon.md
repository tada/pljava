## Optionally-built example code for XML processing with Saxon

In the source directory `org/postgresql/pljava/example/saxon` is example code
for XML processing functions similar to `XMLQUERY` and `XMLTABLE` but using
the XQuery language as the SQL/XML standard actually specifies (in contrast
to the similar functions built into PostgreSQL, which support only XPath,
and XPath 1.0, at that).

This code is not built by default, because it pulls in the sizeable [Saxon-HE][]
library from Saxonica, and because (unlike the rest of PL/Java) it requires
Java 8.

To include these optional functions when building the examples, be sure to use
a Java 8 build environment, and add `-Psaxon-examples` to the `mvn` command
line.

### Using the Saxon examples

The simplest installation method is to use `sqlj.install_jar` twice, once to
install the PL/Java examples jar in the usual way (perhaps with the name `ex`
and with `deploy => true`), and once to install (perhaps with the name `saxon`)
the Saxon-HE jar that Maven will have downloaded during the build. That jar
will be found in your Maven repository (likely `~/.m2/repository/` unless you
have directed it elsewhere) below the path `net/sf/saxon`.

Then use `sqlj.set_classpath` to set a path including both jars (`'ex:saxon'` if
you used the names suggested above).

This is work-in-progress code, currently incomplete, and for purposes of
example. Two known current limitations:

* `XMLTABLE` output columns can have non-XML ("atomic") types only. As a
    common use of `XMLTABLE` is to process XML and get atomic types out, it
    should be quite useful even with this limitation. An XML type can be
    returned, if needed, by using a `text` output column, wrapping the XQuery
    column expression in `serialize()`, and then applying SQL `XMLPARSE` to
    the resulting column, at some cost in efficiency.

* `XMLTABLE` column expressions must have the exact XQuery types corresponding
    to the output columns' SQL types; the automatic casts provided in the spec
    are not yet implemented. This is no blocker in practice, as any XQuery
    column expression can be written with an explicit cast to the needed type,
    which is exactly what the spec's automated behavior would be.

Both of these limitations are intended to be temporary.

### Calling XML functions without SQL syntactic sugar

The XML querying and `XMLTABLE` functions built into PostgreSQL get special
treatment from the SQL parser to give them syntax that is more SQLish than
an ordinary function call.

The functions provided here have to work as ordinary SQL user-defined
functions, so calls to them can look a bit more verbose when written out
in SQL, but in a way that can be recognized as a straightforward rewriting
of the SQLish standard syntax.

For example, suppose there is a table `catalog_as_xml` with a single row
whose `x` column is a (respectably sized) XML document recording the stuff
in `pg_catalog`. It could be created like this:

    CREATE TABLE catalog_as_xml(x) AS
      SELECT schema_to_xml('pg_catalog', false, true, '');

#### An `XMLQUERY`-like function

In the syntax of the SQL/XML standard, here is a query that would return an XML
element representing the declaration of the function with the name
`numeric_avg` (if PostgreSQL really had the standard `XMLQUERY` function built
in):

    SELECT XMLQUERY('/pg_catalog/pg_proc[proname eq $FUNCNAME]'
                    PASSING BY VALUE x, 'numeric_avg' AS FUNCNAME
                    RETURNING CONTENT EMPTY ON EMPTY)
    FROM catalog_as_xml;

It binds the 'context item' of the query to `x`, and the `FUNCNAME`
parameter to the given value, then evaluates the query and returns XML
"CONTENT" (a tree structure with a document node at the root, but not
necessarily meeting all the requirements of an XML "DOCUMENT"). It can be
rewritten as this call to the `xq_ret_content` method provided here:

    SELECT javatest.xq_ret_content('/pg_catalog/pg_proc[proname eq $FUNCNAME]',
                                   PASSING => p, nullOnEmpty => false)
    FROM catalog_as_xml,
    LATERAL (SELECT x AS ".", 'numeric_avg' AS "FUNCNAME") AS p;

In the rewritten form, the type of value returned is determined by which
function is called, and the parameters to pass to the query are moved out to
a separate `SELECT` that supplies their values, types, and names (with
the context item now given the name ".") and is passed by its alias into the
query function.

An alert reader may notice that the example above includes a named parameter,
`FUNCNAME`, and it is spelled in uppercase in the XQuery expression that uses
it, and is spelled in uppercase _and quoted_ in the sub-`SELECT` that supplies
it. The reason is an unconditional `toUppercase()` in PL/Java's internal JDBC
driver, which is not anything the JDBC standard requires, but has been there
in PL/Java since 2005. For now, therefore, no matter how a parameter name is
spelled in the sub-`SELECT`, it must appear in uppercase in the XQuery
expression using it, or it will not be recognized. A future PL/Java release
is highly likely to stop forcibly uppercasing the names. At that time, any code
relying on the uppercasing will break. Therefore, it is wisest, until then, to
call this function with all parameter names spelled in uppercase both in the
SQL and in the XQuery text, and on the SQL side that requires quoting the name
to avoid the conventional lowercasing done by PostgreSQL.

In the standard, parameters and results (of XML types) can be passed
`BY VALUE` or `BY REF`, where the latter means that the same
nodes will retain their XQuery node identities over calls (note that this is
a meaning unrelated to what "by value" and "by reference" usually mean in
PostgreSQL's documentation). PostgreSQL's implementation of the XML type
provides no way for `BY REF` semantics to be implemented, so everything
happening here happens `BY VALUE` implicitly, and does not need to be
specified.

#### An `XMLTABLE`-like function

The function `xmltable` here implements (much of) the
standard function of the same name. Because it is the same name, it has to
be either schema-qualified or double-quoted in a call to avoid confusion
with the reserved word. A rewritten form of the
[first example in the PostgreSQL manual][xmltex1] could be:

    SELECT xmltable.*
    FROM
      xmldata,
    
      LATERAL (SELECT data AS ".", 'not specified'::text AS "DPREMIER") AS p,
    
      "xmltable"('//ROWS/ROW', PASSING => p, COLUMNS => ARRAY[
       'data(@id)', null, 'COUNTRY_NAME',
       'COUNTRY_ID', 'SIZE[@unit eq "sq_km"]',
       'concat(SIZE[@unit ne "sq_km"], " ", SIZE[@unit ne "sq_km"]/@unit)',
       'let $e := PREMIER_NAME
        return if ( empty($e) ) then $DPREMIER else $e'
      ]) AS (
       id int, ordinality int, "COUNTRY_NAME" text, country_id text,
       size_sq_km float, size_other text, premier_name text
      );

[xmltex1]: https://www.postgresql.org/docs/10/static/functions-xml.html#FUNCTIONS-XML-PROCESSING-XMLTABLE

Again, the context item and a parameter (here the desired default value for
`PREMIER`, passed in as the parameter `DPREMIER`) are supplied by a separate
query producing the row `p` that is given as `"xmltable"`'s `PASSING` argument.
The result column names and types are now specified in the `AS` list following
the function call, and the column XML Query expressions are supplied as the
`COLUMNS` array. The array must have length equal to the result column `AS`
list (there is no defaulting an omitted column expression to an element test
using the column's name, as there is in the standard function). The array is
allowed to have one null element, marking that column `FOR ORDINALITY`.

The parameter being passed into the XQuery expressions here, `DPREMIER`, is
spelled in uppercase (and, on the SQL side, quoted), for the reasons explained
above for the `XMLQUERY`-like function.

In the first column expression, `@id` is wrapped in `data()` to return the value
of the attribute, as `@id` by itself would be a bare XML attribute node, outside
of any XML element. Many implementations (including the XPath-based
pseudo-XMLTABLE built in to PostgreSQL) will allow a bare attribute node in a
column expression result, and assume the attribute's value is wanted, but a
strict interpretation of the spec appears to require raising `err:XPTY0004` in
that case. So, just use `data()` to wrap any attribute node being returned in
a column expression.

More on that issue and the spec can be found at "About bare attribute nodes"
[in the code comments][assignrowvalues].

#### Syntax in older PostgreSQL versions

The desugared syntax shown above can be used in PostgreSQL versions as old
as 9.5. In 9.4 and 9.3, the same syntax, but with `=>` replaced by `:=` for
the named parameters, can be used. The functions remain usable in still
earlier PostgreSQL versions, but with increasingly convoluted SQL syntax
needed to call them; before 9.3, for example, there was no `LATERAL` in a
`SELECT`, and a function could not refer to earlier `FROM` items. Before 9.0,
named-parameter notation can't be used in function calls. Before 8.4, the
functions would have to be declared without their `DEFAULT` clauses and the
`IntervalStyle` settings, and would not work with PostgreSQL interval values.

### Minimizing startup time

Saxon is a large library, and benefits greatly from precompilation into a
memory-mappable persistent cache, using the
[application class data sharing][appcds] feature in Oracle Java or in
OpenJDK with Hotspot, or the [class sharing][j9cds] feature in OpenJDK with
OpenJ9.

The OpenJ9 feature is simpler to set up. Because it can cache classes straight
from PL/Java installed jars, the setup can be done exactly as described above,
and the OpenJ9 class sharing, if enabled, will just work. OpenJ9 class-sharing
setup [instructions are here][j9cds].

The Hotspot `AppCDS` feature is more work to set up, and can only cache classes
on the JVM system classpath, so the Saxon jar would have to be installed on
the filesystem and named in `pljava.classpath` instead of simply installing it
in PL/Java. It also needs to be stripped of its `jarsigner` metadata, which the
Hotspot `AppCDS` can't handle. Hotspot `AppCDS` setup
[general instructions are here][appcds], and specific details for setting up
this example for `AppCDS` can be found on the
[performance-tuning wiki page][ptwp] in the section devoted to it.

A comparison shown on that performance-tuning page appears
to give Hotspot a significant advantage for a Saxon-heavy workload, so the more
complex Hotspot setup may remain worthwhile as long as that comparison holds.

The `AppCDS` feature in Oracle Java is still (when last checked) a commercial
feature, not to be used in production without a specific license from Oracle.
OpenJDK, as of Java 10, ships Hotspot with the same feature included, without
the encumbrance.


[appcds]: ../install/appcds.html
[j9cds]: ../install/oj9vmopt.html#How_to_set_up_class_sharing_in_OpenJ9
[Saxon-HE]: http://www.saxonica.com/html/products/products.html
[ptwp]: https://github.com/tada/pljava/wiki/Performance-tuning
[assignrowvalues]: ../pljava-examples/apidocs/org/postgresql/pljava/example/saxon/S9.html#assignRowValues-java.sql.ResultSet-int-
