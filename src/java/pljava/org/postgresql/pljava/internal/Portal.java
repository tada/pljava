/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>Portal</code> correspons to the internal PostgreSQL
 * <code>Portal</code> type.
 *
 * @author Thomas Hallgren
 */
public class Portal extends NativeStruct
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
			return this._getName();
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
			return this._getPortalPos();
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
			return this._getTupleDesc();
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
			return this._fetch(forward, count);
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
			this._invalidate();
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
			return this._isAtEnd();
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
			return this._isAtStart();
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
			return this._isPosOverflow();
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
			return this._move(forward, count);
		}
	}

	private native String _getName()
	throws SQLException;

	private native int _getPortalPos()
	throws SQLException;

	private native TupleDesc _getTupleDesc()
	throws SQLException;

	private native int _fetch(boolean forward, int count)
	throws SQLException;

	private native void _invalidate();

	private native boolean _isAtEnd()
	throws SQLException;

	private native boolean _isAtStart()
	throws SQLException;
	
	private native boolean _isPosOverflow()
	throws SQLException;

	private native int _move(boolean forward, int count)
	throws SQLException;
}
