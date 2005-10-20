/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>Portal</code> correspons to the internal PostgreSQL
 * <code>Portal</code> type.
 *
 * @author Thomas Hallgren
 */
public class Portal extends JavaHandle
{
	/**
	 * Performs an <code>SPI_cursor_close</code>.
	 */
	public void close()
	{
		this.invalidate();
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
			return _getName(this.getNative());
		}
	}

	/**
	 * Returns the value of the <code>portalPos</code> attribute.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public int getPortalPos()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getPortalPos(this.getNative());
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
			return _getTupleDesc(this.getNative());
		}
	}

	/**
	 * Performs an <code>SPI_cursor_fetch</code>.
	 * @param forward Set to <code>true</code> for forward, <code>false</code> for backward.
	 * @param count Maximum number of rows to fetch.
	 * @return The actual number of fetched rows.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public int fetch(boolean forward, int count)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _fetch(this.getNative(), forward, count);
		}
	}

	/**
	 * Invalidates this structure and frees up memory using the
	 * internal function <code>SPI_cursor_close</code>
	 */
	public void invalidate()
	{
		synchronized(Backend.THREADLOCK)
		{
			long pointer = this.getNative();
			if(pointer != 0)
			{
				this.clearNative();
				_invalidate(pointer);
			}
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
			return _isAtEnd(this.getNative());
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
			return _isAtStart(this.getNative());
		}
	}

	/**
	 * Returns the value of the <code>posOverflow</code> attribute.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public boolean isPosOverflow()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _isPosOverflow(this.getNative());
		}
	}

	/**
	 * Performs an <code>SPI_cursor_move</code>.
	 * @param forward Set to <code>true</code> for forward, <code>false</code> for backward.
	 * @param count Maximum number of rows to fetch.
	 * @return The value of the global variable <code>SPI_result</code>.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public int move(boolean forward, int count)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _move(this.getNative(), forward, count);
		}
	}

	private static native String _getName(long pointer)
	throws SQLException;

	private static native int _getPortalPos(long pointer)
	throws SQLException;

	private static native TupleDesc _getTupleDesc(long pointer)
	throws SQLException;

	private static native int _fetch(long pointer, boolean forward, int count)
	throws SQLException;

	private static native void _invalidate(long pointer);

	private static native boolean _isAtEnd(long pointer)
	throws SQLException;

	private static native boolean _isAtStart(long pointer)
	throws SQLException;
	
	private static native boolean _isPosOverflow(long pointer)
	throws SQLException;

	private static native int _move(long pointer, boolean forward, int count)
	throws SQLException;
}
