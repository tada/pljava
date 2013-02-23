/*
 * Copyright (c) 2004-2013 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 */
package org.postgresql.pljava.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.ResultSetHandle;

public class SetOfRecordTest implements ResultSetHandle {
	public static ResultSetHandle executeSelect(String selectSQL)
			throws SQLException {
		return new SetOfRecordTest(selectSQL);
	}

	private final PreparedStatement m_statement;

	public SetOfRecordTest(String selectSQL) throws SQLException {
		Connection conn = DriverManager
				.getConnection("jdbc:default:connection");
		m_statement = conn.prepareStatement(selectSQL);
	}

	@Override
	public void close() throws SQLException {
		m_statement.close();
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		return m_statement.executeQuery();
	}
}
