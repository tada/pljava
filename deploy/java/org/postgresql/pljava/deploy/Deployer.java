/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.deploy;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * @author Thomas Hallgren
 */
public class Deployer
{
	private static final int CMD_AMBIGUOUS = -2;
	private static final int CMD_UNKNOWN   = -1;
	private static final int CMD_UNINSTALL = 0;
	private static final int CMD_INSTALL   = 1;
	private static final int CMD_REINSTALL = 2;
	private static final int CMD_USER      = 3;
	private static final int CMD_PASSWORD  = 4;
	private static final int CMD_DATABASE  = 5;
	private static final int CMD_HOSTNAME  = 6;
	private static final int CMD_PORT      = 7;
	private static final int CMD_WINDOWS   = 8;
	
	private final Connection m_connection;

	private static final ArrayList s_commands = new ArrayList();
	
	static
	{
		s_commands.add(CMD_UNINSTALL, "uninstall");
		s_commands.add(CMD_INSTALL,   "install");
		s_commands.add(CMD_REINSTALL, "reinstall");
		s_commands.add(CMD_USER,      "user");
		s_commands.add(CMD_PASSWORD,  "password");
		s_commands.add(CMD_DATABASE,  "database");
		s_commands.add(CMD_HOSTNAME,  "host");
		s_commands.add(CMD_PORT,      "port");
		s_commands.add(CMD_WINDOWS,   "windows");
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
		out.println("    [ -port <portNumber>   ]    # default is blank");
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
		String portNumber  = null;
		boolean unix       = true;
		int cmd = CMD_UNKNOWN;

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
				case CMD_INSTALL:
				case CMD_UNINSTALL:
				case CMD_REINSTALL:
					if(cmd != CMD_UNKNOWN)
					{	
						printUsage();
						return;
					}
					cmd = optCmd;
					break;

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

				case CMD_PORT:
					if(++idx < top)
					{
						portNumber = argv[idx];
						if(portNumber.length() > 0
						&& portNumber.charAt(0) != '-')
							break;
					}
					printUsage();
					return;

				case CMD_WINDOWS:
					unix = false;
					break;

				default:
					printUsage();
					return;
				}
			}
		}
		if(cmd == CMD_UNKNOWN)
		{	
			printUsage();
			return;
		}

		try
		{
			Class.forName(driverClass);
			
			StringBuffer cc = new StringBuffer();
			cc.append("jdbc:");
			cc.append(subsystem);
			cc.append("://");
			cc.append(hostName);
			if(portNumber != null)
			{	
				cc.append(':');
				cc.append(portNumber);
			}
			cc.append('/');
			cc.append(database);
			Connection c = DriverManager.getConnection(
					cc.toString(),
					userName,
					password);

			Deployer deployer = new Deployer(c);

			if(cmd == CMD_UNINSTALL || cmd == CMD_REINSTALL)
			{
				deployer.dropSQLJSchema();
			}

			if(cmd == CMD_INSTALL || cmd == CMD_REINSTALL)
			{
				deployer.createSQLJSchema();
				deployer.initJavaHandler(unix);
				deployer.initializeSQLJSchema();
			}
			c.close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public Deployer(Connection c)
	{
		m_connection = c;
	}

	public void dropSQLJSchema()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		stmt.execute("DROP SCHEMA sqlj CASCADE");

		try
		{
			stmt.execute("DROP LANGUAGE java CASCADE");
		}
		catch(SQLException e)
		{
			/* ignore */
		}
		stmt.close();
	}

	public void createSQLJSchema()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		stmt.execute("CREATE SCHEMA sqlj");
		stmt.close();
	}

	public void initializeSQLJSchema()
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();

		stmt.execute(
			"CREATE TABLE sqlj.jar_repository(" +
			"	jarId		SERIAL PRIMARY KEY," +
			"	jarName		VARCHAR(100) UNIQUE NOT NULL," +
			"   jarOrigin   VARCHAR(500) NOT NULL," +
			"   deploymentDesc INT" +
		")");

		stmt.execute(
			"CREATE TABLE sqlj.jar_entry(" +
			"   entryId     SERIAL PRIMARY KEY," +
			"	entryName	VARCHAR(200) NOT NULL," +
			"	jarId		INT NOT NULL REFERENCES sqlj.jar_repository ON DELETE CASCADE," +
			"   entryImage  BYTEA NOT NULL," +
			"   UNIQUE(jarId, entryName)" +
			")");

		stmt.execute(
			"ALTER TABLE sqlj.jar_repository" +
			"   ADD FOREIGN KEY (deploymentDesc) REFERENCES sqlj.jar_entry ON DELETE SET NULL");

		// Create the table maintaining the class path.
		//
		stmt.execute(
			"CREATE TABLE sqlj.classpath_entry(" +
			"	schemaName	VARCHAR(30) NOT NULL," +
			"	ordinal		INT2 NOT NULL," +	// Ordinal in class path
			"	jarId		INT NOT NULL REFERENCES sqlj.jar_repository ON DELETE CASCADE," +
			"	PRIMARY KEY(schemaName, ordinal)" +
			")");

		// These are the proposed SQL standard methods.
		//
		stmt.execute(
			"CREATE FUNCTION sqlj.install_jar(VARCHAR, VARCHAR, BOOLEAN) RETURNS void" +
			"	AS 'org.postgresql.pljava.management.Commands.installJar'" +
			"	LANGUAGE java");

		stmt.execute(
			"CREATE FUNCTION sqlj.replace_jar(VARCHAR, VARCHAR, BOOLEAN) RETURNS void" +
			"	AS 'org.postgresql.pljava.management.Commands.replaceJar'" +
			"	LANGUAGE java");

		stmt.execute(
			"CREATE FUNCTION sqlj.remove_jar(VARCHAR, BOOLEAN) RETURNS void" +
			"	AS 'org.postgresql.pljava.management.Commands.removeJar'" +
			"	LANGUAGE java");

		// This function is not as proposed. It's more Java'ish. The proposal
		// using sqlj.alter_jar_path is in my opinion bloated and will not be
		// well received in the Java community. Luckily, the support is suggested
		// to be optional.
		//
		stmt.execute(
			"CREATE FUNCTION sqlj.set_classpath(VARCHAR, VARCHAR) RETURNS void" +
			"	AS 'org.postgresql.pljava.management.Commands.setClassPath'" +
			"	LANGUAGE java");

		stmt.execute(
			"CREATE FUNCTION sqlj.get_classpath(VARCHAR) RETURNS VARCHAR" +
			"	AS 'org.postgresql.pljava.management.Commands.getClassPath'" +
			"	LANGUAGE java");

		stmt.close();
	}

	public void initJavaHandler(boolean unix)
	throws SQLException
	{
		Statement stmt = m_connection.createStatement();
		stmt.execute(
			"CREATE FUNCTION sqlj.java_call_handler()" +
			" RETURNS language_handler" +
			" AS '" + (unix ? "lib" : "") + "pljava'" +
			" LANGUAGE C");

		stmt.execute("CREATE LANGUAGE java HANDLER sqlj.java_call_handler");
		stmt.close();
	}
}
