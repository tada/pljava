/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava;

import java.sql.SQLException;

/**
 * The <code>Tuple</code> correspons to the internal PostgreSQL
 * <code>HeapTuple</code> and adds an association to its <code>
 * TupleDesc</code>.
 *
 * @author Thomas Hallgren
 */
public class Tuple extends NativeStruct
{
	private final TupleDesc m_tupleDesc;

	public Tuple(TupleDesc tupleDesc)
	{
		m_tupleDesc = tupleDesc;
	}

	/**
	 * Obtains a value from the underlying native <code>HeapTuple</code> structure.
	 * @param index Index of value in the structure (one based).
	 * @return The value or <code>null</code>.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public Object getObject(int index)
	throws SQLException
	{
		return this._getObject(m_tupleDesc, index);
	}

	private native Object _getObject(TupleDesc tupleDesc, int oneBasedIndex)
	throws SQLException;
}
