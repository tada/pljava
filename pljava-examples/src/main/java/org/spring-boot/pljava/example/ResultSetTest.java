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
 *   Filip Hrbek
 */
package org.postgresql.pljava.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Provides a {@link #executeSelect function} that takes any SQL {@code SELECT}
 * query as a string, executes it, and returns the {@code ResultSet} produced
 * as a single string column, the first row being a header, then one per row
 * of the {@code ResultSet}, semicolons delimiting the original columns.
 * @author Filip Hrbek
 */
public class ResultSetTest {
	public static Iterator<String> executeSelect(String selectSQL) throws SQLException {
		if (!selectSQL.toUpperCase().trim().startsWith("SELECT ")) {
			throw new SQLException("Not a SELECT statement");
		}

		return new ResultSetTest(selectSQL).iterator();
	}

	private ArrayList<String> m_results;

	public ResultSetTest(String selectSQL) throws SQLException {
		Connection conn = DriverManager
				.getConnection("jdbc:default:connection");
		m_results = new ArrayList<>();
		StringBuffer result;

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery(selectSQL);
		ResultSetMetaData rsmd = rs.getMetaData();

		int cnt = rsmd.getColumnCount();
		result = new StringBuffer();
		for (int i = 1; i <= cnt; i++) {
			result.append((rsmd.getColumnName(i) + "("
					+ rsmd.getColumnClassName(i) + ")").replaceAll("(\\\\|;)",
					"\\$1") + ";");
		}
		m_results.add(result.toString());

		while (rs.next()) {
			result = new StringBuffer();
			Object rsObject = null;
			for (int i = 1; i <= cnt; i++) {
				rsObject = rs.getObject(i);
				if (rsObject == null) {
					rsObject = "<NULL>";
				}
				result.append(rsObject.toString()
						.replaceAll("(\\\\|;)", "\\$1") + ";");
			}
			m_results.add(result.toString());
		}
		rs.close();
	}

	public void close() {
	}

	private Iterator<String> iterator() {
		return m_results.iterator();
	}
}
