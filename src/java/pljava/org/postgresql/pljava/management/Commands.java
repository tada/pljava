/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.management;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.postgresql.pljava.internal.AclId;
import org.postgresql.pljava.internal.Oid;

/**
 * This methods of this class are implementations of SQLJ commands.
 * <h1>SQJL functions</h1>
 * <h2>install_jar</h2>
 * The install_jar command loads a jarfile from a location appointed by an URL
 * into the SQLJ jar repository. It is an error if a jar with the given name
 * already exists in the repository.
 * <h3>Usage</h3>
 * <blockquote><code>SELECT sqlj.install_jar(&lt;jar_url&gt;, &lt;jar_name&gt;, ;&lt;deploy&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td valign="top"><b>jar_url</b></td>
 * <td>The URL that denotes the location of the jar that should be loaded
 * </td>
 * </tr>
 * <tr>
 * <td valign="top"><b>jar_name</b></td>
 * <td>This is the name by which this jar can be referenced once it has been
 * loaded</td>
 * </tr>
 * <tr>
 * <td valign="top"><b>deploy</b></td>
 * <td>True if the jar should be deployed according to a {@link
 * org.postgresql.pljava.management.SQLDeploymentDescriptor deployment descriptor},
 * false otherwise</td>
 * </tr>
 * </table></blockquote>
 * <h2>replace_jar</h2>
 * The replace_jar will replace a loaded jar with another jar. Use this command
 * to update already loaded files. It's an error if the jar is not found.
 * <h3>Usage</h3>
 * <blockquote><code>SELECT sqlj.replace_jar(&lt;jar_url&gt;, &lt;jar_name&gt;, ;&lt;redeploy&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td valign="top"><b>jar_url</b></td>
 * <td>The URL that denotes the location of the jar that should be loaded
 * </td>
 * </tr>
 * <tr>
 * <td valign="top"><b>jar_name</b></td>
 * <td>The name of the jar to be replaced</td>
 * </tr>
 * <tr>
 * <td valign="top"><b>redeploy</b></td>
 * <td>True if the old and new jar should be undeployed and deployed according
 * to their respective {@link
 * org.postgresql.pljava.management.SQLDeploymentDescriptor deployment descriptors},
 * false otherwise</td>
 * </tr>
 * </table></blockquote>
 * <h2>remove_jar</h2>
 * The remove_jar will drop the jar from the jar repository. Any classpath that
 * references this jar will be updated accordingly. It's an error if the jar is
 * not found.
 * <h3>Usage</h3>
 * <blockquote><code>SELECT sqlj.remove_jar(&lt;jar_name&gt;, &lt;undeploy&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td valign="top"><b>jar_name</b></td>
 * <td>The name of the jar to be removed</td>
 * </tr>
 * <tr>
 * <td valign="top"><b>undeploy</b></td>
 * <td>True if the jar should be undeployed according to its {@link
 * org.postgresql.pljava.management.SQLDeploymentDescriptor deployment descriptor},
 * false otherwise</td>
 * </tr>
 * </table></blockquote>
 * <h2>get_classpath</h2>
 * The get_classpath will return the classpath that has been defined for the
 * given schema or NULL if the schema has no classpath. It's an error if the
 * given schema does not exist.
 * <h3>Usage</h3>
 * <blockquote><code>SELECT sqlj.get_classpath(&lt;schema&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td><b>schema</b></td>
 * <td>The name of the schema</td>
 * </tr>
 * </table></blockquote>
 * <h2>set_classpath</h2>
 * The set_classpath will define a classpath for the given schema. A classpath
 * consists of a colon separated list of jar names. It's an error if the given
 * schema does not exist or if one or more jar names references non existent
 * jars.
 * <h3>Usage</h3>
 * <blockquote><code>SELECT sqlj.set_classpath(&lt;schema&gt;, &lt;classpath&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td><b>schema</b></td>
 * <td>The name of the schema</td>
 * </tr>
 * <tr>
 * <td><b>classpath</b></td>
 * <td>The colon separated list of jar names</td>
 * </tr>
 * </table></blockquote>
 *
 * @author Thomas Hallgren
 */
public class Commands
{
	/**
	 * Installs a new Jar in the database jar repository under name <code>jarName</code>.
	 * Once installed classpaths can be defined that refrences this jar. This
	 * method is exposed in SQL as
	 * <code>sqlj.install_jar(VARCHAR, VARCHAR, BOOLEAN)</code>.
	 * 
	 * @param urlString
	 *            The location of the jar that will be installed.
	 * @param jarName
	 *            The name by which the system will refer to this jar.
	 * @param deploy
	 *            If set, execute install commands found in the deployment descriptor.
	 * @throws SQLException
	 *             if the <code>jarName</code> contains characters that are
	 *             invalid or if the named jar already exists in the system.
	 * @see #setClassPath
	 */
	public static void installJar(String urlString, String jarName, boolean deploy)
	throws SQLException
	{
		assertJarName(jarName);

		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		try
		{
			if(getJarId(conn, jarName) >= 0)
				throw new SQLException("A jar named '" + jarName + "' already exists");

			PreparedStatement stmt = conn.prepareStatement(
				"INSERT INTO sqlj.jar_repository(jarName, jarOrigin) VALUES(?, ?)");
			try
			{
				stmt.setString(1, jarName);
				stmt.setString(2, urlString);
				if(stmt.executeUpdate() != 1)
					throw new SQLException("Jar repository insert did not insert 1 row");
			}
			finally
			{
				try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
			}

			int jarId = getJarId(conn, jarName);
			if(jarId < 0)
				throw new SQLException("Unable to obtain id of '" + jarName + "'");

			addClassImages(conn, jarId, urlString);
			if(deploy)
				deployInstall(conn, jarId);
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
	 * @param urlString The location of the jar that will be installed.
	 * @param jarName The name by which the system referes this jar.
	 * @param redeploy If set, execute remove commands found in the deployment
	 * descriptor of the old jar and install commands found in the deployment
	 * descriptor of the new jar.
	 * @throws SQLException if the named jar cannot be found in the repository.
	 */
	public static void replaceJar(String urlString, String jarName, boolean redeploy)
	throws SQLException
	{
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		try
		{
			int jarId = getJarId(conn, jarName);
			if(jarId < 0)
				throw new SQLException("No Jar named '" + jarName + "' is known to the system");

			if(redeploy)
				deployRemove(conn, jarId);
		
			PreparedStatement stmt = conn.prepareStatement(
				"UPDATE sqlj.jar_repository SET jarOrigin = ?, deploymentDesc = NULL WHERE jarId = ?");
			try
			{
				stmt.setString(1, urlString);
				stmt.setInt(2, jarId);
				if(stmt.executeUpdate() != 1)
					throw new SQLException("Jar repository update did not update 1 row");
			}
			finally
			{
				try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
			}

			stmt = conn.prepareStatement("DELETE FROM sqlj.jar_entry WHERE jarId = ?");
			try
			{
				stmt.setInt(1, jarId);
				stmt.executeUpdate();
			}
			finally
			{
				try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
			}
			addClassImages(conn, jarId, urlString);
			if(redeploy)
				deployInstall(conn, jarId);
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
	 * @param undeploy If set, execute remove commands found in the deployment
	 * descriptor of the jar.
	 * @throws SQLException if the named jar cannot be found in the repository.
	 */
	public static void removeJar(String jarName, boolean undeploy)
	throws SQLException
	{
		assertJarName(jarName);

		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		try
		{
			int jarId = getJarId(conn, jarName);
			if(jarId < 0)
				throw new SQLException("No Jar named '" + jarName + "' is known to the system");
			
			if(undeploy)
				deployRemove(conn, jarId);

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
			
			if("public".equals(schemaName))
			{
				if(!AclId.getSessionUser().isSuperuser())
					throw new SQLException("Permission denied. Only a super user can set the classpath of the public schema");
			}
			else
			{
				schemaName = schemaName.toLowerCase();
				Oid schemaId = getSchemaId(conn, schemaName);
				if(schemaId == null)
					throw new SQLException("No such schema: " + schemaName);
				if(!AclId.getSessionUser().hasSchemaCreatePermission(schemaId))
					throw new SQLException("Permission denied. User must have create permission on the target schema in order to set the classpath");
			}

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
	
						int jarId = getJarId(stmt, jarName);
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
						stmt.executeUpdate();
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
	 * Return the classpath that has been defined for the schema named <code>schemaName</code>
	 *
	 * This method is exposed in SQL as <code>sqlj.get_classpath(VARCHAR)</code>.
	 * 
	 * @param schemaName
	 *            Name of the schema for which this path is valid.
	 * @return The defined classpath or <code>null</code> if this schema has no classpath.
	 * @throws SQLException
	 */
	public static String getClassPath(String schemaName)
	throws SQLException
	{
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		try
		{
			if(schemaName == null || schemaName.length() == 0)
				schemaName = "public";
			else
				schemaName = schemaName.toLowerCase();

			PreparedStatement stmt = conn.prepareStatement(
				"SELECT r.jarName" +
				" FROM sqlj.jar_repository r INNER JOIN sqlj.classpath_entry c ON r.jarId = c.jarId" +
				" WHERE c.schemaName = ? ORDER BY c.ordinal");
			
			try
			{
				stmt.setString(1, schemaName);
				ResultSet rs = stmt.executeQuery();
				try
				{
					StringBuffer buf = null;
					while(rs.next())
					{
						if(buf == null)
							buf = new StringBuffer();
						else
							buf.append(':');
						buf.append(rs.getString(1));
					}
					return (buf == null) ? null : buf.toString();
				}
				finally
				{
					try { rs.close(); } catch(SQLException e) { /* ignore */ }
				}
			}
			finally
			{
				try { stmt.close(); } catch(SQLException e) { /* ignore */ }
			}
		}
		finally
		{
			try { conn.close(); } catch(SQLException e) { /* ignore */ }
		}
	}

	protected static void addClassImages(Connection conn, int jarId, String urlString)
	throws SQLException
	{
		InputStream urlStream = null;
		PreparedStatement stmt = null;
		PreparedStatement descIdStmt = null;
		ResultSet rs = null;

		try
		{
			int deployImageId = -1;
			URL url = new URL(urlString);
			urlStream = url.openStream();

			byte[] buf = new byte[1024];
			ByteArrayOutputStream img = new ByteArrayOutputStream();
			stmt = conn.prepareStatement(
				"INSERT INTO sqlj.jar_entry(entryName, jarId, entryImage) VALUES(?, ?, ?)");

			JarInputStream jis = new JarInputStream(urlStream);
			for(;;)
			{
				JarEntry je = jis.getNextJarEntry();
				if(je == null)
					break;

				if(je.isDirectory())
					continue;

				String entryName = je.getName();
				Attributes attrs = je.getAttributes();
				
				boolean isDepDescr = false;
				if(attrs != null)
				{	
					isDepDescr = "true".equalsIgnoreCase(
								attrs.getValue("SQLJDeploymentDescriptor"));

					if(isDepDescr && deployImageId >= 0)
						throw new SQLException("Only one SQLJDeploymentDescriptor allowed");
				}

				int nBytes;
				img.reset();
				while((nBytes = jis.read(buf)) > 0)
					img.write(buf, 0, nBytes);
				jis.closeEntry();

				stmt.setString(1, entryName);
				stmt.setInt(2, jarId);
				stmt.setBytes(3, img.toByteArray());
				if(stmt.executeUpdate() != 1)
					throw new SQLException("Jar entry insert did not insert 1 row");
				
				if(isDepDescr)
				{
					descIdStmt = conn.prepareStatement(
							"SELECT entryId FROM sqlj.jar_entry" +
							" WHERE jarId = ? AND entryName = ?");
					descIdStmt.setInt(1, jarId);
					descIdStmt.setString(2, entryName);
					rs = descIdStmt.executeQuery();
					if(!rs.next())
						throw new SQLException("Failed to refecth row in sqlj.jar_entry");
					
					deployImageId = rs.getInt(1);
				}
			}
			if(deployImageId >= 0)
			{
				stmt.close();
				stmt = conn.prepareStatement(
					"UPDATE sqlj.jar_repository SET deploymentDesc = ? WHERE jarId = ?");
				stmt.setInt(1, deployImageId);
				stmt.setInt(2, jarId);
				if(stmt.executeUpdate() != 1)
					throw new SQLException("Jar repository update did not insert 1 row");
			}
		}
		catch(IOException e)
		{
			throw new SQLException("I/O exception reading jar file: " + e.getMessage());
		}
		finally
		{
			if(urlStream != null)
				try { urlStream.close(); } catch(IOException e) { /* ignore */ }
			if(rs != null)
				try { rs.close(); } catch(SQLException e) { /* ignore */ }
			if(descIdStmt != null)
				try { descIdStmt.close(); } catch(SQLException e) { /* ignore */ }
			if(stmt != null)
				try { stmt.close(); } catch(SQLException e) { /* ignore */ }
		}
	}

	protected static void deployInstall(Connection conn, int jarId)
	throws SQLException
	{
		SQLDeploymentDescriptor depDesc = getDeploymentDescriptor(conn, jarId);
		if(depDesc != null)
			depDesc.install(conn);
	}

	protected static void deployRemove(Connection conn, int jarId)
	throws SQLException
	{
		SQLDeploymentDescriptor depDesc = getDeploymentDescriptor(conn, jarId);
		if(depDesc != null)
			depDesc.remove(conn);
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
			return getJarId(stmt, jarName);
		}
		finally
		{
			try { stmt.close(); } catch(SQLException e) { /* ignore close errors */ }
		}
	}

	protected static int getJarId(PreparedStatement stmt, String jarName)
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

	protected static SQLDeploymentDescriptor getDeploymentDescriptor(Connection conn, int jarId)
	throws SQLException
	{
		PreparedStatement stmt = conn.prepareStatement(
			"SELECT e.entryImage" +
			" FROM sqlj.jar_repository r INNER JOIN sqlj.jar_entry e" +
			"   ON r.deploymentDesc = e.entryId" +
			" WHERE r.jarId = ?");
		try
		{
			stmt.setInt(1, jarId);
			ResultSet rs = stmt.executeQuery();
			try
			{
				if(!rs.next())
					return null;

				byte[] bytes = rs.getBytes(1);
				if(bytes.length == 0)
					return null;
				
				// Accodring to the SQLJ standard, this entry must be
				// UTF8 encoded.
				//
				return  new SQLDeploymentDescriptor(new String(bytes, "UTF8"), "postgresql");
			}
			catch (UnsupportedEncodingException e)
			{
				// Excuse me? No UTF8 encoding?
				//
				throw new SQLException("JVM does not support UTF8!!");
			}
			catch(ParseException e)
			{
				throw new SQLException(e.getMessage() + " at " + e.getErrorOffset());
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
}
