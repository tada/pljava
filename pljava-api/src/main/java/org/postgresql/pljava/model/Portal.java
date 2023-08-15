/*
 * Copyright (c) 2023 Tada AB and other contributors, as listed below.
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

/**
 * Models a PostgreSQL {@code Portal}, an object representing the ongoing
 * execution of a query and capable of returning a {@link TupleDescriptor} for
 * the result, and fetching tuples of the result, either all at once, or in
 * smaller batches.
 */
public interface Portal extends AutoCloseable
{
	@Override
	void close(); // AutoCloseable without checked exceptions

	/**
	 * Returns the {@link TupleDescriptor} describing any tuples that may be
	 * fetched from this {@code Portal}.
	 */
	TupleDescriptor tupleDescriptor() throws SQLException;
}
