/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
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
