/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.management;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.postgresql.pljava.internal.Oid;
import org.postgresql.pljava.sqlj.Loader;

/**
 * @author Thomas Hallgren
 */
public class Commands
{
	/**
	 * Returns the <code>byte[]</code> image representing the contents read using the
	 * opened input stream of the given <code>URL</code>.
	 * @param urlString The string representation of a valid {@link java.net.URL}.
	 * @return The bytes read from the location that the <code>URL</code> points to.
	 * @throws IOException
	 */
	public static byte[] getURLImage(String urlString)
	throws SQLException
	{
		try
		{
			URL url = new URL(urlString);
			InputStream jarInput = url.openStream();
			try
			{
				ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream();
				byte[] transferBuffer = new byte[4096];
				int rdLen;
				while((rdLen = jarInput.read(transferBuffer)) > 0)
					imageBuffer.write(transferBuffer, 0, rdLen);
				return imageBuffer.toByteArray();
			}
			finally
			{
				try { jarInput.close(); } catch(IOException e) { /* ignore close errors */ }
			}
		}
		catch(IOException e)
		{
			throw new SQLException("I/O exception: " + e.getMessage());
		}
	}

	/**
	 * Installs a new Jar in the database jar repository under name <code>jarName</code>.
	 * Once installed classpaths can be defined that refrences this jar. This
	 * method is exposed in SQL as
	 * <code>sqlj.install_jar(VARCHAR, VARCHAR, BOOLEAN)</code>.
	 * 
	 * @param jarName
	 *            The name by which the system will refer to this jar.
	 * @param urlString
	 *            The location of the jar that will be installed.
	 * @param deploy
	 *            Ignored at present
	 * @throws SQLException
	 *             if the <code>jarName</code> contains characters that are
	 *             invalid or if the named jar already exists in the system.
	 * @see #setClassPath
	 */
	public static void installJar(String jarName, String urlString, boolean deploy)
	throws SQLException
	{
		assertJarName(jarName);

		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		try
		{
			if(getJarId(conn, jarName) >= 0)
				throw new SQLException("A jar named '" + jarName + "' already exists");
			byte[] bytes = getURLImage(urlString);
			
			PreparedStatement stmt = conn.prepareStatement(
				"INSERT INTO sqlj.jar_repository(jarName, jarImage) VALUES(?, ?)");
			try
			{
				stmt.setString(1, jarName);
				stmt.setBytes(2, bytes);
				if(stmt.executeUpdate() != 1)
					throw new SQLException("Jar repository insert did not insert 1 row");
			}
			finally
			{
				try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
			}
		}
		finally
		{
			try { conn.close(); } catch(SQLException e) { /* ignore close errors */ }
		}
	}

	/**
	 * Replaces the image of jar named <code>jarName</code> in the database jar
	 * repository. This method is exposed in SQL as <code>
	 * sqlj.replace_jar(VARCHAR, VARCHAR, BOOLEAN)</code>.
	 * @param jarName The name by which the system referes this jar.
	 * @param urlString The location of the jar that will be installed.
	 * @param redeploy Ignored at present
	 * @throws SQLException if the named jar cannot be found in the repository.
	 */
	public static void replaceJar(String jarName, String urlString, boolean redeploy)
	throws SQLException
	{
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		try
		{
			int jarId = getJarId(conn, jarName);
			if(jarId < 0)
				throw new SQLException("No Jar named '" + jarName + "' is known to the system");
	
			byte[] bytes = getURLImage(urlString);
			
			PreparedStatement stmt = conn.prepareStatement(
				"UPDATE sqlj.jar_repository SET jarImage = ? WHERE jarId = ?");
			try
			{
				stmt.setBytes(1, bytes);
				stmt.setInt(2, jarId);
				if(stmt.executeUpdate() != 1)
					throw new SQLException("Jar repository update did not update 1 row");
				invalidateJarLoaders(conn, jarId);
			}
			finally
			{
				try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
			}
		}
		finally
		{
			try { conn.close(); } catch(SQLException e) { /* ignore close errors */ }
		}
	}

	/**
	 * Removes the jar named <code>jarName</code> from the database jar
	 * repository. Class path entries that references this jar will also be
	 * removed (just the entry, not the whole path). This method is exposed in
	 * SQL as <code>sqlj.remove_jar(VARCHAR, BOOLEAN)</code>.
	 * @param jarName The name by which the system referes this jar.
	 * @param undeploy Ignored at present
	 * @throws SQLException if the named jar cannot be found in the repository.
	 */
	public static void removeJar(String jarName, String url, boolean undeploy)
	throws SQLException
	{
		assertJarName(jarName);

		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		try
		{
			int jarId = getJarId(conn, jarName);
			if(jarId < 0)
				throw new SQLException("No Jar named '" + jarName + "' is known to the system");
			
			// Must invalidate the loaders first. The classpaths will be removed
			// by a cascading delete.
			//
			invalidateJarLoaders(conn, jarId);

			PreparedStatement stmt = conn.prepareStatement(
				"DELETE FROM sqlj.jar_repository WHERE jarId = ?");
			try
			{
				stmt.setInt(1, jarId);
				if(stmt.executeUpdate() != 1)
					throw new SQLException("Jar repository update did not update 1 row");
			}
			finally
			{
				try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
			}
		}
		finally
		{
			try { conn.close(); } catch(SQLException e) { /* ignore close errors */ }
		}	
	}

	/**
	 * Define the class path to use for Java functions, triggers, and procedures
	 * that are created in the schema named <code>schemaName</code>
	 *
	 * This method is exposed in SQL as <code>sqlj.set_classpath(VARCHAR, VARCHAR)</code>.
	 * 
	 * @param schemaName
	 *            Name of the schema for which this path is valid.
	 * @param path
	 *            Colon separated list of names. Each name must denote the name
	 *            of a jar that is present in the jar repository.
	 * @throws SQLException
	 *             If no schema can be found with the givene name, or if one or
	 *             several names of the path denotes a nonexistant jar file.
	 */
	public static void setClassPath(String schemaName, String path)
	throws SQLException
	{
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		try
		{
			if(schemaName == null || schemaName.length() == 0)
				schemaName = "public";
			else
				schemaName = schemaName.toLowerCase();

			if(getSchemaId(conn, schemaName) == null)
				throw new SQLException("No such schema: " + schemaName);

			PreparedStatement stmt;
			ArrayList entries = null;
			if(path != null && path.length() > 0)
			{	
				// Collect and verify that all entries in the path represents a
				// valid jar
				//
				entries = new ArrayList();
				stmt = conn.prepareStatement(
					"SELECT jarId FROM sqlj.jar_repository WHERE jarName = ?");
				try
				{
					for(;;)
					{
						int colon = path.indexOf(':');
						String jarName;
						if(colon >= 0)
						{	
							jarName = path.substring(0, colon);
							path = path.substring(colon + 1);
						}
						else
							jarName = path;
	
						int jarId = getJarId(conn, stmt, jarName);
						if(jarId < 0)
							throw new SQLException("No such jar: " + jarName);
		
						entries.add(new Integer(jarId));
						if(colon < 0)
							break;
					}
				}
				finally
				{
					try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
				}
			}

			// Delete the old classpath
			//
			stmt = conn.prepareStatement(
				"DELETE FROM sqlj.classpath_entry WHERE schemaName = ?");
			try
			{
				stmt.setString(1, schemaName);
				stmt.executeUpdate();
			}
			finally
			{
				try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
			}

			if(entries != null)
			{	
				// Insert the new path.
				//
				stmt = conn.prepareStatement(
					"INSERT INTO sqlj.classpath_entry(schemaName, ordinal, jarId) VALUES(?, ?, ?)");
				try
				{
					int top = entries.size();
					for(int idx = 0; idx < top; ++idx)
					{
						int jarId = ((Integer)entries.get(idx)).intValue();
						stmt.setString(1, schemaName);
						stmt.setInt(2, idx + 1);
						stmt.setInt(3, jarId);
						stmt.executeQuery();
					}
				}
				finally
				{
					try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
				}
			}
		}
		finally
		{
			try { conn.close(); } catch(SQLException e) { /* ignore close errors */ }
		}
	}

	/**
	 * Throws an exception if the given name cannot be used as the name of
	 * a jar.
	 * @param jarName The naem to check. 
	 * @throws IOException
	 */
	protected static void assertJarName(String jarName)
	throws SQLException
	{
		if(jarName != null)
		{
			int len = jarName.length();
			if(len > 0 && Character.isJavaIdentifierStart(jarName.charAt(0)))
			{
				int idx = 1;
				for(; idx < len; ++idx)
					if(!Character.isJavaIdentifierPart(jarName.charAt(idx)))
						break;

				if(idx == len)
					return;
			}
		}
		throw new SQLException("The jar name '" + jarName + "' is not a valid name");
	}

	/**
	 * Returns the primary key identifier for the given Jar.
	 * @param conn The connection to use for the query.
	 * @param jarName The name of the jar.
	 * @return The primary key value of the given jar or <code>-1</code> if no
	 * such jar is found.
	 * @throws SQLException
	 */
	protected static int getJarId(Connection conn, String jarName)
	throws SQLException
	{
		PreparedStatement stmt = conn.prepareStatement(
		"SELECT jarId FROM sqlj.jar_repository WHERE jarName = ?");
		try
		{
			return getJarId(conn, stmt, jarName);
		}
		finally
		{
			try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
		}
	}

	protected static int getJarId(Connection conn, PreparedStatement stmt, String jarName)
	throws SQLException
	{
		stmt.setString(1, jarName);
		ResultSet rs = stmt.executeQuery();
		try
		{
			if(!rs.next())
				return -1;
			return rs.getInt(1);
		}
		finally
		{
			try { rs.close(); } catch(SQLException e) { /* ignore close errors */ }
		}
	}

	/**
	 * Returns the Oid for the given Schema.
	 * @param conn The connection to use for the query.
	 * @param schemaName The name of the schema.
	 * @return The Oid of the given schema or <code>null</code> if no such
	 * schema is found.
	 * @throws SQLException
	 */
	protected static Oid getSchemaId(Connection conn, String schemaName)
	throws SQLException
	{
		PreparedStatement stmt = conn.prepareStatement(
			"SELECT oid FROM pg_namespace WHERE nspname = ?");
		try
		{
			stmt.setString(1, schemaName);
			ResultSet rs = stmt.executeQuery();
			try
			{
				if(!rs.next())
					return null;
				return (Oid)rs.getObject(1);
			}
			finally
			{
				try { rs.close(); } catch(SQLException e) { /* ignore close errors */ }
			}
		}
		finally
		{
			try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
		}
	}

	/**
	 * Invalidates all {@link java.lang.ClassLoader ClassLoaders} that &quot;see&quot;
	 * the jar who&squot;s primary key is jarId
	 * @param conn The database connection to use for the query.
	 * @param jarId The primary key of the jar.
	 * @throws SQLException
	 */
	protected static void invalidateJarLoaders(Connection conn, int jarId)
	throws SQLException
	{
		PreparedStatement stmt = conn.prepareStatement(
		"SELECT DISTINCT schemaName FROM sqlj.classpath_entry WHERE jarId = ?");
		try
		{
			stmt.setInt(1, jarId);
			ResultSet rs = stmt.executeQuery();
			try
			{
				while(rs.next())
					Loader.invalidateSchemaLoader(rs.getString(1));
			}
			finally
			{
				try { rs.close(); } catch(SQLException e) { /* ignore close errors */ }
			}
		}
		finally
		{
			try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
		}
	}
}
