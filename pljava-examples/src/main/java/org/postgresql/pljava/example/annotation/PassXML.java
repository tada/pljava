/*
 * Copyright (c) 2018-2021 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLData;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Types;

import java.sql.SQLDataException;
import java.sql.SQLException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import java.io.IOException;

import java.util.Map;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static javax.xml.transform.OutputKeys.ENCODING;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;

import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.postgresql.pljava.Adjusting;
import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.MappedUDT;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

import static org.postgresql.pljava.example.LoggerTest.logMessage;

/* Imports needed just for the SAX flavor of "low-level XML echo" below */
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.XMLReader;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.ext.LexicalHandler;

/* Imports needed just for the StAX flavor of "low-level XML echo" below */
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import org.xml.sax.SAXException;

/* Imports needed just for xmlTextNode below (serializing via SAX, StAX, DOM) */
import org.xml.sax.helpers.AttributesImpl;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;


/**
 * Class illustrating use of {@link SQLXML} to operate on XML data.
 *<p>
 * This class also serves as the mapping class for a composite type
 * {@code javatest.onexml}, the better to verify that {@link SQLData}
 * input/output works too. That's why it has to implement SQLData.
 *<p>
 * Everything mentioning the type XML here needs a conditional implementor tag
 * in case of being loaded into a PostgreSQL instance built without that type.
 */
@SQLAction(provides="postgresql_xml", install=
	"SELECT CASE (SELECT 1 FROM pg_type WHERE typname = 'xml') WHEN 1" +
	" THEN set_config('pljava.implementors', 'postgresql_xml,' || " +
	" current_setting('pljava.implementors'), true) " +
	"END"
)

@SQLAction(implementor="postgresql_ge_80400",
	provides="postgresql_xml_ge84",
	install=
	"SELECT CASE (SELECT 1 FROM pg_type WHERE typname = 'xml') WHEN 1" +
	" THEN set_config('pljava.implementors', 'postgresql_xml_ge84,' || " +
	" current_setting('pljava.implementors'), true) " +
	"END"
)

@SQLAction(implementor="postgresql_xml_ge84", requires="echoXMLParameter",
	install=
	"WITH" +
	" s(how) AS (SELECT generate_series(1, 7))," +
	" t(x) AS (" +
	"  SELECT table_to_xml('pg_catalog.pg_operator', true, false, '')" +
	" )," +
	" r(howin, howout, isdoc) AS (" +
	"  SELECT" +
	"   i.how, o.how," +
	"   javatest.echoxmlparameter(x, i.how, o.how) IS DOCUMENT" +
	"  FROM" +
	"   t, s AS i, s AS o" +
	"  WHERE" +
	"   NOT (i.how = 6 and o.how = 7)" + // 6->7 unreliable in some JREs
	" ) " +
	"SELECT" +
	" CASE WHEN every(isdoc)" +
	"  THEN javatest.logmessage('INFO', 'SQLXML echos succeeded')" +
	"  ELSE javatest.logmessage('WARNING', 'SQLXML echos had problems')" +
	" END " +
	"FROM" +
	" r"
)

@SQLAction(implementor="postgresql_xml_ge84", requires="proxiedXMLEcho",
	install=
	"WITH" +
	" s(how) AS (SELECT unnest('{1,2,4,5,6,7}'::int[]))," +
	" t(x) AS (" +
	"  SELECT table_to_xml('pg_catalog.pg_operator', true, false, '')" +
	" )," +
	" r(how, isdoc) AS (" +
	"  SELECT" +
	"	how," +
	"	javatest.proxiedxmlecho(x, how) IS DOCUMENT" +
	"  FROM" +
	"	t, s" +
	" )" +
	"SELECT" +
	" CASE WHEN every(isdoc)" +
	"  THEN javatest.logmessage('INFO', 'proxied SQLXML echos succeeded')" +
	"  ELSE javatest.logmessage('WARNING'," +
	"       'proxied SQLXML echos had problems')" +
	" END " +
	"FROM" +
	" r"
)

@SQLAction(implementor="postgresql_xml_ge84", requires="lowLevelXMLEcho",
	install={
	"SELECT" +
	" preparexmlschema('schematest', $$" +
	"<xs:schema" +
	" xmlns:xs='http://www.w3.org/2001/XMLSchema'" +
	" targetNamespace='urn:testme'" +
	" elementFormDefault='qualified'>" +
	" <xs:element name='row'>" +
	"  <xs:complexType>" +
	"   <xs:sequence>" +
	"    <xs:element name='textcol' type='xs:string' nillable='true'/>" +
	"    <xs:element name='intcol' type='xs:integer' nillable='true'/>" +
	"   </xs:sequence>" +
	"  </xs:complexType>" +
	" </xs:element>" +
	"</xs:schema>" +
	"$$, 'http://www.w3.org/2001/XMLSchema', 5)",

	"WITH" +
	" s(how) AS (SELECT unnest('{4,5,7}'::int[]))," +
	" r(isdoc) AS (" +
	" SELECT" +
	"  javatest.lowlevelxmlecho(" +
	"   query_to_xml(" +
	"    'SELECT ''hi'' AS textcol, 1 AS intcol', true, true, 'urn:testme'"+
	"   ), how, params) IS DOCUMENT" +
	" FROM" +
	"  s," +
	"  (SELECT 'schematest' AS schema) AS params" +
	" )" +
	"SELECT" +
	" CASE WHEN every(isdoc)" +
	"  THEN javatest.logmessage('INFO', 'XML Schema tests succeeded')" +
	"  ELSE javatest.logmessage('WARNING'," +
	"       'XML Schema tests had problems')" +
	" END " +
	"FROM" +
	" r"
	}
)

@SQLAction(implementor="postgresql_xml",
		   requires={"prepareXMLTransform", "transformXML"},
	install={
		"REVOKE EXECUTE ON FUNCTION javatest.prepareXMLTransformWithJava" +
		" (pg_catalog.varchar, pg_catalog.xml, integer, boolean, boolean," +
		"  pg_catalog.RECORD)" +
		" FROM PUBLIC",

		"SELECT" +
		" javatest.prepareXMLTransform('distinctElementNames'," +
		"'<xsl:transform version=''1.0''" +
		" xmlns:xsl=''http://www.w3.org/1999/XSL/Transform''" +
		" xmlns:exsl=''http://exslt.org/common''" +
		" xmlns:set=''http://exslt.org/sets''" +
		" extension-element-prefixes=''exsl set''" +
		">" +
		" <xsl:output method=''xml'' indent=''no''/>" +
		" <xsl:template match=''/''>" +
		"  <xsl:variable name=''enames''>" +
		"   <xsl:for-each select=''//*''>" +
		"    <ename><xsl:value-of select=''local-name()''/></ename>" +
		"   </xsl:for-each>" +
		"  </xsl:variable>" +
		"  <xsl:for-each" +
		"   select=''set:distinct(exsl:node-set($enames)/ename)''>" +
		"   <xsl:sort select=''string()''/>" +
		"   <den><xsl:value-of select=''.''/></den>" +
		"  </xsl:for-each>" +
		" </xsl:template>" +
		"</xsl:transform>', how => 5, enableExtensionFunctions => true)",

		"SELECT" +
		" javatest.prepareXMLTransformWithJava('getPLJavaVersion'," +
		"'<xsl:transform version=''1.0''" +
		" xmlns:xsl=''http://www.w3.org/1999/XSL/Transform''" +
		" xmlns:java=''http://xml.apache.org/xalan/java''" +
		" exclude-result-prefixes=''java''" +
		">" +
		" <xsl:template match=''/''>" +
		"  <xsl:value-of" +
		"   select=''java:java.lang.System.getProperty(" +
		"    \"org.postgresql.pljava.version\")''" +
		"  />" +
		" </xsl:template>" +
		"</xsl:transform>', enableExtensionFunctions => true)",

		"SELECT" +
		" CASE WHEN" +
		"  javatest.transformXML('distinctElementNames'," +
		"   '<a><c/><e/><b/><b/><d/></a>', 5, 5)::text" +
		"  =" +
		"   '<den>a</den><den>b</den><den>c</den><den>d</den><den>e</den>'"+
		"  THEN javatest.logmessage('INFO', 'XSLT 1.0 test succeeded')" +
		"  ELSE javatest.logmessage('WARNING', 'XSLT 1.0 test failed')" +
		" END",

		"SELECT" +
		" CASE WHEN" +
		"  javatest.transformXML('getPLJavaVersion', '')::text" +
		"  OPERATOR(pg_catalog.=) extversion" +
		"  THEN javatest.logmessage('INFO', 'XSLT 1.0 with Java succeeded')" +
		"  ELSE javatest.logmessage('WARNING', 'XSLT 1.0 with Java failed')" +
		" END" +
		" FROM pg_catalog.pg_extension" +
		" WHERE extname = 'pljava'"
	}
)
@MappedUDT(schema="javatest", name="onexml", structure="c1 xml",
		   implementor="postgresql_xml",
           comment="A composite type mapped by the PassXML example class")
public class PassXML implements SQLData
{
	static SQLXML s_sx;

	static TransformerFactory s_tf = TransformerFactory.newDefaultInstance();

	static Map<String,Templates> s_tpls = new HashMap<>();

	static Map<String,Schema> s_schemas = new HashMap<>();

	@Function(schema="javatest", implementor="postgresql_xml")
	public static String inXMLoutString(SQLXML in) throws SQLException
	{
		return in.getString();
	}

	@Function(schema="javatest", implementor="postgresql_xml")
	public static SQLXML inStringoutXML(String in) throws SQLException
	{
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		SQLXML result = c.createSQLXML();
		result.setString(in);
		return result;
	}

	/**
	 * Echo an XML parameter back, exercising seven different ways
	 * (howin =&gt; 1-7) of reading an SQLXML object, and seven
	 * (howout =&gt; 1-7) of returning one.
	 *<p>
	 * If howin =&gt; 0, the XML parameter is simply saved in a static. It can
	 * be read in a subsequent call with sx =&gt; null, but only in the same
	 * transaction.
	 *<p>
	 * The "echoing" is done (in the {@code echoXML} method below) using a
	 * {@code Transformer}, that is, the "TrAX" Transformation API for XML
	 * supplied in Java. It illustrates how an identity {@code Transformer} can
	 * be used to get the XML content from the source to the result for any of
	 * the APIs selectable by howin and howout.
	 *<p>
	 * It also illustrates something else. When using StAX (6 for howin
	 * or howout) and XML of the {@code CONTENT} flavor (multiple top-level
	 * elements, characters outside the top element, etc.), it is easy to
	 * construct examples that fail. The fault is not really with the StAX API,
	 * nor with TrAX proper, but with the small handful of bridge classes that
	 * were added to the JRE with StAX's first appearance, to make it
	 * interoperate with TrAX. It is not that those classes completely overlook
	 * the {@code CONTENT} case: they make some efforts to handle it. Just not
	 * the right ones, and given the Java developers' usual reluctance to change
	 * such longstanding behavior, that's probably not getting fixed.
	 *<p>
	 * Moral: StAX is a nice API, have no fear to use it directly in
	 * freshly-developed code, but: when using TrAX, make every effort to supply
	 * a {@code Transformer} with {@code Source} and {@code Result} objects of
	 * <em>any</em> kind other than StAX.
	 */
	@Function(schema="javatest", implementor="postgresql_xml",
			  provides="echoXMLParameter")
	public static SQLXML echoXMLParameter(SQLXML sx, int howin, int howout)
	throws SQLException
	{
		if ( null == sx )
			sx = s_sx;
		if ( 0 == howin )
		{
			s_sx = sx;
			return null;
		}
		return echoSQLXML(sx, howin, howout);
	}

	/**
	 * Echo an XML parameter back, but with parameter and return types of
	 * PostgreSQL {@code text}.
	 *<p>
	 * The other version of this method needs a conditional implementor tag
	 * because it cannot be declared in a PostgreSQL instance that was built
	 * without {@code libxml} support and the PostgreSQL {@code XML} type.
	 * But this version can, simply by mapping the {@code SQLXML} parameter
	 * and return types to the SQL {@code text} type. The Java code is no
	 * different.
	 *<p>
	 * Note that it's possible for both declarations to coexist in PostgreSQL
	 * (because as far as it is concerned, their signatures are different), but
	 * these two Java methods cannot have the same name (because they differ
	 * only in annotations, not in the declared Java types). So, this one needs
	 * a slightly tweaked name, and a {@code name} attribute in the annotation
	 * so PostgreSQL sees the right name.
	 */
	@Function(schema="javatest", name="echoXMLParameter", type="text")
	public static SQLXML echoXMLParameter_(
		@SQLType("text") SQLXML sx, int howin, int howout)
	throws SQLException
	{
		return echoXMLParameter(sx, howin, howout);
	}

	/**
	 * "Echo" an XML parameter not by creating a new writable {@code SQLXML}
	 * object at all, but simply returning the passed-in readable one untouched.
	 */
	@Function(schema="javatest", implementor="postgresql_xml")
	public static SQLXML bounceXMLParameter(SQLXML sx) throws SQLException
	{
		return sx;
	}

	/**
	 * Just like {@link bounceXMLParameter} but with parameter and return typed
	 * as {@code text}, and so usable on a PostgreSQL instance lacking the XML
	 * type.
	 */
	@Function(schema="javatest", type="text", name="bounceXMLParameter")
	public static SQLXML bounceXMLParameter_(@SQLType("text") SQLXML sx)
	throws SQLException
	{
		return sx;
	}

	/**
	 * Just like {@link bounceXMLParameter} but with the parameter typed as
	 * {@code text} and the return type left as XML, so functions as a cast.
	 *<p>
	 * Slower than the other cases, because it must verify that the input really
	 * is XML before blindly calling it a PostgreSQL XML type. But the speed
	 * compares respectably to PostgreSQL's own CAST(text AS xml), at least for
	 * larger values; I am seeing Java pull ahead right around 32kB of XML data
	 * and beat PG by a factor of 2 or better at sizes of 1 or 2 MB.
	 * Unsurprisingly, PG has the clear advantage when values are very short.
	 */
	@Function(schema="javatest", implementor="postgresql_xml")
	public static SQLXML castTextXML(@SQLType("text") SQLXML sx)
	throws SQLException
	{
		return sx;
	}

	/**
	 * Precompile an XSL transform {@code source} and save it (for the
	 * current session) as {@code name}.
	 *<p>
	 * Each value of {@code how}, 1-7, selects a different way of presenting
	 * the {@code SQLXML} object to the XSL processor.
	 *<p>
	 * Passing {@code true} for {@code enableExtensionFunctions} allows the
	 * transform to use extensions that the Java XSLT implementation supports,
	 * such as functions from EXSLT. Those are disabled by default.
	 *<p>
	 * Passing {@code false} for {@code builtin} will allow a
	 * {@code TransformerFactory} other than Java's built-in one to be found
	 * using the usual search order and the context class loader (normally
	 * the PL/Java class path for the schema where this function is declared).
	 * The default of {@code true} ensures that the built-in Java XSLT 1.0
	 * implementation is used. A transformer implementation other than Xalan
	 * may not recognize the feature controlled by
	 * {@code enableExtensionFunctions}, so failure to configure that feature
	 * will be logged as a warning if {@code builtin} is {@code false}, instead
	 * of thrown as an exception.
	 *<p>
	 * Out of the box, Java's transformers only support XSLT 1.0. See the S9
	 * example for more capabilities (at the cost of downloading the Saxon jar).
	 */
	@Function(schema="javatest", implementor="postgresql_xml",
			  provides="prepareXMLTransform")
	public static void prepareXMLTransform(String name, SQLXML source,
		@SQLType(defaultValue="0") int how,
		@SQLType(defaultValue="false") boolean enableExtensionFunctions,
		@SQLType(defaultValue="true") boolean builtin,
		@SQLType(defaultValue={}) ResultSet adjust)
	throws SQLException
	{
		prepareXMLTransform(
			name, source, how, enableExtensionFunctions, adjust, builtin,
			/* withJava */ false);
	}

	/**
	 * Precompile an XSL transform {@code source} and save it (for the
	 * current session) as {@code name}, where the transform may call Java
	 * methods.
	 *<p>
	 * Otherwise identical to {@code prepareXMLTransform}, this version sets the
	 * {@code TransformerFactory}'s {@code extensionClassLoader} (to the context
	 * class loader, normally the PL/Java class path for the schema where this
	 * function is declared), so the transform will be able to use
	 * xalan's Java call syntax to call any public Java methods that would be
	 * accessible to this class. (That can make a big difference in usefulness
	 * for the otherwise rather limited XSLT 1.0.)
	 *<p>
	 * As with {@code enableExtensionFunctions}, failure by the transformer
	 * implementation to recognize or allow the {@code extensionClassLoader}
	 * property will be logged as a warning if {@code builtin} is {@code false},
	 * rather than thrown as an exception.
	 *<p>
	 * This example function will be installed with {@code EXECUTE} permission
	 * revoked from {@code PUBLIC}, as it essentially confers the ability to
	 * create arbitrary new Java functions, so should only be granted to roles
	 * you would be willing to grant {@code USAGE ON LANGUAGE java}.
	 *<p>
	 * Because this function only prepares the transform, and
	 * {@link #transformXML transformXML} applies it, there is some division of
	 * labor in determining what limits apply to its behavior. The use of this
	 * method instead of {@code prepareXMLTransform} determines whether the
	 * transform is allowed to see external Java methods at all; it will be
	 * the policy permissions granted to {@code transformXML} that control what
	 * those methods can do when the transform is applied. For now, that method
	 * is defined in the trusted/sandboxed {@code java} language, so this
	 * function could reasonably be granted to any role with {@code USAGE} on
	 * {@code java}. If, by contrast, {@code transformXML} were declared in the
	 * 'untrusted' {@code javaU}, it would be prudent to allow only superusers
	 * access to this function, just as only they can {@code CREATE FUNCTION} in
	 * an untrusted language.
	 */
	@Function(schema="javatest", implementor="postgresql_xml",
			  provides="prepareXMLTransform")
	public static void prepareXMLTransformWithJava(String name, SQLXML source,
		@SQLType(defaultValue="0") int how,
		@SQLType(defaultValue="false") boolean enableExtensionFunctions,
		@SQLType(defaultValue="true") boolean builtin,
		@SQLType(defaultValue={}) ResultSet adjust)
	throws SQLException
	{
		prepareXMLTransform(
			name, source, how, enableExtensionFunctions, adjust, builtin,
			/* withJava */ true);
	}

	private static void prepareXMLTransform(String name, SQLXML source, int how,
		boolean enableExtensionFunctions, ResultSet adjust, boolean builtin,
		boolean withJava)
	throws SQLException
	{
		TransformerFactory tf =
			builtin
			? TransformerFactory.newDefaultInstance()
			: TransformerFactory.newInstance();
		String exf =
		  "http://www.oracle.com/xml/jaxp/properties/enableExtensionFunctions";
		String ecl = "jdk.xml.transform.extensionClassLoader";
		Source src = sxToSource(source, how, adjust);
		try
		{
			try
			{
				tf.setFeature(exf, enableExtensionFunctions);
			}
			catch ( TransformerConfigurationException e )
			{
				logMessage("WARNING",
					"non-builtin transformer: ignoring " + e.getMessage());
			}

			if ( withJava )
			{
				try
				{
					tf.setAttribute(ecl,
						Thread.currentThread().getContextClassLoader());
				}
				catch ( IllegalArgumentException e )
				{
					logMessage("WARNING",
					"non-builtin transformer: ignoring " + e.getMessage());
				}
			}

			s_tpls.put(name, tf.newTemplates(src));
		}
		catch ( TransformerException te )
		{
			throw new SQLException(
				"Preparing XML transformation: " + te.getMessage(), te);
		}
	}

	/**
	 * Transform some XML according to a named transform prepared with
	 * {@code prepareXMLTransform}.
	 *<p>
	 * Pass null for {@code transformName} to get a plain identity transform
	 * (not such an interesting thing to do, unless you also specify indenting).
	 */
	@Function(schema="javatest", implementor="postgresql_xml",
			  provides="transformXML")
	public static SQLXML transformXML(
		String transformName, SQLXML source,
		@SQLType(defaultValue="0") int howin,
		@SQLType(defaultValue="0") int howout,
		@SQLType(defaultValue={}) ResultSet adjust,
		@SQLType(optional=true) Boolean indent,
		@SQLType(optional=true) Integer indentWidth)
	throws SQLException
	{
		Templates tpl = null == transformName? null: s_tpls.get(transformName);
		Source src = sxToSource(source, howin, adjust);

		if ( Boolean.TRUE.equals(indent)  &&  0 == howout )
			howout = 4; // transformer only indents if writing a StreamResult

		Connection c = DriverManager.getConnection("jdbc:default:connection");
		SQLXML result = c.createSQLXML();
		Result rlt = sxToResult(result, howout, adjust);

		try
		{
			Transformer t =
				null == tpl ? s_tf.newTransformer() : tpl.newTransformer();
			/*
			 * For the non-SAX/StAX/DOM flavors of output, you're responsible
			 * for setting the Transformer to use the server encoding.
			 */
			if ( rlt instanceof StreamResult )
				t.setOutputProperty(ENCODING,
					System.getProperty("org.postgresql.server.encoding"));
			else if ( Boolean.TRUE.equals(indent) )
				logMessage("WARNING",
					"indent requested, but howout specifies a non-stream " +
					"Result type; no indenting will happen");

			if ( null != indent )
				t.setOutputProperty("indent", indent ? "yes" : "no");
			if ( null != indentWidth )
				t.setOutputProperty(
					"{http://xml.apache.org/xalan}indent-amount",
					"" + indentWidth);

			t.transform(src, rlt);
		}
		catch ( TransformerException te )
		{
			throw new SQLException("Transforming XML: " + te.getMessage(), te);
		}

		return ensureClosed(rlt, result, howout);
	}

	/**
	 * Precompile a schema {@code source} in schema language {@code lang}
	 * and save it (for the current session) as {@code name}.
	 *<p>
	 * Each value of {@code how}, 1-7, selects a different way of presenting
	 * the {@code SQLXML} object to the schema parser.
	 *<p>
	 * The {@code lang} parameter is a URI that identifies a known schema
	 * language. The only language a Java runtime is required to support is
	 * W3C XML Schema 1.0, with URI {@code http://www.w3.org/2001/XMLSchema}.
	 */
	@Function(schema="javatest", implementor="postgresql_xml")
	public static void prepareXMLSchema(
		String name, SQLXML source, String lang, int how)
	throws SQLException
	{
		try
		{
			s_schemas.put(name,
				SchemaFactory.newInstance(lang)
				.newSchema(sxToSource(source, how)));
		}
		catch ( SAXException e )
		{
			throw new SQLException(
				"failed to prepare schema: " + e.getMessage(), e);
		}
	}

	private static SQLXML echoSQLXML(SQLXML sx, int howin, int howout)
	throws SQLException
	{
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		SQLXML rx = c.createSQLXML();
		Source src = sxToSource(sx, howin);
		Result rlt = sxToResult(rx, howout);

		try
		{
			Transformer t = s_tf.newTransformer();
			/*
			 * For the non-SAX/StAX/DOM flavors of output, you're responsible
			 * for setting the Transformer to use the server encoding.
			 */
			if ( howout < 5 )
				t.setOutputProperty(ENCODING,
					System.getProperty("org.postgresql.server.encoding"));
			t.transform(src, rlt);
		}
		catch ( TransformerException te )
		{
			throw new SQLException("XML transformation failed", te);
		}

		return ensureClosed(rlt, rx, howout);
	}

	/**
	 * Echo the XML parameter back, using lower-level manipulations than
	 * {@code echoXMLParameter}.
	 *<p>
	 * This illustrates how the simple use of {@code t.transform(src,rlt)}
	 * in {@code echoSQLXML} substitutes for a lot of fiddly case-by-case code,
	 * but when coding for a specific case, all the generality of {@code
	 * transform} may not be needed. It can be interesting to compare memory use
	 * when XML values are large.
	 *<p>
	 * This method has been revised to demonstrate, even for low-level
	 * manipulations, how much fiddliness can now be avoided through use of the
	 * {@link Adjusting.XML.SourceResult} class, and how to make adjustments to
	 * parsing restrictions by passing the optional row-typed parameter
	 * <em>adjust</em>, which defaults to an empty row. For example, passing
	 *<pre>
	 * adjust =&gt; (select a from
	 *            (true as allowdtd, true as expandentityreferences) as a)
	 *</pre>
	 * would allow a document that contains an internal DTD subset and uses
	 * entities defined there.
	 *<p>
	 * The older, pre-{@code SourceResult} code for doing low-level XML echo
	 * has been moved to the {@code oldSchoolLowLevelEcho} method below. It can
	 * still be exercised by calling this method, explicitly passing
	 * {@code adjust => NULL}.
	 */
	@Function(schema="javatest", implementor="postgresql_xml_ge84",
		provides="lowLevelXMLEcho")
	public static SQLXML lowLevelXMLEcho(
		SQLXML sx, int how, @SQLType(defaultValue={}) ResultSet adjust)
	throws SQLException
	{
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		SQLXML rx = c.createSQLXML();

		if ( null == adjust )
			return oldSchoolLowLevelEcho(rx, sx, how);

		Adjusting.XML.SourceResult axsr =
			rx.setResult(Adjusting.XML.SourceResult.class);

		switch ( how )
		{
		/*
		 * The first four cases all present the content as unparsed bytes or
		 * characters, so there is nothing to adjust on the source side.
		 */
		case 1:
			axsr.set(new StreamSource(sx.getBinaryStream()));
			break;
		case 2:
			axsr.set(new StreamSource(sx.getCharacterStream()));
			break;
		case 3:
			axsr.set(sx.getString());
			break;
		case 4:
			axsr.set(sx.getSource(StreamSource.class));
			break;
		/*
		 * The remaining cases present the content in parsed form, and therefore
		 * may involve parsers that can be adjusted according to the supplied
		 * preferences.
		 */
		case 5:
			axsr.set(applyAdjustments(adjust,
				sx.getSource(Adjusting.XML.SAXSource.class)));
			break;
		case 6:
			axsr.set(applyAdjustments(adjust,
				sx.getSource(Adjusting.XML.StAXSource.class)));
			break;
		case 7:
			axsr.set(applyAdjustments(adjust,
				sx.getSource(Adjusting.XML.DOMSource.class)));
			break;
		default:
			throw new SQLDataException(
				"how must be 1-7 for lowLevelXMLEcho", "22003");
		}

		/*
		 * Adjustments can also be applied to the SourceResult itself, where
		 * they will affect any implicitly-created parser used to verify or
		 * re-encode the content, if it was supplied in unparsed form.
		 */
		return applyAdjustments(adjust, axsr).get().getSQLXML();
	}

	/**
	 * Apply adjustments (supplied as a row type with a named column for each
	 * desired adjustment and its value) to an instance of
	 * {@link Adjusting.XML.Parsing}.
	 *<p>
	 * Column names in the <em>adjust</em> row are case-insensitive versions of
	 * the method names in {@link Adjusting.XML.Parsing}, and the value of each
	 * column should be of the appropriate type (at present, boolean for all of
	 * them).
	 * @param adjust A row type as described above, possibly of no columns if no
	 * adjustments are wanted
	 * @param axp An instance of Adjusting.XML.Parsing
	 * @return axp, after applying any adjustments
	 */
	public static <T extends Adjusting.XML.Parsing<? super T>>
	T applyAdjustments(ResultSet adjust, T axp)
	throws SQLException
	{
		ResultSetMetaData rsmd = adjust.getMetaData();
		int n = rsmd.getColumnCount();

		for ( int i = 1; i <= n; ++i )
		{
			String k = rsmd.getColumnLabel(i);
			if ( "allowDTD".equalsIgnoreCase(k) )
				axp.allowDTD(adjust.getBoolean(i));
			else if ( "externalGeneralEntities".equalsIgnoreCase(k) )
				axp.externalGeneralEntities(adjust.getBoolean(i));
			else if ( "externalParameterEntities".equalsIgnoreCase(k) )
				axp.externalParameterEntities(adjust.getBoolean(i));
			else if ( "loadExternalDTD".equalsIgnoreCase(k) )
				axp.loadExternalDTD(adjust.getBoolean(i));
			else if ( "xIncludeAware".equalsIgnoreCase(k) )
				axp.xIncludeAware(adjust.getBoolean(i));
			else if ( "expandEntityReferences".equalsIgnoreCase(k) )
				axp.expandEntityReferences(adjust.getBoolean(i));
			else if ( "elementAttributeLimit".equalsIgnoreCase(k) )
				axp.elementAttributeLimit(adjust.getInt(i));
			else if ( "entityExpansionLimit".equalsIgnoreCase(k) )
				axp.entityExpansionLimit(adjust.getInt(i));
			else if ( "entityReplacementLimit".equalsIgnoreCase(k) )
				axp.entityReplacementLimit(adjust.getInt(i));
			else if ( "maxElementDepth".equalsIgnoreCase(k) )
				axp.maxElementDepth(adjust.getInt(i));
			else if ( "maxGeneralEntitySizeLimit".equalsIgnoreCase(k) )
				axp.maxGeneralEntitySizeLimit(adjust.getInt(i));
			else if ( "maxParameterEntitySizeLimit".equalsIgnoreCase(k) )
				axp.maxParameterEntitySizeLimit(adjust.getInt(i));
			else if ( "maxXMLNameLimit".equalsIgnoreCase(k) )
				axp.maxXMLNameLimit(adjust.getInt(i));
			else if ( "totalEntitySizeLimit".equalsIgnoreCase(k) )
				axp.totalEntitySizeLimit(adjust.getInt(i));
			else if ( "accessExternalDTD".equalsIgnoreCase(k) )
				axp.accessExternalDTD(adjust.getString(i));
			else if ( "accessExternalSchema".equalsIgnoreCase(k) )
				axp.accessExternalSchema(adjust.getString(i));
			else if ( "schema".equalsIgnoreCase(k) )
			{
				try
				{
					axp.schema(s_schemas.get(adjust.getString(i)));
				}
				catch (UnsupportedOperationException e)
				{
				}
			}
			else
				throw new SQLDataException(
					"unrecognized name \"" + k + "\" for parser adjustment",
					"22000");
		}
		return axp;
	}

	/**
	 * An obsolescent example, showing what was required to copy from one
	 * {@code SQLXML} object to another, using the various supported APIs,
	 * without using {@link Adjusting.XML.SourceResult}, or at least without
	 * using it much. It is still used in case 4 to be sure of getting a
	 * {@code StreamResult} that matches the byte-or-character-ness of the
	 * {@code StreamSource}. How to handle that case without
	 * {@code SourceResult} is left as an exercise.
	 */
	private static SQLXML oldSchoolLowLevelEcho(SQLXML rx, SQLXML sx, int how)
	throws SQLException
	{
		try
		{
			switch ( how )
			{
			case 1:
				InputStream is = sx.getBinaryStream();
				OutputStream os = rx.setBinaryStream();
				shovelBytes(is, os);
				break;
			case 2:
				Reader r = sx.getCharacterStream();
				Writer w = rx.setCharacterStream();
				shovelChars(r, w);
				break;
			case 3:
				rx.setString(sx.getString());
				break;
			case 4:
				StreamSource ss = sx.getSource(StreamSource.class);
				Adjusting.XML.StreamResult sr =
					rx.setResult(Adjusting.XML.StreamResult.class);
				is = ss.getInputStream();
				r  = ss.getReader();
				if ( null != is )
				{
					os = sr.preferBinaryStream().get().getOutputStream();
					shovelBytes(is, os);
					break;
				}
				if ( null != r )
				{
					w  = sr.preferCharacterStream().get().getWriter();
					shovelChars(r, w);
					break;
				}
				throw new SQLDataException(
					"StreamSource contained neither InputStream nor Reader");
			case 5:
				SAXSource sxs = sx.getSource(SAXSource.class);
				SAXResult sxr = rx.setResult(SAXResult.class);
				XMLReader xr  = sxs.getXMLReader();
				if ( null == xr )
				{
					SAXParserFactory spf = SAXParserFactory.newInstance();
					spf.setNamespaceAware(true);
					xr = spf.newSAXParser().getXMLReader();
					/*
					 * Important: before copying this example code for another
					 * use, consider whether the input XML might be untrusted.
					 * If so, the new XMLReader created here should have several
					 * features given safe default settings as outlined in the
					 * OWASP guidelines. (This branch is not reached when sx is
					 * a PL/Java native SQLXML instance, as xr will be non-null
					 * and already configured.)
					 */
				}
				ContentHandler ch = sxr.getHandler();
				xr.setContentHandler(ch);
				if ( ch instanceof DTDHandler )
					xr.setDTDHandler((DTDHandler)ch);
				LexicalHandler lh = sxr.getLexicalHandler();
				if ( null == lh  &&  ch instanceof LexicalHandler )
				lh = (LexicalHandler)ch;
				if ( null != lh )
					xr.setProperty(
						"http://xml.org/sax/properties/lexical-handler", lh);
				xr.parse(sxs.getInputSource());
				break;
			case 6:
				StAXSource sts = sx.getSource(StAXSource.class);
				StAXResult str = rx.setResult(StAXResult.class);
				XMLOutputFactory xof = XMLOutputFactory.newInstance();
				/*
				 * The Source has either an event reader or a stream reader. Use
				 * the event reader directly, or create one around the stream
				 * reader.
				 */
				XMLEventReader xer = sts.getXMLEventReader();
				if ( null == xer )
				{
					XMLInputFactory  xif = XMLInputFactory .newInstance();
					xif.setProperty(xif.IS_NAMESPACE_AWARE, true);
					/*
					 * Important: before copying this example code for another
					 * use, consider whether the input XML might be untrusted.
					 * If so, the new XMLInputFactory created here might want
					 * several properties given safe default settings as
					 * outlined in the OWASP guidelines. (When sx is a PL/Java
					 * native SQLXML instance, the XMLStreamReader obtained
					 * below will already have been so configured.)
					 */
					xer = xif.createXMLEventReader(sts.getXMLStreamReader());
				}
				/*
				 * Were you thinking the above could be simply
				 * createXMLEventReader(sts) by analogy with the writer below?
				 * Good thought, but the XMLInputFactory implementation that's
				 * included in OpenJDK doesn't implement the case where the
				 * Source argument is a StAXSource! Two lines would do it.
				 */
				/*
				 * Because of a regression in Java 9 and later, the line below,
				 * while working in Java 8 and earlier, will produce a
				 * ClassCastException in Java 9 through (for sure) 12, (almost
				 * certainly) 13, and on until some future version fixes the
				 * regression, if ever, if 'str' wraps any XMLStreamWriter
				 * implementation other than the inaccessible one from the guts
				 * of the JDK itself. The bug has been reported but (as of this
				 * writing) is still in the maddening limbo phase of the Java
				 * bug reporting cycle, where no bug number can refer to it. See
				 * lowLevelXMLEcho() above for code to do this copy successfully
				 * using an Adjusting.XML.SourceResult.
				 */
				XMLEventWriter xew = xof.createXMLEventWriter(str);
				xew.add(xer);
				xew.close();
				xer.close();
				break;
			case 7:
				DOMSource ds = sx.getSource(DOMSource.class);
				DOMResult dr = rx.setResult(DOMResult.class);
				dr.setNode(ds.getNode());
				break;
			default:
				throw new SQLDataException(
					"how must be 1-7 for lowLevelXMLEcho", "22003");
			}
		}
		catch ( IOException e )
		{
			throw new SQLException(
				"IOException in lowLevelXMLEcho", "58030", e);
		}
		catch (
			ParserConfigurationException | SAXException | XMLStreamException e )
		{
			throw new SQLException(
				"XML exception in lowLevelXMLEcho", "22000", e);
		}
		return rx;
	}

	/**
	 * Proxy a PL/Java SQLXML source object as if it were of a non-PL/Java
	 * implementing class, to confirm that it can still be returned successfully
	 * to PostgreSQL.
	 * @param sx readable {@code SQLXML} object to proxy
	 * @param how 1,2,4,5,6,7 determines what subclass of {@code Source} will be
	 * returned by {@code getSource}.
	 */
	@Function(schema="javatest", implementor="postgresql_xml",
	          provides="proxiedXMLEcho")
	public static SQLXML proxiedXMLEcho(SQLXML sx, int how)
	throws SQLException
	{
		return new SQLXMLProxy(sx, how);
	}

	/**
	 * Supply a sequence of bytes to be the exact (encoded) content of an XML
	 * value, which will be returned; if the encoding is not UTF-8, the value
	 * should begin with an XML Decl that names the encoding.
	 *<p>
	 * Constructs an {@code SQLXML} instance that will return the supplied
	 * content as a {@code StreamSource} wrapping an {@code InputStream}, or via
	 * {@code getBinaryStream}, but fail if asked for any other form.
	 */
	@Function(schema="javatest", implementor="postgresql_xml",
	          provides="mockedXMLEchoB")
	public static SQLXML mockedXMLEcho(byte[] bytes)
	throws SQLException
	{
		return new SQLXMLMock(bytes);
	}

	/**
	 * Supply a sequence of characters to be the exact (Unicode) content of an
	 * XML value, which will be returned; if the value begins with an XML Decl
	 * that names an encoding, the content will be assumed to contain only
	 * characters representable in that encoding.
	 *<p>
	 * Constructs an {@code SQLXML} instance that will return the supplied
	 * content as a {@code StreamSource} wrapping a {@code Reader}, or via
	 * {@code getCharacterStream}, but fail if asked for any other form.
	 */
	@Function(schema="javatest", implementor="postgresql_xml",
	          provides="mockedXMLEchoC")
	public static SQLXML mockedXMLEcho(String chars)
	throws SQLException
	{
		return new SQLXMLMock(chars);
	}

	/**
	 * Text-typed variant of lowLevelXMLEcho (does not require XML type).
	 *<p>
	 * It does declare a parameter default, limiting it to PostgreSQL 8.4 or
	 * later.
	 */
	@Function(schema="javatest", name="lowLevelXMLEcho",
		type="text", implementor="postgresql_ge_80400")
	public static SQLXML lowLevelXMLEcho_(@SQLType("text") SQLXML sx, int how,
		@SQLType(defaultValue={}) ResultSet adjust)
	throws SQLException
	{
		return lowLevelXMLEcho(sx, how, adjust);
	}

	/**
	 * Low-level XML echo where the Java parameter and return type are String.
	 */
	@Function(schema="javatest", implementor="postgresql_xml", type="xml")
	public static String lowLevelXMLEcho(@SQLType("xml") String x)
	throws SQLException
	{
		return x;
	}

	/**
	 * Create some XML, pass it to a {@code SELECT ?} prepared statement,
	 * retrieve it from the result set, and return it via the out-parameter
	 * result set of this {@code RECORD}-returning function.
	 */
	@Function(schema="javatest", type="RECORD")
	public static boolean xmlInStmtAndRS(ResultSet out) throws SQLException
	{
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		SQLXML x = c.createSQLXML();
		x.setString("<a/>");
		PreparedStatement ps = c.prepareStatement("SELECT ?");
		ps.setObject(1, x, Types.SQLXML);
		ResultSet rs = ps.executeQuery();
		rs.next();
		if ( Types.SQLXML != rs.getMetaData().getColumnType(1) )
			logMessage("WARNING",
				"ResultSetMetaData.getColumnType() misreports SQLXML");
		x = rs.getSQLXML(1);
		ps.close();
		out.updateObject(1, x);
		return true;
	}

	/**
	 * Test serialization into the PostgreSQL server encoding by returning
	 * a text node, optionally wrapped in an element, containing the supplied
	 * stuff.
	 *<p>
	 * The stuff is supplied as a {@code bytea} and a named <em>encoding</em>,
	 * so it is easy to supply stuff that isn't in the server encoding and see
	 * what the serializer does with it.
	 *<p>
	 * As of this writing, if the <em>stuff</em>, decoded according to
	 * <em>encoding</em>, contains characters that are not representable in the
	 * server encoding, the serializers supplied in the JRE will:
	 *<ul>
	 *<li>SAX, DOM: replace the character with a numeric character reference if
	 * the node is wrapped in an element, but not outside of an element; there,
	 * PL/Java ensures an {@code UnmappableCharacterException} is thrown, as the
	 * serializer would otherwise silently lose information by replacing the
	 * character with a {@code ?}.
	 *<li>StAX: replace the character with a numeric character reference whether
	 * wrapped in an element or not (outside of an element, this officially
	 * violates the letter of the XML spec, but does not lose information, and
	 * is closer to the spirit of SQL/XML with its {@code XML(CONTENT)} type).
	 *</ul>
	 * @param stuff Content to be used in the text node
	 * @param encoding Name of an encoding; stuff will be decoded to Unicode
	 * according to this encoding, and then serialized into the server encoding,
	 * where possible.
	 * @param how Integer specifying which XML API to test, like every other how
	 * in this class; here the only valid choices are 5 (SAX), 6 (StAX), or
	 * 7 (DOM).
	 * @param inElement True if the text node should be wrapped in an element.
	 * @return The resulting XML content.
	 */
	@Function(schema="javatest", implementor="postgresql_xml")
	public static SQLXML xmlTextNode(
		byte[] stuff, String encoding, int how, boolean inElement)
		throws Exception
	{
		if ( 5 > how || how > 7 )
			throw new SQLDataException(
				"how must be 5-7 for xmlTextNode", "22003");

		String stuffString = new String(stuff, encoding);
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		SQLXML rx = c.createSQLXML();

		switch ( how )
		{
		case 5:
			SAXResult sxr = rx.setResult(SAXResult.class);
			sxr.getHandler().startDocument();
			if ( inElement )
				sxr.getHandler().startElement("", "sax", "sax",
					new AttributesImpl());
			sxr.getHandler().characters(
				stuffString.toCharArray(), 0, stuffString.length());
			if ( inElement )
				sxr.getHandler().endElement("", "sax", "sax");
			sxr.getHandler().endDocument();
			break;
		case 6:
			StAXResult stxr = rx.setResult(StAXResult.class);
			stxr.getXMLStreamWriter().writeStartDocument();
			if ( inElement )
				stxr.getXMLStreamWriter().writeStartElement("", "stax", "");
			stxr.getXMLStreamWriter().writeCharacters(stuffString);
			if ( inElement )
				stxr.getXMLStreamWriter().writeEndElement();
			stxr.getXMLStreamWriter().writeEndDocument();
			break;
		case 7:
			DOMResult dr = rx.setResult(DOMResult.class);
			/*
			 * Why request features XML and Traversal?
			 * If the only features requested are from the set
			 * {Core, XML, LS} and maybe XPath, you get a brain-damaged
			 * DOMImplementation that violates the org.w3c.dom.DOMImplementation
			 * contract, as createDocument still tries to make a document
			 * element even when passed null,null,null when, according to the
			 * contract, it should not. To get the real implementation that
			 * works, ask for some feature it supports outside of that core set.
			 * I don't really need Traversal, but by asking for it, I get what
			 * I do need.
			 */
			Document d = DOMImplementationRegistry.newInstance()
				.getDOMImplementation("XML Traversal")
				.createDocument(null, null, null);
			DocumentFragment df = d.createDocumentFragment();
			( inElement ? df.appendChild(d.createElement("dom")) : df )
				.appendChild(d.createTextNode(stuffString));
			dr.setNode(df);
			break;
		}
		return rx;
	}

	/**
	 * Create and leave some number of SQLXML objects unclosed, unused, and
	 * unreferenced, as a test of reclamation.
	 * @param howmany Number of SQLXML instances to create.
	 * @param how If nonzero, the flavor of writing to request on the object
	 * before abandoning it; if zero, it is left in its initial, writable state.
	 */
	@Function(schema="javatest")
	public static void unclosedSQLXML(int howmany, int how) throws SQLException
	{
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		while ( howmany --> 0 )
		{
			SQLXML sx = c.createSQLXML();
			if ( 0 < how )
				sxToResult(sx, how);
		}
	}


	/**
	 * Return some instance of {@code Source} for reading an {@code SQLXML}
	 * object, depending on the parameter {@code how}.
	 *<p>
	 * Note that this method always returns a {@code Source}, even for cases
	 * 1 and 2 (obtaining readable streams directly from the {@code SQLXML}
	 * object; this method wraps them in {@code Source}), and case 3
	 * ({@code getString}; this method creates a {@code StringReader} and
	 * returns it wrapped in a {@code Source}.
	 */
	private static Source sxToSource(SQLXML sx, int how) throws SQLException
	{
		switch ( how )
		{
			case  1: return new StreamSource(sx.getBinaryStream());
			case  2: return new StreamSource(sx.getCharacterStream());
			case  3: return new StreamSource(new StringReader(sx.getString()));
			case  4: return     sx.getSource(StreamSource.class);
			case  5: return     sx.getSource(SAXSource.class);
			case  6: return     sx.getSource(StAXSource.class);
			case  7: return     sx.getSource(DOMSource.class);
			default: throw new SQLDataException("how should be 1-7", "22003");
		}
	}

	/**
	 * Return some instance of {@code Result} for writing an {@code SQLXML}
	 * object, depending on the parameter {@code how}.
	 *<p>
	 * Note that this method always returns a {@code Result}, even for cases
	 * 1 and 2 (obtaining writable streams directly from the {@code SQLXML}
	 * object; this method wraps them in {@code Result}), and case 3
	 * ({@code setString}; this method creates a {@code StringWriter} and
	 * returns it wrapped in a {@code Result}.
	 *<p>
	 * In case 3, it will be necessary, after writing, to get the {@code String}
	 * from the {@code StringWriter}, and call {@code setString} with it.
	 */
	private static Result sxToResult(SQLXML sx, int how) throws SQLException
	{
		switch ( how )
		{
			case  1: return new StreamResult(sx.setBinaryStream());
			case  2: return new StreamResult(sx.setCharacterStream());
			case  3: return new StreamResult(new StringWriter());
			case  4: return     sx.setResult(StreamResult.class);
			case  5: return     sx.setResult(SAXResult.class);
			case  6: return     sx.setResult(StAXResult.class);
			case  7:
				DOMResult r = sx.setResult(DOMResult.class);
				allowFragment(r); // else it'll accept only DOCUMENT form
				return r;
			default: throw new SQLDataException("how should be 1-7", "22003");
		}
	}

	/**
	 * Return some instance of {@code Source} for reading an {@code SQLXML}
	 * object, depending on the parameter {@code how}, applying any adjustments
	 * in {@code adjust}.
	 *<p>
	 * Allows {@code how} to be zero, meaning to let the implementation choose
	 * what kind of {@code Source} to present. Otherwise identical to the other
	 * {@code sxToSource}.
	 */
	private static Source sxToSource(SQLXML sx, int how, ResultSet adjust)
	throws SQLException
	{
		Source s;
		switch ( how )
		{
			case  0: s = sx.getSource(Adjusting.XML.Source.class); break;
			case  1:
			case  2:
			case  3:
			case  4:
				return sxToSource(sx, how); // no adjustments on a StreamSource
			case  5: s = sx.getSource(Adjusting.XML.SAXSource.class); break;
			case  6: s = sx.getSource(Adjusting.XML.StAXSource.class); break;
			case  7: s = sx.getSource(Adjusting.XML.DOMSource.class); break;
			default: throw new SQLDataException("how should be 0-7", "22003");
		}

		if ( s instanceof Adjusting.XML.Source )
			return applyAdjustments(adjust, (Adjusting.XML.Source<?>)s).get();
		return s;
	}

	/**
	 * Return some instance of {@code Result} for writing an {@code SQLXML}
	 * object, depending on the parameter {@code how} applying any adjustments
	 * in {@code adjust}.
	 *<p>
	 * Allows {@code how} to be zero, meaning to let the implementation choose
	 * what kind of {@code Result} to present. Otherwise identical to the other
	 * {@code sxToResult}.
	 */
	private static Result sxToResult(SQLXML sx, int how, ResultSet adjust)
	throws SQLException
	{
		Result r;
		switch ( how )
		{
			case  1: // you might wish you could adjust a raw BinaryStream
			case  2: // or CharacterStream
			case  3: // or String, but you can't. Ask for a StreamResult.
			case  5: // SAXResult needs no adjustment
			case  6: // StAXResult needs no adjustment
			case  7: // DOMResult needs no adjustment
				return sxToResult(sx, how);
			case  4: r = sx.setResult(Adjusting.XML.StreamResult.class); break;
			case  0: r = sx.setResult(Adjusting.XML.Result.class); break;
			default: throw new SQLDataException("how should be 0-7", "22003");
		}

		if ( r instanceof Adjusting.XML.Result )
			return applyAdjustments(adjust, (Adjusting.XML.Result<?>)r).get();
		return r;
	}

	/**
	 * Ensure the closing of whatever method was used to add content to
	 * an {@code SQLXML} object.
	 *<p>
	 * Before a {@code SQLXML} object that has been written to can be used by
	 * PostgreSQL (returned as a function result, plugged in as a prepared
	 * statement parameter or into a {@code ResultSet}, etc.), the method used
	 * for writing it must be "closed" to ensure the writing is complete.
	 *<p>
	 * If it is set with {@link SQLXML#setString setString}, nothing more is
	 * needed; {@code setString} obviously sets the whole value at once. Any
	 * {@code OutputStream} or {@code Writer} obtained from
	 * {@link SQLXML#setBinaryStream setBinaryStream} or
	 * {@link SQLXML#setCharacterStream setCharacterStream}, or from
	 * {@link SQLXML#setResult setResult}{@code (StreamResult.class)}, has to be
	 * explicitly closed (a {@link Transformer} does not close its
	 * {@link Result} when the transformation is complete!).
	 * Those are cases 1, 2, and 4 here.
	 *<p>
	 * Cases 5 ({@code SAXResult}) and 6 ({@code StAXResult}) need no special
	 * attention; though the {@code Transformer} does not close them, the ones
	 * returned by this {@code SQLXML} implementation are set up to close
	 * themselves when the {@code endDocument} event is written.
	 *<p>
	 * Case 3 (test of {@code setString} is handled specially here. As this
	 * class allows testing of all techniques for writing the {@code SQLXML}
	 * object, and most of those involve a {@code Result}, case 3 is handled
	 * by also constructing a {@code Result} over a {@link StringWriter} and
	 * having the content written into that; this method then extracts the
	 * content from the {@code StringWriter} and passes it to {@code setString}.
	 * For cases 1 and 2, likewise, the stream obtained with
	 * {@code getBinaryStream} or {@code getCharacterStream} has been wrapped in
	 * a {@code Result} for generality in this example.
	 *<p>
	 * A typical application will not need the generality seen here; it
	 * will usually know which technique it is using to write the {@code SQLXML}
	 * object, and only needs to know how to close that if it needs closing.
	 * @param r The {@code Result} onto which writing was done.
	 * @param sx The {@code SQLXML} object being written.
	 * @param how The integer used in this example class to select which method
	 * of writing the {@code SQLXML} object was to be tested.
	 * @return The {@code SQLXML} object {@code sx}, because why not?
	 */
	public static SQLXML ensureClosed(Result r, SQLXML sx, int how)
	throws SQLException
	{
		switch ( how )
		{
		case 1:
		case 2:
		case 4:
			StreamResult sr = (StreamResult)r;
			OutputStream os = sr.getOutputStream();
			Writer w = sr.getWriter();
			try
			{
				if ( null != os )
					os.close();
				if ( null != w )
					w.close();
			}
			catch ( IOException ioe )
			{
				throw new SQLException(
					"Failure closing SQLXML result", "XX000");
			}
			break;
		case 3:
			StringWriter sw = (StringWriter)((StreamResult)r).getWriter();
			String s = sw.toString();
			sx.setString(s);
			break;
		}
		return sx;
	}

	/**
	 * Configure a {@code DOMResult} to accept {@code CONTENT} (a/k/a
	 * document fragment), not only the more restrictive {@code DOCUMENT}.
	 *<p>
	 * The other forms of {@code Result} that can be requested will happily
	 * accept {@code XML(CONTENT)} and not just {@code XML(DOCUMENT)}.
	 * The {@code DOMResult} is pickier, however: if you first call
	 * {@link DOMResult#setNode setNode} with a {@code DocumentFragment}, it
	 * will accept either form, but if you leave the node unset when passing the
	 * {@code DOMResult} to a transformer, the transformer will default to
	 * putting a {@code Document} node there, and then it will not accept a
	 * fragment.
	 *<p>
	 * If you need to handle fragments, this method illustrates how to pre-load
	 * the {@code DOMResult} with an empty {@code DocumentFragment}. Note that
	 * if you use some XML processing package that supplies its own classes
	 * implementing DOM nodes, you may need to use a {@code DocumentFragment}
	 * instance obtained from that package.
	 */
	public static void allowFragment(DOMResult r) throws SQLException
	{
		try
		{
			r.setNode(DocumentBuilderFactory.newInstance()
				.newDocumentBuilder().newDocument()
					.createDocumentFragment());
		}
		catch ( ParserConfigurationException pce )
		{
			throw new SQLException("Failed initializing DOMResult", pce);
		}
	}

	private static void shovelBytes(InputStream is, OutputStream os)
	throws IOException
	{
		byte[] b = new byte[8192];
		int got;
		while ( -1 != (got = is.read(b)) )
			os.write(b, 0, got);
		is.close();
		os.close();
	}

	private static void shovelChars(Reader r, Writer w)
	throws IOException
	{
		char[] b = new char[8192];
		int got;
		while ( -1 != (got = r.read(b)) )
			w.write(b, 0, got);
		r.close();
		w.close();
	}

	/**
	 * Test the MappedUDT (in one direction anyway).
	 *<p>
	 * Creates a {@code PassXML} object, the Java class that maps the
	 * {@code javatest.onexml} composite type, which has one member, of XML
	 * type. Stores a {@code SQLXML} value in that field of the {@code PassXML}
	 * object, and passes that to an SQL query that expects and returns
	 * {@code javatest.onexml}. Retrieves the XML from the value field of the
	 * {@code PassXML} object created to map the result of the query.
	 * @return The original XML value, if all goes well.
	 */
	@Function(schema="javatest", implementor="postgresql_xml")
	public static SQLXML xmlFromComposite() throws SQLException
	{
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		PreparedStatement ps =
			c.prepareStatement("SELECT CAST(? AS javatest.onexml)");
		SQLXML x = c.createSQLXML();
		x.setString("<a/>");
		PassXML obj = new PassXML();
		obj.m_value = x;
		obj.m_typeName = "javatest.onexml";
		ps.setObject(1, obj);
		ResultSet r = ps.executeQuery();
		r.next();
		obj = (PassXML)r.getObject(1);
		ps.close();
		return obj.m_value;
	}

	/*
	 * Required to serve as a MappedUDT:
	 */
	/**
	 * No-arg constructor required of objects that will implement
	 * {@link SQLData}.
	 */
	public PassXML() { }

	private String m_typeName;
	private SQLXML m_value;

	@Override
	public String getSQLTypeName() { return m_typeName; }

	@Override
	public void readSQL(SQLInput stream, String typeName) throws SQLException
	{
		m_typeName = typeName;
		m_value = (SQLXML) stream.readObject();
	}

	@Override
	public void writeSQL(SQLOutput stream) throws SQLException
	{
		stream.writeSQLXML(m_value);
	}

	/**
	 * Class that will proxy methods to another {@code SQLXML} class.
	 *<p>
	 * Used for testing the PL/Java can accept input for PostgreSQL from an
	 * {@code SQLXML} object not of its own implementation (for example, one
	 * obtained from a different JDBC driver from some other database).
	 *<p>
	 * Only the {@code getSource} method is specially treated, to allow
	 * exercising the various flavors of source.
	 */
	public static class SQLXMLProxy implements SQLXML
	{
		private SQLXML m_sx;
		private int m_how;

		public SQLXMLProxy(SQLXML sx, int how)
		{
			if ( null == sx )
				throw new NullPointerException("Null SQLXMLProxy target");
			if ( 1 > how  ||  how > 7  ||  how == 3 )
				throw new IllegalArgumentException(
					"\"how\" must be 1, 2, 4, 5, 6, or 7");
			m_sx = sx;
			m_how = how;
		}

		@Override
		public void free() throws SQLException { m_sx.free(); }

		@Override
		public InputStream getBinaryStream() throws SQLException
		{
			return m_sx.getBinaryStream();
		}

		@Override
		public OutputStream setBinaryStream() throws SQLException
		{
			return m_sx.setBinaryStream();
		}

		@Override
		public Reader getCharacterStream() throws SQLException
		{
			return m_sx.getCharacterStream();
		}

		@Override
		public Writer setCharacterStream() throws SQLException
		{
			return m_sx.setCharacterStream();
		}

		@Override
		public String getString() throws SQLException
		{
			return m_sx.getString();
		}

		@Override
		public void setString(String value) throws SQLException
		{
			m_sx.setString(value);
		}

		@Override
		@SuppressWarnings("unchecked") // all the fun's when sourceClass is null
		public <T extends Source> T getSource(Class<T> sourceClass)
		throws SQLException
		{
			if ( null == sourceClass )
			{
				switch ( m_how )
				{
				case 1:
					return (T)new StreamSource(m_sx.getBinaryStream());
				case 2:
					return (T)new StreamSource(m_sx.getCharacterStream());
				case 4:
					sourceClass = (Class<T>)StreamSource.class;
					break;
				case 5:
					sourceClass = (Class<T>)SAXSource.class;
					break;
				case 6:
					sourceClass = (Class<T>)StAXSource.class;
					break;
				case 7:
					sourceClass = (Class<T>)DOMSource.class;
					break;
				}
			}
			return m_sx.getSource(sourceClass);
		}

		@Override
		public <T extends Result> T setResult(Class<T> resultClass)
		throws SQLException
		{
			return m_sx.setResult(resultClass);
		}
	}

	/**
	 * Class that will mock an {@code SQLXML} instance, returning only binary or
	 * character stream data from a byte array or string supplied at
	 * construction.
	 */
	public static class SQLXMLMock implements SQLXML
	{
		private String m_chars;
		private byte[] m_bytes;

		public SQLXMLMock(String content)
		{
			if ( null == content )
				throw new NullPointerException("Null SQLXMLMock content");
			m_chars = content;
		}

		public SQLXMLMock(byte[] content)
		{
			if ( null == content )
				throw new NullPointerException("Null SQLXMLMock content");
			m_bytes = content;
		}

		@Override
		public void free() throws SQLException { }

		@Override
		public InputStream getBinaryStream() throws SQLException
		{
			if ( null != m_bytes )
				return new ByteArrayInputStream(m_bytes);
			throw new UnsupportedOperationException(
				"SQLXMLMock.getBinaryStream");
		}

		@Override
		public OutputStream setBinaryStream() throws SQLException
		{
			throw new UnsupportedOperationException(
				"SQLXMLMock.setBinaryStream");
		}

		@Override
		public Reader getCharacterStream() throws SQLException
		{
			if ( null != m_chars )
				return new StringReader(m_chars);
			throw new UnsupportedOperationException(
				"SQLXMLMock.getCharacterStream");
		}

		@Override
		public Writer setCharacterStream() throws SQLException
		{
			throw new UnsupportedOperationException(
				"SQLXMLMock.setCharacterStream");
		}

		@Override
		public String getString() throws SQLException
		{
			if ( null != m_chars )
				return m_chars;
			throw new UnsupportedOperationException(
				"SQLXMLMock.getString");
		}

		@Override
		public void setString(String value) throws SQLException
		{
			throw new UnsupportedOperationException(
				"SQLXMLMock.setString");
		}

		@Override
		@SuppressWarnings("unchecked") // sourceClass==StreamSource is verified
		public <T extends Source> T getSource(Class<T> sourceClass)
		throws SQLException
		{
			if ( null != sourceClass && StreamSource.class != sourceClass )
				throw new UnsupportedOperationException(
					"SQLXMLMock.getSource(" + sourceClass.getName() + ")");
			if ( null != m_chars )
				return (T) new StreamSource(new StringReader(m_chars));
			return (T) new StreamSource(new ByteArrayInputStream(m_bytes));
		}

		@Override
		public <T extends Result> T setResult(Class<T> resultClass)
		throws SQLException
		{
			throw new UnsupportedOperationException(
				"SQLXMLMock.setResult");
		}
	}
}
