/*
 * Copyright (c) 2019-2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import org.postgresql.pljava.adt.spi.Datum;

import static org.postgresql.pljava.model.CharsetEncoding.SERVER_ENCODING;

import org.postgresql.pljava.pg.DatumImpl;

/**
 * Class adapting a {@code ByteBufferXMLReader} to a
 * {@code Datum.Input}.
 */
public abstract class VarlenaXMLRenderer
extends ByteBufferXMLReader implements DatumImpl
{
	private final Datum.Input m_input;

	protected final CharsetDecoder m_decoder;

	/**
	 * A duplicate of the {@code Datum.Input}'s byte buffer,
	 * so its {@code position} can be updated by the
	 * {@code XMLEventReader} operations without affecting the original
	 * (therefore multiple streams may read one {@code Input}).
	 */
	private ByteBuffer m_movingBuffer;

	public VarlenaXMLRenderer(Datum.Input input) throws SQLException
	{
		m_input = input;
		Charset cs = SERVER_ENCODING.charset();
		m_decoder = cs.newDecoder();
	}

	@Override
	public long adopt() throws SQLException
	{
		throw new UnsupportedOperationException(
			"adopt() on a synthetic XML rendering");
	}

	@Override
	public String toString()
	{
		return toString(this);
	}

	@Override
	public String toString(Object o)
	{
		return ((DatumImpl)m_input).toString(o);
	}

	@Override
	protected void pin() throws SQLException
	{
		m_input.pin();
	}

	@Override
	protected void unpin()
	{
		m_input.unpin();
	}

	@Override
	protected ByteBuffer buffer() throws SQLException
	{
		if ( null == m_movingBuffer )
		{
			ByteBuffer b = m_input.buffer();
			m_movingBuffer = b.duplicate().order(b.order());
		}
		return m_movingBuffer;
	}

	@Override
	public void close() throws IOException
	{
		m_input.close();
	}
}
