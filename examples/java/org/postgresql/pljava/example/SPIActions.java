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
				"SELECT id, name, salary FROM employees WHERE salary > ?");
			
			insert = conn.prepareStatement(
				"INSERT INTO costlyEmployees(id, name, salary) VALUES (?, ?, ?");
			
			delete = conn.prepareStatement(
				"DELETE FROM employees WHERE id = ?");

			select.setInt(1, salary);
			rs = select.executeQuery();
			int rowNo = 0;
			while(rs.next())
			{
				System.out.println("Processing row " + ++rowNo);
				System.out.flush();
				int id = rs.getInt(1);
				String name = rs.getString(2);
				int empSal = rs.getInt(3);
				
				insert.setInt(1, id);
				insert.setString(2, name);
				insert.setInt(3, empSal);
				int nRows = insert.executeUpdate();
				System.out.println("Insert processed " + nRows + " rows");
				System.out.flush();
				
				delete.setInt(1, id);
				nRows = delete.executeUpdate();
				System.out.println("Delete processed " + nRows + " rows");
				System.out.flush();
			}
			if(rowNo == 0)
				System.out.println("No row found");
			System.out.flush();
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
