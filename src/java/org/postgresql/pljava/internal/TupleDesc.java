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
 * The <code>TupleDesc</code> correspons to the internal PostgreSQL
 * <code>TupleDesc</code>.
 *
 * @author Thomas Hallgren
 */
public class TupleDesc extends NativeStruct
{
	/**
	 * Returns the name of the column at <code>index</code>.
	 * @param index The one based index of the column.
	 * @return The name of the column.
	 * @throws SQLException If the index is out of range for this
	 * tuple descriptor.
	 */
	public String getColumnName(int index)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getColumnName(index);
		}
	}

	/**
	 * Returns the index of the column named <code>colName</code>.
	 * @param colName The name of the column.
	 * @return The index for column <code>colName</code>.
	 * @throws SQLException If no column with the given name can
	 * be found in this tuple descriptor.
	 */
	public int getColumnIndex(String colName)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getColumnIndex(colName);
		}
	}

	/**
	 * Returns the number of columns in this tuple descriptor.
	 */
	public int size()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._size();
		}
	}

	private native String _getColumnName(int index) throws SQLException;
	private native int _getColumnIndex(String colName) throws SQLException;
	private native int _size();
}
