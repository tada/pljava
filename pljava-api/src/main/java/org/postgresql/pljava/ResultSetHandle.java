/*
 * Copyright (c) 2004-2015 Tada AB and other contributors, as listed below.
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
 * that are declared to return <code>SET OF</code> a complex type in the form
 * of a {@link java.sql.ResultSet}. The primary motivation for this interface is
 * that an implementation that returns a ResultSet must be able to close the
 * connection and statement when no more rows are requested.
 *
 * A function returning a <code>SET OF</code> a complex type generated on the
 * fly (rather than obtained from a query) would return
 * {@link ResultSetProvider} instead. One returning a <code>SET OF</code> a
 * simple type should simply return an {@link java.util.Iterator}.
 * @author Thomas Hallgren
 */
public interface ResultSetHandle
{
	/**
	 * An implementation of this method will probably execute a query
	 * and return the result of that query.
	 * @return The ResultSet that represents the rows to be returned.
	 * @throws SQLException
	 */
	ResultSet getResultSet()
	throws SQLException;

	/**
	 * Called after the last row has returned or when the query evaluator decides
	 * that it does not need any more rows.
	 */
	void close()
	throws SQLException;
}
