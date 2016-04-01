/*
 * Copyright (c) 2004-2016 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava.example.annotation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;

import org.postgresql.pljava.annotation.Function;

/**
 * This implementation uses another function that returns a set of a complex
 * type, concatenates the name and value of that type and returns this as
 * a set of a scalar type. Somewhat cumbersome way to display properties
 * but it's a good test.
 *
 * @author Thomas Hallgren
 */
public class UsingPropertiesAsScalarSet
{
	@Function
	public static Iterator<String> getProperties()
	throws SQLException
	{
		StringBuilder bld = new StringBuilder();
		ArrayList<String> list = new ArrayList<>();
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		Statement  stmt = conn.createStatement();
		try
		{
			ResultSet rs = stmt.executeQuery(
				"SELECT name, value FROM propertyExampleAnno()");
			try
			{
				while(rs.next())
				{
					bld.setLength(0);
					bld.append(rs.getString(1));
					bld.append(" = ");
					bld.append(rs.getString(2));
					list.add(bld.toString());
				}
				return list.iterator();
			}
			finally
			{
				try { rs.close(); } catch(SQLException e) {}
			}
		}
		finally
		{
			try { stmt.close(); } catch(SQLException e) {}
		}
	}
}
