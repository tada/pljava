/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>SPITupleTable</code> correspons to the internal PostgreSQL
 * <code>SPITupleTable</code> type.
 *
 * @author Thomas Hallgren
 */
public class SPITupleTable extends NativeStruct
{
	/**
	 * Returns the number of <code>Tuple</code> instances contained in this table.
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
	 * Returns the <code>Tuple</code> at the given index.
	 * @param position Index of desired slot. First slot has index zero. 
	 */
	public Tuple getSlot(int position)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getSlot(position);
		}
	}

	/**
	 * Returns the <code>TupleDesc</code> for the <code>Tuple</code> instances of
	 * this table. 
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
	 * Invalidates this structure and call the internal function <code>
	 * SPI_freetuptable</code> to free up memory.
	 */
	public void invalidate()
	{
		synchronized(Backend.THREADLOCK)
		{
			this._invalidate();
		}
	}

	private native int _getCount()
	throws SQLException;
	
	private native Tuple _getSlot(int position)
	throws SQLException;

	private native TupleDesc _getTupleDesc()
	throws SQLException;

	private native void _invalidate();
}
