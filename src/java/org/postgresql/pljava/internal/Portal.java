/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
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
	public native String getName()
	throws SQLException;

	/**
	 * Returns the value of the <code>portalPos</code> attribute.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public native int getPortalPos()
	throws SQLException;

	/**
	 * Returns the TupleDesc that describes the row Tuples for this
	 * Portal.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public native TupleDesc getTupleDesc()
	throws SQLException;

	/**
	 * Performs an <code>SPI_cursor_fetch</code>.
	 * @param forward Set to <code>true</code> for forward, <code>false</code> for backward.
	 * @param count Maximum number of rows to fetch.
	 * @return The actual number of fetched rows.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public native int fetch(boolean forward, int count)
	throws SQLException;

	/**
	 * Invalidates this structure and frees up memory using the
	 * internal function <code>SPI_cursor_close</code>
	 */
	public native void invalidate();

	/**
	 * Returns the value of the <code>atEnd</code> attribute.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public native boolean isAtEnd()
	throws SQLException;
	
	/**
	 * Returns the value of the <code>atStart</code> attribute.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public native boolean isAtStart()
	throws SQLException;
	
	/**
	 * Returns the value of the <code>posOverflow</code> attribute.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public native boolean isPosOverflow()
	throws SQLException;

	/**
	 * Performs an <code>SPI_cursor_move</code>.
	 * @param forward Set to <code>true</code> for forward, <code>false</code> for backward.
	 * @param count Maximum number of rows to fetch.
	 * @return The value of the global variable <code>SPI_result</code>.
	 * @throws SQLException if the handle to the native structur is stale.
	 */
	public native int move(boolean forward, int count)
	throws SQLException;
}
