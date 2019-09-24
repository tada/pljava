/*
 * Copyright (c) 2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import java.nio.charset.CharacterCodingException;

import java.sql.SQLException;

import org.xml.sax.SAXException;

import org.postgresql.pljava.internal.VarlenaWrapper;
import org.postgresql.pljava.internal.VarlenaXMLRenderer;

public class PgNodeTreeAsXML extends VarlenaXMLRenderer
{
	PgNodeTreeAsXML(VarlenaWrapper.Input vwi) throws SQLException
	{
		super(vwi);
	}

	public static final String LPAR_TOK = "(";
	public static final String RPAR_TOK = ")";
	public static final String LBRA_TOK = "{";
	public static final String RBRA_TOK = "}";

	@Override
	protected EventCarrier next(ByteBuffer buf)
	{
		if ( 0 == buf.remaining() )
			return null;
		try
		{
			final CharBuffer cb = m_decoder.decode(buf);
			return new EventCarrier()
			{
				@Override
				public void toSAX() throws IOException, SAXException
				{
					m_attributes.clear();
					m_attributes.addAttribute(
						"", "", "value", "CDATA", "");
					String tok;
					while ( null != (tok = lowToken(cb)) )
					{
						m_attributes.setValue(0, tok);
						content().startElement(
							"", "", "lowToken", m_attributes);
						content().endElement("", "", "lowToken");
					}
				}
			};
		}
		catch ( CharacterCodingException e )
		{
			return exceptionCarrier(e);
		}
	}

	private String lowToken(CharBuffer cb)
	{
		int beg = cb.position();
		int end = cb.limit();
		int cur = beg;
		char ch = 0; // sop for javac

		while ( cur < end )
		{
			ch = cb.get(cur);
			if ( ' ' == ch  ||  '\n' == ch  ||  '\t' == ch )
				beg = ++cur;
			else
				break;
		}

		if ( cur == end )
		{
			cb.position(cur);
			return null;
		}

		if ( '(' == ch  ||  ')' == ch  ||  '{' == ch  ||  '}' == ch )
		{
			cb.position(++cur);
			switch ( ch )
			{
				case '(': return LPAR_TOK;
				case ')': return RPAR_TOK;
				case '{': return LBRA_TOK;
				case '}': return RBRA_TOK;
			}
		}

		StringBuilder sb = new StringBuilder();

		while ( -1 == "(){} \n\t".indexOf(ch) )
		{
			++ cur;
			if ( '\\' == ch  &&  cur < end )
				sb.append(cb.get(cur++));
			else
				sb.append(ch);
			if ( cur == end )
				break;
			ch = cb.get(cur);
		}

		cb.position(cur);

		if ( 2 == sb.length()  &&  0 == sb.indexOf("<>") )
			return "";

		return sb.toString();
	}
}
