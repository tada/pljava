/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.test;

import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

/**
 * Some fairly crude tests.
 *
 * @author Thomas Hallgren
 */
public class Tester
{
	private final Connection m_connection;

	public static void main(String[] argv)
	{
		try
		{
			Class.forName("org.postgresql.Driver");
			Connection c = DriverManager.getConnection(
					"jdbc:postgresql://localhost/postgres",
					"postgres",
					null);
			Tester t = new Tester(c);
			
			t.initializeLanguage();
			t.testParameters();
			t.testInsertUsernameTrigger();
			t.testModdatetimeTrigger();
			t.testSPIActions();
			t.close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public Tester(Connection conn)
	{
		m_connection = conn;
	}

	public void close()
	throws SQLException
	{
		m_connection.close();
	}

	public void testParameters()
	throws SQLException
	{
		this.testTimestamp();
		this.testInt();
	}

	public void testSPIActions()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		try
		{
			stmt.execute("DROP TABLE employees1");
			stmt.execute("DROP TABLE employees2");
		}
		catch(SQLException e)
		{
			// No problem really. Just means that it didn't exist.
			//
			System.out.println(e.getMessage());
		}

		stmt.execute(
				"CREATE TABLE employees1 (" +
				" id		int PRIMARY KEY," +
				" name		text," +	
				" salary	int)");

		stmt.execute(
				"CREATE TABLE employees2 (" +
				" id		int PRIMARY KEY," +
				" name		text," +	
				" salary	int)");

		stmt.execute("INSERT INTO employees1 VALUES(" +
			"1, 'Calvin Forrester', 10000)");
		stmt.execute("INSERT INTO employees1 VALUES(" +
			"2, 'Edwin Archer', 20000)");
		stmt.execute("INSERT INTO employees1 VALUES(" +
			"3, 'Rebecka Shawn', 30000)");
		stmt.execute("INSERT INTO employees1 VALUES(" +
			"4, 'Priscilla Johnson', 25000)");

		stmt.execute(
				"CREATE FUNCTION transferPeople(int)" +
				" RETURNS int" +
				" AS 'org.postgresql.pljava.example.SPIActions.transferPeopleWithSalary'" +
				" LANGUAGE java");

		stmt.execute("SELECT transferPeople(20000)");

		ResultSet rs = stmt.executeQuery("SELECT * FROM employees2");
		while(rs.next())
		{
			int id = rs.getInt(1);
			String name = rs.getString(2);
			int salary = rs.getInt(3);
			System.out.println(
					"Id = \"" + id +
					"\", name = \"" + name +
					"\", salary = \"" + salary + "\"");
		}
		rs.close();
	}
	public void testModdatetimeTrigger()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		try
		{
			stmt.execute("DROP TABLE mdt");
		}
		catch(SQLException e)
		{
			// No problem really. Just means that it didn't exist.
			//
			System.out.println(e.getMessage());
		}

		stmt.execute(
			"CREATE TABLE mdt (" +
			" id		int4," +
			" idesc		text," +	
			" moddate	timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL)");

		stmt.execute(
			"CREATE FUNCTION moddatetime()" +
			" RETURNS trigger" +
			" AS 'org.postgresql.pljava.example.Triggers.moddatetime'" +
			" LANGUAGE java");

		stmt.execute(
			"CREATE TRIGGER mdt_moddatetime" +
			" BEFORE UPDATE ON mdt" +
			" FOR EACH ROW" +
			" EXECUTE PROCEDURE moddatetime (moddate)");

		stmt.execute("INSERT INTO mdt VALUES (1, 'first')");
		stmt.execute("INSERT INTO mdt VALUES (2, 'second')");
		stmt.execute("INSERT INTO mdt VALUES (3, 'third')");

		ResultSet rs = stmt.executeQuery("SELECT * FROM mdt");
		while(rs.next())
		{
			int id = rs.getInt(1);
			String idesc = rs.getString(2);
			Timestamp moddate = rs.getTimestamp(3);
			System.out.println(
					"Id = \"" + id +
					"\", idesc = \"" + idesc +
					"\", moddate = \"" + moddate + "\"");
		}
		rs.close();
		
		stmt.execute("UPDATE mdt SET id = 4 WHERE id = 1");
		stmt.execute("UPDATE mdt SET id = 5 WHERE id = 2");
		stmt.execute("UPDATE mdt SET id = 6 WHERE id = 3");

		rs = stmt.executeQuery("SELECT * FROM mdt");
		while(rs.next())
		{
			int id = rs.getInt(1);
			String idesc = rs.getString(2);
			Timestamp moddate = rs.getTimestamp(3);
			System.out.println(
					"Id = \"" + id +
					"\", idesc = \"" + idesc +
					"\", moddate = \"" + moddate + "\"");
		}
		rs.close();
		stmt.close();
	}
	
	public void testInsertUsernameTrigger()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		try
		{
			stmt.execute("DROP TABLE username_test");
		}
		catch(SQLException e)
		{
			// No problem really. Just means that it didn't exist.
			//
			System.out.println(e.getMessage());
		}

		stmt.execute(
				"CREATE TABLE username_test (" +
				" name		text," +	
		" username	text not null)");

		stmt.execute(
				"CREATE FUNCTION insert_username()" +
				" RETURNS trigger" +
				" AS 'org.postgresql.pljava.example.Triggers.insertUsername'" +
		" LANGUAGE java");

		stmt.execute(
				"CREATE TRIGGER insert_usernames" +
				" BEFORE INSERT OR UPDATE ON username_test" +
				" FOR EACH ROW" +
		" EXECUTE PROCEDURE insert_username (username)");

		stmt.execute("INSERT INTO username_test VALUES ('nothing', 'thomas')");
		stmt.execute("INSERT INTO username_test VALUES ('null', null)");
		stmt.execute("INSERT INTO username_test VALUES ('empty string', '')");
		stmt.execute("INSERT INTO username_test VALUES ('space', ' ')");
		stmt.execute("INSERT INTO username_test VALUES ('tab', '	')");
		stmt.execute("INSERT INTO username_test VALUES ('name', 'name')");

		ResultSet rs = stmt.executeQuery("SELECT * FROM username_test");
		while(rs.next())
		{
			String name = rs.getString(1);
			String username = rs.getString(2);
			System.out.println("Name = \"" + name + "\", username = \"" + username + "\"");
		}
		rs.close();
		stmt.close();
	}

	public void testTimestamp()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		stmt.execute(
			"CREATE FUNCTION java_getTimestamp()" +
			" RETURNS timestamp" +
			" AS 'org.postgresql.pljava.example.Parameters.getTimestamp'" +
			" LANGUAGE java");

		stmt.execute(
			"CREATE FUNCTION java_getTimestamptz()" +
			" RETURNS timestamptz" +
			" AS 'org.postgresql.pljava.example.Parameters.getTimestamp'" +
			" LANGUAGE java");

		ResultSet rs = stmt.executeQuery(
				"SELECT java_getTimestamp(), java_getTimestamptz()");
		if(!rs.next())
			System.out.println("Unable to position ResultSet");
		else
			System.out.println(
				"Timestamp = " + rs.getTimestamp(1) +
				", Timestamptz = " + rs.getTimestamp(2));
		rs.close();
		stmt.close();
	}

	public void testInt()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		
		/*
		 * Test parameter override from int primitive to java.lang.Integer
		 */
		stmt.execute(
			"CREATE FUNCTION java_addOne(int)" +
			" RETURNS int" +
			" AS 'org.postgresql.pljava.example.Parameters.addOne(java.lang.Integer)'" +
			" LANGUAGE java");

		/*
		 * Test return value override (stipulated by the Java method rather than
		 * in the function declaration. Seems to be that way according to the
		 * SQL-2003 spec).
		 */
		stmt.execute(
			"CREATE FUNCTION nullOnEven(int)" +
			" RETURNS int" +
			" AS 'org.postgresql.pljava.example.Parameters.nullOnEven'" +
			" LANGUAGE java");

		/*
		 * Test function call within function call and function that returns an object
		 * rather than a primitive to reflect null values.
		 */
		ResultSet rs = stmt.executeQuery("SELECT java_addOne(java_addOne(54)), nullOnEven(1), nullOnEven(2)");
		if(!rs.next())
			System.out.println("Unable to position ResultSet");
		else
		{
			System.out.println("54 + 2 = " + rs.getInt(1));
			int n = rs.getInt(2);
			System.out.println("nullOnEven(1) = " + (rs.wasNull() ? "null" : Integer.toString(n)));
			n = rs.getInt(3);
			System.out.println("nullOnEven(2) = " + (rs.wasNull() ? "null" : Integer.toString(n)));
		}
		rs.close();
		stmt.close();
	}
	
	public void initializeLanguage()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		try
		{
			stmt.execute("DROP FUNCTION java_call_handler() CASCADE");
		}
		catch(SQLException e)
		{
			System.out.println("No java_call_handler seems to be present");
		}

		stmt.execute(
			"CREATE FUNCTION java_call_handler()" +
			" RETURNS language_handler" +
			" AS 'libpljava.so'" +
			" LANGUAGE C");

		stmt.execute(
			"CREATE LANGUAGE java HANDLER java_call_handler");
	}
}
