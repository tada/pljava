/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>Tuple</code> correspons to the internal PostgreSQL
 * <code>HeapTuple</code>.
 *
 * @author Thomas Hallgren
 */
public class Tuple extends NativeStruct
{
	/**
	 * Obtains a value from the underlying native <code>HeapTuple</code>
	 * structure.
	 * @param tupleDesc The Tuple descriptor for this instance.
	 * @param index Index of value in the structure (one based).
	 * @return The value or <code>null</code>.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public Object getObject(TupleDesc tupleDesc, int index)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getObject(tupleDesc, index);
		}
	}

	private native Object _getObject(TupleDesc tupleDesc, int index)
	throws SQLException;
}
