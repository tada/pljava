/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Some methods used for testing the SPI JDBC driver.
 *
 * @author Thomas Hallgren
 */
public class SPIActions
{
	public static void log(String str)
	{
		System.out.println(str);
		System.out.flush();
	}

	public static void transferPeopleWithSalary(int salary)
	throws SQLException
	{
		Connection conn = DriverManager.getConnection("jdbc:postgresql:pljava");
		PreparedStatement select = null;
		PreparedStatement insert = null;
		PreparedStatement delete = null;
		ResultSet rs = null;

		try
		{
			select = conn.prepareStatement(
				"SELECT id, name, salary FROM employees1 WHERE salary > ?");

			insert = conn.prepareStatement(
				"INSERT INTO employees2(id, name, salary) VALUES (?, ?, ?");

			delete = conn.prepareStatement(
				"DELETE FROM employees1 WHERE id = ?");

			select.setInt(1, salary);
			rs = select.executeQuery();
			int rowNo = 0;
			while(rs.next())
			{
				log("Processing row " + ++rowNo);
				int id = rs.getInt(1);
				String name = rs.getString(2);
				int empSal = rs.getInt(3);
				
				insert.setInt(1, id);
				insert.setString(2, name);
				insert.setInt(3, empSal);
				int nRows = insert.executeUpdate();
				log("Insert processed " + nRows + " rows");
				
				delete.setInt(1, id);
				nRows = delete.executeUpdate();
				log("Delete processed " + nRows + " rows");
			}
			if(rowNo == 0)
				log("No row found");
		}
		finally
		{
			if(select != null)
				select.close();
			if(insert != null)
				insert.close();
			if(delete != null)
				delete.close();
			conn.close();
		}
	}
}
