/*
 * Copyright (c) 2018- Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.ResultSetHandle;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;

/**
 * Demonstrate holdability of ResultSets (test for issue 168).
 *<p>
 * The {@code stashResultSet} method will execute a query and save its
 * {@code ResultSet} (wrapped in a {@code ResultSetHandle} in a static
 * for later retrieval. The {@code unstashResultSet} method, called later
 * in the same transaction, retrieves and returns the result set. A call after
 * the transaction has ended will fail.
 *<p>
 * The query selects all rows from {@code pg_description}, a table that should
 * always exist, with more rows than the default connection {@code fetchSize},
 * to ensure the stashed {@code ResultSet} has work to do.
 */
@SQLAction(requires={"Holdability.stash", "Holdability.unstash"}, install={

	"SELECT javatest.stashResultSet()",

	"SELECT " +
	" CASE" +
	"  WHEN 1000 < count(*) THEN javatest.logmessage('INFO', 'Holdability OK')"+
	"  ELSE javatest.logmessage('WARNING', 'Holdability suspicious')" +
	" END" +
	" FROM javatest.unstashResultSet()"
})
public class Holdability implements ResultSetHandle
{
	private static Holdability s_stash;

	private ResultSet m_resultSet;
	private Statement m_stmt;

	private Holdability(Statement s, ResultSet rs)
	{
		m_stmt = s;
		m_resultSet = rs;
	}

	/**
	 * Query all rows from {@code pg_description}, but stash the
	 * {@code ResultSet} for retrieval later in the same transaction by
	 * {@code unstashResultSet}.
	 *<p>
	 * This must be called in an open, multiple-statement (non-auto) transaction
	 * to have any useful effect.
	 */
	@Function(schema="javatest", provides="Holdability.stash")
	public static void stashResultSet() throws SQLException
	{
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		PreparedStatement s = c.prepareStatement(
			"SELECT * FROM pg_catalog.pg_description");
		ResultSet rs = s.executeQuery();
		s_stash = new Holdability(s, rs);
	}

	/**
	 * Return the results stashed earlier in the same transaction by
	 * {@code stashResultSet}.
	 */
	@Function(
		schema="javatest",
		type="pg_catalog.pg_description",
		provides="Holdability.unstash"
	)
	public static ResultSetHandle unstashResultSet() throws SQLException
	{
		return s_stash;
	}

	/*
	 * Necessary methods to implement ResultSetHandle follow.
	 */

	@Override
	public ResultSet getResultSet() throws SQLException
	{
		return m_resultSet;
	}

	@Override
	public void close() throws SQLException
	{
		Connection c = m_stmt.getConnection();
		m_stmt.close();
		c.close();
	}
}
