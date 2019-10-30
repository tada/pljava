# XML view of non-XML data

Because Java has a rich ecosystem of APIs and tools for XML processing,
and JDBC supports those directly with the [SQLXML data type](sqlxml.html),
it may be useful to offer XML "views" of other PostgreSQL data types that
are not XML, but are similarly tree-structured.

A preview of such a feature is included in this release, allowing values
of PostgreSQL's `pg_node_tree` type to be retrieved as if they were XML.


## `pg_node_tree`

The `pg_node_tree` type is a representation of PostgreSQL internal data
structures, serialized to a text form found in various places in the
system catalogs: default expressions for attributes, types, or function
parameters, constraint and index expressions, trigger and policy qualifiers,
rewrite rule actions, and so on.

To make full use of the information in a `pg_node_tree` would require access
to all of the PostgreSQL native structure definitions used in it (which could
become feasible in a future PostgreSQL version if some
[current work-in-progress][gfntugm] is completed and released). On the other
hand, depending on need, some partial information may be usefully extracted
from a `pg_node_tree` using a simple syntactic transformation and standard
tools for XML querying.

For an example of the current `pg_node_tree` syntax, here is the
`yes_or_no_check` constraint in PostgreSQL 12 (on a little-endian machine):

```
SELECT conbin FROM pg_constraint WHERE conname = 'yes_or_no_check';

{SCALARARRAYOPEXPR :opno 98 :opfuncid 67 :useOr true :inputcollid 100
:args ({RELABELTYPE :arg {COERCETODOMAINVALUE :typeId 1043 :typeMod 7
:collation 100 :location 133} :resulttype 25 :resulttypmod -1
:resultcollid 100 :relabelformat 2 :location -1} {ARRAYCOERCEEXPR
:arg {ARRAY :array_typeid 1015 :array_collid 100 :element_typeid 1043
:elements ({CONST :consttype 1043 :consttypmod -1 :constcollid 100
:constlen -1 :constbyval false :constisnull false :location 143
:constvalue 7 [ 28 0 0 0 89 69 83 ]} {CONST :consttype 1043 :consttypmod -1
:constcollid 100 :constlen -1 :constbyval false :constisnull false :location 150
:constvalue 6 [ 24 0 0 0 78 79 ]}) :multidims false :location -1}
:elemexpr {RELABELTYPE :arg {CASETESTEXPR :typeId 1043 :typeMod -1 :collation 0}
:resulttype 25 :resulttypmod -1 :resultcollid 100 :relabelformat 2 :location -1}
:resulttype 1009 :resulttypmod -1 :resultcollid 100 :coerceformat 2 :location -1
}) :location 139}
```

A Java function receiving a `pg_node_tree` as an argument could be declared
this way:

```java
@Function
public static void pgNodeTreeAsXML(@SQLType("pg_node_tree") SQLXML pgt)
{
    ...
```

A parameter with the Java type `SQLXML` would normally lead to a parameter
type of `xml` in the generated SQL function declaration, but here the
`@SQLType` annotation is used to change that, declaring a function that accepts
a `pg_node_tree` in SQL, but presents it to Java as the `SQLXML` type.

The [`pljava-examples` jar][ex] includes just such a function, only declared to
return `xml` rather than `void`. In fact, it returns its argument untouched, so
it can be treated as XML by the surrounding query. Its full implementation is:

```java
@Function
public static SQLXML pgNodeTreeAsXML(@SQLType("pg_node_tree") SQLXML pgt)
throws SQLException
{
    return pgt;
}
```

Using that function (and the XQuery [serialize][] function with the `indent`
option for readability, courtesy of [XQuery-based `XMLTABLE`][xbxt]), the same
node tree can be viewed in a more familiar structured syntax:

```
SELECT
    xmltable.*
  FROM
    pg_constraint,
    LATERAL (SELECT PgNodeTreeAsXML(conbin) AS ".") AS p,
    "xmltable"('serialize(., map{"indent":true()})',
      passing => p, columns => '{.}') AS (indented text)
  WHERE
    conname = 'yes_or_no_check';

<SCALARARRAYOPEXPR>
   <member name="opno">98</member>
   <member name="opfuncid">67</member>
   <member name="useOr">true</member>
   <member name="inputcollid">100</member>
   <member name="args">
      <list>
         <RELABELTYPE>
            <member name="arg">
               <COERCETODOMAINVALUE>
                  <member name="typeId">1043</member>
                  <member name="typeMod">7</member>
                  <member name="collation">100</member>
                  <member name="location">133</member>
               </COERCETODOMAINVALUE>
            </member>
            <member name="resulttype">25</member>
            <member name="resulttypmod">-1</member>
            <member name="resultcollid">100</member>
            <member name="relabelformat">2</member>
            <member name="location">-1</member>
         </RELABELTYPE>
         <ARRAYCOERCEEXPR>
            <member name="arg">
               <ARRAY>
                  <member name="array_typeid">1015</member>
                  <member name="array_collid">100</member>
                  <member name="element_typeid">1043</member>
                  <member name="elements">
                     <list>
                        <CONST>
                           <member name="consttype">1043</member>
                           <member name="consttypmod">-1</member>
                           <member name="constcollid">100</member>
                           <member name="constlen">-1</member>
                           <member name="constbyval">false</member>
                           <member name="constisnull">false</member>
                           <member name="location">143</member>
                           <member name="constvalue" length="7">1C000000594553</member>
                        </CONST>
                        <CONST>
                           <member name="consttype">1043</member>
                           <member name="consttypmod">-1</member>
                           <member name="constcollid">100</member>
                           <member name="constlen">-1</member>
                           <member name="constbyval">false</member>
                           <member name="constisnull">false</member>
                           <member name="location">150</member>
                           <member name="constvalue" length="6">180000004E4F</member>
                        </CONST>
                     </list>
                  </member>
                  <member name="multidims">false</member>
                  <member name="location">-1</member>
               </ARRAY>
            </member>
            <member name="elemexpr">
               <RELABELTYPE>
                  <member name="arg">
                     <CASETESTEXPR>
                        <member name="typeId">1043</member>
                        <member name="typeMod">-1</member>
                        <member name="collation">0</member>
                     </CASETESTEXPR>
                  </member>
                  <member name="resulttype">25</member>
                  <member name="resulttypmod">-1</member>
                  <member name="resultcollid">100</member>
                  <member name="relabelformat">2</member>
                  <member name="location">-1</member>
               </RELABELTYPE>
            </member>
            <member name="resulttype">1009</member>
            <member name="resulttypmod">-1</member>
            <member name="resultcollid">100</member>
            <member name="coerceformat">2</member>
            <member name="location">-1</member>
         </ARRAYCOERCEEXPR>
      </list>
   </member>
   <member name="location">139</member>
</SCALARARRAYOPEXPR>
```

Although exact interpretation of all that isn't possible without heavy reference
to the PostgreSQL source, it can be eyeballed for a decent idea of what is
going on, and simple queries could extract useful information for some
purposes. For example, this simple XPath would return the `Oid`s of all types
that are used in constants within the expression:

```
number(//CONST/member[@name = 'consttype'])
```

### Some details of the mapping

* A `<list>` either has no attribute, and children that are, recursively,
    `pg_node_tree` structures, or it has an `all` attribute with value
    `int`, `oid`, or `bit` and its children all are `<v>` elements with
    numeric content representing integers, `Oid`s, or bit numbers in a bit set,
    respectively.
* A `<CONST>` representing a typed SQL `NULL` will have a `constvalue` member
    with no `length` attribute and no content. Otherwise, the `constvalue`
    member will have content of type `xs:hexBinary` and a `length` attribute
    indicating how many octets of the binary content are used. For types
    with `constbyval` true, the hex content will always be the full width of
    a `Datum`, though the `length` may be smaller. For types with `constbyval`
    false, the `length` attribute matches the length of the binary content.
* A `<CONST>` with a `constlen` of `-1` represents a type with a `varlena`
    representation, as described under [Database Physical Storage][dps].
    The `constvalue` in such a case is the entire `varlena`, including its
    header.

The two `<CONST>` elements in the example above have type 1043
(`CHARACTER VARYING`) and `varlena` representations, so the `constvalue` members
consist of a four-octet header followed by the three ASCII characters `YES` or
the two characters `NO`, respectively. The one-octet length difference changes
the `varlena` header value by four (from `18` to `1C`) because the two
lowest-order bits of the header (on little-endian hardware) are usurped for
TOAST.

It is [possible][gfntugm1] that a future PostgreSQL version will
change the current idiosyncratic syntax, or serialize to JSON instead.

## Limits of the current XML view implementation

Implementation of XML views is work in progress. The current implementation
has these limitations:

* It is read-only. There is no provision yet for writing an XML-viewable type
    by returning `SQLXML` from a Java function, or passing `SQLXML` to a
    `ResultSet`, `PreparedStatement`, or `SQLOutput`.
* A fully-compliant readable `SQLXML` implementation should support
    `getBinaryStream`, `getCharacterStream`, `getString`, and `getSource` with
    any of the four must-support subtypes of `Source`. The current XML-view
    implementation will support only `getSource(SAXSource.class)` or
    `getSource(null)` (which will return a `SAXSource`). All other cases will
    throw an `SQLFeatureNotSupportedException`.

[gfntugm]: https://www.postgresql.org/message-id/20190828234136.fk2ndqtld3onfrrp%40alap3.anarazel.de
[gfntugm1]: https://www.postgresql.org/message-id/20190921091527.GI31596%40fetter.org
[ex]: ../examples/examples.html
[serialize]: https://www.w3.org/TR/xpath-functions-31/#func-serialize
[xbxt]: ../examples/saxon.html#An_XMLTABLE-like_function
[dps]: https://www.postgresql.org/docs/9.4/storage-toast.html
