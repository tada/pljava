/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>HeapTupleHeader</code> correspons to the internal PostgreSQL
 * <code>HeapTupleHeader</code> struct.
 *
 * @author Thomas Hallgren
 */
public class HeapTupleHeader extends JavaWrapper
{
	HeapTupleHeader(long pointer)
	{
		super(pointer);
	}

	/**
	 * Obtains a value from the underlying native <code>HeapTupleHeader</code>
	 * structure.
	 * @param index Index of value in the structure (one based).
	 * @return The value or <code>null</code>.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public Object getObject(int index)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getObject(this.getNativePointer(), index);
		}
	}

	/**
	 * Obtains the TupleDesc from the underlying native <code>HeapTupleHeader
	 * </code> structure.
	 * @return The TupleDesc that describes this tuple.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public TupleDesc getTupleDesc()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getTupleDesc(this.getNativePointer());
		}
	}

	protected native void _free(long pointer);

	private static native TupleDesc _getTupleDesc(long pointer)
	throws SQLException;

	private static native Object _getObject(long pointer, int index)
	throws SQLException;
}
