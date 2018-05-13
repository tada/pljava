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

import javax.xml.transform.Result;
import javax.xml.transform.Source;
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

	private static String echoSQLXML(SQLXML sx, int how) throws SQLException
	{
		Source src;

		switch ( how )
		{
			case 1:
				src = new StreamSource(sx.getBinaryStream());
				break;
			case 2:
				src = new StreamSource(sx.getCharacterStream());
				break;
			case 3:
				src = new StreamSource(new StringReader(sx.getString()));
				break;
			case 4:
				src = sx.getSource(DOMSource.class);
				break;
			case 5:
				src = sx.getSource(SAXSource.class);
				break;
			case 6:
				src = sx.getSource(StAXSource.class);
				break;
			case 7:
				src = sx.getSource(StreamSource.class);
				break;
			default:
				throw new SQLDataException("how should be 1-7", "22003");
		}

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
