/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
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
	public native int getCount()
	throws SQLException;

	/**
	 * Returns the <code>Tuple</code> at the given index.
	 * @param index Index of desired slot. First slot has index zero. 
	 */
	public native Tuple getSlot(int position)
	throws SQLException;

	/**
	 * Returns the <code>TupleDesc</code> for the <code>Tuple</code> instances of
	 * this table. 
	 */
	public native TupleDesc getTupleDesc()
	throws SQLException;

	/**
	 * Invalidates this structure and call the internal function <code>
	 * SPI_freetuptable</code> to free up memory.
	 */
	public native void invalidate();
}
