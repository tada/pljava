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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Logger;

import org.postgresql.pljava.ResultSetProvider;

/**
 * Example returning (varchar,varchar) rows, one for each String-valued
 * attribute in the JDBC {@link DatabaseMetaData}.
 * @author Filip Hrbek
 */
public class MetaDataStrings implements ResultSetProvider {
	public static ResultSetProvider getDatabaseMetaDataStrings()
			throws SQLException {
		try {
			return new MetaDataStrings();
		} catch (SQLException e) {
			throw new SQLException("Error reading DatabaseMetaData",
					e.getMessage());
		}
	}

	String[] methodNames;

	String[] methodResults;

	public MetaDataStrings() throws SQLException {
		Logger log = Logger.getAnonymousLogger();

		class MethodComparator implements Comparator<Method> {
			@Override
			public int compare(Method a, Method b) {
				return a.getName().compareTo(b.getName());
			}
		}

		Connection conn = DriverManager
				.getConnection("jdbc:default:connection");
		DatabaseMetaData md = conn.getMetaData();
		Method[] m = DatabaseMetaData.class.getMethods();
		Arrays.sort(m, new MethodComparator());
		Class<?> prototype[];
		Class<?> returntype;
		Object[] args = new Object[0];
		String result = null;
		ArrayList<String> mn = new ArrayList<>();
		ArrayList<String> mr = new ArrayList<>();

		for (int i = 0; i < m.length; i++) {
			prototype = m[i].getParameterTypes();
			if (prototype.length > 0)
				continue;

			returntype = m[i].getReturnType();
			if (!returntype.equals(String.class))
				continue;

			try {
				result = (String) m[i].invoke(md, args);
				log.info("Method: " + m[i].getName() + " => Success");
			} catch (Exception e) {
				log.info("Method: " + m[i].getName() + " => " + e.getMessage());
			}

			mn.add(m[i].getName());
			mr.add(result);
		}

		methodNames = mn.toArray(new String[0]);
		methodResults = mr.toArray(new String[0]);
	}

	@Override
	public boolean assignRowValues(ResultSet receiver, int currentRow)
			throws SQLException {
		if (currentRow < methodNames.length) {
			receiver.updateString(1, methodNames[currentRow]);
			receiver.updateString(2, methodResults[currentRow]);
			return true;
		}
		return false;
	}

	@Override
	public void close() {
	}
}
