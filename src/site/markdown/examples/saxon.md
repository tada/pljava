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
example.

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

    SELECT XMLQUERY('/pg_catalog/pg_proc[proname eq $NAME]'
                    PASSING BY VALUE x, 'numeric_avg' AS NAME
                    RETURNING CONTENT EMPTY ON EMPTY)
    FROM catalog_as_xml;

It binds the 'context item' of the query to `x`, and the `NAME`
parameter to the given value, then evaluates the query and returns XML
"CONTENT" (a tree structure with a document node at the root, but not
necessarily meeting all the requirements of an XML "DOCUMENT"). It can be
rewritten as this call to the `xq_ret_content` method provided here:

    SELECT javatest.xq_ret_content('/pg_catalog/pg_proc[proname eq $NAME]',
                                   PASSING => p, nullOnEmpty => false)
    FROM catalog_as_xml,
    LATERAL (SELECT x AS ".", 'numeric_avg' AS NAME) AS p;

In the rewritten form, the type of value returned is determined by which
function is called, and the parameters to pass to the query are moved out to
a separate `SELECT` that supplies their values, types, and names (with
the context item now given the name ".") and is passed by its alias into the
query function.

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
    
      LATERAL (SELECT data AS ".", 'not specified'::text AS DPREMIER) AS p,
    
      "xmltable"('//ROWS/ROW', PASSING => p, COLUMNS => ARRAY[
       'xs:int(@id)', null, 'string(COUNTRY_NAME)',
       'string(COUNTRY_ID)', 'xs:double(SIZE[@unit eq "sq_km"])',
       'concat(SIZE[@unit ne "sq_km"], " ", SIZE[@unit ne "sq_km"]/@unit)',
       'let $e := zero-or-one(PREMIER_NAME)/string()
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

The explicit casts and `zero-or-one` will not be needed once the
full automatic casting rules (for now only partially implemented) are
in place. The default, as shown, is handled by passing the desired default
value as a parameter and rewriting the column expression to apply it in place
of an empty sequence. This lacks, for now, some functionality of the standard
`XMLTABLE`, where the default expression can refer to other columns of the
same output row.

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
