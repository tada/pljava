/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
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

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.List;

import org.postgresql.pljava.Adapter;

/**
 * A temporary test jig during TupleTableSlot development, not intended to last.
 */
public interface SlotTester
{
	/**
	 * Unwrap a {@link ResultSet} instance from the legacy JDBC layer as a
	 * {@link Portal} instance so results can be retrieved using new API.
	 * @param rs a ResultSet, which can only be an SPIResultSet obtained from
	 * the legacy JDBC implementation, not yet closed or used to fetch anything,
	 * and will be closed.
	 */
	Portal unwrapAsPortal(ResultSet rs) throws SQLException;

	/**
	 * Execute <var>query</var>, returning its complete result as a {@code List}
	 * of {@link TupleTableSlot}.
	 */
	List<TupleTableSlot> test(String query);

	/**
	 * Return one of the predefined {@link Adapter} instances, given knowledge
	 * of the class name and static final field name within that class inside
	 * PL/Java's implementation module.
	 *<p>
	 * Example:
	 *<pre>
	 * adapterPlease(
	 *  "org.postgresql.pljava.pg.adt.Primitives", "FLOAT8_INSTANCE");
	 *</pre>
	 */
	Adapter adapterPlease(String clazz, String field)
	throws ReflectiveOperationException;

	/**
	 * A temporary marker interface used on classes or interfaces whose
	 * static final fields should be visible to {@code adapterPlease}.
	 */
	interface Visible
	{
	}
}
