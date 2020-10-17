/*
 * Copyright (c) 2019-2020 Tada AB and other contributors, as listed below.
 * Portions Copyright (c) 1996-2019, PostgreSQL Global Development Group
 * Portions Copyright (c) 1994, Regents of the University of California
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Parsing logic from src/backend/nodes/read.c: Andrew Yu, Nov 2, 1994
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import java.nio.charset.CharacterCodingException;

import java.sql.SQLException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.SAXException;

import org.postgresql.pljava.internal.VarlenaWrapper;
import org.postgresql.pljava.internal.VarlenaXMLRenderer;

/**
 * An adapter presenting PostgreSQL's {@code pg_node_tree} type (a serialized
 * representation of a tree data structure) through the XML API (in, currently,
 * an ad-hoc, schemaless rendering, but one with which some practical use might
 * be made of the information, after a little study).
 */
public class PgNodeTreeAsXML extends VarlenaXMLRenderer
{
	PgNodeTreeAsXML(VarlenaWrapper.Input vwi) throws SQLException
	{
		super(vwi);
	}

	/*
	 * Special returns from the low-level tokenizer.
	 */
	public static final String LPAR_TOK = "(";
	public static final String RPAR_TOK = ")";
	public static final String LBRA_TOK = "{";
	public static final String RBRA_TOK = "}";

	enum NodeTokenType
	{
		T_Integer, T_Float, T_String, T_BitString,
		RIGHT_PAREN, LEFT_PAREN, LEFT_BRACE, OTHER_TOKEN;

		private static final Pattern s_maybeNumber =
			Pattern.compile("^[-+]?+\\.?+\\d");

		static NodeTokenType of(String token)
		{
			if ( LPAR_TOK == token )
				return LEFT_PAREN;
			if ( RPAR_TOK == token )
				return RIGHT_PAREN;
			if ( LBRA_TOK == token )
				return LEFT_BRACE;
			if ( token.startsWith("\"") && token.endsWith("\"") )
				return T_String;
			if ( token.startsWith("b") )
				return T_BitString;

			if ( s_maybeNumber.matcher(token).lookingAt() )
			{
				try
				{
					Integer.parseInt(token);
					return T_Integer;
				}
				catch ( NumberFormatException e )
				{
					return T_Float;
				}
			}

			return OTHER_TOKEN;
		}
	}

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
				public void toSAX()
				throws IOException, SAXException, SQLException
				{
					nodeRead(null);
				}

				private String nextToken()
				{
					return PgNodeTreeAsXML.this.nextToken(cb);
				}

				private void nodeRead(String token)
				throws IOException, SAXException, SQLException
				{
					if ( null == token )
						if ( null == (token = nextToken()) )
							return;

					NodeTokenType type = NodeTokenType.of(token);
					switch ( type )
					{
					case LEFT_BRACE:
						parseNodeString();
						break;
					case LEFT_PAREN:
						if ( null == (token = nextToken()) )
							throw new SQLException(
								"unterminated List structure");
						String listType =
							"i".equals(token) ? "int" :
							"o".equals(token) ? "oid" :
							"b".equals(token) ? "bit" : /* not in PG source! */
							null;

						if ( null != listType )
						{
							startElement("list",
								cleared().withAttribute("all", listType));
							for (;;)
							{
								if ( null == (token = nextToken()) )
									throw new SQLException(
										"unterminated List structure");
								if ( RPAR_TOK == token )
									break;
								startElement("v");
								characters(token);
								endElement("v");
							}
						}
						else
						{
							startElement("list");
							for (;;)
							{
								if ( RPAR_TOK == token )
									break;
								nodeRead(token);
								if ( null == (token = nextToken()) )
									throw new SQLException(
										"unterminated List structure");
							}
						}
						endElement("list");
						break;
					case RIGHT_PAREN:
						throw new SQLException("unexpected right parenthesis");
					case OTHER_TOKEN:
						if ( token.isEmpty() )
						{
							startElement("null");
							endElement("null");
						}
						else
							throw new SQLException(
								"unrecognized token: \"" + token + '"');
						break;
					case T_Integer:
					case T_Float:
						startElement(type.name());
						characters(token);
						endElement(type.name());
						break;
					case T_String:
						startElement(type.name());
						characters(token.substring(1, token.length() - 1));
						endElement(type.name());
						break;
					case T_BitString:
						startElement(type.name());
						characters(token.substring(1));
						endElement(type.name());
						break;
					}
				}

				private void parseNodeString()
				throws IOException, SAXException, SQLException
				{
					String token = nextToken();
					if ( null == token  ||  RBRA_TOK == token )
						throw new SQLException(
							"badly formatted node string \"" + token + '"');
					String tokname = token;
					boolean seenMember = false;
					boolean isCONST;

					startElement(tokname);
					isCONST = "CONST".equals(tokname);
					for (;;)
					{
						if ( null == (token = nextToken()) )
							throw new SQLException(
								"unterminated node structure");
						if ( RBRA_TOK == token )
							break;

						if ( token.startsWith(":") )
						{
							if ( seenMember )
								endElement("member");
							seenMember = true;
							String name = token.substring(1);
							if ( isCONST  &&  "constvalue".equals(name) )
								readDatum();
							else
								startElement("member",
									cleared()
										.withAttribute("name", name));
							continue;
						}

						if ( LBRA_TOK == token  ||  LPAR_TOK == token )
						{
							nodeRead(token);
							continue;
						}

						if ( ! seenMember )
							throw new SQLException("node value outside member");
						characters(token);
					}
					if ( seenMember )
						endElement("member");
					endElement(tokname);
				}

				private void readDatum()
				throws IOException, SAXException, SQLException
				{
					String token = nextToken();
					if ( null == token )
						throw new SQLException(
							"malformed constvalue (expected length)");
					/*
					 * The length can be <> which nextToken() returns as ""
					 * which means the constvalue is null with no more to read.
					 */
					if ( token.isEmpty() )
					{
						startElement("member",
							cleared()
								.withAttribute("name", "constvalue"));
						return;
					}
					startElement("member",
						cleared()
							.withAttribute("name", "constvalue")
							.withAttribute("length", token));
					token = nextToken();
					if ( ! "[".equals(token) )
						throw new SQLException("malformed constvalue " +
							"(expected \"[\" got \"" + token + "\")");
					for (;;)
					{
						if ( null == (token = nextToken()) )
							throw new SQLException("unterminated constvalue");
						if ( "]".equals(token) )
							break;
						int b = Integer.parseInt(token);
						assert -128 <= b && b < 128 : "constvalue out of range";
						characters(Integer.toHexString(512 + b)
							.substring(1).toUpperCase());
					}
					// caller will add the </member>
				}
			};
		}
		catch ( CharacterCodingException e )
		{
			return exceptionCarrier(e);
		}
	}

	String nextToken(CharBuffer cb)
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
