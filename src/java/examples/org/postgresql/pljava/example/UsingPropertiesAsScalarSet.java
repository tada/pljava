/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2004 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;

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
	public static Iterator getProperties()
	throws SQLException
	{
		StringBuffer bld = new StringBuffer();
		ArrayList list = new ArrayList();
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		Statement  stmt = conn.createStatement();
		try
		{
			ResultSet rs = stmt.executeQuery("SELECT name, value FROM propertyExample()");
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
