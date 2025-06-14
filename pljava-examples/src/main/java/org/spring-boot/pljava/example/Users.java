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

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.postgresql.pljava.ResultSetHandle;

/**
 * Uses the {@code ResultSetHandle} interface to implement two functions,
 * {@link #listNonSupers listNonSupers} and {@link #listSupers listSupers},
 * returning the corresponding subsets of rows from {@code pg_user}.
 */
public class Users implements ResultSetHandle {
	public static ResultSetHandle listNonSupers() {
		return new Users("usesuper = false");
	}

	public static ResultSetHandle listSupers() {
		return new Users("usesuper = true");
	}

	private final String m_filter;

	private Statement m_statement;

	public Users(String filter) {
		m_filter = filter;
	}

	@Override
	public void close() throws SQLException {
		m_statement.close();
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		m_statement = DriverManager.getConnection("jdbc:default:connection")
				.createStatement();
		return m_statement.executeQuery("SELECT * FROM pg_user WHERE "
				+ m_filter);
	}
}
