/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>TupleTableSlot</code> correspons to the internal PostgreSQL
 * <code>TupleTableSlot</code>.
 *
 * @author Thomas Hallgren
 */
public class TupleTableSlot extends NativeStruct
{
	public Tuple getTuple()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getTuple();
		}
	}

	public TupleDesc getTupleDesc()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getTupleDesc();
		}
	}

	private native Tuple _getTuple() throws SQLException;
	private native TupleDesc _getTupleDesc() throws SQLException;
}
