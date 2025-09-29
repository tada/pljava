/*
 * Copyright (c) 2023-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.model;

import java.sql.SQLException;

import java.util.List;

/**
 * Models a PostgreSQL {@code Portal}, an object representing the ongoing
 * execution of a query and capable of returning a {@link TupleDescriptor} for
 * the result, and fetching tuples of the result, either all at once, or in
 * smaller batches.
 */
public interface Portal extends AutoCloseable
{
	/**
	 * The direction modes that can be used with {@link #fetch fetch}
	 * and {@link #move move}.
	 */
	enum Direction { FORWARD, BACKWARD, ABSOLUTE, RELATIVE }

	/**
	 * A distinguished value for the <var>count</var> argument to
	 * {@link #fetch fetch} or {@link #move move}.
	 */
	long ALL = CatalogObject.Factory.INSTANCE.fetchAll();
	
	@Override
	void close(); // AutoCloseable without checked exceptions

	/**
	 * Returns the {@link TupleDescriptor} describing any tuples that may be
	 * fetched from this {@code Portal}.
	 */
	TupleDescriptor tupleDescriptor() throws SQLException;

	/**
	 * Fetches <var>count</var> more tuples (or {@link #ALL ALL} of them) in the
	 * specified direction.
	 * @return a notional List of the fetched tuples. Iterating through the list
	 * may return the same TupleTableSlot repeatedly, with each tuple in turn
	 * stored in the slot.
	 * @see "PostgreSQL documentation for SPI_scroll_cursor_fetch"
	 */
	List<TupleTableSlot> fetch(Direction dir, long count)
	throws SQLException;

	/**
	 * Moves the {@code Portal}'s current position <var>count</var> rows (or
	 * {@link #ALL ALL} possible) in the specified direction.
	 * @return the number of rows by which the position actually moved
	 * @see "PostgreSQL documentation for SPI_scroll_cursor_move"
	 */
	long move(Direction dir, long count)
	throws SQLException;
}
