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
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLXML;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import java.util.Map;
import java.util.HashMap;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;

import org.postgresql.pljava.annotation.Function;

public class PassXML
{
	static SQLXML s_sx;

	static TransformerFactory s_tf = TransformerFactory.newInstance();

	static Map<String,Templates> s_tpls = new HashMap<String,Templates>();

	/**
	 * Echo an XML parameter back, exercising seven different ways
	 * (howin => 1-7) of reading an SQLXML object, and six (howout => 1-6)
	 * of returning one.
	 *<p>
	 * If howin => 0, the XML parameter is simply saved in a static. It can be
	 * read in a subsequent call with sx => null, but only in the same
	 * transaction.
	 */
	@Function(schema="javatest")
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
	 * Precompile an XSL transform {@code source} and save it (for the
	 * current session) as {@code name}.
	 *<p>
	 * Each value of {@code how}, 1-7, selects a different way of presenting
	 * the {@code SQLXML} object to the XSL processor.
	 *<p>
	 * Preparing a transform with
	 * {@link TransformerFactoory.newTemplates newTemplates()} seems to require
	 * {@link Function.Trust.UNSANDBOXED Trust.UNSANDBOXED}, at least for the
	 * XSLTC transform compiler in newer JREs.
	 */
	@Function(schema="javatest", trust=Function.Trust.UNSANDBOXED)
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

	@Function(schema="javatest")
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
			case  7: return     sx.setResult(DOMResult.class);
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
}
