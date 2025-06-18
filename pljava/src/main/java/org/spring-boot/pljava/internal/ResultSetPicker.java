/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB - Thomas Hallgren
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.ResultSetHandle;
import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.jdbc.SingleRowWriter;

/**
 * An adapter class used internally when a set-returning user function returns
 * a {@code ResultSetHandle}, presenting it as a {@link ResultSetProvider}
 * instead.
 *<p>
 * Note on the current implementation:
 * this class operates by fetching every field of every row of the result set
 * as a Java object via the one-argument {@code getObject}, then storing it into
 * the writable result set supplied by PL/Java. Apart from being rather
 * inefficient, this can involve conversions through legacy types (such as
 * {@code java.sql.Timestamp} when the JSR 310 {@code java.time} conversions are
 * better specified). In cases where that isn't acceptable, the user function
 * should be declared to return {@code ResultSetProvider} and do this work
 * itself.
 */
public class ResultSetPicker implements ResultSetProvider.Large
{
	private final ResultSetHandle m_resultSetHandle;
	private final ResultSet m_resultSet;

	public ResultSetPicker(ResultSetHandle resultSetHandle)
	throws SQLException
	{
		m_resultSetHandle = resultSetHandle;
		m_resultSet = resultSetHandle.getResultSet();
	}

	public boolean assignRowValues(ResultSet receiver, long currentRow)
	throws SQLException
	{
		if(m_resultSet == null || !m_resultSet.next())
			return false;

		((SingleRowWriter)receiver).copyRowFrom(m_resultSet);
		return true;
	}

	public void close()
	throws SQLException
	{
		m_resultSet.close();
		m_resultSetHandle.close();
	}
}
