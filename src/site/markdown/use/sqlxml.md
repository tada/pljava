# Working with XML

## In PL/Java before 1.5.1

PL/Java functions before 1.5.1 have been able to access a value of XML type as
a `String` object. That has been workable, but an extra burden if porting code
that used the JDBC 4.0 `java.sql.SQLXML` API, and with notable shortcomings.

### Shortcomings

#### Character set encoding

PostgreSQL stores XML values serialized according to `server_encoding`, and
depending on that setting, conversion to a Java `String` can involve
transcoding.

XML has rules to handle characters that may be representable in one encoding
but not another, but the `String` conversion is unaware of them, and may fail
to produce a transcoding that represents the same XML value.

#### Memory footprint

While a database design using XML may be such that each XML datum is
individually very small, it is also easy to store---or generate in
queries---large XML values. When mapped to a Java `String`, such an XML value
must have its full, uncompressed, character-serialized size allocated
on the Java heap and be copied there from native memory, before the Java
code even begins to make use of it. Even in cases where the Java processing to
be done could be organized to stream through parse events in constant-bounded
memory, the `String` representation forces the entire XML value to occupy Java
memory at once. Any tuning of PL/Java's heap size allowance could have to
consider a worst-case estimate of that size, or risk failures at run time.

## The JDBC 4.0 `java.sql.SQLXML` API

PL/Java 1.5.1 adds support for this API. A Java parameter or return type in a
a PL/Java function can be declared to be `SQLXML`, and such objects can be
retrieved from `ResultSet` and `SQLInput` objects, and used as
`PreparedStatement` parameters or in `SQLOutput` and updatable `ResultSet`
objects.

### Reading a PostgreSQL XML value as a _readable_ `SQLXML` object

An `SQLXML` instance can have the "conceptual states" _readable_ and _not
readable_, _writable_ and _not writable_. In PL/Java, an instance passed in as a
parameter to a function, or retrieved from a `ResultSet`, is _readable_ and _not
writable_, and can be used as input to Java processing using any of the
following methods:

`getBinaryStream()`
: Obtain an `InputStream` with the raw, byte-stream-serialized XML, which will
    have to be passed to an XML parser. The parser will have to determine the
    encoding used from the declaration at the start of the stream, or assume
    UTF-8 if there is none, as the standard provides.

`getCharacterStream()`
: Like `getBinaryStream` but as a stream of Java characters, with the underlying
    encoding already decoded. May be convenient for use with parsing code that
    isn't able to recognize and honor the encoding declaration, but any standard
    XML parser would work as well from `getBinaryStream`, which should be
    preferred when possible. A parser working from the binary stream is able to
    handle transcoding, if needed, in an XML-aware way. With this method, any
    needed transcoding is done without XML-awareness to produce the character
    stream.

`getString()`
: Obtain the entire serialized XML value decoded as a Java `String`. Has the
    same memory footprint and encoding implications discussed for the legacy
    conversion to `String`, but may be convenient for some purposes or for
    values known to be small.

`getSource(javax.xml.transform.stream.StreamSource.class)`
: Equivalent to one of the first two methods, but with the stream wrapped in
    a `Source` object, directly usable with Java XML transformation APIs.

`getSource(javax.xml.transform.sax.SAXSource.class)`
: Obtain a `Source` object that presents the XML in parsed form via the SAX API,
    where the caller can register callback methods for XML constructs of
    interest, and then have Java stream through the XML value, calling those
    methods.

`getSource(javax.xml.transform.sax.StAXSource.class)`
: Obtain a `Source` object that presents the XML in parsed form via the StAX
    API, where the value can be streamed through by calling StAX pull methods
    to get one XML construct at a time. Java code written to this API can more
    clearly reflect the expected structure of the XML document, compared to
    code written in the callback style for SAX.

`getSource(javax.xml.transform.sax.DOMSource.class)`
: Obtain a `Source` object presenting the XML fully parsed as a navigable,
    in-memory DOM tree.

`getSource(null)`
: Obtain a `Source` object of a type chosen by the implementation. Useful when
    the `Source` object will be passed to a standard Java transformation API,
    which can handle any of the above forms, letting the `SQLXML` implementation
    choose one that it implements efficiently.

Exactly one of these methods can be called exactly once on a _readable_ `SQLXML`
object, which is thereafter _not readable_. (The _not readable_ state prevents
a second call to any of the getter methods; it does not, of course, prevent
reading the XML content through the one stream, `String`, or `Source` obtained
from the getter method that was just called.)

Except in the `String` or DOM form, which bring the entire XML value into Java
memory at once, the XML content is streamed directly from native PostgreSQL
memory as Java code reads it, never accumulating in the Java heap unless that
is what the application code does with it. Java heap sizing, therefore, can
be based on just what the application Java code will do with the data.

The most convenient API to use in an application will often be SAX or StAX,
in which the code can operate at the level of already-parsed, natural XML
constructs. Code designed to work with a navigable DOM tree can easily obtain
that form (but it should be understood that DOM will pull the entire content
into Java memory at once, in a memory-hungry form that can easily be twenty
times the size of the serialized value).

#### Obtaining a _readable_ `SQLXML` object

To obtain a _readable_ instance, declare `java.sql.SQLXML` as the type of a
function parameter where PostgreSQL will pass an XML argument, or use the
`getSQLXML` or `getObject(..., SQLXML.class)` methods on a `ResultSet`, or the
`readSQLXML` or `readObject(SQLXML.class)` methods on `SQLInput`. A fully
JDBC-4.0 compliant driver would also return `SQLXML` instances from the
non-specific `getObject` and `readObject` methods, but in PL/Java, those have
historically returned `String`. Because 1.5.1 is not a major release, their
behavior has not changed, and the more-specific methods must be used to obtain
`SQLXML` instances.

### Creating/returning a PostgreSQL XML value with a _writable_ `SQLXML` object

PL/Java will supply an empty `SQLXML` instance that is _writable_ and _not
readable_ via the `Connection` method `createSQLXML()`. It can be used as an
output destination for any of several Java XML APIs, through a selection of
`set...` methods exactly mirroring the available `get...` methods described
above.

_The API is unusual: except for `setString`, which takes a `String` parameter
and returns `void` as a typical "setter" method would, the other setter methods
are used for the object they return---an `OutputStream`, `Writer`, or
`Result`---which the calling code should then use to add content to the XML
value._

Exactly one setter method can be called exactly once on a _writable_ `SQLXML`
object, which is thereafter _not writable_. (The _not writable_ state prevents
a second call to any setter method; XML content must still be written via the
stream or `Result` obtained from the one setter that was just called, except
in the case of `setString`, which populates the value at once.) Content being
written to the `SQLXML` object is accumulated in PostgreSQL native memory,
not the Java heap.

A `SQLXML` object, once it has been fully written and closed, can be
returned from a Java function, passed as a `PreparedStatement` parameter to a
nested query, or stored into writable `ResultSet`s used for composite function
or trigger results. It can be used exactly once in any of those ways, which
transfer its ownership back to PostgreSQL, leaving it inaccessible from Java.

#### When a _writable_ `SQLXML` object is considered closed

A _writable_ `SQLXML` object cannot be presented to PostgreSQL before it is
closed to confirm that writing is complete. (One written by `setString` is
considered written, closed, and ready to use immediately.)

When it is written using a stream obtained from `setBinaryStream`,
`setCharacterStream`, or
`setResult(javax.xml.transform.stream.StreamResult.class)`, it
is considered closed when the stream's `close` method is called.
This will typically _not_ be done by a Java `Transformer` with the stream
as its result, and so should be explicitly called after such a transformation
completes.

When written using a `SAXResult`, it is considered closed when the
`ContentHandler`'s `endDocument` method is called, and when written using a
`StAXResult`, it is considered closed when the `XMLStreamWriter`'s
`writeEndDocument` method is called. When one of these flavors of `Result` is
used with a Java `Transformer`, these methods will have been called in the
normal course of the transformation, so nothing special needs to be done after
the transformation completes.

What it means to `close` a `DOMResult` is murkier. The application code must
call the `DOMResult`'s `setNode` method, passing what will be the root node of
the result document. This can be done before or after (or while) child nodes and
content are added to that node. However, to avoid undefined behavior,
application code must make no further modification to that DOM tree after the
`SQLXML` object has been presented to PostgreSQL (whether via a
`PreparedStatement` `set` method, `ResultSet` `update` method,
`SQLOutput` `write` method, or returned as the function result).

#### Using a `Result` object as a `Transformer` result

Classes that extend `javax.xml.transform.Transformer` will generally accept
any flavor of `Result` object and select the right API to write the
transformation result to it. There is often no need to care which `Result`
flavor to provide, so it is common to call `setResult(null)` to let the
`SQLXML` implementation itself choose a flavor based on implementation-specific
efficiency considerations.

In the case of a `DOMResult`, if the `Result` object is simply passed to a
`Transformer` without calling `setNode` first, the `Transformer` itself will
put an empty `Document` node there, which is then populated with the
transformation results.

A `Document` node, however, enforces conformance to the strict rules of
`XML(DOCUMENT)` form (described below). If the content to be written will
conform only to the looser rules of `XML(CONTENT)` form, application code should
call `setNode` supplying an empty `DocumentFragment` node, before passing the
`Result` object to a `Transformer`.

The flavor of `Result` returned by `setResult(null)` will never
(in PL/Java) be `DOMResult`.

### Using an unread _readable_ `SQLXML` object as a written one

The general rule that only a _writable_ instance (that has been written and
closed) can be used as a function result, or passed into a nested query, admits
one exception, allowing a _readable_ instance that Java code has obtained but
not read. That makes it simple for Java code to obtain an `SQLXML` instance
passed in as a parameter, or from a query, and use it directly as a result or a
nested-query parameter. Any one instance can be used this way no more than once.

### `XML(DOCUMENT)` and `XML(CONTENT)`

An XML value in SQL can have the type `XML(DOCUMENT)` or `XML(CONTENT)` (as
those are defined in the ISO SQL standard, 2006 and later), which PostgreSQL
does not currently treat as distinguishable types. The `DOCUMENT` form must have
exactly one root element, may have a document-type declaration (DTD), and has
strict limits on where other
constructs (other than comments and processing instructions) can occur. A value
in `CONTENT` form may have no root element, or more than one element at top
level, and other constructs such as character data outside of a root element
where `DOCUMENT` form would not allow them.

#### How both forms are accommodated when reading

Java code using a _readable_ `SQLXML` instance as input should be prepared to
encounter either form (unless it has out-of-band knowledge of which form will be
supplied). If it requests a `DOMSource`, `getNode()` will return a `Document`
node, if the value met all the requirements for `DOCUMENT`, or a
`DocumentFragment` node, if it was parsable as `CONTENT`. Java code requesting a
`SAXSource` or `StAXSource` should be prepared to handle a sequence of
constructs that might not be encountered when parsing a strictly conforming
`DOCUMENT`. Java code that requests an `InputStream`, `Reader`, `String`, or
`StreamSource` will be on its own to parse the data in whichever form appears.

##### Effect on parsing of whitespace

In `DOCUMENT` form, any whitespace outside of the single root element is
considered markup, not character data. When the value is parsable as `DOCUMENT`,
and read through PL/Java's `SAXSource` or `StAXSource`, no whitespace that
occurs outside of the root element will be reported to the application.
PL/Java's `DOMSource` will present a `Document` node with no whitespace
text-node children outside of the root element.

If the value parses as `CONTENT`, PL/Java's `DOMSource` will present a
`DocumentFragment` node with all character data, including whitespace,
preserved. The streaming operation of the `SAXSource` and `StAXSource` is more
complicated, and lossy for whitespace (only if it occurs outside of any element)
ahead of the first parse event that would not be possible in `DOCUMENT` form.
All whitespace beyond that point is preserved.

#### How both forms are accommodated when writing

Java code using a _writable_ SQLXML instance to produce a result may write
either `DOCUMENT` or `CONTENT` form. If using `DOMResult`, it must supply a
`DocumentFragment` node to produce a `CONTENT` result, as a `Document` node will
enforce the `DOCUMENT` requirements.

### An `SQLXML` object has transaction lifetime

The JDBC spec provides that an `SQLXML` instance is "valid for the duration of
the transaction in which it was created." One PL/Java function can hold an
`SQLXML` instance (in a static or session variable or data structure), and other
PL/Java functions called later in the same transaction can continue reading from
or writing to it. If the transaction has committed or rolled back, those
operations will generate an exception.

Once a _writable_ `SQLXML` object, or an unread, _readable_ one, has been
presented to PostgreSQL as the result of a PL/Java function or through a
`PreparedStatement`/`ResultSet`/`SQLOutput` setter method, it is no longer
accessible in Java.

During a transaction, resources held by a `SQLXML` object are reclaimed as soon
as a _readable_ one has been fully read, or a _writable_ one has been presented
to PostgreSQL and PostgreSQL is done with it. If application code holds a
readable `SQLXML` object that it determines it will not read, or a writable one
it will not present to PostgreSQL, it can call the `free` method to allow the
resources to be reclaimed sooner than the transaction's end.

### Lazy detoasting

PostgreSQL can represent large XML values in "TOASTed" form, which may be in
memory but compressed (XML typically compresses to a small fraction of its
serialized size), or may be a small pointer to a location in storage. A
_readable_ `SQLXML` instance over a TOASTed value will not be detoasted until
Java code actually begins to read it, so the memory footprint of an instance
being held but not yet read is kept low.

### Validation of content

Some of the methods by which a _writable_ instance can be written are not
XML-specific APIs, but allow arbitrary content to be written (as a `String`,
`Writer`, or `OutputStream`). When written by those methods, type safety is
upheld by verifying that the written content can be successfully reparsed,
accepting either `DOCUMENT` or `CONTENT` form.

It remains possible to declare the Java type `String` for function parameters
and returns of XML type, and to retrieve and supply `String` for `ResultSet`
columns and `PreparedStatement` parameters of XML type. This legacy mapping
from `String` to XML uses PostgreSQL's `xml_in` function to verify the form of a
`String` from Java. That function may reject some valid values if the server
configuration variable `xmloption` is not first set to `DOCUMENT` or `CONTENT`
to match the type of the value.

#### Validation against a schema

Java's XML APIs support validation using a choice of schema languages;
support for XML Schema 1.0 is included in the Java runtime, and implementations
of others can be placed on the class path.

A `schema` method is available through the "Extended API to configure
XML parsers" described below, but will only work on a `SAXSource` or `DOMSource`
(or a `StreamResult`, which uses a SAX parser to validate the stream written).
Other limitations are described under "known limitations" below.

More flexibly, `javax.xml.validation.Validator` or
`javax.xml.validation.ValidatorHandler` can be used in more situations and with
fewer limitations.

### Usable with or without native XML support in PostgreSQL

In symmetry to using Java `String` for SQL XML types, PL/Java allows the Java
`SQLXML` type to be used with PostgreSQL data of type `text`. This allows full
use of the Java XML APIs even in PostgreSQL instances built without XML support.
All of the `SQLXML` behaviors described above also apply in this usage.

If a _readable_ `SQLXML` instance obtained from a `text` value is directly used
to set or return a value of PostgreSQL's XML type, the XML-ness of the content
is verified.

## Extensions to the `java.sql.SQLXML` API

### Extended API to configure XML parsers

Retrieving or verifying the XML content in an `SQLXML` object can involve
applying an XML parser. The full XML specification includes features that can
require an XML parser to retrieve external resources or consume unexpected
amounts of memory. The full feature support may be an asset in an environment
where the XML content will always be from a known, trusted source, or a
liability if less is known about the XML content being processed.

The [Open Web Application Security Project][OWASP] (OWASP) advocates for the
default use of settings that strictly limit the related features of Java XML
parsers, as outlined in a ["cheat sheet"][cheat] the organization publishes.

However, the recommended defaults really are severely restrictive (for example,
disabling document-type declarations by default will cause PL/Java's `SQLXML`
implementation to reject all XML values that contain DTDs). Therefore, there
must be a simple and clear way for code to selectively adjust the settings, or
adopting the strictest settings by default would pose an unacceptable burden to
developers.

The traditional Java way to adjust the XML parser is overwhelmingly fiddly,
involving `setFeature` or `setProperty` calls that identify the feature to be
set by passing an arcane URI that might be found in the documentation, or the
[cheat sheet][cheat], or cargo-culted from some other code base. In some cases,
the streamlined `SQLXML` API conceals the steps where adjustments would have
to be applied. With no better way to adjust the parser, it would be an
unrealistic developer burden to adopt the restrictive defaults and expect the
developer to relax them.

Therefore, PL/Java has an extension API documented at the
[org.postgresql.pljava.Adjusting.XML class][adjx]. With the API, it is possible
to obtain a `Source` object from an `SQLXML` instance `sqx` in either the
standard or extended way shown in this example for a `SAXSource`:

    SAXSource src = sqx.getSource(SAXSource.class); // OR
    SAXSource src = sqx.getSource(Adjusting.XML.SAXSource.class)
                       .allowDTD(true).get();

The first form would obtain a `SAXSource` configured with the restrictive,
OWASP-recommended defaults, which would reject any content with a DTD. The
second form would obtain a `SAXSource` configured to allow a DTD in the
content, with other parser features left at the restrictive defaults.

#### Additional adjustments in recent Java versions

Additional security-related adjustments have appeared in various Java releases,
and are described in the [Java API for XML Processing Security Guide][jaxps].
They include a number of configurable limits on maximum sizes and nesting
depths, and limits to the set of protocols allowable for fetching external
resources. Corresponding methods are provided in [PL/Java's API][adjx].
Also see "known limitations" below.

#### Supplying a SAX or DOM `EntityResolver` or `Schema`

Methods are provided to set an `EntityResolver` that controls how a SAX or DOM
parser resolves references to external entities, or a `Schema` by which a SAX
or DOM parser can validate content while parsing. Corresponding methods are
supplied in PL/Java's API, but are implemented only when operating on a
`SAXSource` or `DOMSource` (or `StreamResult`, affecting its validation of
the content written).

For StAX, control of resolution is done with a slightly different class,
`XMLResolver`, which can be set on a StAX parser as an ordinary property;
this can be done with PL/Java's `setFirstMatchingProperty` method.

A StAX parser cannot have a `Schema` directly assigned, but can be used
with a `javax.xml.validation.Validator`.

Complete details can be found [in the API documentation][adjx].

#### Using XML Catalogs when running on Java 9 or later

When running on Java 9 or later, a local XML Catalog can be set up to
efficiently and securely resolve what would otherwise be external resource
references. The registration of a Catalog on a Java 9 or later parser involves
only existing methods for setting features/properties, as described
[in the Catalog API documentation][catapi], and can be done with the
`setFirstSupportedFeature` and `setFirstSupportedProperty` methods
in PL/Java's `Adjusting` API.

### Extended API to set the content of a PL/Java `SQLXML` instance

When a `SQLXML` instance is returned from a PL/Java function, or passed in to
a PL/Java `ResultSet` or `PreparedStatement`, it is used directly if it is an
instance of PL/Java's internal implementation.

However, a PL/Java function might reasonably use another JDBC driver and obtain
a `SQLXML` instance from a connection to some other database. If such a
'foreign' `SQLXML` object is returned from a function, or passed to a PL/Java
`ResultSet` or `PreparedStatement`, its content must first be copied to a new
instance created by PL/Java's driver. This happens transparently (but implies
that the 'foreign' instance must be in _readable_ state at the time, and
afterward will not be).

The transparent copy is made by passing `null` as `sourceClass` to the foreign
object's `getSource` method, so the foreign object is in control of the type of
`Source` it will return. PL/Java will copy from a `StreamSource`, `SAXSource`,
`StAXSource`, or `DOMSource`. In the case of a `StreamSource`, an XML parser
will be involved, either to verify that the stream is XML, or to parse and
reserialize it if necessary to adapt its encoding to the server's. The parser
used by default will have the default, restrictive settings.

To allow adjustment of those settings, the copying operation can be invoked
explicitly through the `Adjusting.XML.SourceResult` class. For example, when
_sx_ is a 'foreign' `SQLXML` object, the transparent operation

    return sx;

is equivalent to

    return conn.createSQLXML().setResult(Adjusting.XML.SourceResult.class)
               .set(sx.getSource(null)).get().getSQLXML();

where _conn_ is the PL/Java JDBC connection named by
`jdbc:default:connection`. To adjust the parser settings, as usual, adjusting
methods can be chained after the `set` and before the `get`. The explicit form
also allows passing a `sourceClass` other than `null` to the foreign object's
`getSource` method, if there is a reason not to let the foreign object choose
the type of `Source` to return.

### `SQLXML` views of non-XML data

There are the beginnings of a feature supporting
[XML views of non-XML data](xmlview.html), so that some data types that are
not XML, but are similarly tree-structured, can be manipulated in Java using
Java's extensive support for XML.

[OWASP]: https://www.owasp.org/index.php/About_The_Open_Web_Application_Security_Project
[cheat]: https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#java
[adjx]: ../pljava-api/apidocs/org.postgresql.pljava/org/postgresql/pljava/Adjusting.XML.html
[jaxps]: https://docs.oracle.com/en/java/javase/13/security/java-api-xml-processing-jaxp-security-guide.html
[catapi]: https://docs.oracle.com/javase/9/core/xml-catalog-api1.htm#JSCOR-GUID-51446739-F878-4B70-A36F-47FBBE12A26A

## Known limitations

### Limitations of `StAX` support

PL/Java's `StAXSource` supplies an `XMLStreamReader` that only supports the
expected usage pattern:

```
while ( streamReader.hasNext() )
{
  streamReader.next();
  /* methods that query state of the current parse event */
}
```

It would be unexpected to reorder that pattern so that queries of the current
event occur after `hasNext` but before `next`, and may produce
`IllegalStateException`s or incorrect results from a `StAXSource` supplied
by PL/Java.

### Compatibility of `StAX` with `TrAX` (Java's transformation API)

The `javax.xml.transform` APIs are required to accept any of a specified
four types of `Source` and `Result`: `StreamSource`, `DOMSource`, `SAXSource`,
or `StAXSource` (and their `Result` counterparts). However, `StAX` was a later
addition to the family. While `TrAX` is a mature and reliable transformation
API, and `StAX` is well suited for direct use in new code that will parse or
generate XML, the handful of internal bridge classes that were added
to the Java runtime for `StAX` and `TrAX` interoperation are not dependable,
especially when handling `XML(CONTENT)`. When supplying a `Source` or `Result`
to a `Transformer`, a variant other than `StAX` should be chosen whenever
possible, whether PL/Java's or any other implementation.

For convenience, the `SQLXML` API allows passing a null value to `getSource`
or `setResult`, allowing the implementation to choose the type of `Source`
or `Result` to supply. PL/Java's implementation will never supply a `StAX`
variant when not explicitly requested.

### Pay no attention to that man behind the curtain

The processing done "behind the curtain" to be able to handle `XML(CONTENT)`
and `XML(DOCUMENT)` form, when the form is not known in advance, can have
some visible effects when combined with the newer [security limit][jaxps]
adjustments, or `schema` set on a SAX or DOM parser. For example, a very tight
setting of `maxElementDepth` may reveal that elements in the input are
nested one level deeper than expected, or a very tight `maxXMLNameLimit` may
reject a document whose expected names are all shorter. Schema validation for
some schemas and schema languages may likewise report an unexpected element
at the root of the document.

Issues with `maxElementDepth` or `maxXMLNameLimit` can be avoided by using
generous settings chosen to limit extreme resource consumption rather than
trying to set them as tightly as possible.

Problems with schema validation when assigning a `Schema` directly to the
SAX or DOM parser can be alleviated by using a `javax.xml.validation.Validator`
or `ValidatorHandler` instead, layered over PL/Java's parser, where it will
see the expected view of the content.
