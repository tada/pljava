/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
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
import java.util.ArrayList;
import java.io.PrintStream;

/**
 * Some fairly crude tests. All tests are confided to the schema &quot;javatest&quot;
 *
 * @author Thomas Hallgren
 */
public class Tester
{
	private static final int CMD_AMBIGUOUS = -2;
	private static final int CMD_UNKNOWN   = -1;
	private static final int CMD_USER      = 0;
	private static final int CMD_PASSWORD  = 1;
	private static final int CMD_DATABASE  = 2;
	private static final int CMD_HOSTNAME  = 3;

	private final Connection m_connection;

	private static final ArrayList s_commands = new ArrayList();

	static
	{
		s_commands.add(CMD_USER,      "user");
		s_commands.add(CMD_PASSWORD,  "password");
		s_commands.add(CMD_DATABASE,  "database");
		s_commands.add(CMD_HOSTNAME,  "host");
	}

	private static final int getCommand(String arg)
	{
		int top = s_commands.size();
		int candidateCmd = CMD_UNKNOWN;
		for(int idx = 0; idx < top; ++idx)
		{
			if(((String)s_commands.get(idx)).startsWith(arg))
			{
				if(candidateCmd != CMD_UNKNOWN)
					return CMD_AMBIGUOUS;
				candidateCmd = idx;
			}
		}
		return candidateCmd;
	}

	public static void printUsage()
	{
		PrintStream out = System.err;
		out.println("usage: java -jar deploy.jar");
		out.println("    {-install | -uninstall | -reinstall}");
		out.println("    [ -host <hostName>     ]    # default is localhost");
		out.println("    [ -database <database> ]    # default is postgres");
		out.println("    [ -user <userName>     ]    # default is postgres");
		out.println("    [ -password <password> ]    # default is no password");
		out.println("    [ -windows ]                # If the server is on a Windows machine");
	}

	public static void main(String[] argv)
	{
		String driverClass = "org.postgresql.Driver";
		String hostName    = "localhost";
		String database    = "postgres";
		String userName    = "postgres";
		String subsystem   = "postgresql";
		String password    = null;

		int top = argv.length;
		for(int idx = 0; idx < top; ++idx)
		{
			String arg = argv[idx];
			if(arg.length() < 2)
			{	
				printUsage();
				return;
			}

			if(arg.charAt(0) == '-')
			{
				int optCmd = getCommand(arg.substring(1));
				switch(optCmd)
				{
				case CMD_USER:
					if(++idx < top)
					{
						userName = argv[idx];
						if(userName.length() > 0
								&& userName.charAt(0) != '-')
							break;
					}
					printUsage();
					return;

				case CMD_PASSWORD:
					if(++idx < top)
					{
						password = argv[idx];
						if(password.length() > 0
								&& password.charAt(0) != '-')
							break;
					}
					printUsage();
					return;

				case CMD_DATABASE:
					if(++idx < top)
					{
						database = argv[idx];
						if(database.length() > 0
								&& database.charAt(0) != '-')
							break;
					}
					printUsage();
					return;

				case CMD_HOSTNAME:
					if(++idx < top)
					{
						hostName = argv[idx];
						if(hostName.length() > 0
								&& hostName.charAt(0) != '-')
							break;
					}
					printUsage();
					return;

				default:
					printUsage();
					return;
				}
			}
		}
		try
		{
			Class.forName(driverClass);
			Connection c = DriverManager.getConnection(
					"jdbc:" + subsystem + "://" + hostName + '/' + database,
					userName,
					password);

			Tester t = new Tester(c);
			t.initialize();
			t.testParameters();
			t.testInsertUsernameTrigger();
			t.testModdatetimeTrigger();
			t.testSPIActions();
			t.testComplexReturn();
			t.testSetReturn();
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
		stmt.execute(
				"CREATE TABLE javatest.employees1 (" +
				" id		int PRIMARY KEY," +
				" name		varchar(200)," +	
				" salary	int)");

		stmt.execute(
				"CREATE TABLE javatest.employees2 (" +
				" id		int PRIMARY KEY," +
				" name		varchar(200)," +	
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
				"CREATE FUNCTION javatest.transferPeople(int)" +
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

	public void testComplexReturn()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		stmt.execute(
		"CREATE TYPE javatest._testSetReturn AS (base integer, incbase integer, ctime timestamptz)");

		stmt.execute(
			"CREATE FUNCTION javatest.complexReturnExample(int, int)" +
			" RETURNS _testSetReturn" +
			" AS 'org.postgresql.pljava.example.ComplexReturn.complexReturn'" +
			" IMMUTABLE LANGUAGE java");

		stmt.execute(
			"CREATE FUNCTION javatest.complexReturnToString(_testSetReturn)" +
			" RETURNS VARCHAR" +
			" AS 'org.postgresql.pljava.example.ComplexReturn.makeString'" +
			" IMMUTABLE LANGUAGE java");

		ResultSet rs = stmt.executeQuery("SELECT complexReturnToString(complexReturnExample(1, 5))");
		while(rs.next())
		{
			String str = rs.getString(1);
			System.out.println(str);
		}
	}

	public void testSetReturn()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		stmt.execute(
			"CREATE FUNCTION javatest.setReturnExample(int, int)" +
			" RETURNS SETOF javatest._testSetReturn" +
			" AS 'org.postgresql.pljava.example.ComplexReturn.setReturn'" +
			" IMMUTABLE LANGUAGE java");

		ResultSet rs = stmt.executeQuery("SELECT base, incbase, ctime FROM setReturnExample(1, 5)");
		while(rs.next())
		{
			int base = rs.getInt(1);
			int incbase = rs.getInt(2);
			Timestamp ctime = rs.getTimestamp(3);
			System.out.println(
					"Base = \"" + base +
					"\", incbase = \"" + incbase +
					"\", ctime = \"" + ctime + "\"");
		}
	}

	public void testModdatetimeTrigger()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		stmt.execute(
			"CREATE TABLE javatest.mdt (" +
			" id		int4," +
			" idesc		text," +	
			" moddate	timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL)");

		stmt.execute(
			"CREATE FUNCTION javatest.moddatetime()" +
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
		stmt.execute(
			"CREATE TABLE javatest.username_test (" +
			" name		text," +	
			" username	text not null)");

		stmt.execute(
			"CREATE FUNCTION javatest.insert_username()" +
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
			"CREATE FUNCTION javatest.java_getTimestamp()" +
			" RETURNS timestamp" +
			" AS 'org.postgresql.pljava.example.Parameters.getTimestamp'" +
			" LANGUAGE java");

		stmt.execute(
			"CREATE FUNCTION javatest.java_getTimestamptz()" +
			" RETURNS timestamptz" +
			" AS 'org.postgresql.pljava.example.Parameters.getTimestamp'" +
			" LANGUAGE java");

		stmt.execute(
			"CREATE FUNCTION javatest.java_print(date)" +
			" RETURNS void" +
			" AS 'org.postgresql.pljava.example.Parameters.print'" +
			" LANGUAGE java");

		stmt.execute(
			"CREATE FUNCTION javatest.java_print(timetz)" +
			" RETURNS void" +
			" AS 'org.postgresql.pljava.example.Parameters.print'" +
			" LANGUAGE java");

		stmt.execute(
			"CREATE FUNCTION javatest.java_print(timestamptz)" +
			" RETURNS void" +
			" AS 'org.postgresql.pljava.example.Parameters.print'" +
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

		// Test parameter overloading. Set log_min_messages (in posgresql.conf)
		// to INFO or higher and watch the result.
		//
		stmt.execute("SELECT java_print(current_date)");
		stmt.execute("SELECT java_print(current_time)");
		stmt.execute("SELECT java_print(current_timestamp)");
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
			"CREATE FUNCTION javatest.java_addOne(int)" +
			" RETURNS int" +
			" AS 'org.postgresql.pljava.example.Parameters.addOne(java.lang.Integer)'" +
			" LANGUAGE java");

		/*
		 * Test return value override (stipulated by the Java method rather than
		 * in the function declaration. Seems to be that way according to the
		 * SQL-2003 spec).
		 */
		stmt.execute(
			"CREATE FUNCTION javatest.nullOnEven(int)" +
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

	public void initialize()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		try
		{
			stmt.execute("DROP SCHEMA javatest CASCADE");
		}
		catch(SQLException e)
		{
		}
		stmt.execute("CREATE SCHEMA javatest");
		stmt.execute("SET search_path TO javatest,public");
	}
}
