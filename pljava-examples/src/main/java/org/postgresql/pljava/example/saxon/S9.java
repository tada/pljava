/*
 * Copyright (c) 2018-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.saxon;

import java.math.BigDecimal;
import java.math.BigInteger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import static java.sql.ResultSetMetaData.columnNoNulls;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Types;

import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLNonTransientException;
import java.sql.SQLSyntaxErrorException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import static java.time.ZoneOffset.UTC;

import static java.util.Arrays.asList;
import static java.util.Arrays.fill;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.Source;
import javax.xml.transform.Result;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static javax.xml.XMLConstants.XML_NS_URI;
import static javax.xml.XMLConstants.XML_NS_PREFIX;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE;

import net.sf.saxon.event.Receiver;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.NamespaceConstant;

import static net.sf.saxon.om.NameChecker.isValidNCName;

import net.sf.saxon.query.StaticQueryContext;

import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.regex.RegularExpression;

import net.sf.saxon.s9api.Destination;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.ItemTypeFactory;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SAXDestination;
import net.sf.saxon.s9api.SequenceType;
import static net.sf.saxon.s9api.SequenceType.makeSequenceType;
import net.sf.saxon.s9api.XdmAtomicValue;
import static net.sf.saxon.s9api.XdmAtomicValue.makeAtomicValue;
import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import static net.sf.saxon.s9api.XdmNodeKind.DOCUMENT;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;

import net.sf.saxon.s9api.SaxonApiException;

import net.sf.saxon.trans.XPathException;

import net.sf.saxon.serialize.SerializationProperties;

import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.Converter;

import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Base64BinaryValue;
import net.sf.saxon.value.CalendarValue;
import net.sf.saxon.value.HexBinaryValue;
import net.sf.saxon.value.StringValue;
import static net.sf.saxon.value.StringValue.getStringLength;

import org.postgresql.pljava.ResultSetProvider;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLType;
import static org.postgresql.pljava.annotation.Function.OnNullInput.CALLED;

/* For the xmltext function, which only needs plain SAX and not Saxon */

import javax.xml.transform.sax.SAXResult;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Class illustrating use of XQuery with Saxon as the
 * implementation, using its native "s9api".
 *<p>
 * Supplies alternative, XML Query-based (as the SQL/XML standard dictates)
 * implementation of some of SQL/XML, where the implementation in core
 * PostgreSQL is limited to the capabilities of XPath (and XPath 1.0, at that).
 *<p>
 * Without the syntatic sugar built into the core PostgreSQL parser, calls to
 * a function in this class can look a bit more verbose in SQL, but reflect a
 * straightforward rewriting from the standard syntax. For example, suppose
 * there is a table {@code catalog_as_xml} with a single row whose {@code x}
 * column is a (respectably sized) XML document recording the stuff in
 * {@code pg_catalog}. It could be created like this:
 *<pre>
 * CREATE TABLE catalog_as_xml(x) AS
 *   SELECT schema_to_xml('pg_catalog', false, true, '');
 *</pre>
 *<h2>Functions/predicates from ISO 9075-14 SQL/XML</h2>
 *<h3>XMLQUERY</h3>
 *<p>
 * In the syntax of the SQL/XML standard, here is a query that would return
 * an XML element representing the declaration of a function with a specified
 * name:
 *<pre>
 * SELECT XMLQUERY('/pg_catalog/pg_proc[proname eq $FUNCNAME]'
 *                 PASSING BY VALUE x, 'numeric_avg' AS FUNCNAME
 *                 RETURNING CONTENT EMPTY ON EMPTY)
 * FROM catalog_as_xml;
 *</pre>
 *<p>
 * It binds the 'context item' of the query to {@code x}, and the {@code NAME}
 * parameter to the given value, then evaluates the query and returns XML
 * "CONTENT" (a tree structure with a document node at the root, but not
 * necessarily meeting all the requirements of an XML "DOCUMENT"). It can be
 * rewritten as this call to the {@link #xq_ret_content xq_ret_content} method:
 *<pre>
 * SELECT javatest.xq_ret_content('/pg_catalog/pg_proc[proname eq $FUNCNAME]',
 *                                PASSING =&gt; p, nullOnEmpty =&gt; false)
 * FROM catalog_as_xml,
 * LATERAL (SELECT x AS ".", 'numeric_avg' AS "FUNCNAME") AS p;
 *</pre>
 *<p>
 * In the rewritten form, the form of result wanted ({@code RETURNING CONTENT})
 * is implicit in the called function name ({@code xq_ret_content}), and the
 * parameters to pass to the query are moved out to a separate {@code SELECT}
 * that supplies their values, types, and names (with the context item now given
 * the name ".") and is passed by its alias into the query function.
 *<p>
 * Because of an unconditional uppercasing that PL/Java's JDBC driver currently
 * applies to column names, any parameter names, such as {@code FUNCNAME} above,
 * must be spelled in uppercase where used in the XQuery text, or they will not
 * be recognized. Because the unconditional uppercasing is highly likely to be
 * dropped in a future PL/Java release, it is wisest until then to use only
 * parameter names that really are uppercase, both in the XQuery text where they
 * are used and in the SQL expression that supplies them. In PostgreSQL,
 * identifiers that are not quoted are <em>lower</em>cased, so they must be both
 * uppercase and quoted, in the SQL syntax, to be truly uppercase.
 *<p>
 * In the standard, parameters and results (of XML types) can be passed
 * {@code BY VALUE} or {@code BY REF}, where the latter means that the same
 * nodes will retain their XQuery node identities over calls (note that this is
 * a meaning unrelated to what "by value" and "by reference" usually mean in
 * PostgreSQL's documentation). PostgreSQL's implementation of the XML type
 * provides no way for {@code BY REF} semantics to be implemented, so everything
 * happening here happens {@code BY VALUE} implicitly, and does not need to be
 * specified.
 *<h3>XMLEXISTS</h3>
 *<p>
 * The function {@link #xmlexists xmlexists} here implements the
 * standard function of the same name. Because it is the same name, it has to
 * be either schema-qualified or double-quoted in a call to avoid confusion
 * with the reserved word. In the syntax of the SQL/XML standard, here is a
 * query returning a boolean value indicating whether a function with the
 * specified name is declared:
 *<pre>
 * SELECT XMLEXISTS('/pg_catalog/pg_proc[proname eq $FUNCNAME]'
 *                  PASSING BY VALUE x, 'numeric_avg' AS FUNCNAME)
 * FROM catalog_as_xml;
 *</pre>
 *<p>
 * It can be rewritten as this call to the {@link #xmlexists xmlexists} method:
 *<pre>
 * SELECT "xmlexists"('/pg_catalog/pg_proc[proname eq $FUNCNAME]',
 *                    PASSING =&gt; p)
 * FROM catalog_as_xml,
 * LATERAL (SELECT x AS ".", 'numeric_avg' AS "FUNCNAME") AS p;
 *</pre>
 *<h3>XMLTABLE</h3>
 *<p>
 * The function {@link #xmltable xmltable} here implements (much of) the
 * standard function of the same name. Because it is the same name, it has to
 * be either schema-qualified or double-quoted in a call to avoid confusion
 * with the reserved word. A rewritten form of the <a href=
'https://www.postgresql.org/docs/10/static/functions-xml.html#FUNCTIONS-XML-PROCESSING-XMLTABLE'
>first example in the PostgreSQL manual</a> could be:
 *<pre>
 * SELECT xmltable.*
 * FROM
 *	xmldata,
 *
 *	LATERAL (SELECT data AS ".", 'not specified'::text AS "DPREMIER") AS p,
 *
 *	"xmltable"('//ROWS/ROW', PASSING =&gt; p, COLUMNS =&gt; ARRAY[
 *	 'data(@id)', null, 'COUNTRY_NAME',
 *	 'COUNTRY_ID', 'SIZE[@unit eq "sq_km"]',
 *	 'concat(SIZE[@unit ne "sq_km"], " ", SIZE[@unit ne "sq_km"]/@unit)',
 *	 'let $e := PREMIER_NAME
 *	  return if ( empty($e) )then $DPREMIER else $e'
 *	]) AS (
 *	 id int, ordinality int8, "COUNTRY_NAME" text, country_id text,
 *	 size_sq_km float, size_other text, premier_name text
 *	);
 *</pre>
 *<p>
 * In the first column expression, without the {@code data()} function, the
 * result would be a bare attribute node (one not enclosed in an XML element).
 * Many implementations will accept a bare attribute as a column expression
 * result, and simply assume the attribute's value is wanted, but it appears
 * that a strict implementation of the spec must raise {@code err:XPTY0004} in
 * such a case. This implementation is meant to be strict, so the attribute is
 * wrapped in {@code data()} to extract and return its value. (See
 * "About bare attribute nodes" in {@link #assignRowValues assignRowValues}
 * for more explanation.)
 *<p>
 * The {@code DPREMIER} parameter passed from SQL to the XQuery expression is
 * spelled in uppercase (and also, in the SQL expression supplying it, quoted),
 * for the reasons explained above for the {@code xq_ret_content} function.
 *<h3>XMLCAST</h3>
 *<p>
 * An ISO standard cast expression like
 *<pre>
 * XMLCAST(v AS wantedtype)
 *</pre>
 * can be rewritten with this idiom and the {@link #xmlcast xmlcast} function
 * provided here:
 *<pre>
 * (SELECT r FROM (SELECT v) AS o, xmlcast(o) AS (r wantedtype))
 *</pre>
 *<h2>XQuery regular-expression functions in ISO 9075-2 Foundations</h2>
 * The methods {@link #like_regex like_regex},
 * {@link #occurrences_regex occurrences_regex},
 * {@link #position_regex position_regex},
 * {@link #substring_regex substring_regex}, and
 * {@link #translate_regex translate_regex} provide, with slightly altered
 * syntax, the ISO SQL predicate and functions of the same names.
 *<p>
 * For the moment, they will only match newlines in the way W3C XQuery
 * specifies, not in the more-flexible Unicode-compatible way ISO SQL specifies,
 * and for the ones where ISO SQL allows {@code USING CHARACTERS} or
 * {@code USING OCTETS}, only {@code USING CHARACTERS} will work.
 *<h2>Extensions</h2>
 *<h3>XQuery module prolog allowed</h3>
 *<p>
 * Where any function here accepts an XQuery
 *<a href='https://www.w3.org/TR/xquery-31/#id-expressions'
 *>"expression"</a> according to the SQL specification, in fact an XQuery
 *<a href='https://www.w3.org/TR/xquery-31/#dt-main-module'
 *>"main module"</a> will be accepted. Therefore, the query can be preceded by
 * a prolog declaring namespaces, options, local variables and functions, etc.
 *<h3>Saxon extension to XQuery regular expressions</h3>
 *<p>
 * Saxon's implementation of XQuery regular expressions will accept a
 * nonstandard <em>flag</em> string ending with {@code ;j} to use Java regular
 * expressions rather than XQuery ones. That extension is available in the
 * XQuery regular-expression methods provided here.
 * @author Chapman Flack
 */
public class S9 implements ResultSetProvider.Large
{
	private S9(
		XdmSequenceIterator<XdmItem> xsi,
		XQueryEvaluator[] columnXQEs,
		SequenceType[] columnStaticTypes,
		XMLBinary enc)
	{
		m_sequenceIterator = xsi;
		m_columnXQEs = columnXQEs;
		m_columnStaticTypes = columnStaticTypes;
		m_atomize = new AtomizingFunction [ columnStaticTypes.length ];
		m_xmlbinary = enc;
	}

	final XdmSequenceIterator<XdmItem> m_sequenceIterator;
	final XQueryEvaluator[] m_columnXQEs;
	final SequenceType[] m_columnStaticTypes;
	final SequenceType s_01untypedAtomic = makeSequenceType(
		ItemType.UNTYPED_ATOMIC, OccurrenceIndicator.ZERO_OR_ONE);
	final AtomizingFunction[] m_atomize;
	final XMLBinary m_xmlbinary;
	Binding.Assemblage m_outBindings;

	static final Connection s_dbc;
	static final Processor s_s9p = new Processor(false);
	static final ItemTypeFactory s_itf = new ItemTypeFactory(s_s9p);

	static final Pattern s_intervalSigns;
	static final Pattern s_intervalSignSite;

	enum XMLBinary { HEX, BASE64 };
	enum Nulls { ABSENT, NIL };

	static
	{
		try
		{
			s_dbc =	DriverManager.getConnection("jdbc:default:connection");

			/*
			 * XML Schema thinks an ISO 8601 duration must have no sign
			 * anywhere but at the very beginning before the P. PostgreSQL
			 * thinks that's the one place a sign must never be, and instead
			 * it should appear in front of every numeric field. (PostgreSQL
			 * accepts input where the signs vary, and there are cases where it
			 * cannot be normalized away: P1M-1D is a thing, and can't be
			 * simplified until anchored at a date to know how long the month
			 * is! The XML Schema type simply can't represent that, so mapping
			 * of such a value must simply fail, as we'll ensure below.)
			 * So, here's a regex with a capturing group for a leading -, and
			 * one for any field-leading -, and one for the absence of a field-
			 * leading -. Any PostgreSQL or XS duration ought to match overall,
			 * but the capturing group matches should be either (f,f,t) or
			 * (f,t,f) for a PostgreSQL duration, or either (f,f,t) or (t,f,t)
			 * for an XS duration. (f,t,t) would be a PostgreSQL interval with
			 * mixed signs, and inconvertible.
			 */
			s_intervalSigns = Pattern.compile(
			"(-)?+(?:[PYMWDTH](?:(?:(-)|())\\d++)?+)++(?:(?:[.,]\\d*+)?+S)?+");
			/*
			 * To convert from the leading-sign form, need to find every spot
			 * where a digit follows a [PYMWDTH] to insert a - there.
			 */
			s_intervalSignSite = Pattern.compile("(?<=[PYMWDTH])(?=\\d)");
		}
		catch ( SQLException e )
		{
			throw new ExceptionInInitializerError(e);
		}
	}

	static class PredefinedQueryHolders
	{
		static final XQueryCompiler s_xqc = s_s9p.newXQueryCompiler();
		static final QName s_qEXPR = new QName("EXPR");

		static class DocumentWrapUnwrap
		{
			static final XQueryExecutable INSTANCE;

			static
			{
				try
				{
					INSTANCE = s_xqc.compile(
						"declare construction preserve;" +
						"declare variable $EXPR as item()* external;" +
						"data(document{$EXPR}/child::node())");
				}
				catch ( SaxonApiException e )
				{
					throw new ExceptionInInitializerError(e);
				}
			}
		}
	}

	/**
	 * PostgreSQL (as of 12) lacks the XMLTEXT function, so here it is.
	 *<p>
	 * As long as PostgreSQL does not have the {@code XML(SEQUENCE)} type,
	 * this can only be the {@code XMLTEXT(sve RETURNING CONTENT)} flavor, which
	 * does create a text node with {@code sve} as its value, but returns the
	 * text node wrapped in a document node.
	 *<p>
	 * This function doesn't actually require Saxon, but otherwise fits in with
	 * the theme here, implementing missing parts of SQL/XML for PostgreSQL.
	 * @param sve SQL string value to use in a text node
	 * @return XML content, the text node wrapped in a document node
	 */
	@Function(schema="javatest")
	public static SQLXML xmltext(String sve) throws SQLException
	{
		SQLXML rx = s_dbc.createSQLXML();
		ContentHandler ch = rx.setResult(SAXResult.class).getHandler();

		try
		{
			ch.startDocument();
			/*
			 * It seems XMLTEXT() should be such a trivial function to write,
			 * but already it reveals a subtlety in the SAX API docs. They say
			 * the third argument to characters() is "the number of characters
			 * to read from the array" and that follows a long discussion of how
			 * individual characters can (with code points above U+FFFF) consist
			 * of more than one Java char value.
			 *
			 * And yet, when you try it out (and include some characters above
			 * U+FFFF in the input), you discover the third argument isn't the
			 * number of characters, has to be the number of Java char values.
			 */
			ch.characters(sve.toCharArray(), 0, sve.length());
			ch.endDocument();
		}
		catch ( SAXException e )
		{
			rx.free();
			throw new SQLException(e.getMessage(), e);
		}

		return rx;
	}

	/**
	 * An implementation of XMLCAST.
	 *<p>
	 * Will be declared to take and return type {@code RECORD}, where each must
	 * have exactly one component, just because that makes it easy to use
	 * existing JDBC metadata queries to find out the operand and target SQL
	 * data types.
	 *<p>
	 * Serving suggestion: rewrite this ISO standard expression
	 *<pre>
	 * XMLCAST(v AS wantedtype)
	 *</pre>
	 * to this idiomatic one:
	 *<pre>
	 * (SELECT r FROM (SELECT v) AS o, xmlcast(o) AS (r wantedtype))
	 *</pre>
	 * @param operand a one-row, one-column record supplied by the caller, whose
	 * one typed value is the operand to be cast.
	 * @param base64 true if binary SQL values should be base64-encoded in XML;
	 * if false (the default), values will be encoded in hex.
	 * @param target a one-row, one-column record supplied by PL/Java from the
	 * {@code AS} clause after the function call, whose one column's type is the
	 * type to be cast to.
	 */
	@Function(
		schema="javatest",
		type="pg_catalog.record",
		onNullInput=CALLED,
		settings="IntervalStyle TO iso_8601"
	)
	public static boolean xmlcast(
		ResultSet operand, @SQLType(defaultValue="false") Boolean base64,
		ResultSet target)
		throws SQLException
	{
		if ( null == operand )
			throw new SQLDataException(
				"xmlcast \"operand\" must be (in this implementation) " +
				"a non-null row type", "22004");

		if ( null == base64 )
			throw new SQLDataException(
				"xmlcast \"base64\" must be true or false, not null", "22004");
		XMLBinary enc = base64 ? XMLBinary.BASE64 : XMLBinary.HEX;

		assert null != target : "PL/Java supplied a null output record???";

		if ( 1 != operand.getMetaData().getColumnCount() )
			throw new SQLDataException(
				"xmlcast \"operand\" must be a row type with exactly " +
				"one component", "22000");

		if ( 1 != target.getMetaData().getColumnCount() )
			throw new SQLDataException(
				"xmlcast \"target\" must be a row type with exactly " +
				"one component", "22000");

		Binding.Parameter op =
			new BindingsFromResultSet(operand, false).iterator().next();

		Binding.Parameter tg =
			new BindingsFromResultSet(target, null).iterator().next();

		int sd = op.typeJDBC();
		int td = tg.typeJDBC();

		int castcase =
			(Types.SQLXML == sd ? 2 : 0) | (Types.SQLXML == td ? 1 : 0);

		switch ( castcase )
		{
		case 0: // neither sd nor td is an XML type
			throw new SQLSyntaxErrorException(
				"at least one of xmlcast \"operand\" or \"target\" must " +
				"be of XML type", "42804");
		case 3: // both XML
			/*
			 * In an implementation closely following the spec, this case would
			 * be handled in parse analysis and rewritten from an XMLCAST to a
			 * plain CAST, and this code would never see it. This is a plain
			 * example function without benefit of a parser that can do that.
			 * In a DBMS with all the various SQL:2006 XML subtypes, there would
			 * be nontrivial work to do here, but casting from PostgreSQL's one
			 * XML type to itself is more of a warm-up exercise.
			 */
			target.updateSQLXML(1, operand.getSQLXML(1));
			return true;
		case 1: // something non-XML being cast to XML
			assertCanCastAsXmlSequence(sd, "operand");
			Object v = op.valueJDBC();
			if ( null == v )
			{
				target.updateNull(1);
				return true;
			}
			ItemType xsbt =
				mapSQLDataTypeToXMLSchemaDataType(op, enc, Nulls.ABSENT);
			Iterator<XdmItem> tv =
				xmlCastAsSequence(v, enc, xsbt).iterator();
			try
			{
				target.updateSQLXML(1,
					returnContent(tv, /*nullOnEmpty*/ false));
			}
			catch ( SaxonApiException | XPathException e )
			{
				throw new SQLException(e.getMessage(), "10000", e);
			}
			return true;
		case 2: // XML being cast to something non-XML
			assertCanCastAsXmlSequence(td, "target");
			SQLXML sx = operand.getSQLXML(1);
			if ( null == sx )
			{
				target.updateNull(1);
				return true;
			}
			DocumentBuilder dBuilder = s_s9p.newDocumentBuilder();
			Source source = sx.getSource(null);
			try
			{
				XdmValue xv = dBuilder.build(source);
				XQueryEvaluator xqe =
					PredefinedQueryHolders.DocumentWrapUnwrap.INSTANCE.load();
				xqe.setExternalVariable(PredefinedQueryHolders.s_qEXPR, xv);
				xv = xqe.evaluate();
				/*
				 * It's zero-or-one, or XPTY0004 was thrown here.
				 */
				if ( 0 == xv.size() )
				{
					target.updateNull(1);
					return true;
				}
				XdmAtomicValue av = (XdmAtomicValue)xv;
				xmlCastAsNonXML(
					av, ItemType.UNTYPED_ATOMIC, tg, target, 1, enc);
			}
			catch ( SaxonApiException | XPathException e )
			{
				throw new SQLException(e.getMessage(), "10000", e);
			}
			return true;
		}

		throw new SQLFeatureNotSupportedException(
			"cannot yet xmlcast from " + op.typePG() +
			" to " + tg.typePG(), "0A000");
	}

	/**
	 * A simple example corresponding to {@code XMLQUERY(expression
	 * PASSING BY VALUE passing RETURNING CONTENT {NULL|EMPTY} ON EMPTY)}.
	 * @param expression An XQuery expression. Must not be {@code null} (in the
	 * SQL standard {@code XMLQUERY} syntax, it is not even allowed to be an
	 * SQL expression at all, only a string literal).
	 * @param nullOnEmpty pass {@code true} to get a null return in place of
	 * an empty sequence, or {@code false} to just get the empty sequence.
	 * @param passing A row value whose columns will be supplied to the query
	 * as parameters. Columns with names (typically supplied with {@code AS})
	 * appear as predeclared external variables with matching names (in no
	 * namespace) in the query, with types derived from the SQL types of the
	 * row value's columns. There may be one (and no more than one)
	 * column with {@code AS "."} which, if present, will be bound as the
	 * context item. (The name {@code ?column?}, which PostgreSQL uses for an
	 * otherwise-unnamed column, is also accepted, which will often allow the
	 * context item to be specified with no {@code AS} at all. Beware, though,
	 * that PostgreSQL likes to invent column names from any function or type
	 * name that may appear in the value expression, so this shorthand will not
	 * always work, while {@code AS "."} will.) PL/Java's internal JDBC uppercases all column
	 * names, so any uses of the corresponding variables in the query must have
	 * the names in upper case. It is safest to also uppercase their appearances
	 * in the SQL (for which, in PostgreSQL, they must be quoted), so that the
	 * JDBC uppercasing is not being relied on. It is likely to be dropped in a
	 * future PL/Java release.
	 * @param namespaces An even-length String array where, of each pair of
	 * consecutive entries, the first is a namespace prefix and the second is
	 * the URI to which to bind it. The zero-length prefix sets the default
	 * element and type namespace; if the prefix has zero length, the URI may
	 * also have zero length, to declare that unprefixed elements are in no
	 * namespace.
	 */
	@Function(
		schema="javatest",
		onNullInput=CALLED,
		settings="IntervalStyle TO iso_8601"
	)
	public static SQLXML xq_ret_content(
		String expression, Boolean nullOnEmpty,
		@SQLType(defaultValue={}) ResultSet passing,
		@SQLType(defaultValue={}) String[] namespaces)
		throws SQLException
	{
		/*
		 * The expression itself may not be null (in the standard, it isn't
		 * even allowed to be dynamic, and can only be a string literal!).
		 */
		if ( null == expression )
			throw new SQLDataException(
				"XMLQUERY expression may not be null", "22004");

		if ( null == nullOnEmpty )
			throw new SQLDataException(
				"XMLQUERY nullOnEmpty may not be null", "22004");

		try
		{
			XdmSequenceIterator<XdmItem> x1 =
				evalXQuery(expression, passing, namespaces);
			return null == x1 ? null : returnContent(x1, nullOnEmpty);
		}
		catch ( SaxonApiException | XPathException e )
		{
			throw new SQLException(e.getMessage(), "10000", e);
		}
	}

	/**
	 * An implementation of {@code XMLEXISTS(expression
	 * PASSING BY VALUE passing)}, using genuine XQuery.
	 * @param expression An XQuery expression. Must not be {@code null} (in the
	 * SQL standard {@code XMLQUERY} syntax, it is not even allowed to be an
	 * SQL expression at all, only a string literal).
	 * @param passing A row value whose columns will be supplied to the query
	 * as parameters. Columns with names (typically supplied with {@code AS})
	 * appear as predeclared external variables with matching names (in no
	 * namespace) in the query, with types derived from the SQL types of the
	 * row value's columns. There may be one (and no more than one)
	 * column with {@code AS "."} which, if present, will be bound as the
	 * context item. (The name {@code ?column?}, which PostgreSQL uses for an
	 * otherwise-unnamed column, is also accepted, which will often allow the
	 * context item to be specified with no {@code AS} at all. Beware, though,
	 * that PostgreSQL likes to invent column names from any function or type
	 * name that may appear in the value expression, so this shorthand will not
	 * always work, while {@code AS "."} will.) PL/Java's internal JDBC uppercases all column
	 * names, so any uses of the corresponding variables in the query must have
	 * the names in upper case. It is safest to also uppercase their appearances
	 * in the SQL (for which, in PostgreSQL, they must be quoted), so that the
	 * JDBC uppercasing is not being relied on. It is likely to be dropped in a
	 * future PL/Java release.
	 * @param namespaces An even-length String array where, of each pair of
	 * consecutive entries, the first is a namespace prefix and the second is
	 * the URI to which to bind it. The zero-length prefix sets the default
	 * element and type namespace; if the prefix has zero length, the URI may
	 * also have zero length, to declare that unprefixed elements are in no
	 * namespace.
	 * @return True if the expression evaluates to a nonempty sequence, false if
	 * it evaluates to an empty one. Null if a context item is passed and its
	 * SQL value is null.
	 */
	@Function(
		schema="javatest",
		onNullInput=CALLED,
		settings="IntervalStyle TO iso_8601"
	)
	public static Boolean xmlexists(
		String expression,
		@SQLType(defaultValue={}) ResultSet passing,
		@SQLType(defaultValue={}) String[] namespaces)
		throws SQLException
	{
		/*
		 * The expression itself may not be null (in the standard, it isn't
		 * even allowed to be dynamic, and can only be a string literal!).
		 */
		if ( null == expression )
			throw new SQLDataException(
				"XMLEXISTS expression may not be null", "22004");

		XdmSequenceIterator<XdmItem> x1 =
			evalXQuery(expression, passing, namespaces);
		if ( null == x1 )
			return null;
		if ( ! x1.hasNext() )
			return false;
		x1.close();
		return true;
	}

	/**
	 * Implementation factor of XMLEXISTS and XMLQUERY.
	 * @return null if a context item is passed and its SQL value is null
	 */
	private static XdmSequenceIterator<XdmItem> evalXQuery(
		String expression, ResultSet passing, String[] namespaces)
		throws SQLException
	{
		Binding.Assemblage bindings = new BindingsFromResultSet(passing, true);

		try
		{
			XQueryCompiler xqc = createStaticContextWithPassedTypes(
				bindings, namespaceBindings(namespaces));

			XQueryEvaluator xqe = xqc.compile(expression).load();

			if ( storePassedValuesInDynamicContext(xqe, bindings, true) )
				return null;

			/*
			 * For now, punt on whether the <XQuery expression> is evaluated
			 * with XML 1.1 or 1.0 lexical rules....  XXX
			 */
			return xqe.iterator();
		}
		catch ( SaxonApiException | XPathException e )
		{
			throw new SQLException(e.getMessage(), "10000", e);
		}
	}

	/**
	 * Perform the final steps of <em>something</em> {@code RETURNING CONTENT},
	 * with or without {@code nullOnEmpty}.
	 *<p>
	 * The effects are to be the same as if the supplied sequence were passed
	 * as {@code $EXPR} to {@code document{$EXPR}}.
	 */
	private static SQLXML returnContent(
		Iterator<XdmItem> x, boolean nullOnEmpty)
	throws SQLException, SaxonApiException, XPathException
	{
		if ( nullOnEmpty  &&  ! x.hasNext() )
			return null;

		SQLXML rsx = s_dbc.createSQLXML();
		/*
		 * Keep this simple by requesting a specific type of Result rather
		 * than letting PL/Java choose. It happens (though this is a detail of
		 * the implementation) that SAXResult won't be a bad choice.
		 */
		SAXResult sr = rsx.setResult(SAXResult.class);
		/*
		 * Michael Kay recommends the following as equivalent to the SQL/XML-
		 * mandated behavior of evaluating document{$x}.
		 * https://sourceforge.net/p/saxon/mailman/message/36969060/
		 */
		SAXDestination d = new SAXDestination(sr.getHandler());
		Receiver r = d.getReceiver(
			s_s9p.getUnderlyingConfiguration().makePipelineConfiguration(),
			new SerializationProperties());
		r.open();
		while ( x.hasNext() )
			r.append(x.next().getUnderlyingValue());
		r.close();
		return rsx;
	}

	/**
	 * An implementation of (much of) XMLTABLE, using genuine XML Query.
	 *<p>
	 * The {@code columns} array must supply a valid XML Query expression for
	 * every column in the column definition list that follows the call of this
	 * function in SQL, except that the column for ordinality, if wanted, is
	 * identified by a {@code null} entry in {@code columns}. Syntax sugar in
	 * the standard allows an omitted column expression to imply an element test
	 * for an element with the same name as the column; that doesn't work here.
	 *<p>
	 * For now, this implementation lacks the ability to specify defaults for
	 * when a column expression produces an empty sequence. It is possible to
	 * do defaults explicitly by rewriting a query expression <em>expr</em> as
	 * {@code let $e := }<em>expr</em>{@code return if(empty($e))then $D else $e}
	 * and supplying the default <em>D</em> as another query parameter, though
	 * such defaults will be evaluated only once when {@code xmltable} is called
	 * and will not be able to refer to other values in an output row.
	 * @param rows The single XQuery expression whose result sequence generates
	 * the rows of the resulting table. Must not be null.
	 * @param columns Array of XQuery expressions, exactly as many as result
	 * columns in the column definition list that follows the SQL call to this
	 * function. This array must not be null. It is allowed for one element (and
	 * no more than one) to be null, marking the corresponding column to be
	 * "FOR ORDINALITY" (the column must be of "exact numeric with scale zero"
	 * type; PostgreSQL supports 64-bit row counters, so {@code int8} is
	 * recommended).
	 * @param passing A row value whose columns will be supplied to the query
	 * as parameters, just as described for
	 * {@link #xq_ret_content xq_ret_content()}. If a context item is supplied,
	 * it is the context item for the {@code rows} query (the {@code columns}
	 * queries get their context item from the {@code rows} query's result). Any
	 * named parameters supplied here are available both in the {@code rows}
	 * expression and (though this goes beyond the standard) in every expression
	 * of {@code columns}, with their values unchanging from row to row.
	 * @param namespaces An even-length String array where, of each pair of
	 * consecutive entries, the first is a namespace prefix and the second is
	 * to URI to which to bind it, just as described for
	 * {@link #xq_ret_content xq_ret_content()}.
	 * @param base64 whether the effective, in-scope 'xmlbinary' setting calls
	 * for base64 or (the default, false) hexadecimal.
	 */
	@Function(
		schema="javatest",
		onNullInput=CALLED,
		settings="IntervalStyle TO iso_8601"
	)
	public static ResultSetProvider xmltable(
		String rows, String[] columns,
		@SQLType(defaultValue={}) ResultSet passing,
		@SQLType(defaultValue={}) String[] namespaces,
		@SQLType(defaultValue="false") Boolean base64)
		throws SQLException
	{
		if ( null == rows )
			throw new SQLDataException(
				"XMLTABLE row expression may not be null", "22004");

		if ( null == columns )
			throw new SQLDataException(
				"XMLTABLE columns expression array may not be null", "22004");

		if ( null == base64 )
			throw new SQLDataException(
				"XMLTABLE base64 parameter may not be null", "22004");
		XMLBinary enc = base64 ? XMLBinary.BASE64 : XMLBinary.HEX;

		Binding.Assemblage rowBindings =
			new BindingsFromResultSet(passing, true);

		Iterable<Map.Entry<String,String>> namespacepairs =
			namespaceBindings(namespaces);

		XQueryEvaluator[] columnXQEs = new XQueryEvaluator[ columns.length ];
		SequenceType[] columnStaticTypes = new SequenceType[ columns.length ];

		try
		{
			XQueryCompiler rowXQC = createStaticContextWithPassedTypes(
				rowBindings, namespacepairs);

			XQueryExecutable rowXQX = rowXQC.compile(rows);

			Binding.Assemblage columnBindings =
				new BindingsFromXQX(rowXQX, rowBindings);

			XQueryCompiler columnXQC = createStaticContextWithPassedTypes(
				columnBindings, namespacepairs);

			boolean ordinalitySeen = false;
			for ( int i = 0; i < columns.length; ++ i )
			{
				String expr = columns[i];
				if ( null == expr )
				{
					if ( ordinalitySeen )
						throw new SQLSyntaxErrorException(
							"No more than one column expression may be null " +
							"(=> \"for ordinality\")", "42611");
					ordinalitySeen = true;
					continue;
				}
				XQueryExecutable columnXQX = columnXQC.compile(expr);
				columnStaticTypes[i] = makeSequenceType(
					columnXQX.getResultItemType(),
					columnXQX.getResultCardinality());
				columnXQEs[i] = columnXQX.load();
				storePassedValuesInDynamicContext(
					columnXQEs[i], columnBindings, false);
			}

			XQueryEvaluator rowXQE = rowXQX.load();
			XdmSequenceIterator<XdmItem> rowIterator;
			if ( storePassedValuesInDynamicContext(rowXQE, rowBindings, true) )
				rowIterator = (XdmSequenceIterator<XdmItem>)
					XdmEmptySequence.getInstance().iterator();
			else
				rowIterator = rowXQE.iterator();
			return new S9(rowIterator, columnXQEs, columnStaticTypes, enc);
		}
		catch ( SaxonApiException | XPathException e )
		{
			throw new SQLException(e.getMessage(), "10000", e);
		}
	}

	/**
	 * Called when PostgreSQL has no need for more rows of the tabular result.
	 */
	@Override
	public void close()
	{
		m_sequenceIterator.close();
	}

	/**
	 * <a id='assignRowValues'>Produce and return one row</a> of
	 * the {@code XMLTABLE} result table per call.
	 *<p>
	 * The row expression has already been compiled and its evaluation begun,
	 * producing a sequence iterator. The column XQuery expressions have all
	 * been compiled and are ready to evaluate, and the compiler's static
	 * analysis has bounded the data types they will produce. Because of the
	 * way the set-returning function protocol works, we don't know the types
	 * of the SQL output columns yet, until the first call of this function,
	 * when the {@code receive} parameter's {@code ResultSetMetaData} can be
	 * inspected to find out. So that will be the first thing done when called
	 * with {@code currentRow} of zero.
	 *<p>
	 * Each call will then: (a) get the next value from the row expression's
	 * sequence iterator, then for each column, (b) evaluate that column's
	 * XQuery expression on the row value, and (c) assign that column's result
	 * to the SQL output column, casting to the proper type (which the SQL/XML
	 * spec has very exacting rules on how to do).
	 *<p>
	 * A note before going any further: this implementation, while fairly
	 * typical of a PostgreSQL set-returning user function, is <em>not</em> the
	 * way the SQL/XML spec defines {@code XMLTABLE}. The official behavior of
	 * {@code XMLTABLE} is defined in terms of a rewriting, at the SQL level,
	 * into a much-expanded SQL query where each result column appears as an
	 * {@code XMLQUERY} call applying the column expression, wrapped in an
	 * {@code XMLCAST} to the result column type (with a
	 * {@code CASE WHEN XMLEXISTS} thrown in to support column defaults).
	 *<p>
	 * As an ordinary user function, this example cannot rely on any fancy
	 * query rewriting during PostgreSQL's parse analysis. The slight syntax
	 * desugaring needed to transform a standard {@code XMLTABLE} call into a
	 * call of this "xmltable" is not too hard to learn and do by hand, but no
	 * one would ever want to write out by hand the whole longwinded "official"
	 * expansion prescribed in the spec. So this example is a compromise.
	 *<p>
	 * The main thing lost in the compromise is the handling of column defaults.
	 * The full rewriting with per-column SQL expressions means that each
	 * column default expression can be evaluated exactly when/if needed, which
	 * is often the desired behavior. This implementation as an ordinary
	 * function, whose arguments all get evaluated ahead of the call, can't
	 * really do that. Otherwise, there's nothing in the spec that's inherently
	 * unachievable in this implementation.
	 *<p>
	 * Which brings us to the matter of casting each column expression result
	 * to the proper type for its SQL result column.
	 *<p>
	 * Like any spec, {@code SQL/XML} does not mandate that an implementation
	 * must be done in exactly the way presented in the spec (rewritten so each
	 * column value is produced by an {@code XMLQUERY} wrapped in an
	 * {@code XMLCAST}). The requirement is to produce the equivalent result.
	 *<p>
	 * A look at the rewritten query shows that each column XQuery result value
	 * must be representable as some value in SQL's type system, not once, but
	 * twice: first as the result returned by {@code XMLQUERY} and passed along
	 * to {@code XMLCAST}, and finally with the output column's type as the
	 * result of the {@code XMLCAST}.
	 *<p>
	 * Now, the output column type can be whatever is wanted. Importantly, it
	 * can be either an XML type, or any ordinary SQL scalar type, like a
	 * {@code float} or a {@code date}. Likewise, the XQuery column expression
	 * may have produced some atomic value (like an {@code xs:double} or
	 * {@code xs:date}), or some XML node, or any sequence of any of those.
	 *<p>
	 * What are the choices for the type in the middle: the SQL value returned
	 * by {@code XMLQUERY} and passed on to {@code XMLCAST}?
	 *<p>
	 * There are two. An ISO-standard SQL {@code XMLQUERY} can specify
	 * {@code RETURNING SEQUENCE} or {@code RETURNING CONTENT}. The first option
	 * produces the type {@code XML(SEQUENCE)}, a useful type that PostgreSQL
	 * does not currently have. {@code XML(SEQUENCE)} can hold exactly whatever
	 * an XQuery expression can produce: a sequence of any length, of any
	 * mixture of atomic values and XML nodes (even such oddities as attribute
	 * nodes outside of any element), in any order. An {@code XML(SEQUENCE)}
	 * value need not look anything like what "XML" normally brings to mind.
	 *<p>
	 * With the other option, {@code RETURNING CONTENT}, the result of
	 * {@code XMLQUERY} has to be something that PostgreSQL's {@code xml} type
	 * could store: a serialized document with XML structure, but without the
	 * strict requirements of exactly one root element with no text outside it.
	 * At the limit, a completely non-XMLish string of ordinary text is
	 * perfectly acceptable XML {@code CONTENT}, as long as it uses the right
	 * {@code &...;} escapes for any characters that could look like XML markup.
	 *<p>
	 * {@code XMLCAST} is able to accept either form as input, and deliver it
	 * to the output column as whatever type is needed. But the spec leaves no
	 * wiggle room as to which form to use:
	 *<ul>
	 *<li>If the result column type is {@code XML(SEQUENCE)}, then the
	 * {@code XMLQUERY} is to specify {@code RETURNING SEQUENCE}. It produces
	 * the column's result type directly, so the {@code XMLCAST} has nothing
	 * to do.
	 *<li>In every other case (<em>every</em> other case), the {@code XMLQUERY}
	 * is to specify {@code RETURNING CONTENT}.
	 *</ul>
	 *<p>
	 * At first blush, that second rule should sound crazy. Imagine a column
	 * definition like
	 *<pre>
	 * growth float8 PATH 'math:pow(1.0 + $RATE, count(year))'
	 *</pre>
	 * The expression produces an {@code xs:double}, which can be assigned
	 * directly to a PostgreSQL {@code float8}, but the rule in the spec will
	 * have it first converted to a decimal string representation, made into
	 * a text node, wrapped in a document node, and returned as XML, to be
	 * passed along to {@code XMLCAST}, which parses it, discards the wrapping
	 * document node, parses the text content as a double, and returns that as
	 * a proper value of the result column type (which, in this example, it
	 * already is).
	 *<p>
	 * The spec does not go into why this rule was chosen. The only rationale
	 * that makes sense to me is that the {@code XML(SEQUENCE)} data type
	 * is an SQL feature (X190) that not every implementation will support,
	 * so the spec has to define {@code XMLTABLE} using a rewritten query that
	 * can work on systems that do not have that type. (PostgreSQL itself, at
	 * present, does not have it.)
	 *<p>
	 * The first rule, when {@code XML(SEQUENCE)} is the result column type,
	 * will naturally never be in play except on a system that has that type, in
	 * which case it can be used directly. But even such a system must still
	 * produce, in all other cases, results that match what a system without
	 * that type would produce. All those cases are therefore defined as if
	 * going the long way through {@code XML(CONTENT)}.
	 *<p>
	 * Whenever the XQuery expression can be known to produce a (possibly empty
	 * or) singleton sequence of an atomic type, the long round trip can be
	 * shown to be idempotent, and we can skip right to casting the atomic type
	 * to the SQL result column type. A few other cases could be short-circuited
	 * the same way. But in general, for cases involving nodes or non-singleton
	 * sequences, it is safest to follow the spec punctiliously; the steps are
	 * defined in terms of XQuery constructs like {@code document {...}} and
	 * {@code data()}, which have specs of their own with many traps for the
	 * unwary, and the XQuery library provides implementations of them that are
	 * already tested and correct.
	 *<p>
	 * Though most of the work can be done by the XQuery library, it may be
	 * helpful to look closely at just what the specification entails.
	 *<p>
	 * Again, but for the case of an {@code XML(SEQUENCE)} result column, in all
	 * other cases the result must pass through
	 * {@code XMLQUERY(... RETURNING CONTENT EMPTY ON EMPTY)}. That, in turn, is
	 * defined as equivalent to {@code XMLQUERY(... RETURNING SEQUENCE)} with
	 * the result then passed to {@code XMLDOCUMENT(... RETURNING CONTENT)},
	 * whose behavior is that of a
	 * <a href='https://www.w3.org/TR/xquery-31/#id-documentConstructors'>
	 * document node constructor</a> in XQuery, with
	 * <a href='https://www.w3.org/TR/xquery-31/#dt-construction-mode'>
	 * construction mode</a> {@code preserve}. The first step of that behavior
	 * is the same as Step 1e in the processing of
	 * <a href='https://www.w3.org/TR/xquery-31/#id-content'>direct element
	 * constructor content</a>. The remaining steps are those laid out for the
	 * document node constructor.
	 *<p>
	 * Clarity demands flattening this nest of specifications into a single
	 * ordered list of the steps to apply:
	 *<ul>
	 *<li>Any item in the sequence that is an array is flattened (its elements
	 * become items in the sequence).
	 *<li>If any item is a function, {@code err:XQTY0105} is raised.
	 *<li>Any sequence {@code $s} of adjacent atomic values is replaced by
	 * {@code string-join($s, ' ')}.
	 *<li>Any XML node in the sequence is copied (as detailed in the spec).
	 *<li>After all the above, any document node that may exist in the resulting
	 * sequence is flattened (replaced by its children).
	 *<li>A single text node is produced for any run of adjacent text nodes in
	 * the sequence (including any that have newly become adjacent by the
	 * flattening of document nodes), by concatenation with no separator (unlike
	 * the earlier step where atomic values were concatenated with a space as
	 * the separator).
	 *<li>If the sequence directly contains any attribute or namespace node,
	 * {@code err:XPTY0004} is raised. <b>More on this below.</b>
	 *<li>The sequence resulting from the preceding steps is wrapped in one
	 * new document node (as detailed in the spec).
	 *</ul>
	 *<p>
	 * At this point, the result could be returned to SQL as a value of
	 * {@code XML(CONTENT(ANY))} type, to be passed to an {@code XMLCAST}
	 * invocation. This implementation avoids that, and simply proceeds with the
	 * existing Java in-memory representation of the document tree, to the
	 * remaining steps entailed in an {@code XMLCAST} to the output column type:
	 *<ul>
	 *<li>If the result column type is an XML type, rewriting would turn the
	 * {@code XMLCAST} into a simple {@code CAST} and that's that. Otherwise,
	 * the result column has some non-XML, SQL type, and:
	 *<li>The algorithm "Removing XQuery document nodes from an XQuery sequence"
	 * is applied. By construction, we know the only such node is the one the
	 * whole sequence was recently wrapped in, two steps ago (you get your
	 * house back, you get your dog back, you get your truck back...).
	 *<li>That sequence of zero or more XML nodes is passed to the
	 *<a href='https://www.w3.org/TR/xpath-functions-31/#func-data'>fn:data</a>
	 * function, producing a sequence of zero or more atomic values, which will
	 * all have type {@code xs:untypedAtomic} (because the document-wrapping
	 * stringified any original atomic values and wrapped them in text nodes,
	 * for which the
	 * <a href='https://www.w3.org/TR/xpath-datamodel-31/#acc-summ-typed-value'>
	 * typed-value</a> is {@code xs:untypedAtomic} by definition). This sequence
	 * also has cardinality zero-or-more, and may be shorter or longer than the
	 * original.
	 *<li>If the sequence is empty, the result column is assigned {@code NULL}
	 * (or the column's default value, if one was specified). Otherwise, the
	 * sequence is known to have length one or more, and:
	 *<li>The spec does not say this (which may be an oversight or bug), but the
	 * sequence must be checked for length greater than one, raising
	 * {@code err:XPTY0004} in that case. The following steps require it to be a
	 * singleton.
	 *<li>It is labeled as a singleton sequence of {@code xs:anyAtomicType} and
	 * used as input to an XQuery {@code cast as} expression. (Alternatively, it
	 * could be labeled a one-or-more sequence of {@code xs:anyAtomicType},
	 * leaving the length check to be done by {@code cast as}, which would raise
	 * the same error {@code err:XPTY0004}, if longer than one.)
	 *<li>The {@code cast as} is to the XQuery type determined as in
	 * {@code determineXQueryFormalType} below, based on the SQL type of the
	 * result column; or, if the SQL type is a date/time type with no time zone,
	 * there is a first {@code cast as} to a specific XSD date/time type, which
	 * is (if it has a time zone) first adjusted to UTC, then stripped of its
	 * time zone, followed by a second {@code cast as} from that type to the one
	 * determined from the result column type. Often, that will be the same type
	 * as was used for the time zone adjustment, and the second {@code cast as}
	 * will have nothing to do.
	 *<li>The XQuery value resulting from the cast is converted and assigned to
	 * the SQL-typed result column, a step with many details but few surprises,
	 * therefore left for the morbidly curious to explore in the code. The flip
	 * side of the time zone removal described above happens here: if the SQL
	 * column type expects a time zone and the incoming value lacks one, it is
	 * given a zone of UTC.
	 *</ul>
	 *<p>
	 * The later steps above, those following the length-one check, are
	 * handled by {@code xmlCastAsNonXML} below.
	 *<p>
	 * The earlier steps, from the start through the {@code XMLCAST} early steps
	 * of document-node unwrapping, can all be applied by letting the original
	 * result sequence be {@code $EXPR} in the expression:
	 *<pre>
	 * declare construction preserve;
	 * data(document { $EXPR } / child::node())
	 *</pre>
	 * which may seem a bit of an anticlimax after seeing how many details lurk
	 * behind those tidy lines of code.
	 *<p>
	 * <strong>About bare attribute nodes</strong>
	 *<p>
	 * One consequence of the rules above deserves special attention.
	 * Consider something like:
	 *<pre>
	 * XMLTABLE('.' PASSING '&lt;a foo="bar"/&gt;' COLUMNS c1 VARCHAR PATH 'a/@foo');
	 *</pre>
	 *<p>
	 * The result of the column expression is an XML attribute node all on its
	 * own, with name {@code foo} and value {@code bar}, not enclosed in any
	 * XML element. In the data type {@code XML(SEQUENCE)}, an attribute node
	 * can appear standalone like that, but not in {@code XML(CONTENT)}.
	 *<p>
	 * Db2, Oracle, and even the XPath-based pseudo-XMLTABLE built into
	 * PostgreSQL, will all accept that query and produce the result "bar".
	 *<p>
	 * However, a strict interpretation of the spec cannot produce that result,
	 * because the result column type ({@code VARCHAR}) is not
	 * {@code XML(SEQUENCE)}, meaning the result must be as if passed through
	 * {@code XMLDOCUMENT(... RETURNING CONTENT)}, and the XQuery
	 * {@code document { ... }} constructor is required to raise
	 * {@code err:XPTY0004} upon encountering any bare attribute node. The
	 * apparently common, convenient behavior of returning the attribute node's
	 * value component is not, strictly, conformant.
	 *<p>
	 * This implementation will raise {@code err:XPTY0004}. That can be avoided
	 * by simply wrapping any such bare attribute in {@code data()}:
	 *<pre>
	 * ... COLUMNS c1 VARCHAR PATH 'a/data(@foo)');
	 *</pre>
	 *<p>
	 * It is possible the spec has an editorial mistake and did not intend to
	 * require an error for this usage, in which case this implementation can
	 * be changed to match a future clarification of the spec.
	 */
	@Override
	public boolean assignRowValues(ResultSet receive, long currentRow)
	throws SQLException
	{
		if ( 0 == currentRow )
		{
			m_outBindings = new BindingsFromResultSet(receive, m_columnXQEs);
			int i = -1;
			AtomizingFunction atomizer = null;
			for ( Binding.Parameter p : m_outBindings )
			{
				SequenceType staticType = m_columnStaticTypes [ ++ i ];
				/*
				 * A null in m_columnXQEs identifies the ORDINALITY column,
				 * if any. Assign nothing to m_atomize[i], it won't be used.
				 */
				if ( null == m_columnXQEs [ i ] )
					continue;

				if ( Types.SQLXML == p.typeJDBC() )
					continue;

				/*
				 * Ok, the output column type is non-XML; choose an atomizer,
				 * either a simple identity if the result type is statically
				 * known to be zero-or-one atomic, or the long way through the
				 * general-purpose one. If the type is statically known to be
				 * the empty sequence (weird, but not impossible), the identity
				 * atomizer suffices and we're on to the next column.
				 */
				OccurrenceIndicator occur = staticType.getOccurrenceIndicator();
				if ( OccurrenceIndicator.ZERO == occur )
				{
					m_atomize [ i ] = (v, col) -> v;
					continue;
				}

				/* So, it isn't known to be empty. If the column
				 * expression type isn't known to be atomic, or isn't known to
				 * be zero-or-one, then the general-purpose atomizer--a trip
				 * through data(document { ... } / child::node())--must be used.
				 * This atomizer will definitely produce a sequence of length
				 * zero or one, raising XPTY0004 otherwise. So the staticType
				 * can be replaced by xs:anyAtomicType?. xmlCastAsNonXML will
				 * therefore be passed xs:anyAtomicType, as in the spec.
				 *    BUT NO ... Saxon is more likely to find a converter from
				 * xs:untypedAtomic than from xs:anyAtomicType.
				 */
				ItemType itemType = staticType.getItemType();
				if ( occur.allowsMany()
					|| ! ItemType.ANY_ATOMIC_VALUE.subsumes(itemType)
					/*
					 * The following tests may be punctilious to a fault. If we
					 * have a bare Saxon atomic type of either xs:base64Binary
					 * or xs:hexBinary type, Saxon will happily and successfully
					 * convert it to a binary string; but if we have the same
					 * thing as a less-statically-determinate type that we'll
					 * put through the atomizer, the conversion will fail unless
					 * its encoding matches the m_xmlbinary setting. That could
					 * seem weirdly unpredictable to a user, so we'll just
					 * (perversely) disallow the optimization (which would
					 * succeed) in the cases where the specified, unoptimized
					 * behavior would be to fail.
					 */
					|| ItemType.HEX_BINARY.subsumes(itemType)
						&& (XMLBinary.HEX != m_xmlbinary)
					|| ItemType.BASE64_BINARY.subsumes(itemType)
						&& (XMLBinary.BASE64 != m_xmlbinary)
				   )
				{
					if ( null == atomizer )
					{
						XQueryEvaluator docWrapUnwrap =	PredefinedQueryHolders
							.DocumentWrapUnwrap.INSTANCE.load();
						atomizer = (v, col) ->
						{
							docWrapUnwrap.setExternalVariable(
								PredefinedQueryHolders.s_qEXPR, v);
							v = docWrapUnwrap.evaluate();
							/*
							 * It's already zero-or-one, or XPTY0004 was thrown
							 */
							return v;
						};
					}
					m_atomize [ i ] = atomizer;
					/*
					 * The spec wants anyAtomicType below instead of
					 * untypedAtomic. But Saxon's getConverter is more likely
					 * to fail to find a converter from anyAtomicType to an
					 * arbitrary type, than from untypedAtomic. So use that.
					 */
					m_columnStaticTypes [ i ] = s_01untypedAtomic;
				}
				else
				{
					/*
					 * We know we'll be getting zero-or-one atomic value, so
					 * the atomizing function can be the identity.
					 */
					m_atomize [ i ] = (v, col) -> v;
				}
			}
		}

		if ( ! m_sequenceIterator.hasNext() )
			return false;

		++ currentRow; // for use as 1-based ordinality column

		XdmItem it = m_sequenceIterator.next();

		int i = 0;
		for ( Binding.Parameter p : m_outBindings )
		{
			XQueryEvaluator xqe = m_columnXQEs [ i ];
			AtomizingFunction atomizer = m_atomize [ i ];
			SequenceType staticType = m_columnStaticTypes [ i++ ];

			if ( null == xqe )
			{
				receive.updateLong( i, currentRow);
				continue;
			}

			try
			{
				xqe.setContextItem(it);

				if ( null == atomizer ) /* => result type was found to be XML */
				{
					receive.updateSQLXML(
						i, returnContent(xqe.iterator(), false));
					continue;
				}

				XdmValue x1 = xqe.evaluate();
				x1 = atomizer.apply(x1, i);

				/*
				 * The value is now known to be atomic and either exactly
				 * one or zero-or-one. May as well just use size() to see if
				 * it's empty.
				 */
				if ( 0 == x1.size() )
				{
					receive.updateNull(i); // XXX Handle defaults some day
					continue;
				}
				XdmAtomicValue av = (XdmAtomicValue)x1.itemAt(0);
				xmlCastAsNonXML(
					av, staticType.getItemType(), p, receive, i, m_xmlbinary);
			}
			catch ( SaxonApiException | XPathException e )
			{
				throw new SQLException(e.getMessage(), "10000", e);
			}
		}
		return true;
	}

	/**
	 * Store the values of any passed parameters and/or context item into the
	 * dynamic context, returning true if the overall query should
	 * short-circuit and return null.
	 *<p>
	 * The specification requires the overall query to return null if a
	 * context item is specified in the bindings and its value is null.
	 * @param xqe XQuery evaluator into which to store the values.
	 * @param passing The bindings whose values should be installed.
	 * @param setContextItem True to handle the context item, if present in the
	 * bindings. False to skip any processing of the context item, in cases
	 * where the caller will handle that.
	 * @return True if the overall query's return should be null, false if the
	 * query should proceed to evaluation.
	 */
	private static boolean storePassedValuesInDynamicContext(
		XQueryEvaluator xqe, Binding.Assemblage passing, boolean setContextItem)
		throws SQLException, SaxonApiException
	{
		/*
		 * Is there or is there not a context item?
		 */
		if ( ! setContextItem  ||  null == passing.contextItem() )
		{
			/* "... there is no context item in XDC." */
		}
		else
		{
			Object cve = passing.contextItem().valueJDBC();
			if ( null == cve )
				return true;
			XdmValue ci;
			if ( cve instanceof XdmNode ) // XXX support SEQUENCE input someday
			{
				ci = (XdmNode)cve;
			}
			else
				ci = xmlCastAsSequence(
					cve, XMLBinary.HEX, passing.contextItem().typeXS());
			switch ( ci.size() )
			{
			case 0:
				/* "... there is no context item in XDC." */
				break;
			case 1:
				xqe.setContextItem(ci.itemAt(0));
				break;
			default:
				throw new SQLDataException(
					"invalid XQuery context item", "2200V");
			}
		}

		/*
		 * For each <XML query variable> XQV:
		 */
		for ( Binding.Parameter p : passing )
		{
			String name = p.name();
			Object v = p.valueJDBC();
			XdmValue vv;
			if ( null == v )
				vv = XdmEmptySequence.getInstance();
			else if ( v instanceof XdmNode ) // XXX support SEQUENCE someday
			{
				vv = (XdmNode)v;
			}
			else
				vv = xmlCastAsSequence(
					v, XMLBinary.HEX, p.typeXS().getItemType());
			xqe.setExternalVariable(new QName(name), vv);
		}

		return false;
	}

	/**
	 * Return a s9api {@link XQueryCompiler XQueryCompiler} with static context
	 * preconfigured as the Syntax Rules dictate.
	 * @param pt The single-row ResultSet representing the passed parameters
	 * and context item, if any.
	 * @param nameToIndex A Map, supplied empty, that on return will map
	 * variable names for the dynamic context to column indices in {@code pt}.
	 * If a context item was supplied, its index will be entered in the map
	 * with the null key.
	 */
	private static XQueryCompiler createStaticContextWithPassedTypes(
		Binding.Assemblage pt, Iterable<Map.Entry<String,String>> namespaces)
		throws SQLException, XPathException
	{
		XQueryCompiler xqc = s_s9p.newXQueryCompiler();
		xqc.declareNamespace(
			"sqlxml", "http://standards.iso.org/iso9075/2003/sqlxml");
		// https://sourceforge.net/p/saxon/mailman/message/20318550/ :
		xqc.declareNamespace("xdt", W3C_XML_SCHEMA_NS_URI);

		for ( Map.Entry<String,String> e : namespaces )
			xqc.declareNamespace(e.getKey(), e.getValue());

		/*
		 * This business of predeclaring global external named variables
		 * is not an s9api-level advertised ability in Saxon, hence the
		 * various getUnderlying.../getStructured... methods here to access
		 * the things that make it happen.
		 */
		StaticQueryContext sqc = xqc.getUnderlyingStaticContext();

		for ( Binding.Parameter p : pt )
		{
			String name = p.name();
			int ct = p.typeJDBC();
			assertCanCastAsXmlSequence(ct, name);
			SequenceType st = p.typeXS();
			sqc.declareGlobalVariable(
				new QName(name).getStructuredQName(),
				st.getUnderlyingSequenceType(), null, true);
		}

		/*
		 * Apply syntax rules to the context item, if any.
		 */
		Binding.ContextItem ci = pt.contextItem();
		if ( null != ci )
		{
			int ct = ci.typeJDBC();
			assertCanCastAsXmlSequence(ct, "(context item)");
			ItemType it = ci.typeXS();
			xqc.setRequiredContextItemType(it);
		}

		return xqc;
	}

	/**
	 * Check that something's type is "convertible to XML(SEQUENCE)
	 * according to the Syntax Rules of ... <XML cast specification>."
	 * That turns out not to be a very high bar; not much is excluded
	 * by those rules except collection, row, structured, or
	 * reference typed <value expression>s.
	 * @param jdbcType The {@link Types JDBC type} to be checked.
	 * @param what A string to include in the exception message if the
	 * check fails.
	 * @throws SQLException if {@code jdbcType} is one of the prohibited types.
	 */
	private static void assertCanCastAsXmlSequence(int jdbcType, String what)
	throws SQLException
	{
		if ( Types.ARRAY == jdbcType || Types.STRUCT == jdbcType
			|| Types.REF == jdbcType )
			throw new SQLSyntaxErrorException(
				"The type of \"" + what + "\" is not suitable for " +
				"XMLCAST to XML(SEQUENCE).", "42804");
	}

	/**
	 * The "determination of an XQuery formal type notation" algorithm.
	 *<p>
	 * This is relied on for parameters and context items passed to
	 * {@code XMLQUERY} and therefore, {@code XMLTABLE} (and also, in the spec,
	 * {@code XMLDOCUMENT} and {@code XMLPI}). Note that it does <em>not</em>
	 * take an {@code XMLBinary} parameter, but rather imposes hexadecimal form
	 * unconditionally, so in the contexts where this is called, any
	 * {@code xmlbinary} setting is ignored.
	 * @param b a {@code Binding} from which the JDBC type can be retrieved
	 * @param forContextItem whether the type being derived is for a context
	 * item or (if false) for a named parameter.
	 * @return a {@code SequenceType} (always a singleton in the
	 * {@code forContextItem} case)
	 */
	private static SequenceType determineXQueryFormalType(
		Binding b, boolean forContextItem)
		throws SQLException
	{
		int sd = b.typeJDBC();
		OccurrenceIndicator suffix;
		/*
		 * The SQL/XML standard uses a formal type notation straight out of
		 * the XQuery 1.0 and XPath 2.0 Formal Semantics document, and that is
		 * strictly more fine-grained and expressive than anything you can
		 * actually say in the form of XQuery SequenceTypes. This method will
		 * simply return the nearest approximation in the form of a sequence
		 * type; some of the standard's distinct formal type notations will
		 * collapse into the same SequenceType.
		 *  That also means the various cases laid out in the standard will,
		 * here, all simply assign some ItemType to 'it', and therefore the
		 * tacking on of the occurrence suffix can be factored out for the
		 * very end.
		 */
		ItemType it;

		if ( forContextItem )
			suffix = OccurrenceIndicator.ONE;
		// else if sd is XML(SEQUENCE) - we don't have this type yet
		//	suffix = OccurrenceIndicator.ZERO_OR_MORE;
		/*
		 * Go through the motions of checking isNullable, though PL/Java's JDBC
		 * currently hardcodes columnNullableUnknown. Maybe someday it won't.
		 */
		else if ( b.knownNonNull() )
			suffix = OccurrenceIndicator.ONE;
		else
			suffix = OccurrenceIndicator.ZERO_OR_ONE;

		// Define ET... for {DOCUMENT|CONTENT}(XMLSCHEMA) case ... not supported

		// if SD is XML(DOCUMENT(UNTYPED)) - not currently tracked, can't tell
		//	it = s_itf.getDocumentTest(item type for xdt:untyped);
		// else if SD is XML(DOCUMENT(ANY)) - not currently tracked, can't tell
		//	it = s_itf.getDocumentTest(item type for xs:anyType);
		// else if SD is XML(DOCUMENT(XMLSCHEMA)) - unsupported and can't tell
		//	it = s_itf.getDocumentTest(the ET... we didn't define earlier)
		// else if SD is XML(CONTENT(UNTYPED)) - which we're not tracking ...
		//	at s9api granularity, there's no test for this that's not same as:
		// else if SD is XML(CONTENT(ANY)) - which we must assume for ANY XML
		if ( Types.SQLXML == sd )
			it = s_itf.getNodeKindTest(DOCUMENT);
		// else if SD is XML(CONTENT(XMLSCHEMA)) - we don't track and can't tell
		//	at s9api granularity, there's no test that means this anyway.
		// else if SD is XML(SEQUENCE) - we really should have this type, but no
		//	it = it.ANY_ITEM
		else // it ain't XML, it's some SQL type
		{
			ItemType xmlt = mapSQLDataTypeToXMLSchemaDataType(
				b, XMLBinary.HEX, Nulls.ABSENT);
			// ItemType pt = xmlt.getUnderlyingItemType().getPrimitiveType()
			//  .somehowGetFromUnderlyingPTBackToS9apiPT() - ugh, the hard part
			/*
			 * The intention here is to replace any derived type with the
			 * primitive type it is based on, *except* for three types that are
			 * technically derived: integer (from decimal), yearMonthDuration
			 * and dayTimeDuration (from duration). Those are not replaced, so
			 * they stand, as if they were honorary primitive types.
			 *
			 * For now, it's simplified greatly by mapSQLDataType... skipping
			 * the construction of a whole derived XML Schema snippet, and just
			 * returning the type we want anyway. Also, no need to dive under
			 * the s9api layer to try to make getPrimitiveType work.
			 */
			it = xmlt;
		}

		SequenceType xftn = makeSequenceType(it, suffix);
		return xftn;
	}

	@SuppressWarnings("fallthrough")
	private static ItemType mapSQLDataTypeToXMLSchemaDataType(
		Binding b, XMLBinary xmlbinary, Nulls nulls)
		throws SQLException
	{
		/*
		 * Nearly all of the fussing about specified in the standard
		 * for this method is to create XML Schema derived types that
		 * accurately reflect the typmod information for the SQL type
		 * in question. Then, in determineXQueryFormalType (the only
		 * client of this method so far!), all of that is thrown away
		 * and our painstakingly specified derived type is replaced with
		 * the primitive type we based it on. That simplifies a lot. :)
		 * For now, forget the derived XML Schema declarations, and just
		 * return the primitive types they would be based on.
		 *
		 * The need for the nulls parameter vanishes if no XML Schema snippets
		 * are to be generated.
		 *
		 * If the full XML Schema snippet generation ever proves to be
		 * needed, one hacky way to get it would be with a SELECT
		 * query_to_xmlschema('SELECT null::type-in-question', false, false,
		 * '') where the same derivations are already implemented (though it
		 * produces some different results; that work may have been done from
		 * an earlier version of the standard).
		 */
		switch ( b.typeJDBC() )
		{
		case Types.CHAR:
		case Types.VARCHAR:
		case Types.CLOB:
			return ItemType.STRING;

		case Types.BINARY:
		case Types.VARBINARY:
		case Types.BLOB:
			return XMLBinary.HEX == xmlbinary ?
				ItemType.HEX_BINARY : ItemType.BASE64_BINARY;

		case Types.NUMERIC:
		case Types.DECIMAL:
			/*
			 * Go through the motions to get the scale and do this right,
			 * though PL/Java's getScale currently hardcodes a -1 return.
			 * Maybe someday it won't.
			 */
			int scale = b.scale();
			return 0 == scale ? ItemType.INTEGER : ItemType.DECIMAL;

		case Types.INTEGER:
			return ItemType.INT;
		case Types.SMALLINT:
			return ItemType.SHORT;
		case Types.BIGINT:
			return ItemType.LONG;

		case Types.REAL:
			return ItemType.FLOAT; // could check P, MINEXP, MAXEXP here.
		case Types.FLOAT:
			assert false; // PG should always report either REAL or DOUBLE
			/*FALLTHROUGH*/
		case Types.DOUBLE:
			return ItemType.DOUBLE;

		case Types.BOOLEAN:
			return ItemType.BOOLEAN;

		case Types.DATE:
			return ItemType.DATE;

		case Types.TIME:
			return ItemType.TIME;

		case Types.TIME_WITH_TIMEZONE:
			return ItemType.TIME; // restrictive facet would make sense here

		case Types.TIMESTAMP:
			return ItemType.DATE_TIME;

		case Types.TIMESTAMP_WITH_TIMEZONE:
			return ItemType.DATE_TIME_STAMP; // xsd 1.1 equivalent of facet!

		// There's no JDBC Types.INTERVAL; handle it after switch

		// Good luck finding out from JDBC if it's a domain

		// PG doesn't have DISTINCT types per se

		// PL/Java's JDBC doesn't support PostgreSQL's arrays as ARRAY

		// PG doesn't seem to have multisets (JDBC doesn't grok them either)

		// Types.SQLXML we could recognize, but for determineFormalTypes it has
		// been handled already, and it's not yet clear what would be
		// appropriate to return (short of the specified XMLSchema snippet),
		// probably just document.

		// So punt all these for now; what hasn't been handled in this switch
		// can be handled specially after the switch falls through, and what
		// isn't, isn't supported just now.
		}

		String typeName = b.typePG();
		if ( "interval".equals(typeName) )
		{
			/*
			 * XXX This isn't right yet; it needs to be refined to a
			 * YEAR_MONTH_DURATION or a DAY_TIME_DURATION in the appropriate
			 * cases, and for that it needs access to the typmod information
			 * for the type, which getColumnTypeName doesn't now provide.
			 */
			return ItemType.DURATION;
		}

		throw new SQLNonTransientException(String.format(
			"Mapping SQL type \"%s\" to XML type not supported", typeName),
			"0N000");
	}

	/**
	 * Implement that portion of the {@code <XML cast>} specification where
	 * the target data type is sequence, and (for now, anyway) the source is
	 * not an XML type; the only caller, so far, handles that case separately.
	 * @param v The SQL value to be cast (in the form of an Object from JDBC).
	 * @param enc Whether binary values should be encoded in hex or base 64.
	 * @param xst The formal static XS type derived from the SQL type of v.
	 * @return An {@code XdmValue}, {@code null} if {@code v} is null.
	 */
	private static XdmValue xmlCastAsSequence(
		Object v, XMLBinary enc, ItemType xst)
		throws SQLException
	{
		if ( null == v )
			return null;
		/*
		 * What happens next in the standard is one of the most breathtaking
		 * feats of obscurantism in the whole document. It begins, plausibly
		 * enough, by using mapValuesOfSQLTypesToValuesOfXSTypes to produce
		 * the lexical form of the XS type (but with XML metacharacters escaped,
		 * if it's a string type). Then:
		 * 1. That lexical form is to be fed to an XML parser, producing an
		 *	  XQuery document node that NEVER can be a well-formed document (it
		 *	  is expected to satisfy document { text ? } where the text node is
		 *	  just the lexical value form we started with, now with the escaped
		 *	  metacharacters unescaped again as a consequence of parsing). For
		 *	  some source types, mapValuesOfSQLTypesToValuesOfXSTypes can
		 *	  produce a string that parses to XML with element content: row
		 *	  types, arrays, multisets, XML. Clearly, those cases can't satisfy
		 *	  the formal type assumed here, and they are cases this routine
		 *	  won't be expected to handle: XML handled separately by the caller,
		 *	  arrays/structs/etc. being ruled out by assertCanCastAsXmlSequence.
		 * 2. That document node is made the $TEMP parameter of an XML Query,
		 *    '$TEMP cast as XSBTN' (where XSBTN is a QName for the result type
		 *    chosen according to the rules) and the sequence resulting from
		 *    that query is the result of the cast.
		 *
		 * Step (1) can only succeed if the XML parser doesn't insist on well-
		 * formed documents, as the stock JRE parser emphatically does. And the
		 * ultimate effect of that whole dance is that the cast in (2) casts a
		 * document node to the target type, which means the document node gets
		 * atomized, which, for a document node, means everything is thrown away
		 * save the concatenated values of its descendant text nodes (or node,
		 * in this case; haven't we seen that value somewhere before?), assigned
		 * the type xs:untypedAtomic, and then that's operated on by the cast.
		 *
		 * Because this implementation's in PL/Java, the value v received here
		 * has already been mapped from an SQL type to a Java type according to
		 * JDBC's rules as PL/Java implements them, so there's one degree of
		 * removal from the specified algorithm anyway. And the s9api
		 * XdmAtomicValue already has constructors from several of the expected
		 * Java types, as well as one taking a lexical form and explicit type.
		 * Beause this is /example/ code, rather than slavishly implementing the
		 * specified algorithm, it will assume that that is either roughly or
		 * exactly equivalent to what these s9api constructors in fact do, and
		 * just use them; conformance-testing code could then check for exact
		 * equivalence if there's enough interest to write it.
		 *
		 * So, we will NOT start with this:
		 *
		 *	 String xmlv = mapValuesOfSQLTypesToValuesOfXSTypes(
		 *	 	 v, enc, Nulls.ABSENT, true);
		 *
		 * Instead, we'll derive this type first ...
		 */
		ItemType xsbt;
		// year-month interval type => xsbt = YEAR_MONTH_DURATION
		// day-time interval type => xsbt = DAY_TIME_DURATION
		xsbt = xst; // we have a winner!
		// xs non-built-in atomic type => xsbt = getPrimitiveType(ugh).

		/*
		 * ... and then use this method instead:
		 */
		try
		{
			return mapJDBCofSQLvalueToXdmAtomicValue(v, enc, xsbt);
		}
		catch ( SaxonApiException | XPathException e )
		{
			throw new SQLException(e.getMessage(), "10000", e);
		}
	}

	@FunctionalInterface
	interface CastingFunction
	{
		AtomicValue apply(AtomicValue v) throws XPathException;
	}

	@FunctionalInterface
	interface CasterSupplier
	{
		CastingFunction get() throws SQLException, XPathException;
	}

	@FunctionalInterface
	interface AtomizingFunction
	{
		/**
		 * @param v sequence to be atomized
		 * @param columnIndex only to include in exception if result has more
		 * than one item
		 */
		XdmValue apply(XdmValue v, int columnIndex)
		throws SaxonApiException, XPathException;
	}

	private static XPathException noPrimitiveCast(ItemType vt, ItemType xt)
	{
		return new XPathException(
			"Casting from " + vt.getTypeName() + " to " + xt.getTypeName() +
			" can never succeed", "XPTY0004");
	}

	/**
	 * Handle the case of XMLCAST to a non-XML target type when the cast operand
	 * is already a single atomic value.
	 *<p>
	 * The caller, if operating on a sequence, must itself handle the case of
	 * an empty sequence (returning null, per General Rule 4c in :2011), or a
	 * sequence of length greater than one (raising XPTY0004, which is not
	 * specified in :2011, but the exclusion of such a sequence is implicit in
	 * rules 4g and 4h; Db2 silently drops all but the first item, unlike
	 * Oracle, which raises XPTY0004).
	 * @param av The atomic operand value
	 * @param p The parameter binding, recording the needed type information
	 * @param rs ResultSet into which the value will be stored
	 * @param col Index of the result column
	 */
	private static void xmlCastAsNonXML(
		XdmAtomicValue av, ItemType vt,
		Binding.Parameter p, ResultSet rs, int col, XMLBinary enc)
		throws SQLException, XPathException
	{
		XdmAtomicValue bv;
		ItemType xt = p.typeXT(enc);

		CastingFunction caster = p.atomicCaster(vt, () ->
		{
			ConversionRules rules = vt.getConversionRules();
			Converter c1;
			ItemType t1;
			Converter c2;

			switch ( p.typeJDBC() )
			{
			case Types.TIMESTAMP:
				t1 = ItemType.DATE_TIME;
				break;
			case Types.TIME:
				t1 = ItemType.TIME;
				break;
			case Types.DATE:
				t1 = ItemType.DATE;
				break;
			default:
				c1 = rules.getConverter(
					(AtomicType)vt.getUnderlyingItemType(),
					(AtomicType)xt.getUnderlyingItemType());
				if ( null == c1 )
					throw noPrimitiveCast(vt, xt);
				return (AtomicValue v) -> c1.convert(v).asAtomic();
			}
			/*
			 * Nothing left here but the rest of the three date/timey cases
			 * partly handled above.
			 */
			c1 = rules.getConverter(
				(AtomicType)vt.getUnderlyingItemType(),
				(AtomicType)t1.getUnderlyingItemType());
			c2 = rules.getConverter(
				(AtomicType)t1.getUnderlyingItemType(),
				(AtomicType)xt.getUnderlyingItemType());
			if ( null == c1  ||  null == c2 )
				throw noPrimitiveCast(vt, xt);
			return (AtomicValue v) ->
			{
				v = c1.convert(v).asAtomic();
				v = ((CalendarValue)v).adjustTimezone(0).removeTimezone();
				return c2.convert(v).asAtomic();
			};
		});

		bv = makeAtomicValue(caster.apply(av.getUnderlyingValue()));

		if ( ItemType.STRING.subsumes(xt) )
			rs.updateString(col, bv.getStringValue());

		else if ( ItemType.HEX_BINARY.subsumes(xt) )
			rs.updateBytes(col,
				((HexBinaryValue)bv.getUnderlyingValue()).getBinaryValue());
		else if ( ItemType.BASE64_BINARY.subsumes(xt) )
			rs.updateBytes(col,
				((Base64BinaryValue)bv.getUnderlyingValue()).getBinaryValue());

		else if ( ItemType.DECIMAL.subsumes(xt) )
			rs.updateObject(col, bv.getValue());

		/*
		 * The standard calls for throwing "data exception - numeric value out
		 * of range" rather than forwarding a float or double inf, -inf, or nan
		 * to SQL, but PostgreSQL supports those values, and these conversions
		 * preserve them.
		 *  Because of the collapsing in typeXT(), xt will never be FLOAT,
		 * only DOUBLE. JDBC is supposed to handle assigning a double to a float
		 * column, anyway.
		 */
		else if ( ItemType.DOUBLE.subsumes(xt) )
			rs.updateObject(col, bv.getValue());

		else if ( ItemType.DATE.subsumes(xt) )
			rs.updateObject(col, bv.getLocalDate());
		else if ( ItemType.DATE_TIME.subsumes(xt) )
		{
			if ( ((CalendarValue)bv.getUnderlyingValue()).hasTimezone() )
				rs.updateObject(col, bv.getOffsetDateTime());
			else
			{
				LocalDateTime jv = bv.getLocalDateTime();
				rs.updateObject(col,
					Types.TIMESTAMP_WITH_TIMEZONE == p.typeJDBC() ?
						jv.atOffset(UTC) : jv);
			}
		}
		else if ( ItemType.TIME.subsumes(xt) ) // no handy tz/notz distinction
		{
			if ( ((CalendarValue)bv.getUnderlyingValue()).hasTimezone() )
				rs.updateObject(col, OffsetTime.parse(bv.getStringValue()));
			else
			{
				LocalTime jv = LocalTime.parse(bv.getStringValue());
				rs.updateObject(col,
					Types.TIME_WITH_TIMEZONE == p.typeJDBC() ?
						jv.atOffset(UTC) : jv);
			}
		}

		else if ( ItemType.YEAR_MONTH_DURATION.subsumes(xt) )
			rs.updateString(col, toggleIntervalRepr(bv.getStringValue()));
		else if ( ItemType.DAY_TIME_DURATION.subsumes(xt) )
			rs.updateString(col, toggleIntervalRepr(bv.getStringValue()));
		else if ( ItemType.DURATION.subsumes(xt) ) // need this case for now
			rs.updateString(col, toggleIntervalRepr(bv.getStringValue()));

		else if ( ItemType.BOOLEAN.subsumes(xt) )
			rs.updateObject(col, bv.getValue());
		else
			throw new SQLNonTransientException(String.format(
				"Mapping XML type \"%s\" to SQL value not supported", xt),
				"0N000");
	}

	/**
	 * Like the "Mapping values of SQL data types to values of XML Schema
	 * data types" algorithm, except after the SQL values have already been
	 * converted to Java values according to JDBC rules.
	 *<p>
	 * Also, this uses Saxon s9api constructors for the XML Schema values, which
	 * accept the Java types directly. As a consequence, where the target type
	 * {@code xst} is {@code xs:hexBinary} or {@code xs:base64Binary}, that type
	 * will be produced, regardless of the passed {@code encoding}. This might
	 * not be strictly correct, but is probably safest until an oddity in the
	 * spec can be clarified: {@code determineXQueryFormalType} will always
	 * declare {@code xs:hexBinary} as the type for an SQL byte string, and it
	 * would violate type safety to construct a value here that honors the
	 * {@code encoding} parameter but isn't of the declared formal type.
	 */
	private static XdmAtomicValue mapJDBCofSQLvalueToXdmAtomicValue(
		Object dv, XMLBinary encoding, ItemType xst)
		throws SQLException, SaxonApiException, XPathException
	{
		if ( ItemType.STRING.equals(xst) )
			return new XdmAtomicValue((String)dv);

		if ( ItemType.HEX_BINARY.equals(xst) )
			return makeAtomicValue(new HexBinaryValue((byte[])dv));
		if ( ItemType.BASE64_BINARY.equals(xst) )
			return makeAtomicValue(new Base64BinaryValue((byte[])dv));

		if ( ItemType.INTEGER.equals(xst) )
			return new XdmAtomicValue(((BigInteger)dv).toString(), xst);
		if ( ItemType.DECIMAL.equals(xst) )
			return new XdmAtomicValue((BigDecimal)dv);
		if ( ItemType.INT.equals(xst) )
			return new XdmAtomicValue((Integer)dv);
		if ( ItemType.SHORT.equals(xst) )
			return new XdmAtomicValue((Short)dv);
		if ( ItemType.LONG.equals(xst) )
			return new XdmAtomicValue((Long)dv);
		if ( ItemType.FLOAT.equals(xst) )
			return new XdmAtomicValue((Float)dv);
		if ( ItemType.DOUBLE.equals(xst) )
			return new XdmAtomicValue((Double)dv);

		if ( ItemType.BOOLEAN.equals(xst) )
			return new XdmAtomicValue((Boolean)dv);

		if ( ItemType.DATE.equals(xst) )
		{
			if ( dv instanceof LocalDate )
				return new XdmAtomicValue((LocalDate)dv);
			return new XdmAtomicValue(dv.toString(), xst);
		}

		if ( ItemType.TIME.equals(xst) )
			return new XdmAtomicValue(dv.toString(), xst);

		if ( ItemType.DATE_TIME.equals(xst) )
		{
			if ( dv instanceof LocalDateTime )
				return new XdmAtomicValue((LocalDateTime)dv);
			return new XdmAtomicValue(dv.toString(), xst);
		}

		if ( ItemType.DATE_TIME_STAMP.equals(xst) )
		{
			if ( dv instanceof OffsetDateTime )
				return new XdmAtomicValue((OffsetDateTime)dv);
			return new XdmAtomicValue(dv.toString(), xst);
		}

		if ( ItemType.DURATION.equals(xst) )
			return new XdmAtomicValue(toggleIntervalRepr((String)dv), xst);

		throw new SQLNonTransientException(String.format(
			"Mapping SQL value to XML type \"%s\" not supported", xst),
			"0N000");
	}

	/*
	 * Toggle the lexical representation of an interval/duration between the
	 * form PostgreSQL likes and the form XML Schema likes. Only negative values
	 * are affected. Positive values are returned unchanged, as are those that
	 * don't fit any expected form; those will probably be reported as malformed
	 * by whatever tries to consume them.
	 */
	static String toggleIntervalRepr(String lex)
	{
		Matcher m = s_intervalSigns.matcher(lex);
		if ( ! m.matches() )
			return lex; // it's weird, just don't touch it
		if ( -1 == m.start(1) )
		{
			if ( -1 != m.start(2)  &&  -1 == m.start(3) ) // it's PG negative
				return '-' + lex.replace("-", "");        // make it XS negative
		}
		else if ( -1 == m.start(2)  &&  -1 != m.start(3) )// it's XS negative
			return m.usePattern(s_intervalSignSite)       // make it PG negative
				.reset(lex.substring(1)).replaceAll("-");
		return lex; // it's either positive, or weird, just don't touch it
	}

	static Iterable<Map.Entry<String,String>> namespaceBindings(String[] nbs)
	throws SQLException
	{
		if ( 1 == nbs.length % 2 )
			throw new SQLSyntaxErrorException(
				"Namespace binding array must have even length", "42000");
		Map<String,String> m = new HashMap<>();

		for ( int i = 0; i < nbs.length; i += 2 )
		{
			String prefix = nbs[i];
			String uri = nbs[1 + i];

			if ( null == prefix  ||  null == uri )
				throw new SQLDataException(
					"Namespace binding array elements must not be null",
					"22004");

			if ( ! "".equals(prefix) )
			{
				if ( ! isValidNCName(prefix) )
					throw new SQLSyntaxErrorException(
						"Not an XML NCname: \"" + prefix + '"', "42602");
				if ( XML_NS_PREFIX.equals(prefix)
					|| XMLNS_ATTRIBUTE.equals(prefix) )
					throw new SQLSyntaxErrorException(
						"Namespace prefix may not be xml or xmlns", "42939");
				if ( XML_NS_URI.equals(uri)
					|| XMLNS_ATTRIBUTE_NS_URI.equals(uri) )
					throw new SQLSyntaxErrorException(
						"Namespace URI has a disallowed value", "42P17");
				if ( "".equals(uri) )
					throw new SQLSyntaxErrorException(
						"URI for non-default namespace may not be zero-length",
						"42P17");
			}

			String was = m.put(prefix.intern(), uri.intern());

			if ( null != was )
				throw new SQLSyntaxErrorException(
					"Namespace prefix \"" + prefix + "\" multiply bound (" +
					"to \"" + was + "\" and \"" + uri + "\")", "42712");
		}

		return Collections.unmodifiableSet(m.entrySet());
	}

	static class Binding
	{
		String typePG() throws SQLException
		{
			if ( null != m_typePG )
				return m_typePG;
			return m_typePG = implTypePG();
		}

		int typeJDBC() throws SQLException
		{
			if ( null != m_typeJDBC )
				return m_typeJDBC;
			int tj = implTypeJDBC();
			/*
			 * The JDBC types TIME_WITH_TIMEZONE and TIMESTAMP_WITH_TIMEZONE
			 * first appear in JDBC 4.2 / Java 8. PL/Java's JDBC driver does
			 * not yet return those values. As a workaround until it does,
			 * recheck here using the PG type name string, if TIME or TIMESTAMP
			 * is the JDBC type that the driver returned.
			 *
			 * Also for backward compatibility, the driver still returns
			 * Types.OTHER for XML, rather than Types.SQLXML. Check and fix that
			 * here too.
			 */
			switch ( tj )
			{
			case Types.OTHER:
				if ( "xml".equals(typePG()) )
					tj = Types.SQLXML;
				break;
			case Types.TIME:
				if ( "timetz".equals(typePG()) )
					tj = Types.TIME_WITH_TIMEZONE;
				break;
			case Types.TIMESTAMP:
				if ( "timestamptz".equals(typePG()) )
					tj = Types.TIMESTAMP_WITH_TIMEZONE;
				break;
			default:
			}
			return m_typeJDBC = tj;
		}

		Object valueJDBC() throws SQLException
		{
			if ( m_valueJDBCValid )
				return m_valueJDBC;
			/*
			 * When JDBC 4.2 added support for the JSR 310 date/time types, for
			 * back-compatibility purposes, it did not change what types a plain
			 * getObject(...) would return for them, which could break existing
			 * code. Instead, it's necessary to use the form of getObject that
			 * takes a Class<?>, and ask for the new classes explicitly.
			 *
			 * Similarly, PL/Java up through 1.5.0 has always returned a String
			 * from getObject for a PostgreSQL xml type. Here, the JDBC standard
			 * provides that a SQLXML object should be returned, and that should
			 * happen in a future major PL/Java release, but for now, the plain
			 * getObject will still return String, so it is also necessary to
			 * ask for the SQLXML type explicitly. In fact, we will ask for
			 * XdmNode, as it might be referred to more than once (if a
			 * parameter), and a SQLXML can't be read more than once, nor would
			 * there be any sense in building an XdmNode from it more than once.
			 */
			switch ( typeJDBC() )
			{
			case Types.DATE:
				return setValueJDBC(implValueJDBC(LocalDate.class));
			case Types.TIME:
				return setValueJDBC(implValueJDBC(LocalTime.class));
			case Types.TIME_WITH_TIMEZONE:
				return setValueJDBC(implValueJDBC(OffsetTime.class));
			case Types.TIMESTAMP:
				return setValueJDBC(implValueJDBC(LocalDateTime.class));
			case Types.TIMESTAMP_WITH_TIMEZONE:
				return setValueJDBC(implValueJDBC(OffsetDateTime.class));
			case Types.SQLXML:
				return setValueJDBC(implValueJDBC(XdmNode.class));
			default:
			}
			return setValueJDBC(implValueJDBC());
		}

		boolean knownNonNull() throws SQLException
		{
			if ( null != m_knownNonNull )
				return m_knownNonNull;
			return m_knownNonNull = implKnownNonNull();
		}

		int scale() throws SQLException
		{
			if ( null != m_scale )
				return m_scale;
			return m_scale = implScale();
		}

		static class ContextItem extends Binding
		{
			/**
			 * Return the XML Schema type of this input binding for a context
			 * item.
			 *<p>
			 * Because it is based on {@code determinXQueryFormalType}, this
			 * method is not parameterized by {@code XMLBinary}, and will always
			 * map a binary-string SQL type to {@code xs:hexBinary}.
			 */
			ItemType typeXS() throws SQLException
			{
				if ( null != m_typeXS )
					return m_typeXS;
				SequenceType st = implTypeXS(true);
				assert OccurrenceIndicator.ONE == st.getOccurrenceIndicator();
				return m_typeXS = st.getItemType();
			}

			protected ItemType m_typeXS;
		}

		static class Parameter extends Binding
		{
			String name()
			{
				return m_name;
			}

			SequenceType typeXS() throws SQLException
			{
				if ( null != m_typeXS )
					return m_typeXS;
				return m_typeXS = implTypeXS(false);
			}

			/**
			 * Return the XML Schema type collapsed according to the Syntax Rule
			 * deriving {@code XT} for {@code XMLCAST}.
			 *<p>
			 * The intent of the rule is unclear, but it involves collapsing
			 * certain sets of more-specific types that {@code typeXS} might
			 * return into common supertypes, for use only in an intermediate
			 * step of {@code xmlCastAsNonXML}. Unlike {@code typeXS}, this
			 * method must be passed an {@code XMLBinary} parameter reflecting
			 * the hex/base64 choice currently in scope.
			 * @param enc whether to use {@code xs:hexBinary} or
			 * {@code xs:base64Binary} as the XML Schema type corresponding to a
			 * binary-string SQL type.
			 */
			ItemType typeXT(XMLBinary enc) throws SQLException
			{
				throw new UnsupportedOperationException(
					"typeXT() on synthetic binding");
			}

			/**
			 * Memoize and return a casting function from a given
			 * {@code ItemType} to the type of this parameter.
			 *<p>
			 * Used only by {@code xmlCastAsNonXML}, which does all the work
			 * of constructing the function; this merely allows it to be
			 * remembered, if many casts to the same output parameter will be
			 * made (as by {@code xmltable}).
			 */
			CastingFunction atomicCaster(ItemType it, CasterSupplier s)
			throws SQLException, XPathException
			{
				throw new UnsupportedOperationException(
					"atomicCaster() on synthetic binding");
			}

			protected SequenceType m_typeXS;

			private final String m_name;

			/**
			 * @param name The SQL name of the parameter
			 * @param checkName True if the name must be a valid NCName (as for
			 * an input parameter from SQL to the XML query context), or false
			 * if the name doesn't matter (as when it describes a result, or the
			 * sole input value of an XMLCAST.
			 * @throws SQLException if the name of a checked input parameter
			 * isn't a valid NCName.
			 */
			protected Parameter(String name, boolean checkName)
			throws SQLException
			{
				if ( checkName  &&  ! isValidNCName(name) )
					throw new SQLSyntaxErrorException(
						"Not an XML NCname: \"" + name + '"', "42602");
				m_name = name;
			}
		}

		protected String m_typePG;
		protected Integer m_typeJDBC;
		protected Boolean m_knownNonNull;
		protected Integer m_scale;
		private Object m_valueJDBC;
		private boolean m_valueJDBCValid;
		protected Object setValueJDBC(Object v)
		{
			m_valueJDBCValid = true;
			return m_valueJDBC = v;
		}

		protected String implTypePG() throws SQLException
		{
			throw new UnsupportedOperationException(
				"typePG() on synthetic binding");
		}

		protected int implTypeJDBC() throws SQLException
		{
			throw new UnsupportedOperationException(
				"typeJDBC() on synthetic binding");
		}

		protected boolean implKnownNonNull() throws SQLException
		{
			throw new UnsupportedOperationException(
				"knownNonNull() on synthetic binding");
		}

		protected int implScale() throws SQLException
		{
			throw new UnsupportedOperationException(
				"scale() on synthetic binding");
		}

		protected Object implValueJDBC() throws SQLException
		{
			throw new UnsupportedOperationException(
				"valueJDBC() on synthetic binding");
		}

		/*
		 * This implementation just forwards to the type-less version, then
		 * fails if that did not return the wanted type. Override if a smarter
		 * behavior is possible.
		 */
		protected <T> T implValueJDBC(Class<T> type) throws SQLException
		{
			return type.cast(implValueJDBC());
		}

		protected SequenceType implTypeXS(boolean forContextItem)
		throws SQLException
		{
			return determineXQueryFormalType(this, forContextItem);
		}

		static class Assemblage implements Iterable<Parameter>
		{
			ContextItem contextItem() { return m_contextItem; }

			@Override
			public Iterator<Parameter> iterator()
			{
				return m_params.iterator();
			}

			protected ContextItem m_contextItem;
			protected Collection<Parameter> m_params = Collections.emptyList();
		}
	}

	static class BindingsFromResultSet extends Binding.Assemblage
	{
		/**
		 * Construct the bindings from a ResultSet representing input parameters
		 * to an XML query.
		 * @param rs ResultSet representing the input parameters. Column names
		 * "." and "?COLUMN?" are treated specially, and used to supply the
		 * query's context item; every other column name must be a valid NCName,
		 * and neither any named parameter nor the context item may be mentioned
		 * more than once.
		 * @param checkNames True if the input parameter names matter (a name of
		 * "." or "?COLUMN?" will define the context item, and any other name
		 * must be a valid NCName); false to skip such checking (as for the
		 * single input value to XMLCAST, whose name doesn't matter).
		 * @throws SQLException if names are duplicated or invalid.
		 */
		BindingsFromResultSet(ResultSet rs, boolean checkNames)
		throws SQLException
		{
			m_resultSet = rs;
			m_rsmd = rs.getMetaData();

			int nParams = m_rsmd.getColumnCount();
			ContextItem contextItem = null;
			Map<String,Binding.Parameter> n2b = new HashMap<>();

			if ( 0 < nParams )
				m_dBuilder = s_s9p.newDocumentBuilder();

			for ( int i = 1; i <= nParams; ++i )
			{
				String label = m_rsmd.getColumnLabel(i);
				if ( checkNames  &&
					("?COLUMN?".equals(label)  ||  ".".equals(label)) )
				{
					if ( null != contextItem )
					throw new SQLSyntaxErrorException(
						"Context item supplied more than once (at " +
						contextItem.m_idx + " and " + i + ')', "42712");
					contextItem = new ContextItem(i);
					continue;
				}

				Parameter was =
					(Parameter)n2b.put(
						label, new Parameter(label, i, checkNames));
				if ( null != was )
					throw new SQLSyntaxErrorException(
						"Name \"" + label + "\" duplicated at positions " +
						was.m_idx +	" and " + i, "42712");
			}

			m_contextItem = contextItem;
			m_params = n2b.values();
		}

		/**
		 * Construct the bindings from a ResultSet representing output
		 * parameters (as from XMLTABLE).
		 * @param rs ResultSet representing the result parameters. Names have
		 * no particular significance and are not subject to any checks.
		 * @param exprs Compiled evaluators for the supplied column expressions.
		 * The number of these must match the number of columns in {@code rs}.
		 * One of these (and no more than one; the caller will have enforced
		 * that) is allowed to be null, making the corresponding column
		 * "FOR ORDINALITY". An ordinality column will be checked to ensure it
		 * has an SQL type that is (ahem) "exact numeric with scale 0 (zero)."
		 * May be null if this is some other general-purpose output result set,
		 * not for an XMLTABLE.
		 * @throws SQLException if numbers of columns and expressions don't
		 * match, or there is an ordinality column and its type is not suitable.
		 */
		@SuppressWarnings("fallthrough")
		BindingsFromResultSet(ResultSet rs, XQueryEvaluator[] exprs)
		throws SQLException
		{
			m_resultSet = rs;
			m_rsmd = rs.getMetaData();

			int nParams = m_rsmd.getColumnCount();
			if ( null != exprs  &&  nParams != exprs.length )
				throw new SQLSyntaxErrorException(
					"Not as many supplied column expressions as output columns",
					"42611");

			Binding.Parameter[] ps = new Binding.Parameter[ nParams ];

			for ( int i = 1; i <= nParams; ++i )
			{
				String label = m_rsmd.getColumnLabel(i);
				Parameter p = new Parameter(label, i, false);
				ps [ i - 1 ] = p;
				if ( null != exprs  &&  null == exprs [ i - 1 ] )
				{
					switch ( p.typeJDBC() )
					{
					case Types.INTEGER:
					case Types.SMALLINT:
					case Types.BIGINT:
						break;
					case Types.NUMERIC:
					case Types.DECIMAL:
						int scale = p.scale();
						if ( 0 == scale  ||  -1 == scale )
							break;
						/*FALLTHROUGH*/
					default:
						throw new SQLSyntaxErrorException(
							"Column FOR ORDINALITY must have an exact numeric" +
							" type with scale zero.", "42611");
					}
				}
			}

			m_params = asList(ps);
		}

		private ResultSet m_resultSet;
		private ResultSetMetaData m_rsmd;
		DocumentBuilder m_dBuilder;

		<T> T typedValueAtIndex(int idx, Class<T> type) throws SQLException
		{
			if ( XdmNode.class != type )
				return m_resultSet.getObject(idx, type);
			try
			{
				SQLXML sx = m_resultSet.getObject(idx, SQLXML.class);
				return type.cast(
					m_dBuilder.build(sx.getSource((Class<Source>)null)));
			}
			catch ( SaxonApiException e )
			{
				throw new SQLException(e.getMessage(), "10000", e);
			}
		}

		class ContextItem extends Binding.ContextItem
		{
			final int m_idx;

			ContextItem(int index) { m_idx = index; }

			protected String implTypePG() throws SQLException
			{
				return m_rsmd.getColumnTypeName(m_idx);
			}

			protected int implTypeJDBC() throws SQLException
			{
				return m_rsmd.getColumnType(m_idx);
			}

			protected int implScale() throws SQLException
			{
				return m_rsmd.getScale(m_idx);
			}

			protected Object implValueJDBC() throws SQLException
			{
				return m_resultSet.getObject(m_idx);
			}

			protected <T> T implValueJDBC(Class<T> type) throws SQLException
			{
				return typedValueAtIndex(m_idx, type);
			}
		}

		class Parameter extends Binding.Parameter
		{
			final int m_idx;
			private ItemType m_typeXT;
			private CastingFunction m_atomCaster;
			private ItemType m_lastCastFrom;

			Parameter(String name, int index, boolean isInput)
			throws SQLException
			{
				super(name, isInput);
				m_idx = index;
			}

			@Override
			ItemType typeXT(XMLBinary enc) throws SQLException
			{
				if ( null != m_typeXT )
					return m_typeXT;

				ItemType it =
					mapSQLDataTypeToXMLSchemaDataType(this, enc, Nulls.ABSENT);
				if ( ! ItemType.ANY_ATOMIC_VALUE.subsumes(it) )
					return m_typeXT = it;

				if ( it.equals(ItemType.INTEGER) )
				{
					int tj = typeJDBC();
					if ( Types.NUMERIC == tj || Types.DECIMAL == tj )
						it = ItemType.DECIMAL;
				}
				else if ( ItemType.INTEGER.subsumes(it) )
					it = ItemType.INTEGER;
				else if ( ItemType.FLOAT.subsumes(it) )
					it = ItemType.DOUBLE;
				else if ( ItemType.DATE_TIME_STAMP.subsumes(it) )
					it = ItemType.DATE_TIME;

				return m_typeXT = it;
			}

			@Override
			CastingFunction atomicCaster(ItemType it, CasterSupplier s)
			throws SQLException, XPathException
			{
				if ( null == m_atomCaster || ! it.equals(m_lastCastFrom) )
				{
					m_atomCaster = s.get();
					m_lastCastFrom = it;
				}
				return m_atomCaster;
			}

			protected String implTypePG() throws SQLException
			{
				return m_rsmd.getColumnTypeName(m_idx);
			}

			protected int implTypeJDBC() throws SQLException
			{
				return m_rsmd.getColumnType(m_idx);
			}

			protected boolean implKnownNonNull() throws SQLException
			{
				return columnNoNulls == m_rsmd.isNullable(m_idx);
			}

			protected int implScale() throws SQLException
			{
				return m_rsmd.getScale(m_idx);
			}

			protected Object implValueJDBC() throws SQLException
			{
				return m_resultSet.getObject(m_idx);
			}

			protected <T> T implValueJDBC(Class<T> type) throws SQLException
			{
				return typedValueAtIndex(m_idx, type);
			}
		}
	}

	static class BindingsFromXQX extends Binding.Assemblage
	{
		/**
		 * Construct a new assemblage of bindings for the static context of an
		 * XMLTABLE column expression. It will have the same named-parameter
		 * bindings passed to the row expression, but the static type of the
		 * context item will be the result type of the row expression. The
		 * {@code ContextItem} in this assemblage will have no associated value;
		 * the caller is responsible for retrieving that from the row evaluator
		 * and storing it in the column expression context every iteration.
		 * @param xqx The result of compiling the row expression; its
		 * compiler-determined static result type will be used as the static
		 * context item type.
		 * @param params The bindings supplied to the row expression. Its named
		 * parameters will be copied as the named parameters here.
		 */
		BindingsFromXQX(XQueryExecutable xqx, Binding.Assemblage params)
		{
			m_params = params.m_params;
			m_contextItem = new ContextItem(xqx.getResultItemType());
		}

		static class ContextItem extends Binding.ContextItem
		{
			ContextItem(ItemType it)
			{
				m_typeXS = it;
				/*
				 * There needs to be a dummy JDBC type to return when queried
				 * for purposes of assertCanCastAsXmlSequence. It can literally
				 * be any type outside of the few that method rejects. Because
				 * the XS type is already known, nothing else will need to ask
				 * for this, or care.
				 */
				m_typeJDBC = Types.OTHER;
			}
		}
	}

	/*
	 * The XQuery-regular-expression-based functions added in 9075-2:2006.
	 *
	 * For each function below, a parameter is marked //strict if the spec
	 * explicitly says the result is NULL when that parameter is NULL. The
	 * parameters not marked //strict (including the non-standard w3cNewlines
	 * added here) all have non-null defaults, so by executive decision, these
	 * functions will all get the onNullInput=RETURNS_NULL treatment, so none of
	 * the null-checking has to be done here. At worst, that may result in a
	 * mystery NULL return rather than an error, if someone explicitly passes
	 * NULL to one of the parameters with a non-null default.
	 */

	/*
	 * Check valid range of 'from' and supported 'usingOctets'.
	 *
	 * Every specified function that has a start position FROM and a USING
	 * clause starts with a check that the start position is in range. This
	 * function factors out that test, returning true if the start position is
	 * /out of range/ (triggering the caller to return the special result
	 * defined for that case), returning false if the value is in range, or
	 * throwing an exception if the length unit specified in the USING clause
	 * isn't supported.
	 */
	private static boolean usingAndLengthCheck(
		String in, int from, boolean usingOctets, String function)
	throws SQLException
	{
		if ( usingOctets )
			throw new SQLFeatureNotSupportedException(
				'"' + function + "\" does not yet support USING OCTETS",
				"0A000");
		return ( 1 > from  ||  from > getStringLength(in) );
	}

	private static void newlinesCheck(boolean w3cNewlines, String function)
	throws SQLException
	{
		if ( ! w3cNewlines )
			throw new SQLFeatureNotSupportedException(
				'"' + function + "\" does not yet support the ISO SQL newline" +
				" conventions, only the original W3C XQuery ones" +
				" (HINT: pass w3cNewlines => true)", "0A000");
	}

	private static RegularExpression compileRE(String pattern, String flags)
	throws SQLException
	{
		try
		{
			return s_s9p.getUnderlyingConfiguration()
				.compileRegularExpression(pattern, flags, "XP30", null);
		}
		catch ( XPathException e )
		{
			if ( NamespaceConstant.ERR.equals(e.getErrorCodeNamespace()) )
			{
				if ( "FORX0001".equals(e.getErrorCodeLocalPart()) )
					throw new SQLDataException(
						"invalid XQuery option flag", "2201T", e);
				if ( "FORX0002".equals(e.getErrorCodeLocalPart()) )
					throw new SQLDataException(
						"invalid XQuery regular expression", "2201S", e);
			}
			throw new SQLException(
				"compiling XQuery regular expression: " + e.getMessage(), e);
		}
	}

	private static CharSequence replace(
		RegularExpression re, CharSequence in, CharSequence with)
		throws SQLException
	{
		/*
		 * Report the standard-mandated error if replacing a zero-length match.
		 * Strictly speaking, this is a test of the length of the match, not of
		 * the input string. Here, though, this private method is only called by
		 * translate_regex, which always passes only the portion of the input
		 * string that matched, so the test is equivalent.
		 *  As to why the SQL committee would make such a point of disallowing
		 * replacement of a zero-length match, that's a good question. See
		 * s_intervalSignSite in this very file for an example where replacing
		 * a zero-length match is just what's wanted. (But that pattern relies
		 * on lookahead/lookbehind operators, which XQuery regular expressions
		 * don't have.)
		 *  When the underlying library is Saxon, there is an Easter egg: if a
		 * regular expression is compiled with a 'flags' string ending in ";j",
		 * a Java regular expression is produced instead of an XQuery one (with
		 * standards conformance cast to the wind). That can be detected with
		 * getFlags() on the regular expression: not looking for ";j", because
		 * that has been stripped out, but for "d" which is a Java regex flag
		 * that Saxon sets by default, and is not a valid XQuery regex flag.
		 *  If the caller has used Saxon's Easter egg to get a Java regex, here
		 * is another Easter egg to go with it, allowing zero-length matches
		 * to be replaced if that's what the caller wants to do.
		 */
		if ( 0 == in.length()  &&  ! re.getFlags().contains("d") )
			throw new SQLDataException(
				"attempt to replace a zero-length string", "2201U");
		try
		{
			return re.replace(in, with);
		}
		catch ( XPathException e )
		{
			if ( NamespaceConstant.ERR.equals(e.getErrorCodeNamespace()) )
			{
				if ( "FORX0003".equals(e.getErrorCodeLocalPart()) )
					throw new SQLDataException(
						"attempt to replace a zero-length string", "2201U", e);
				if ( "FORX0004".equals(e.getErrorCodeLocalPart()) )
					throw new SQLDataException(
						"invalid XQuery replacement string", "2201V", e);
			}
			throw new SQLException(
				"replacing regular expression match: " + e.getMessage(), e);
		}
	}

	interface MatchVector
	{
		int groups();
		int position(int group);
		int length(int group);
	}

	interface ListOfMatchVectors
	{
		/**
		 * Return the MatchVector for one occurrence of a match.
		 *<p>
		 * Any previously-returned MatchVector is invalid after another get.
		 * In multiple calls to get, the occurrence parameter must be strictly
		 * increasing.
		 * After get has returned null, it should not be called again.
		 */
		MatchVector get(int occurrence) throws SQLException;
		void close();
	}

	static class LOMV
	implements ListOfMatchVectors, MatchVector, RegexIterator.MatchHandler
	{
		private RegexIterator m_ri;
		private int m_pos;
		private int m_occurrence;

		LOMV(int startPos, RegexIterator ri)
		{
			m_ri = ri;
			m_pos = startPos;
		}

		static ListOfMatchVectors of(
			String pattern, String flags, String in, int from)
			throws SQLException
		{
			RegularExpression re = compileRE(pattern, flags);
			return of(re, in, from);
		}

		static ListOfMatchVectors of(RegularExpression re, String in, int from)
		{
			RegexIterator ri =
				re.analyze(in.substring(in.offsetByCodePoints(0, from - 1)));
			return new LOMV(from, ri);
		}

		private int[] m_begPositions;
		private int[] m_endPositions;

		@Override // ListOfMatchVectors
		public MatchVector get(int occurrence) throws SQLException
		{
			try
			{
				StringValue sv;
				for ( ;; )
				{
					sv = m_ri.next();
					if ( null == sv )
						return null;
					if ( m_ri.isMatching() )
						if ( ++ m_occurrence == occurrence )
							break;
					m_pos += sv.getStringLength();
				}

				if ( null == m_begPositions )
				{
					int groups = m_ri.getNumberOfGroups();
					/*
					 * Saxon's Apache-derived XQuery engine will report a number
					 * of groups counting $0 (so it will be 1 even if no capture
					 * groups were defined in the expression). In contrast, the
					 * Java regex engine that you get with the Saxon ";j" Easter
					 * egg does not count $0 (so arrays need groups+1 entries).
					 * It's hard to tell from here which flavor was used, plus
					 * the Saxon behavior might change some day, so just spend
					 * the extra + 1 every time.
					 */
					m_begPositions = new int [ groups + 1 ];
					m_endPositions = new int [ groups + 1 ];
				}

				m_begPositions [ 0 ] = m_pos;

				fill(m_begPositions, 1, m_begPositions.length, 0);
				fill(m_endPositions, 1, m_endPositions.length, 0);
				m_ri.processMatchingSubstring(this);

				m_endPositions [ 0 ] = m_pos;

				return this;
			}
			catch ( XPathException e )
			{
				throw new SQLException(
					"evaluating XQuery regular expression: " + e.getMessage(),
					e);
			}
		}

		@Override
		public void close()
		{
			m_ri.close();
		}

		@Override // MatchVector
		public int groups()
		{
			return m_begPositions.length - 1;
		}

		@Override
		public int position(int groupNumber)
		{
			return m_begPositions [ groupNumber ];
		}

		@Override
		public int length(int groupNumber)
		{
			return
				m_endPositions [ groupNumber ] - m_begPositions [ groupNumber ];
		}

		@Override // MatchHandler
		public void characters(CharSequence s)
		{
			m_pos += getStringLength(s);
		}

		@Override
		public void onGroupStart(int groupNumber)
		{
			m_begPositions [ groupNumber ] = m_pos;
		}

		@Override
		public void onGroupEnd(int groupNumber)
		{
			m_endPositions [ groupNumber ] = m_pos;
		}
	}

	/**
	 * Function form of the ISO SQL
	 * <a id='like_regex'>{@code <regex like predicate>}</a>.
	 *<p>
	 * Rewrite the standard form
	 *<pre>
	 * value LIKE_REGEX pattern FLAG flags
	 *</pre>
	 * into this form:
	 *<pre>
	 * like_regex(value, pattern, flag =&gt; flags)
	 *</pre>
	 * where the {@code flag} parameter defaults to no flags if omitted.
	 *<p>
	 * The SQL standard specifies that pattern elements sensitive to newlines
	 * (namely {@code ^}, {@code $}, {@code \s}, {@code \S}, and {@code .}) are
	 * to support the various representations of newline set out in
	 * <a href='http://www.unicode.org/reports/tr18/#RL1.6'>Unicode Technical
	 * Standard #18, RL1.6</a>. That behavior differs from the standard W3C
	 * XQuery newline handling, as described for
	 * <a href='https://www.w3.org/TR/xpath-functions-31/#flags'>the flags
	 * {@code m} and {@code s}</a> and for
	 * <a href='https://www.w3.org/TR/xmlschema11-2/#cces-mce'>the
	 * multicharacter escapes {@code \s} and {@code \S}</a>. As an extension to
	 * ISO SQL, passing {@code w3cNewlines => true} requests the standard W3C
	 * XQuery behavior rather than the UTS#18 behevior for newlines. If the
	 * underlying XQuery library only provides the W3C behavior, calls without
	 * {@code w3cNewlines => true} will throw exceptions.
	 * @param value The string to be tested against the pattern.
	 * @param pattern The XQuery regular expression.
	 * @param flag Optional string of
	 * <a href='https://www.w3.org/TR/xpath-functions-31/#flags'>flags adjusting
	 * the regular expression behavior</a>.
	 * @param w3cNewlines Pass true to allow the regular expression to recognize
	 * newlines according to the W3C XQuery rules rather than those of ISO SQL.
	 * @return True if the supplied value matches the pattern. Null if any
	 * parameter is null.
	 * @throws SQLException SQLDataException with SQLSTATE 2201S if the regular
	 * expression is invalid, 2201T if the flags string is invalid;
	 * SQLFeatureNotSupportedException (0A000) if (in the current
	 * implementation) w3cNewlines is false or omitted.
	 */
	@Function(schema="javatest")
	public static boolean like_regex(
		String value,                          //strict
		String pattern,                        //strict
		@SQLType(defaultValue="") String flag, //strict
		@SQLType(defaultValue="false") boolean w3cNewlines
	)
		throws SQLException
	{
		newlinesCheck(w3cNewlines, "like_regex");
		return compileRE(pattern, flag).containsMatch(value);
	}

	/**
	 * Syntax-sugar-free form of the ISO SQL
	 * <a id='occurrences_regex'>{@code OCCURRENCES_REGEX}</a> function:
	 * how many times does a pattern occur in a string?
	 *<p>
	 * Rewrite the standard form
	 *<pre>
	 * OCCURRENCES_REGEX(pattern FLAG flags IN str FROM position USING units)
	 *</pre>
	 * into this form:
	 *<pre>
	 * occurrences_regex(pattern, flag =&gt; flags, "in" =&gt; str,
	 *                   "from" =&gt; position, usingOctets =&gt; true|false)
	 *</pre>
	 * where all of the named parameters are optional except pattern and "in",
	 * and the standard {@code USING CHARACTERS} becomes
	 * {@code usingOctets => false}, which is the default, and
	 * {@code USING OCTETS} becomes {@code usingOctets => true}. See also
	 * {@link #like_regex like_regex} regarding the {@code w3cNewlines}
	 * parameter.
	 * @param pattern XQuery regular expression to seek in the input string.
	 * @param in The input string.
	 * @param flag Optional string of
	 * <a href='https://www.w3.org/TR/xpath-functions-31/#flags'>flags adjusting
	 * the regular expression behavior</a>.
	 * @param from Starting position in the input string, 1 by default.
	 * @param usingOctets Whether position is counted in characters (actual
	 * Unicode characters, not any smaller encoded unit, not even Java char),
	 * which is the default, or (when true) in octets of the string's encoded
	 * form.
	 * @param w3cNewlines Pass true to allow the regular expression to recognize
	 * newlines according to the W3C XQuery rules rather than those of ISO SQL.
	 * @return The number of occurrences of the pattern in the input string,
	 * starting from the specified position. Null if any parameter is null; -1
	 * if the start position is less than 1 or beyond the end of the string.
	 * @throws SQLException SQLDataException with SQLSTATE 2201S if the regular
	 * expression is invalid, 2201T if the flags string is invalid;
	 * SQLFeatureNotSupportedException (0A000) if (in the current
	 * implementation) usingOctets is true, or w3cNewlines is false or omitted.
	 */
	@Function(schema="javatest")
	public static int occurrences_regex(
		String pattern,                        //strict
		@SQLType(name="\"in\"") String in,     //strict
		@SQLType(defaultValue="") String flag, //strict
		@SQLType(name="\"from\"", defaultValue="1") int from,
		@SQLType(defaultValue="false") boolean usingOctets,
		@SQLType(defaultValue="false") boolean w3cNewlines
	)
		throws SQLException
	{
		if ( usingAndLengthCheck(in, from, usingOctets, "occurrences_regex") )
			return -1; // note: not the same as in position_regex!
		newlinesCheck(w3cNewlines, "occurrences_regex");

		ListOfMatchVectors lomv = LOMV.of(pattern, flag, in, from);

		for ( int i = 1 ;; ++ i )
			if ( null == lomv.get(i) )
				return i - 1;
	}

	/**
	 * Syntax-sugar-free form of the ISO SQL
	 * <a id='position_regex'>{@code POSITION_REGEX}</a> function:
	 * where does a pattern, or part of it, occur in a string?
	 *<p>
	 * Rewrite the standard forms
	 *<pre>
	 * POSITION_REGEX(START pattern FLAG flags IN str FROM position
	 *                OCCURRENCE n GROUP m)
	 * POSITION_REGEX(AFTER pattern FLAG flags IN str FROM position
	 *                OCCURRENCE n GROUP m)
	 *</pre>
	 * into these forms, respectively:
	 *<pre>
	 * position_regex(pattern, flag =&gt; flags, "in" =&gt; str,
	 *                "from" =&gt; position, occurrence =&gt; n,
	 *                "group" =&gt; m)
	 * position_regex(pattern, flag =&gt; flags, "in" =&gt; str,
	 *                "from" =&gt; position, occurrence =&gt; n,
	 *                "group" =&gt; m, after =&gt; true)
	 *</pre>
	 * where all of the named parameters are optional except pattern and "in".
	 * See also {@link #occurrences_regex occurrences_regex} regarding the
	 * {@code usingOctets} parameter, and {@link #like_regex like_regex}
	 * regarding {@code w3cNewlines}.
	 * @param pattern XQuery regular expression to seek in the input string.
	 * @param in The input string.
	 * @param flag Optional string of
	 * <a href='https://www.w3.org/TR/xpath-functions-31/#flags'>flags adjusting
	 * the regular expression behavior</a>.
	 * @param from Starting position in the input string, 1 by default.
	 * @param usingOctets Whether position is counted in characters (actual
	 * Unicode characters, not any smaller encoded unit, not even Java char),
	 * which is the default, or (when true) in octets of the string's encoded
	 * form.
	 * @param after Whether to return the position where the match starts
	 * (when false, the default), or just after the match ends (when true).
	 * @param occurrence If specified as an integer n (default 1), returns the
	 * position starting (or after) the nth match of the pattern in the string.
	 * @param group If zero (the default), returns the position starting (or
	 * after) the match of the whole pattern overall, otherwise if an integer m,
	 * the position starting or after the mth parenthesized group in (the nth
	 * occurrence of) the pattern.
	 * @param w3cNewlines Pass true to allow the regular expression to recognize
	 * newlines according to the W3C XQuery rules rather than those of ISO SQL.
	 * @return The position, in the specified units, starting or just after,
	 * the nth occurrence (or mth capturing group of the nth occurrence) of the
	 * pattern in the input string, starting from the specified position. Null
	 * if any parameter is null; zero if the start position is less than 1 or
	 * beyond the end of the string, if occurrence is less than 1 or greater
	 * than the number of matches, or if group is less than zero or greater than
	 * the number of parenthesized capturing groups in the pattern.
	 * @throws SQLException SQLDataException with SQLSTATE 2201S if the regular
	 * expression is invalid, 2201T if the flags string is invalid;
	 * SQLFeatureNotSupportedException (0A000) if (in the current
	 * implementation) usingOctets is true, or w3cNewlines is false or omitted.
	 */
	@Function(schema="javatest")
	public static int position_regex(
		String pattern,                                         //strict
		@SQLType(name="\"in\"") String in,                      //strict
		@SQLType(defaultValue="") String flag,                  //strict
		@SQLType(name="\"from\"", defaultValue="1") int from,
		@SQLType(defaultValue="false") boolean usingOctets,
		@SQLType(defaultValue="false") boolean after,
		@SQLType(defaultValue="1") int occurrence,              //strict
		@SQLType(name="\"group\"", defaultValue="0") int group, //strict
		@SQLType(defaultValue="false") boolean w3cNewlines
	)
		throws SQLException
	{
		if ( 1 > occurrence )
			return 0;
		if ( 0 > group ) // test group > ngroups after compiling regex
			return 0;
		if ( usingAndLengthCheck(in, from, usingOctets, "position_regex") )
			return 0; // note: not the same as in occurrences_regex!
		newlinesCheck(w3cNewlines, "position_regex");

		ListOfMatchVectors lomv = LOMV.of(pattern, flag, in, from);

		MatchVector mv = lomv.get(occurrence);
		if ( null == mv  ||  mv.groups() < group )
			return 0;

		return mv.position(group) + (after ? mv.length(group) : 0);
	}

	/**
	 * Syntax-sugar-free form of the ISO SQL
	 * <a id='substring_regex'>{@code SUBSTRING_REGEX}</a> function:
	 * return a substring specified by a pattern match in a string.
	 *<p>
	 * Rewrite the standard form
	 *<pre>
	 * SUBSTRING_REGEX(pattern FLAG flags IN str FROM position
	 *                 OCCURRENCE n GROUP m)
	 *</pre>
	 * into this form:
	 *<pre>
	 * substring_regex(pattern, flag =&gt; flags, "in" =&gt; str,
	 *                 "from" =&gt; position, occurrence =&gt; n,
	 *                 "group" =&gt; m)
	 *</pre>
	 * where all of the named parameters are optional except pattern and "in".
	 * See also {@link #position_regex position_regex} regarding the
	 * {@code occurrence} and {@code "group"} parameters,
	 * {@link #occurrences_regex occurrences_regex} regarding
	 * {@code usingOctets}, and {@link #like_regex like_regex}
	 * regarding {@code w3cNewlines}.
	 * @param pattern XQuery regular expression to seek in the input string.
	 * @param in The input string.
	 * @param flag Optional string of
	 * <a href='https://www.w3.org/TR/xpath-functions-31/#flags'>flags adjusting
	 * the regular expression behavior</a>.
	 * @param from Starting position in the input string, 1 by default.
	 * @param usingOctets Whether position is counted in characters (actual
	 * Unicode characters, not any smaller encoded unit, not even Java char),
	 * which is the default, or (when true) in octets of the string's encoded
	 * form.
	 * @param occurrence If specified as an integer n (default 1), returns the
	 * nth match of the pattern in the string.
	 * @param group If zero (the default), returns the match of the whole
	 * pattern overall, otherwise if an integer m, the match of the mth
	 * parenthesized group in (the nth occurrence of) the pattern.
	 * @param w3cNewlines Pass true to allow the regular expression to recognize
	 * newlines according to the W3C XQuery rules rather than those of ISO SQL.
	 * @return The substring matching the nth occurrence (or mth capturing group
	 * of the nth occurrence) of the pattern in the input string, starting from
	 * the specified position. Null if any parameter is null, if the start
	 * position is less than 1 or beyond the end of the string, if occurrence is
	 * less than 1 or greater than the number of matches, or if group is less
	 * than zero or greater than the number of parenthesized capturing groups in
	 * the pattern.
	 * @throws SQLException SQLDataException with SQLSTATE 2201S if the regular
	 * expression is invalid, 2201T if the flags string is invalid;
	 * SQLFeatureNotSupportedException (0A000) if (in the current
	 * implementation) usingOctets is true, or w3cNewlines is false or omitted.
	 */
	@Function(schema="javatest")
	public static String substring_regex(
		String pattern,                                          //strict
		@SQLType(name="\"in\"") String in,                       //strict
		@SQLType(defaultValue="") String flag,                   //strict
		@SQLType(name="\"from\"", defaultValue="1") int from,
		@SQLType(defaultValue="false") boolean usingOctets,
		@SQLType(defaultValue="1") int occurrence,               //strict
		@SQLType(name="\"group\"", defaultValue="0") int group,  //strict
		@SQLType(defaultValue="false") boolean w3cNewlines
	)
		throws SQLException
	{
		if ( 1 > occurrence )
			return null;
		if ( 0 > group ) // test group > ngroups after compiling regex
			return null;
		if ( usingAndLengthCheck(in, from, usingOctets, "substring_regex") )
			return null;
		newlinesCheck(w3cNewlines, "substring_regex");

		ListOfMatchVectors lomv = LOMV.of(pattern, flag, in, from);

		MatchVector mv = lomv.get(occurrence);
		if ( null == mv  ||  mv.groups() < group )
			return null;

		int codePointPos = mv.position(group);
		int codePointLen = mv.length(group);

		int utf16pos = in.offsetByCodePoints(0, codePointPos - 1);
		int utf16end = in.offsetByCodePoints(utf16pos, codePointLen);

		return in.substring(utf16pos, utf16end);
	}

	/**
	 * Syntax-sugar-free form of the ISO SQL
	 * <a id='translate_regex'>{@code TRANSLATE_REGEX}</a> function:
	 * return a string constructed from the input string by replacing one
	 * specified occurrence, or all occurrences, of a matching pattern.
	 *<p>
	 * Rewrite the standard forms
	 *<pre>
	 * TRANSLATE_REGEX(pattern FLAG flags IN str WITH repl FROM position
	 *                 OCCURRENCE ALL)
	 * TRANSLATE_REGEX(pattern FLAG flags IN str WITH repl FROM position
	 *                 OCCURRENCE n)
	 *</pre>
	 * into these forms, respectively:
	 *<pre>
	 * translate_regex(pattern, flag =&gt; flags, "in" =&gt; str,
	 *                 "with" =&gt; repl, "from" =&gt; position)
	 * translate_regex(pattern, flag =&gt; flags, "in" =&gt; str,
	 *                 "with" =&gt; repl, "from" =&gt; position,
	 *                 occurrence =&gt; n)
	 *</pre>
	 * where all of the named parameters are optional except pattern and "in"
	 * (the default for "with" is the empty string, resulting in matches being
	 * deleted).
	 * See also {@link #position_regex position_regex} regarding the
	 * {@code occurrence} parameter,
	 * {@link #occurrences_regex occurrences_regex} regarding
	 * {@code usingOctets}, and {@link #like_regex like_regex}
	 * regarding {@code w3cNewlines}.
	 *<p>
	 * For the specified occurrence (or all occurrences), the matching portion
	 * <em>s</em> of the string is replaced as by the XQuery function
	 * <a href='https://www.w3.org/TR/xpath-functions-31/#func-replace'
	 * >replace</a>(<em>s, pattern, repl, flags</em>). The <em>repl</em> string
	 * may contain {@code $0} to refer to the entire matched substring, or
	 * {@code $}<em>m</em> to refer to the <em>m</em>th parenthesized capturing
	 * group in the pattern.
	 * @param pattern XQuery regular expression to seek in the input string.
	 * @param in The input string.
	 * @param flag Optional string of
	 * <a href='https://www.w3.org/TR/xpath-functions-31/#flags'>flags adjusting
	 * the regular expression behavior</a>.
	 * @param with The replacement string, possibly with $m references.
	 * @param from Starting position in the input string, 1 by default.
	 * @param usingOctets Whether position is counted in characters (actual
	 * Unicode characters, not any smaller encoded unit, not even Java char),
	 * which is the default, or (when true) in octets of the string's encoded
	 * form.
	 * @param occurrence If specified as an integer n (default 0 for "ALL"),
	 * replace the nth match of the pattern in the string.
	 * @param w3cNewlines Pass true to allow the regular expression to recognize
	 * newlines according to the W3C XQuery rules rather than those of ISO SQL.
	 * @return The input string with one occurrence or all occurences of the
	 * pattern replaced, as described above. Null if any parameter is null, or
	 * if the start position is less than 1 or beyond the end of the string.
	 * The input string unchanged if occurrence is less than zero or exceeds the
	 * number of matches.
	 * @throws SQLException SQLDataException with SQLSTATE 2201S if the regular
	 * expression is invalid, 2201T if the flags string is invalid; 2201U if
	 * replacing where the pattern has matched a substring of zero length; 2201V
	 * if the replacement string has improper form (a backslash must be used to
	 * escape any dollar sign or backslash intended literally);
	 * SQLFeatureNotSupportedException (0A000) if (in the current
	 * implementation) usingOctets is true, or w3cNewlines is false or omitted.
	 */
	@Function(schema="javatest")
	public static String translate_regex(
		String pattern, 										 //strict
		@SQLType(name="\"in\"") String in,						 //strict
		@SQLType(defaultValue="") String flag,					 //strict
		@SQLType(name="\"with\"", defaultValue="") String with,  //strict
		@SQLType(name="\"from\"", defaultValue="1") int from,
		@SQLType(defaultValue="false") boolean usingOctets,
		@SQLType(defaultValue="0" /* ALL */) int occurrence,
		@SQLType(defaultValue="false") boolean w3cNewlines
	)
		throws SQLException
	{
		if ( usingAndLengthCheck(in, from, usingOctets, "translate_regex") )
			return null;
		newlinesCheck(w3cNewlines, "translate_regex");
		if ( 0 > occurrence )
			return in;

		RegularExpression re = compileRE(pattern, flag);

		ListOfMatchVectors lomv = LOMV.of(re, in, from);

		MatchVector mv;
		int codePointPos;
		int codePointLen;
		int utf16pos;
		int utf16end;

		if ( 0 < occurrence )
		{
			mv = lomv.get(occurrence);
			if ( null == mv )
				return in;

			codePointPos = mv.position(0);
			codePointLen = mv.length(0);

			utf16pos = in.offsetByCodePoints(0, codePointPos - 1);
			utf16end = in.offsetByCodePoints(utf16pos, codePointLen);

			return
				in.substring(0, utf16pos)
				+ replace(re, in.substring(utf16pos, utf16end), with)
				+ in.substring(utf16end);
		}

		StringBuilder sb = new StringBuilder();
		utf16end = 0;

		for ( int i = 1; null != (mv = lomv.get(i)); ++ i )
		{
			codePointPos = mv.position(0);
			codePointLen = mv.length(0);

			utf16pos = in.offsetByCodePoints(0, codePointPos - 1);

			sb.append(in.substring(utf16end, utf16pos));

			utf16end = in.offsetByCodePoints(utf16pos, codePointLen);

			sb.append(replace(re, in.substring(utf16pos, utf16end), with));
		}

		return sb.append(in.substring(utf16end)).toString();
	}
}
