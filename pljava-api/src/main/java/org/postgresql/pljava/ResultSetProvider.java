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
 *   Purdue University
 */
package org.postgresql.pljava;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * An implementation of this interface is returned from functions and procedures
 * that are declared to return <code>SET OF</code> a complex type. This
 * interface is appropriate when the function will be generating the returned
 * set on the fly; if it will have a {@link ResultSet} obtained from a query,
 * it should just return {@link ResultSetHandle} instead. Functions that
 * return <code>SET OF</code> a simple type should simply return an
 * {@link java.util.Iterator Iterator}.
 *<p>
 * For a function declared to return {@code SETOF RECORD} rather than a specific
 * complex type known in advance, the {@code receiver} argument to
 * {@link #assignRowValues(ResultSet,int) assignRowValues} can be queried to
 * learn the number, names, and types of columns expected by the caller.
 * @author Thomas Hallgren
 */
public interface ResultSetProvider
{
	/**
	 * This method is called once for each row that should be returned from
	 * a procedure that returns a set of rows. The receiver
	 * is a {@code SingleRowWriter}
	 * instance that is used for capturing the data for the row.
	 *<p>
	 * If the return type is {@code RECORD} rather than a specific complex type,
	 * SQL requires a column definition list to follow any use of the function
	 * in a query. The {@link ResultSet#getMetaData() ResultSetMetaData}
	 * of {@code receiver} can be used here to learn the number, names,
	 * and types of the columns expected by the caller. (It can also be used in
	 * the case of a specific complex type, but in that case the names and types
	 * are probably already known.)
	 * @param receiver Receiver of values for the given row.
	 * @param currentRow Row number, zero on the first call, incremented by one
	 * on each subsequent call.
	 * @return {@code true} if a new row was provided, {@code false}
	 * if not (end of data).
	 * @throws SQLException
	 */
	boolean assignRowValues(ResultSet receiver, int currentRow)
	throws SQLException;
	
	/**
	 * Called after the last row has returned or when the query evaluator decides
	 * that it does not need any more rows.
	 */
	void close()
	throws SQLException;
}
