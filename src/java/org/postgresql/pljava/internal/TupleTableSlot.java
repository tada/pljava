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
