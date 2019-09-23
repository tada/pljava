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
					content().characters(cb.array(), 0, cb.remaining());
				}
			};
		}
		catch ( CharacterCodingException e )
		{
			return exceptionCarrier(e);
		}
	}
}
