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
	public native Object getObject(TupleDesc tupleDesc, int index)
	throws SQLException;
}
