/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.example;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * This implementation uses another function that returns a set of a complex
 * type, concatenates the name and value of that type and returns this as
 * a set of a scalar type. Somewhat cumbersome way to display properties
 * but it's a good test.
 *
 * @author Thomas Hallgren
 */
public class UsingPropertiesAsResultSet
{
	public static ResultSet getProperties()
	throws SQLException
	{
		Statement  stmt = DriverManager.getConnection("jdbc:default:connection").createStatement();
		return stmt.executeQuery("SELECT * FROM propertyExample()");
	}
}
