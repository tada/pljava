/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>TupleTable</code> correspons to the internal PostgreSQL
 * <code>TupleTable</code> type.
 *
 * @author Thomas Hallgren
 */
public class TupleTable extends NativeStruct
{
	/**
	 * Returns the number of TupleTableSlots contained in
	 * this TupleTable.
	 */
	public int getCount()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getCount();
		}
	}

	/**
	 * Returns the TupleTableSlot at the given index.
	 * @param position Index of desired slot. First slot has index zero. 
	 */
	public TupleTableSlot getSlot(int position)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getSlot(position);
		}
	}

	private native int _getCount() throws SQLException;
	private native TupleTableSlot _getSlot(int position) throws SQLException;
}
