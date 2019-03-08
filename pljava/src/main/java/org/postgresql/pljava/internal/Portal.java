/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>Portal</code> correspons to the internal PostgreSQL
 * <code>Portal</code> type.
 *
 * @author Thomas Hallgren
 */
public class Portal
{
	private long m_pointer;

	Portal(long pointer)
	{
		m_pointer = pointer;
	}

	/**
	 * Invalidates this structure and frees up memory using the
	 * internal function <code>SPI_cursor_close</code>
	 */
	public void close()
	{
		synchronized(Backend.THREADLOCK)
		{
			_close(m_pointer);
			m_pointer = 0;
		}
	}

	/**
	 * Returns the name of this Portal.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public String getName()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getName(m_pointer);
		}
	}

	/**
	 * Returns the value of the <code>portalPos</code> attribute.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public long getPortalPos()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			long pos = _getPortalPos(m_pointer);
			if ( pos < 0 )
				throw new ArithmeticException(
					"portal position too large to report " +
					"in a Java signed long");
			return pos;
		}
	}

	/**
	 * Returns the TupleDesc that describes the row Tuples for this
	 * Portal.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public TupleDesc getTupleDesc()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getTupleDesc(m_pointer);
		}
	}

	/**
	 * Performs an <code>SPI_cursor_fetch</code>.
	 * @param forward Set to <code>true</code> for forward, <code>false</code> for backward.
	 * @param count Maximum number of rows to fetch.
	 * @return The actual number of fetched rows.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public long fetch(boolean forward, long count)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			long fetched = _fetch(m_pointer, forward, count);
			if ( fetched < 0 )
				throw new ArithmeticException(
					"fetched too many rows to report in a Java signed long");
			return fetched;
		}
	}

	/**
	 * Returns the value of the <code>atEnd</code> attribute.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public boolean isAtEnd()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _isAtEnd(m_pointer);
		}
	}

	/**
	 * Returns the value of the <code>atStart</code> attribute.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public boolean isAtStart()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _isAtStart(m_pointer);
		}
	}

	/**
	 * Checks if the portal is still active. I can be closed either explicitly
	 * using the {@link #close()} mehtod or implicitly due to a pop of invocation
	 * context.
	 */
	public boolean isValid()
	{
		return m_pointer != 0;
	}

	/**
	 * Performs an <code>SPI_cursor_move</code>.
	 * @param forward Set to <code>true</code> for forward, <code>false</code> for backward.
	 * @param count Maximum number of rows to fetch.
	 * @return The actual number of rows moved.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public long move(boolean forward, long count)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			long moved = _move(m_pointer, forward, count);
			if ( moved < 0 )
				throw new ArithmeticException(
					"moved too many rows to report in a Java signed long");
			return moved;
		}
	}

	private static native String _getName(long pointer)
	throws SQLException;

	private static native long _getPortalPos(long pointer)
	throws SQLException;

	private static native TupleDesc _getTupleDesc(long pointer)
	throws SQLException;

	private static native long _fetch(long pointer, boolean forward, long count)
	throws SQLException;

	private static native void _close(long pointer);

	private static native boolean _isAtEnd(long pointer)
	throws SQLException;

	private static native boolean _isAtStart(long pointer)
	throws SQLException;

	private static native long _move(long pointer, boolean forward, long count)
	throws SQLException;
}
