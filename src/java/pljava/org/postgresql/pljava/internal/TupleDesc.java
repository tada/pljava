/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
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
			return this._getColumnIndex(colName.toLowerCase());
		}
	}

	/**
	 * Creates a <code>Tuple</code> that is described by this descriptor and
	 * initialized with the supplied <code>values</code>.
	 * @return The created <code>Tuple</code>.
	 * @throws SQLException If the length of the values array does not
	 * match the size of the descriptor or if the handle of this descriptor
	 * has gone stale.
	 */
	public Tuple formTuple(Object[] values)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._formTuple(values);
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
	private native Tuple _formTuple(Object[] values) throws SQLException;
	private native int _size();
}
