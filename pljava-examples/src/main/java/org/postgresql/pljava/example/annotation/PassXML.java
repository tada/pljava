/*
 * Copyright (c) 2018- Tada AB and other contributors, as listed below.
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
import java.sql.SQLData;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Types;

import java.sql.SQLDataException;
import java.sql.SQLException;

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

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;

import javax.xml.transform.TransformerException;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.MappedUDT;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLActions;
import org.postgresql.pljava.annotation.SQLType;

import static org.postgresql.pljava.example.LoggerTest.logMessage;

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
@SQLActions({
	@SQLAction(provides="postgresql_xml", install=
		"SELECT CASE (SELECT 1 FROM pg_type WHERE typname = 'xml') WHEN 1" +
		" THEN set_config('pljava.implementors', 'postgresql_xml,' || " +
		" current_setting('pljava.implementors'), true) " +
		"END"
	),

	@SQLAction(implementor="postgresql_ge_80400", provides="postgresql_xml_cte",
		install=
		"SELECT CASE (SELECT 1 FROM pg_type WHERE typname = 'xml') WHEN 1" +
		" THEN set_config('pljava.implementors', 'postgresql_xml_cte,' || " +
		" current_setting('pljava.implementors'), true) " +
		"END"
	),

	@SQLAction(implementor="postgresql_xml_cte", requires="echoXMLParameter",
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
})
@MappedUDT(schema="javatest", name="onexml", structure="c1 xml",
		   implementor="postgresql_xml",
           comment="A composite type mapped by the PassXML example class")
public class PassXML implements SQLData
{
	static SQLXML s_sx;

	static TransformerFactory s_tf = TransformerFactory.newInstance();

	static Map<String,Templates> s_tpls = new HashMap<String,Templates>();

	/**
	 * Echo an XML parameter back, exercising seven different ways
	 * (howin =&gt; 1-7) of reading an SQLXML object, and seven
	 * (howout =&gt; 1-7) of returning one.
	 *<p>
	 * If howin =&gt; 0, the XML parameter is simply saved in a static. It can
	 * be read in a subsequent call with sx =&gt; null, but only in the same
	 * transaction.
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
	 * without [@code libxml} support and the PostgreSQL {@code XML} type.
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
	 * Precompile an XSL transform {@code source} and save it (for the
	 * current session) as {@code name}.
	 *<p>
	 * Each value of {@code how}, 1-7, selects a different way of presenting
	 * the {@code SQLXML} object to the XSL processor.
	 *<p>
	 * Preparing a transform with
	 * {@link TransformerFactory#newTemplates newTemplates()} seems to require
	 * {@link Function.Trust#UNSANDBOXED Trust.UNSANDBOXED}, at least for the
	 * XSLTC transform compiler in newer JREs.
	 */
	@Function(schema="javatest", trust=Function.Trust.UNSANDBOXED,
			  implementor="postgresql_xml")
	public static void prepareXMLTransform(String name, SQLXML source, int how)
	throws SQLException
	{
		try
		{
			s_tpls.put(name, s_tf.newTemplates(sxToSource(source, how)));
		}
		catch ( TransformerException te )
		{
			throw new SQLException("XML transformation failed", te);
		}
	}

	/**
	 * Transform some XML according to a named transform prepared with
	 * {@code prepareXMLTransform}.
	 */
	@Function(schema="javatest", implementor="postgresql_xml")
	public static SQLXML transformXML(
		String transformName, SQLXML source, int howin, int howout)
	throws SQLException
	{
		Templates tpl = s_tpls.get(transformName);
		Source src = sxToSource(source, howin);
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		SQLXML result = c.createSQLXML();
		Result rlt = sxToResult(result, howout);

		try
		{
			Transformer t = tpl.newTransformer();
			t.transform(src, rlt);
		}
		catch ( TransformerException te )
		{
			throw new SQLException("XML transformation failed", te);
		}

		return ensureClosed(rlt, result, howout);
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
	 * in {@code echoSQLXML} substitutes for a lot of fiddly case-by-case code
	 * (and not all the cases are even covered here!), but when coding for a
	 * specific case, all the generality of {@code transform} may not be needed.
	 * It can be interesting to compare memory use when XML values are large.
	 */
	@Function(schema="javatest", implementor="postgresql_xml")
	public static SQLXML lowLevelXMLEcho(SQLXML sx, int how)
	throws SQLException
	{
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		SQLXML rx = c.createSQLXML();

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
				StreamResult sr = rx.setResult(StreamResult.class);
				is = ss.getInputStream();
				r  = ss.getReader();
				os = sr.getOutputStream();
				w  = sr.getWriter();
				if ( null != is  &&  null != os )
				{
					shovelBytes(is, os);
					break;
				}
				if ( null != r  &&  null != r )
				{
					shovelChars(r, w);
					break;
				}
				throw new SQLDataException(
					"Unimplemented combination of StreamSource/StreamResult");
			case 5:
			case 6:
				throw new SQLDataException(
					"Unimplemented lowlevel SAX or StAX echo");
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
		return rx;
	}

	/**
	 * Text-typed variant of lowLevelXMLEcho (does not require XML type).
	 */
	@Function(schema="javatest", name="lowLevelXMLEcho", type="text")
	public static SQLXML lowLevelXMLEcho_(@SQLType("text") SQLXML sx, int how)
	throws SQLException
	{
		return lowLevelXMLEcho(sx, how);
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
		x = rs.getObject(1, SQLXML.class);
		ps.close();
		out.updateObject(1, x);
		return true;
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
		obj = r.getObject(1, PassXML.class);
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
}
