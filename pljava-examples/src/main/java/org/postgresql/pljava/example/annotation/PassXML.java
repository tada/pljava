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

import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLXML;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

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
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;

import org.postgresql.pljava.annotation.Function;

public class PassXML
{
	static SQLXML s_sx;

	static TransformerFactory s_tf = TransformerFactory.newInstance();

	static Map<String,Templates> s_tpls = new HashMap<String,Templates>();

	/**
	 * Echo an XML parameter back as a string, exercising seven different ways
	 * (how => 1-7) of reading an SQLXML object.
	 *<p>
	 * If how => 0, the XML parameter is simply saved in a static. It can be
	 * read in a subsequent call with sx => null, but only in the same
	 * transaction.
	 */
	@Function(schema="javatest")
	public static String echoXMLParameter(SQLXML sx, int how)
	throws SQLException
	{
		if ( null == sx )
			sx = s_sx;
		if ( 0 == how )
		{
			s_sx = sx;
			return "(saved)";
		}
		return echoSQLXML(sx, how);
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
	public static String transformXML(
		String transformName, SQLXML source, int how)
	throws SQLException
	{
		Templates tpl = s_tpls.get(transformName);
		Source src = sxToSource(source, how);
		StringWriter sw = new StringWriter();
		Result rlt = new StreamResult(sw);

		try
		{
			Transformer t = tpl.newTransformer();
			t.transform(src, rlt);
		}
		catch ( TransformerException te )
		{
			throw new SQLException("XML transformation failed", te);
		}

		return sw.toString();
	}

	private static Source sxToSource(SQLXML sx, int how) throws SQLException
	{
		switch ( how )
		{
			case  1: return new StreamSource(sx.getBinaryStream());
			case  2: return new StreamSource(sx.getCharacterStream());
			case  3: return new StreamSource(new StringReader(sx.getString()));
			case  4: return     sx.getSource(DOMSource.class);
			case  5: return     sx.getSource(SAXSource.class);
			case  6: return     sx.getSource(StAXSource.class);
			case  7: return     sx.getSource(StreamSource.class);
			default: throw new SQLDataException("how should be 1-7", "22003");
		}
	}

	private static String echoSQLXML(SQLXML sx, int how) throws SQLException
	{
		Source src = sxToSource(sx, how);
		StringWriter sw = new StringWriter();
		Result rlt = new StreamResult(sw);

		try
		{
			Transformer t = s_tf.newTransformer();
			t.transform(src, rlt);
		}
		catch ( TransformerException te )
		{
			throw new SQLException("XML transformation failed", te);
		}

		return sw.toString();
	}
}
