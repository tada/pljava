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
 * The <code>HeapTupleHeader</code> correspons to the internal PostgreSQL
 * <code>HeapTupleHeader</code> struct.
 *
 * @author Thomas Hallgren
 */
public class HeapTupleHeader extends NativeStruct
{
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
			return this._getObject(index);
		}
	}

	/**
	 * Obtains a value from the underlying native <code>HeapTupleHeader</code>
	 * structure.
	 * @param name Name of the attribute.
	 * @return The value or <code>null</code>.
	 * @throws SQLException If the underlying native structure has gone stale
	 * or if the name denotes a non existant attribute.
	 */
	public Object getObject(String name)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getObject(name);
		}
	}

	/**
	 * Obtains the numeric index of attribute <code>name</code> from the
	 * underlying native <code>HeapTupleHeader</code> structure.
	 * @param name Name of the attribute.
	 * @return The index of the attribute or -1 if no such attribute exists.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public int getAttributeIndex(String name)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getAttributeIndex(name);
		}
	}

	private native int _getAttributeIndex(String name)
	throws SQLException;

	private native Object _getObject(int index)
	throws SQLException;

	private native Object _getObject(String name)
	throws SQLException;
}
