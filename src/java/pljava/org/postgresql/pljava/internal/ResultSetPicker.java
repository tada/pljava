/*
 * Copyright (c) 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root directory of this distribution or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.jdbc.SingleRowWriter;

public class ResultSetPicker implements ResultSetProvider
{
	private final ResultSet m_resultSet;

	public ResultSetPicker(ResultSet resultSet)
	{
		m_resultSet = resultSet;
	}

	public boolean assignRowValues(ResultSet receiver, int currentRow)
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
	}
}
