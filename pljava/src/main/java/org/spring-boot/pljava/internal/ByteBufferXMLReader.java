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
package org.postgresql.pljava.internal;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.sql.SQLException;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Wrap a readable {@link ByteBuffer} as a {@link SyntheticXMLReader}.
 *<p>
 * An implementing class must provide a {@link #buffer} method that returns the
 * {@code ByteBuffer}, and the method is responsible for knowing when the memory
 * region windowed by the {@code ByteBuffer} is no longer to be accessed, and
 * throwing an exception in that case (unless the class also overrides
 * {@link #pin} and performs the check there instead).
 *<p>
 * The underlying buffer's {@link ByteBuffer#position() position} may be used to
 * maintain the XML reader's position.
 */
public abstract class ByteBufferXMLReader extends SyntheticXMLReader
{
	private boolean m_done = false;

	/**
	 * Pin resources as needed during a reading operation.
	 *<p>
	 * The implementation is also responsible for tracking whether this
	 * instance has been closed, and throwing an exception if so.
	 */
	protected abstract void pin() throws SQLException;

	/**
	 * Unpin resources after a reading operation.
	 */
	protected abstract void unpin();

	/**
	 * Return the {@link ByteBuffer} being wrapped.
	 *<p>
	 * All uses of the buffer in this class are preceded by {@code pin()} and
	 * followed by {@code unpin()}.
	 */
	protected abstract ByteBuffer buffer() throws SQLException;

	/**
	 * Return null if no more events available, or an {@code EventCarrier}
	 * that carries one or more.
	 *<p>
	 * Start- and end-document events are supplied by the caller, and so should
	 * not be supplied here.
	 *<p>
	 * The pin on the underlying state is held.
	 * @param buf The buffer to read from. Its
	 * {@link ByteBuffer#position position} may be used to maintain input
	 * position.
	 * @return An {@link EventCarrier} representing some XML parse events,
	 * null if none remain.
	 */
	protected abstract EventCarrier next(ByteBuffer buf);

	/**
	 * This implementation invokes {@code next(ByteBuffer)} to get
	 * some more events.
	 *<p>
	 * @return an {@link EventCarrier}, or null if no more.
	 */
	@Override
	protected EventCarrier next()
	{
		if ( m_done )
			return null;

		boolean gotPin = false; // Kludge to get pin() inside the try block
		try
		{
			pin();
			gotPin = true;
			EventCarrier ec = next(buffer());
			if ( null == ec )
				m_done = true;
			return ec;
		}
		catch ( Exception e )
		{
			m_done = true;
			return exceptionCarrier(e);
		}
		finally
		{
			if ( gotPin )
				unpin();
		}
	}

	@Override
	public void parse(InputSource input) throws IOException, SAXException
	{
		parse();
	}
}
