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
 * The <code>ExecutionPlan</code> correspons to the execution plan obtained
 * using an internal PostgreSQL <code>SPI_prepare</code> call.
 *
 * @author Thomas Hallgren
 */
public class ExecutionPlan extends NativeStruct
{
	/**
	 * Set up a cursor that will execute the plan using the internal
	 * <code>SPI_cursor_open</code> function
	 * @param cursorName Name of the cursor or <code>null</code> for a
	 * system generated name.
	 * @param parameters Values for the parameters.
	 * @return The <code>Portal</code> that represents the opened cursor.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public native Portal cursorOpen(String cursorName, Object[] parameters)
	throws SQLException;

	/**
	 * Returns <code>true</code> if this <code>ExecutionPlan</code> can create
	 * a <code>Portal</code> using {@link #cursorOpen}. This is true if the
	 * plan contains only one regular <code>SELECT</code> query.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public native boolean isCursorPlan()
	throws SQLException;

	/**
	 * Execute the plan using the internal <code>SPI_execp</code> function.
	 * @param parameters Values for the parameters.
	 * @param rowCount The maximum number of tuples to create. A value
	 * of <code>rowCount</code> of zero is interpreted as no limit, i.e.,
	 * run to completion.
	 * @return One of the status codes declared in class {@link SPI}.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public native int execp(Object[] parameters, int rowCount)
	throws SQLException;

	/**
	 * Create an execution plan for a statement to be executed later using
	 * the internal <code>SPI_prepare</code> function.
	 * @param statement The command string.
	 * @param argTypes Oids of argument types.
	 * @return An execution plan for the prepared statement.
	 * @throws SQLException
	 */
	public native static ExecutionPlan prepare(String statement, Oid[] argTypes)
	throws SQLException;

	/**
	 * Invalidates this structure and frees up memory using the
	 * internal function <code>SPI_freeplan</code>
	 */
	public native void invalidate();
}
