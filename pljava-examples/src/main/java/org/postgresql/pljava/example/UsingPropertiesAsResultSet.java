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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.ResultSetHandle;

/**
 * This implementation uses another function that returns a set of a complex
 * type and returns the ResultSet produced by a query.
 * 
 * @author Thomas Hallgren
 */
public class UsingPropertiesAsResultSet implements ResultSetHandle {
	public static ResultSetHandle getProperties() throws SQLException {
		return new UsingPropertiesAsResultSet();
	}

	private PreparedStatement m_statement;

	@Override
	public void close() throws SQLException {
		m_statement.close();
		m_statement = null;
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		m_statement = DriverManager.getConnection("jdbc:default:connection")
				.prepareStatement("SELECT * FROM propertyExample()");
		return m_statement.executeQuery();
	}
}
