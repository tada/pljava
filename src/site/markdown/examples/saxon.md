## Optionally-built example code for XML processing with Saxon

In the source directory `org/postgresql/pljava/example/saxon` is example code
for XML processing functions similar to `XMLCAST`, `XMLEXISTS`, `XMLQUERY`, and
`XMLTABLE`, but using the XQuery language as the SQL/XML standard actually
specifies (in contrast to similar functions built into PostgreSQL, which support
only XPath, and XPath 1.0, at that).

The example also implements the four new string functions and one predicate
added in SQL:2006 for regular expression processing using the standardized
XQuery regular expression syntax: `LIKE_REGEX`, `OCCURRENCES_REGEX`, `POSITION_REGEX`,
`SUBSTRING_REGEX`, and `TRANSLATE_REGEX`.

There is also, for completeness, an implementation of `XMLTEXT`, which is
trivial and does not require an XQuery library at all, but is missing from
core PostgreSQL and easy to implement here.

This code is not built by default, because it pulls in the sizeable [Saxon-HE][]
library from Saxonica.

To include these optional functions when building the examples,
add `-Psaxon-examples` to the `mvn` command line.

The functions are presented as examples, not as a full implementation;
for one thing, there is no test suite included to verify their conformance.
Nevertheless, they are intended to be substantially usable subject to the limits
described here, and testing and reports of shortcomings are welcome.

In addition to the open-source and freely-licensed Saxon-HE, the Saxon library
is available in two paid editions, which implement more of the features of
XQuery 3.1 than Saxon-HE does. It should be possible to drop either of those
jar files in place of Saxon-HE (with a working license key) if features are
needed beyond what Saxon-HE provides. Its developers publish
[a matrix][saxmatrix] identifying the features provided in each edition.

### Extension to ISO SQL/XML

Wherever ISO SQL/XML requires one of these functions to accept an XQuery
[expression][xqexpr], in fact an XQuery [main module][xqmainmod] will be
accepted. Therefore, a query can be preceded by a prolog that declares
namespaces, options, local variables and functions, etc. This may simplify
porting queries from Oracle, which permits the same extension.

### Using the Saxon examples

The simplest installation method is to use `sqlj.install_jar` twice, once to
install (perhaps with the name `saxon`) the Saxon-HE jar that Maven will have
downloaded during the build, and once to install the PL/Java examples jar in the
usual way (perhaps with the name `examples` and with `deploy => true`). The
Saxon jar will be found in your Maven repository (likely `~/.m2/repository/`
unless you have directed it elsewhere) below the path `net/sf/saxon`.

The function `sqlj.set_classpath` is used to make installed jars available.
After installing the Saxon jar, if you installed it with the name `saxon`,
add it to the class path:

```
SELECT sqlj.set_classpath('public', 'saxon');
```

This must be done before installing the `examples` jar, so that its dependencies
on Saxon can be resolved.

After both jars are installed, make sure they are both on the classpath. If
the examples jar was installed with the name `examples`:

```
SELECT sqlj.set_classpath('public', 'examples:saxon');
```

*Note: an alternative, shorter procedure is to use
`SET check_function_bodies TO off;` before loading the examples jar.
With the checking turned off, the jar can be installed even if the Saxon jar
has not been installed yet, or has not been added to the class path, so the
order of steps is less critical. Naturally, the example functions that use Saxon
will not work until it has been installed and added to the class path.
`SET check_function_bodies TO off;` simply arranges that missing dependency
errors will be reported later when the functions are used, rather than when
they are created.*

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

#### An `XMLEXISTS`-like predicate

In the syntax of the SQL/XML standard, here is a query that would return a
boolean result indicating whether an SQL function named `numeric_avg`
is declared (if PostgreSQL really had the standard `XMLEXISTS` function built
in):

    SELECT XMLEXISTS('/pg_catalog/pg_proc[proname eq $FUNCNAME]'
                     PASSING BY VALUE x, 'numeric_avg' AS FUNCNAME)
    FROM catalog_as_xml;

It can be rewritten as this call to the `xmlexists` method provided here:

    SELECT "xmlexists"('/pg_catalog/pg_proc[proname eq $FUNCNAME]',
                       PASSING => p)
    FROM catalog_as_xml,
    LATERAL (SELECT x AS ".", 'numeric_avg' AS "FUNCNAME") AS p;

As for the `XMLQUERY`-like function above, , the context item and a parameter
are supplied by a separate query producing the row `p` that is given as the
`PASSING` argument to `"xmlexists"`. The parameter name is capitalized for the
reasons explained above for the `XMLQUERY`-like function.

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

#### An `XMLCAST`-like function

The ISO SQL `XMLCAST` is used to convert XML content into a value of an SQL
data type, or an SQL value to an XML value, following the same
precisely-specified conversion rules that are used for the parameters and
results of the `XMLQUERY` and `XMLTABLE` functions. It can also convert from
one XML type to another, though in PostgreSQL, which has just one XML type, the
conversion is trivial. In a DBMS with support for the full set of XML types
such as `XML(CONTENT)`, `XML(DOCUMENT)`, and `XML(SEQUENCE)`, the rules for
casting one to another are more interesting.

This ordinary-function implementation of `XMLCAST` is used by rewriting an
SQL standard form like

    SELECT XMLCAST(value AS wantedtype)

into a form like

    SELECT result FROM (select value) as v, "xmlcast"(v) AS (result wantedtype)

where either: _value_ is of `xml` type, _wantedtype_ is `xml`, or both; in
other words, the only case `XMLCAST` does not handle is where neither the input
nor result is of `xml` type. Because casting XML to XML is not exciting in
PostgreSQL, the most useful cases are XML to another SQL type, or the reverse.

#### The ISO SQL XQuery regular expression features

The SQL standard specifies a string predicate, `LIKE_REGEX`, for testing a
string against an [XQuery regular expression][xqre] (an extension of
[XML Schema regular expression syntax][xsre]), and four string functions also
based on XQuery regular expressions: `OCCURRENCES_REGEX`, `POSITION_REGEX`,
`SUBSTRING_REGEX`, and `TRANSLATE_REGEX`.

The "flags" parameter to any of these can include any of the
[XQuery regular expression flags `s`, `m`, `i`, `x`, and `q`][xqflags].

As with the `XMLQUERY` and `XMLTABLE` functions, some straightforward rewriting
is needed from the SQL-standard syntax into calls of these ordinary functions.

In the current implementation, all of these functions recognize newlines in the
way specified by XQuery, not the modified way specified for ISO SQL, as further
explained below after the function descriptions. To leave a clear path to a
full implementation, these versions all accept an additional parameter
`w3cNewlines`, which must always be present, for now,  as `w3cNewlines => true`.
Specifying `false`, or omitting this parameter, will mean the ISO SQL newline
treatment is wanted, and will be rejected as an unsupported feature
in this implementation.

To avoid clutter, the `w3cNewlines => true` is not shown in the examples below.

##### [`LIKE_REGEX`][lrx]

A predicate that is `true` if a string matches the regular expression.
The standard syntaxes

    value LIKE_REGEX pattern
    value LIKE_REGEX pattern FLAG flags

can be rewritten to

    like_regex(value, pattern)
    like_regex(value, pattern, flags)
    like_regex(value, pattern, flag => flags)

##### [`OCCURRENCES_REGEX`][orx]

A function to count the occurrences of a pattern in a string. The count can
start from a specific position in the string (the first character has
position 1), and the position can be counted using Unicode characters, or using
octets of the string's encoded form. For now, only `USING CHARACTERS` is
implemented, which can be indicated by passing `usingOctets => false` or
simply omitting it, as `false` is the default. Standard syntax examples like

    OCCURRENCES_REGEX(pattern IN str)
    OCCURRENCES_REGEX(pattern FLAG flags IN str)
    OCCURRENCES_REGEX(pattern IN str FROM position USING CHARACTERS)

can be rewritten to

    occurrences_regex(pattern, str)
    occurrences_regex(pattern, flag => flags, "in" => str)
    occurrences_regex(pattern, str, "from" => position)

##### [`POSITION_REGEX`][prx]

A function to return the position of a regular expression match in a string,
which can optionally return the position of a specific occurrence of the match
(the first, if not specified), or of a particular capturing group within the
desired match. The position reported can be of the first character of the match
of interest (`START`), or of the first character following the match (`AFTER`).
As for `OCCURRENCES_REGEX`, all positions can be expressed `USING CHARACTERS` or
`USING OCTETS`, but only the default `USING CHARACTERS` is implemented here.

Standard syntax examples like

    POSITION_REGEX(START pattern IN str)
    POSITION_REGEX(AFTER pattern IN str)
    POSITION_REGEX(START pattern IN str OCCURRENCE n)
    POSITION_REGEX(START pattern IN str OCCURRENCE n GROUP m)
    POSITION_REGEX(START pattern IN str FROM pos OCCURRENCE n GROUP m)

can be rewritten to

    position_regex(pattern, str)
    position_regex(pattern, str, after => true)
    position_regex(pattern, str, occurrence => n)
    position_regex(pattern, str, occurrence => n, "group" => m)
    position_regex(pattern, str, "from" => pos, occurrence => n, "group" => m)

The result is always relative to the start of the string, not the starting
position. That is, `POSITION_REGEX('d' IN 'abcdef' FROM 3)` is 4, not 2.

##### [`SUBSTRING_REGEX`][srx]

Returns the substring that matched the regular expression, or a specific
occurrence of the expression, or a specific capturing group within the
desired occurrence. Standard syntax examples like

    SUBSTRING_REGEX(pattern IN str)
    SUBSTRING_REGEX(pattern FLAG flags IN str)
    SUBSTRING_REGEX(pattern IN str FROM position)
    SUBSTRING_REGEX(pattern IN str OCCURRENCE n GROUP m)

can be rewritten to

    substring_regex(pattern, str)
    substring_regex(pattern, flag => flags, "in" => str)
    substring_regex(pattern, str, "from" => position)
    substring_regex(pattern, str, occurrence => n, "group" => m)

##### [`TRANSLATE_REGEX`][trx]

Returns a string built from the input string by replacing one specified
occurrence, or all occurrences, of a matching pattern. The
replacement text can include `$0` to include the entire substring
that matched, or `$`_n_ for _n_ a digit 1 through 9,
to include what matched a capturing group in the pattern.
The default behavior of replacing all occurrences applies when
`occurrence` is not specified.

Standard syntax examples like

    TRANSLATE_REGEX(pattern IN str WITH repl)
    TRANSLATE_REGEX(pattern IN str WITH repl OCCURRENCE n)
    TRANSLATE_REGEX(pattern FLAG flags IN str WITH repl)
    TRANSLATE_REGEX(pattern IN str WITH repl FROM position)

can be rewritten to

    translate_regex(pattern, str, "with" => repl)
    translate_regex(pattern, str, "with" => repl, occurrence => n)
    translate_regex(pattern, flag => flags, "in" => str, "with" => repl)
    translate_regex(pattern, str, "with" => repl, "from" => position)

##### Recognition of newlines

A standard XQuery library provides regular expressions that follow the W3C
XQuery rules for newline recognition, in which the `^` and `$` anchors
recognize only the `LINE FEED` character, `U&'\000a'`, the `.` metacharacter
in non-`dotall` mode matches anything other than a `LINE FEED` or
`CARRIAGE RETURN` `U&'\000d'`, the `\s` multicharacter escape matches only
those two characters plus space and horizontal tab, and `\S` is the exact
complement of `\s`.

The ISO SQL specification for these XQuery regular expression features
contains a modification of those rules to conform instead to
[Unicode Technical Standard 18 rule 1.6][uts18rl16], in which several more
Unicode characters are recognized as line boundaries, plus the two-character
sequence `CARRIAGE RETURN` `LINE FEED` (which counts only as one line boundary).
The modified meaning of `\S` becomes "any _single_ character that is not matched
by a _single_ character that matches" `\s` (emphasis added), leaving it no
longer the exact complement of `\s`.

It is difficult to implement the ISO SQL behavior over a standard XQuery
library, so this implementation, for now, does not do so. All of these
functions implement the standard W3C XQuery behavior, which can be "requested"
by passing `w3cNewlines => true`. Without `w3cNewlines => true`,
the call will be interpreted as intending the ISO SQL behavior, and an
`SQLFeatureNotSupportedException` (SQLSTATE `0A000`) will be raised.

##### Nonstandard features

The Saxon XQuery library, implemented in Java, offers the ability to use Java
regular expressions rather than XQuery ones, by passing a _flag_ argument
that ends with `;j` (an invalid flag string per the XQuery spec). This should
not be used in code that intends to be standards-conformant or to run on another
DBMS or XQuery library, but can be useful in some cases for features that Java
regular expressions offer (such as lookahead and lookbehind predicates) that
XQuery regular expressions do not.

###### Java regular expressions and empty-match replacements

This example implementation of `TRANSLATE_REGEX` will detect when a Java
expression rather than an XQuery one is being used, and will then permit
replacement of a zero-length match, rather than raising error `2201U` as the
standard requires. As Java regular expressions include zero-width lookahead and
lookbehind operators, a Java regex can usefully locate zero-width sites for
replacements to be applied.

There are still subtleties involved. A site that is identified by
_negative_ lookahead or lookbehind operators (`(?!)` and `(?<!)`) will be
replaced as expected, but if the positive forms were used (`(?=)` and `(?<=)`),
the replacement will not occur. This example might be expected to insert `!`
for the empty string between `o` and `b`, but does not:

    SELECT translate_regex('(?<=o)(?=b)', 'foobar', "with" => '!',
                           flag => ';j', w3cNewlines => true);
     translate_regex 
    -----------------
     foobar

The reason is that the specification of `TRANSLATE_REGEX` is as if the
matched substring, here an empty string, is matched again _in isolation_
against the original regex to do the replacement, and that empty string no
longer has the `o` and `b` that the original lookbehind and lookahead matched.
It can be made to work by adding an alternative that matches a truly empty
string (`\A\z` in Java syntax):

    SELECT translate_regex('(?<=o)(?=b)|\A\z', 'foobar', "with" => '!',
                           flag => ';j', w3cNewlines => true);
     translate_regex 
    -----------------
     foo!bar

That workaround would also cause the replacement to happen if the input string
is completely empty to start with, which might not be what's wanted.

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
[assignrowvalues]: ../pljava-examples/apidocs/org/postgresql/pljava/example/saxon/S9.html#assignRowValues
[xqre]: https://www.w3.org/TR/xpath-functions-31/#regex-syntax
[xsre]: https://www.w3.org/TR/xmlschema-2/#regexs
[xqflags]: https://www.w3.org/TR/xpath-functions-31/#flags
[uts18rl16]: http://www.unicode.org/reports/tr18/#RL1.6
[lrx]: ../pljava-examples/apidocs/org/postgresql/pljava/example/saxon/S9.html#like_regex
[orx]: ../pljava-examples/apidocs/org/postgresql/pljava/example/saxon/S9.html#occurrences_regex
[prx]: ../pljava-examples/apidocs/org/postgresql/pljava/example/saxon/S9.html#position_regex
[srx]: ../pljava-examples/apidocs/org/postgresql/pljava/example/saxon/S9.html#substring_regex
[trx]: ../pljava-examples/apidocs/org/postgresql/pljava/example/saxon/S9.html#translate_regex
[saxmatrix]: https://www.saxonica.com/html/products/feature-matrix-9-9.html
[xqexpr]: https://www.w3.org/TR/xquery-31/#id-expressions
[xqmainmod]: https://www.w3.org/TR/xquery-31/#dt-main-module
