# Database character set encodings

Strings in Java, by definition, are of Unicode characters. PL/Java 1.5.0 is
rigorous in the treatment of strings passed and returned between Java and
PostgreSQL.

If the database server encoding is `UTF8`, all strings will roundtrip between
PostgreSQL and Java without alteration. With any other server encoding that
is a defined subset of Unicode, strings that include only characters that
exist in the server encoding will be converted successfully.

Characters that cannot be represented in the selected encoding, as well as
any byte sequences that are not valid encodings of characters, will produce
exceptions. Reflecting its role as a database backend component, PL/Java will
not silently introduce character substitutions or other alteration or loss of
string data.

PL/Java does *not* automatically perform, or enforce, any [normalization][n11n]
when strings are passed or returned. Any requirements concerning normalization
must be handled explicitly.

[n11n]: http://unicode.org/faq/normalization.html

## The special encoding `SQL_ASCII`

The encoding `SQL_ASCII`, as described [in the PostgreSQL documentation][mbc],

> ... behaves considerably differently from the other settings. When the server
> character set is `SQL_ASCII`, the server interprets byte values 0-127
> according to the ASCII standard, while byte values 128-255 are taken as
> uninterpreted characters. No encoding conversion will be done when the setting
> is `SQL_ASCII`. Thus, this setting is not so much a declaration that a
> specific encoding is in use, as a declaration of ignorance about the encoding.
> In most cases, if you are working with any non-ASCII data, it is unwise to use
> the `SQL_ASCII` setting because PostgreSQL will be unable to help you by
> converting or validating non-ASCII characters.

[mbc]: http://www.postgresql.org/docs/current/static/multibyte.html

### Using PL/Java with server encoding `SQL_ASCII`

When the server encoding is `SQL_ASCII`, the only safe way for PL/Java to treat
it is as strict ASCII, throwing exceptions if asked to convert any string
involving characters outside of ASCII.

If PL/Java must be used with `SQL_ASCII` as the server encoding, the cases are
(by increasing complexity):

0. The database contains no non-ASCII data (or none that will be touched
    in Java code). There will be no difficulty.

0. The database contains non-ASCII data all known to be in one standard
    encoding. It would be simplest for the database to be recreated with
    this encoding selected, but that may be impractical for various reasons.
    In that case, this can be handled in the same way as the next case.

0. The database contains non-ASCII data in _more than one_ encoding, with
    the application somehow knowing which encoding is used where. That is
    completely possible because `SQL_ASCII` does not guarantee or validate
    anything (which means it can also happen over time without being intended).
    The rigorous approach in this case is to write Java code expecting and
    returning `bytea` and some indication of the encoding to be used, and
    perform explicit conversions.

## Using PL/Java with standard (not `SQL_ASCII`) encodings other than `UTF8`

PL/Java itself will transparently transcode strings from and to any
supported encoding, only throwing exceptions if a string coming from
PostgreSQL contains sequences not valid in the server encoding, or if
a Java string going to PostgreSQL contains characters the server encoding
cannot represent. As long as strings do not include unrepresentable characters,
PL/Java considers any standard encoding as good as any other.

### PostgreSQL's own limitations on encodings other than `UTF8`

Certain features of PostgreSQL itself, however, are degraded in any encoding
except `UTF8`:

* The hexadecimal escapes in a
    [`U&'...'` Unicode character string literal][ulit] are only allowed to
    encode ASCII characters.
    > The Unicode escape syntax works only when the server encoding is UTF8.
    > When other server encodings are used, only code points in the ASCII range
    > (up to \007F) can be specified.
* The [`ascii` and `chr` functions][acfns] behave two different ways, depending
    on whether the server encoding is *a single-byte encoding*, or *any
    multi-byte encoding other than `UTF8`*.
    * If it is a multi-byte encoding other than `UTF8`, they are usable only
        for strictly ASCII characters (values through 127, or 7f hex), and
	no others even if available in the encoding.
    * If it is a single-byte encoding, then they are usable with values
        through 255, and therefore all non-NUL characters available in the
	encoding, but *the numeric value is that of the encoded byte rather
	than the Unicode codepoint*.

[ulit]: http://www.postgresql.org/docs/current/static/sql-syntax-lexical.html#SQL-SYNTAX-STRINGS
[acfns]: http://www.postgresql.org/docs/current/static/functions-string.html

The odd behavior of `ascii` and `chr` is easily worked around, as simple
to/from-codepoint functions are trivial to write in Java or most other PLs.

The restriction on `U&'...'` strings may be more inconvenient. It does not,
of course, *prevent* writing string literals with non-ASCII content; they
can always be written in direct, plain-quoted-literal form, as long as the
server and client encodings both cover the content and an editor or input
method is in use that allows them to be entered, and a font in which they
can be seen. The chief use of the `U&'...'` form may be when, for explicitness
or ease of editing, it is preferred to use a hexadecimal escape instead of
entering a given character directly, but that's exactly what PostgreSQL's
`U&'...'` syntax can't be used for if the server encoding isn't `UTF8`.

Workarounds are possible, of course, such as defining an `IMMUTABLE` function
in Java or another PL that accepts a string with a hexadecimal escape syntax
and returns the decoded string, and using the function call (applied to a
plain string literal with escapes) where otherwise a `U&'...'` literal would
be used.

## Bottom line

Everything is simplest when using PL/Java in a database with `UTF8` as the
server encoding.
