/*
 * Copyright (c) 2004-2018 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>Tuple</code> correspons to the internal PostgreSQL
 * <code>HeapTuple</code>.
 *
 * @author Thomas Hallgren
 */
public class Tuple extends JavaWrapper
{
	Tuple(long pointer)
	{
		super(pointer);
	}

	/**
	 * Obtains a value from the underlying native <code>HeapTuple</code>
	 * structure.
	 *<p>
	 * Conversion to a JDBC 4.1 specified class is best effort, if the native
	 * type system knows how to do so; otherwise, the return value can be
	 * whatever would have been returned in the legacy case. Caller beware!
	 * @param tupleDesc The Tuple descriptor for this instance.
	 * @param index Index of value in the structure (one based).
	 * @param type Desired Java class of the result, if the JDBC 4.1 version
	 * of {@code getObject} has been called; null in all the legacy cases.
	 * @return The value or <code>null</code>.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public Object getObject(TupleDesc tupleDesc, int index, Class<?> type)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getObject(this.getNativePointer(),
				tupleDesc.getNativePointer(), index, type);
		}
	}

	/**
	 * Calls the backend function heap_freetuple(HeapTuple tuple)
	 * @param pointer The native pointer to the source HeapTuple
	 */
	protected native void _free(long pointer);

	private static native Object _getObject(
		long pointer, long tupleDescPointer, int index, Class<?> type)
	throws SQLException;
}
