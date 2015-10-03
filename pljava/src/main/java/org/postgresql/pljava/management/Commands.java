/*
 * Copyright (c) 2004-2015 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava.management;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.io.UnsupportedEncodingException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.postgresql.pljava.internal.AclId;
import org.postgresql.pljava.internal.Backend;
import org.postgresql.pljava.internal.Oid;
import org.postgresql.pljava.jdbc.SQLUtils;
import org.postgresql.pljava.sqlj.Loader;

/**
 * This methods of this class are implementations of SQLJ commands.
 * <h1>SQLJ functions</h1>
 * <h2>install_jar</h2>
 * The install_jar command loads a jar file from a location appointed by an URL
 * or a binary image that constitutes the contents of a jar file into the SQLJ
 * jar repository. It is an error if a jar with the given name already exists in
 * the repository.
 * <h3>Usage 1</h3>
 * <blockquote><code>SELECT sqlj.install_jar(&lt;jar_url&gt;, &lt;jar_name&gt;, &lt;deploy&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td valign="top"><b>jar_url</b></td>
 * <td>The URL that denotes the location of the jar that should be loaded </td>
 * </tr>
 * <tr>
 * <td valign="top"><b>jar_name</b></td>
 * <td>This is the name by which this jar can be referenced once it has been
 * loaded</td>
 * </tr>
 * <tr>
 * <td valign="top"><b>deploy</b></td>
 * <td>True if the jar should be deployed according to a {@link
 * org.postgresql.pljava.management.SQLDeploymentDescriptor deployment
 * descriptor}, false otherwise</td>
 * </tr>
 * </table></blockquote>
 * <h3>Usage 2</h3>
 * <blockquote><code>SELECT sqlj.install_jar(&lt;jar_image&gt;, &lt;jar_name&gt;, &lt;deploy&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td valign="top"><b>jar_image</b></td>
 * <td>The byte array that constitutes the contents of the jar that should be
 * loaded </td>
 * </tr>
 * <tr>
 * <td valign="top"><b>jar_name</b></td>
 * <td>This is the name by which this jar can be referenced once it has been
 * loaded</td>
 * </tr>
 * <tr>
 * <td valign="top"><b>deploy</b></td>
 * <td>True if the jar should be deployed according to a {@link
 * org.postgresql.pljava.management.SQLDeploymentDescriptor deployment
 * descriptor}, false otherwise</td>
 * </tr>
 * </table></blockquote>
 * <h2>replace_jar</h2>
 * The replace_jar will replace a loaded jar with another jar. Use this command
 * to update already loaded files. It's an error if the jar is not found.
 * <h3>Usage 1</h3>
 * <blockquote><code>SELECT sqlj.replace_jar(&lt;jar_url&gt;, &lt;jar_name&gt;, &lt;redeploy&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td valign="top"><b>jar_url</b></td>
 * <td>The URL that denotes the location of the jar that should be loaded </td>
 * </tr>
 * <tr>
 * <td valign="top"><b>jar_name</b></td>
 * <td>The name of the jar to be replaced</td>
 * </tr>
 * <tr>
 * <td valign="top"><b>redeploy</b></td>
 * <td>True if the old and new jar should be undeployed and deployed according
 * to their respective {@link
 * org.postgresql.pljava.management.SQLDeploymentDescriptor deployment
 * descriptors}, false otherwise</td>
 * </tr>
 * </table></blockquote>
 * <h3>Usage 2</h3>
 * <blockquote><code>SELECT sqlj.replace_jar(&lt;jar_image&gt;, &lt;jar_name&gt;, &lt;redeploy&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td valign="top"><b>jar_image</b></td>
 * <td>The byte array that constitutes the contents of the jar that should be
 * loaded </td>
 * </tr>
 * <tr>
 * <td valign="top"><b>jar_name</b></td>
 * <td>The name of the jar to be replaced</td>
 * </tr>
 * <tr>
 * <td valign="top"><b>redeploy</b></td>
 * <td>True if the old and new jar should be undeployed and deployed according
 * to their respective {@link
 * org.postgresql.pljava.management.SQLDeploymentDescriptor deployment
 * descriptors}, false otherwise</td>
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
 * org.postgresql.pljava.management.SQLDeploymentDescriptor deployment
 * descriptor}, false otherwise</td>
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
 * <h2>add_type_mapping</h2>
 * The add_type_mapping defines the mapping between an SQL type and a Java
 * class.
 * <h3>Usage</h3>
 * <blockquote><code>SELECT sqlj.add_type_mapping(&lt;sqlTypeName&gt;, &lt;className&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td><b>sqlTypeName</b></td>
 * <td>The name of the SQL type. The name can be qualified with a
 * schema (namespace). If the schema is omitted, it will be resolved according
 * to the current setting of the search_path.</td>
 * </tr>
 * <tr>
 * <td><b>className</b></td>
 * <td>The name of the class. The class must be found in the classpath in
 * effect for the current schema</td>
 * </tr>
 * </table></blockquote>
 * <h2>drop_type_mapping</h2>
 * The drop_type_mapping removes the mapping between an SQL type and a Java
 * class.
 * <h3>Usage</h3>
 * <blockquote><code>SELECT sqlj.drop_type_mapping(&lt;sqlTypeName&gt;);</code>
 * </blockquote>
 * <h3>Parameters</h3>
 * <blockquote><table>
 * <tr>
 * <td><b>sqlTypeName</b></td>
 * <td>The name of the SQL type. The name can be qualified with a
 * schema (namespace). If the schema is omitted, it will be resolved according
 * to the current setting of the search_path.</td>
 * </tr>
 * </table></blockquote>
 * 
 * @author Thomas Hallgren
 */
public class Commands
{
	private final static Logger s_logger = Logger.getLogger(Commands.class
		.getName());

	/**
	 * Reads the jar found at the specified URL and stores the entries in the
	 * jar_entry table.
	 * 
	 * @param jarId The id used for the foreign key to the jar_repository table
	 * @param urlStream An InputStream (opened on what may have been a URL)
	 * @param sz The expected size of the stream, used as a worst-case
	 * mark/reset limit. The caller might pass -1 if the URLConnection can't
	 * determine a size in advance (a generous guess will be made in that case).
	 * @throws SQLException
	 */
	public static void addClassImages(int jarId, InputStream urlStream, int sz)
	throws SQLException
	{
		PreparedStatement stmt = null;
		PreparedStatement descIdFetchStmt = null;
		PreparedStatement descIdStoreStmt = null;
		ResultSet rs = null;

		try
		{
			byte[] buf = new byte[1024];
			ByteArrayOutputStream img = new ByteArrayOutputStream();
			stmt = SQLUtils
				.getDefaultConnection()
				.prepareStatement(
					"INSERT INTO sqlj.jar_entry(entryName, jarId, entryImage) VALUES(?, ?, ?)");

			BufferedInputStream bis = new BufferedInputStream( urlStream);
			String manifest = rawManifest( bis, sz);
			JarInputStream jis = new JarInputStream(bis);
			if(manifest != null)
			{
				PreparedStatement us = SQLUtils
					.getDefaultConnection()
					.prepareStatement(
						"UPDATE sqlj.jar_repository SET jarManifest = ? WHERE jarId = ?");
				try
				{
					us.setString(1, manifest);
					us.setInt(2, jarId);
					if(us.executeUpdate() != 1)
						throw new SQLException(
							"Jar repository update did not update 1 row");
				}
				finally
				{
					SQLUtils.close(us);
				}
			}

			for(;;)
			{
				JarEntry je = jis.getNextJarEntry();
				if(je == null)
					break;

				if(je.isDirectory())
					continue;

				String entryName = je.getName();

				int nBytes;
				img.reset();
				while((nBytes = jis.read(buf)) > 0)
					img.write(buf, 0, nBytes);
				jis.closeEntry();

				stmt.setString(1, entryName);
				stmt.setInt(2, jarId);
				stmt.setBytes(3, img.toByteArray());
				if(stmt.executeUpdate() != 1)
					throw new SQLException(
						"Jar entry insert did not insert 1 row");
			}

			Matcher ddr = ddrSection.matcher( null != manifest ? manifest : "");
			Matcher cnt = mfCont.matcher( "");
			for ( int ordinal = 0; ddr.find(); ++ ordinal )
			{
				String entryName = cnt.reset( ddr.group( 1)).replaceAll( "");
				if ( descIdFetchStmt == null )
					descIdFetchStmt = SQLUtils.getDefaultConnection()
						.prepareStatement(
							"SELECT entryId FROM sqlj.jar_entry"
								+ " WHERE jarId = ? AND entryName = ?");
				descIdFetchStmt.setInt(1, jarId);
				descIdFetchStmt.setString(2, entryName);
				rs = descIdFetchStmt.executeQuery();
				if(!rs.next())
					throw new SQLException(
						"Failed to refetch row in sqlj.jar_entry");

				int deployImageId = rs.getInt(1);

				if ( descIdStoreStmt == null )
					descIdStoreStmt = SQLUtils.getDefaultConnection()
						.prepareStatement(
							"INSERT INTO sqlj.jar_descriptor"
								+ " (jarId, entryId, ordinal) VALUES"
								+ " ( ?, ?, ? )");
				descIdStoreStmt.setInt(1, jarId);
				descIdStoreStmt.setInt(2, deployImageId);
				descIdStoreStmt.setInt(3, ordinal);
				if ( descIdStoreStmt.executeUpdate() != 1 )
					throw new SQLException(
						"Jar deployment descriptor insert did not insert " +
						"1 row");
			}
		}
		catch(IOException e)
		{
			throw new SQLException("I/O exception reading jar file: "
				+ e.getMessage());
		}
		finally
		{
			SQLUtils.close(rs);
			SQLUtils.close(descIdStoreStmt);
			SQLUtils.close(descIdFetchStmt);
			SQLUtils.close(stmt);
		}
	}

	private final static Pattern ddrSection = Pattern.compile(
	    "(?<=[\\r\\n])Name: ((?:.|(?:\\r\\n?|\\n) )+)(?:(?:\\r\\n?|\\n))" +
		"(?:[^\\r\\n]+(?:\\r\\n?|\\n)(?![\\r\\n]))*" +
		"SQLJDeploymentDescriptor: (?:(?:\\r\\n?|\\r) )*TRUE(?!\\S)",
		Pattern.CASE_INSENSITIVE
	);

	private final static Pattern mfCont = Pattern.compile( "(?:\\r\\n?|\\n) ");

	/**
	 * Read and return a manifest, rewinding the buffered input stream.
	 *
	 * The caller needs to construct a BufferedInputStream over its raw input
	 * stream, and indicate its expected size (for mark/reset purposes). This
	 * method returns the manifest as a String if there is one, else null, and
	 * resets the buffered input stream so the caller can treat it as a
	 * JarInputStream to read the rest of it.
	 *
	 * Why such an exercise? The SQL/JRT specs provide that deployment
	 * descriptors are to be taken, for install, in the order they
	 * are named _in the manifest_, and for remove in the reverse of
	 * their order in the manifest ... at least according to a committee
	 * draft 5CD2-13-JRT-2006-01 I got my hands on; see sections 11.1
	 * and 11.3. That's lovely, but of course Java's Manifest class
	 * doesn't expose the order of its per-file entries! Plan B could be
	 * to use Manifest.write() into a buffer and parse that for the
	 * order, but the API doesn't promise to write it back out in the
	 * original order, and in fact Oracle's implementation doesn't. That
	 * leaves little choice but to sneak in ahead of the JarInputStream and
	 * pluck out the original manifest as a zip entry.
	 */
	private static String rawManifest( BufferedInputStream bis, int markLimit)
	throws IOException
	{
		// If the caller can't say how long the stream is, this mark() limit
		// should be plenty
		bis.mark( markLimit > 0 ? markLimit : 32*1024*1024);
		ZipInputStream zis = new ZipInputStream( bis);
		for ( ZipEntry ze; null != (ze = zis.getNextEntry()); )
		{
			if ( "META-INF/MANIFEST.MF".equals( ze.getName()) )
			{
				StringBuilder sb = new StringBuilder();
				// I'll take my chances on a required charset not being there!
				CharsetDecoder u8 = Charset.forName( "UTF-8").newDecoder();
				InputStreamReader isr = new InputStreamReader( zis, u8);
				char[] b = new char[512];
				for ( int got; -1 != (got = isr.read(b)); )
					sb.append(b, 0, got);
				zis.closeEntry();
				bis.reset();
				return sb.toString();
			}
			zis.closeEntry();
		}
		bis.reset();
		return null;
	}

	/**
	 * Defines the mapping between an SQL type and a Java class.
	 * 
	 * @param sqlTypeName The name of the SQL type. The name can be
	 *            qualified with a schema (namespace). If the schema is omitted,
	 *            it will be resolved according to the current setting of the
	 *            <code>search_path</code>.
	 * @param javaClassName The name of the class. The class must be found in
	 *            the classpath in effect for the current schema
	 * @throws SQLException
	 */
	public static void addTypeMapping(String sqlTypeName, String javaClassName)
	throws SQLException
	{
		PreparedStatement stmt = null;
		try
		{
			ClassLoader loader = Loader.getCurrentLoader();
			Class cls = loader.loadClass(javaClassName);
			if(!SQLData.class.isAssignableFrom(cls))
				throw new SQLException("Class " + javaClassName
					+ " does not implement java.sql.SQLData");

			sqlTypeName = getFullSqlName(sqlTypeName);
			stmt = SQLUtils
				.getDefaultConnection()
				.prepareStatement(
					"INSERT INTO sqlj.typemap_entry(javaName, sqlName) VALUES(?,?)");
			stmt.setString(1, javaClassName);
			stmt.setString(2, sqlTypeName);
			stmt.executeUpdate();
		}
		catch(ClassNotFoundException e)
		{
			throw new SQLException("No such class: " + javaClassName);
		}
		finally
		{
			SQLUtils.close(stmt);
		}
		Loader.clearSchemaLoaders();
	}

	/**
	 * Drops the mapping between an SQL type and a Java class.
	 * 
	 * @param sqlTypeName The name of the SQL type. The name can be
	 *            qualified with a schema (namespace). If the schema is omitted,
	 *            it will be resolved according to the current setting of the
	 *            <code>search_path</code>.
	 * @throws SQLException
	 */
	public static void dropTypeMapping(String sqlTypeName) throws SQLException
	{
		PreparedStatement stmt = null;
		try
		{
			sqlTypeName = getFullSqlName(sqlTypeName);
			stmt = SQLUtils.getDefaultConnection().prepareStatement(
				"DELETE FROM sqlj.typemap_entry WHERE sqlName = ?");
			stmt.setString(1, sqlTypeName);
			stmt.executeUpdate();
		}
		finally
		{
			SQLUtils.close(stmt);
		}
		Loader.clearSchemaLoaders();
	}

	/**
	 * Return the classpath that has been defined for the schema named
	 * <code>schemaName</code> This method is exposed in SQL as
	 * <code>sqlj.get_classpath(VARCHAR)</code>.
	 * 
	 * @param schemaName Name of the schema for which this path is valid.
	 * @return The defined classpath or <code>null</code> if this schema has
	 *         no classpath.
	 * @throws SQLException
	 */
	public static String getClassPath(String schemaName) throws SQLException
	{
		ResultSet rs = null;
		PreparedStatement stmt = null;
		try
		{
			if(schemaName == null || schemaName.length() == 0)
				schemaName = "public";
			else
				schemaName = schemaName.toLowerCase();

			stmt = SQLUtils
				.getDefaultConnection()
				.prepareStatement(
					"SELECT r.jarName"
						+ " FROM sqlj.jar_repository r INNER JOIN sqlj.classpath_entry c ON r.jarId = c.jarId"
						+ " WHERE c.schemaName = ? ORDER BY c.ordinal");

			stmt.setString(1, schemaName);
			rs = stmt.executeQuery();
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
			SQLUtils.close(rs);
			SQLUtils.close(stmt);
		}
	}

	public static String getCurrentSchema() throws SQLException
	{
		Statement stmt = SQLUtils.getDefaultConnection().createStatement();
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery("SELECT current_schema()");
			if(!rs.next())
				throw new SQLException("Unable to obtain current schema");
			return rs.getString(1);
		}
		finally
		{
			SQLUtils.close(rs);
			SQLUtils.close(stmt);
		}
	}

	/**
	 * Installs a new Jar in the database jar repository under name
	 * <code>jarName</code>. Once installed classpaths can be defined that
	 * refrences this jar. This method is exposed in SQL as
	 * <code>sqlj.install_jar(BYTEA, VARCHAR, BOOLEAN)</code>.
	 * 
	 * @param image The byte array that constitutes the jar content.
	 * @param jarName The name by which the system will refer to this jar.
	 * @param deploy If set, execute install commands found in the deployment
	 *            descriptor.
	 * @throws SQLException if the <code>jarName</code> contains characters
	 *             that are invalid or if the named jar already exists in the
	 *             system.
	 * @see #setClassPath
	 */
	public static void installJar(byte[] image, String jarName, boolean deploy)
	throws SQLException
	{
		installJar("streamed byte image", jarName, deploy, image);
	}

	/**
	 * Installs a new Jar in the database jar repository under name
	 * <code>jarName</code>. Once installed classpaths can be defined that
	 * refrences this jar. This method is exposed in SQL as
	 * <code>sqlj.install_jar(VARCHAR, VARCHAR, BOOLEAN)</code>.
	 * 
	 * @param urlString The location of the jar that will be installed.
	 * @param jarName The name by which the system will refer to this jar.
	 * @param deploy If set, execute install commands found in the deployment
	 *            descriptor.
	 * @throws SQLException if the <code>jarName</code> contains characters
	 *             that are invalid or if the named jar already exists in the
	 *             system.
	 * @see #setClassPath
	 */
	public static void installJar(String urlString, String jarName,
		boolean deploy) throws SQLException
	{
		installJar(urlString, jarName, deploy, null);
	}

	/**
	 * Removes the jar named <code>jarName</code> from the database jar
	 * repository. Class path entries that references this jar will also be
	 * removed (just the entry, not the whole path). This method is exposed in
	 * SQL as <code>sqlj.remove_jar(VARCHAR, BOOLEAN)</code>.
	 * 
	 * @param jarName The name by which the system referes this jar.
	 * @param undeploy If set, execute remove commands found in the deployment
	 *            descriptor of the jar.
	 * @throws SQLException if the named jar cannot be found in the repository.
	 */
	public static void removeJar(String jarName, boolean undeploy)
	throws SQLException
	{
		assertJarName(jarName);
		AclId[] ownerRet = new AclId[1];
		int jarId = getJarId(jarName, ownerRet);
		if(jarId < 0)
			throw new SQLException("No Jar named '" + jarName
					       + "' is known to the system", 
					       "4600B");

		AclId user = AclId.getSessionUser();
		if(!(user.isSuperuser() || user.equals(ownerRet[0])))
			throw new SecurityException(
				"Only super user or owner can remove a jar");

		if(undeploy)
			deployRemove(jarId, jarName);

		PreparedStatement stmt = SQLUtils
			.getDefaultConnection()
			.prepareStatement("DELETE FROM sqlj.jar_repository WHERE jarId = ?");
		try
		{
			stmt.setInt(1, jarId);
			if(stmt.executeUpdate() != 1)
				throw new SQLException(
					"Jar repository update did not update 1 row");
		}
		finally
		{
			SQLUtils.close(stmt);
		}
		Loader.clearSchemaLoaders();
	}

	/**
	 * Replaces the image of jar named <code>jarName</code> in the database
	 * jar repository. This method is exposed in SQL as <code>
	 * sqlj.replace_jar(BYTEA, VARCHAR, BOOLEAN)</code>.
	 * 
	 * @param jarImage The byte array that constitutes the jar content.
	 * @param jarName The name by which the system referes this jar.
	 * @param redeploy If set, execute remove commands found in the deployment
	 *            descriptor of the old jar and install commands found in the
	 *            deployment descriptor of the new jar.
	 * @throws SQLException if the named jar cannot be found in the repository.
	 */
	public static void replaceJar(byte[] jarImage, String jarName,
		boolean redeploy) throws SQLException
	{
		replaceJar("streamed byte image", jarName, redeploy, jarImage);
	}

	/**
	 * Replaces the image of jar named <code>jarName</code> in the database
	 * jar repository. This method is exposed in SQL as <code>
	 * sqlj.replace_jar(VARCHAR, VARCHAR, BOOLEAN)</code>.
	 * 
	 * @param urlString The location of the jar that will be installed.
	 * @param jarName The name by which the system referes this jar.
	 * @param redeploy If set, execute remove commands found in the deployment
	 *            descriptor of the old jar and install commands found in the
	 *            deployment descriptor of the new jar.
	 * @throws SQLException if the named jar cannot be found in the repository.
	 */
	public static void replaceJar(String urlString, String jarName,
		boolean redeploy) throws SQLException
	{
		replaceJar(urlString, jarName, redeploy, null);
	}

	/**
	 * Define the class path to use for Java functions, triggers, and procedures
	 * that are created in the schema named <code>schemaName</code> This
	 * method is exposed in SQL as
	 * <code>sqlj.set_classpath(VARCHAR, VARCHAR)</code>.
	 * 
	 * @param schemaName Name of the schema for which this path is valid.
	 * @param path Colon separated list of names. Each name must denote the name
	 *            of a jar that is present in the jar repository.
	 * @throws SQLException If no schema can be found with the givene name, or
	 *             if one or several names of the path denotes a nonexistant jar
	 *             file.
	 */
	public static void setClassPath(String schemaName, String path)
	throws SQLException
	{
		if(schemaName == null || schemaName.length() == 0)
			schemaName = "public";

		if("public".equals(schemaName))
		{
			if(!AclId.getSessionUser().isSuperuser())
				throw new SQLException(
					"Permission denied. Only a super user can set the classpath of the public schema");
		}
		else
		{
			schemaName = schemaName.toLowerCase();
			Oid schemaId = getSchemaId(schemaName);
			if(schemaId == null)
				throw new SQLException("No such schema: " + schemaName);
			if(!AclId.getSessionUser().hasSchemaCreatePermission(schemaId))
				throw new SQLException(
					"Permission denied. User must have create permission on the target schema in order to set the classpath");
		}

		PreparedStatement stmt;
		ArrayList entries = null;
		if(path != null && path.length() > 0)
		{
			// Collect and verify that all entries in the path represents a
			// valid jar
			//
			entries = new ArrayList();
			stmt = SQLUtils.getDefaultConnection().prepareStatement(
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

					int jarId = getJarId(stmt, jarName, null);
					if(jarId < 0)
						throw new SQLException("No such jar: " + jarName);

					entries.add(new Integer(jarId));
					if(colon < 0)
						break;
				}
			}
			finally
			{
				SQLUtils.close(stmt);
			}
		}

		// Delete the old classpath
		//
		stmt = SQLUtils.getDefaultConnection().prepareStatement(
			"DELETE FROM sqlj.classpath_entry WHERE schemaName = ?");
		try
		{
			stmt.setString(1, schemaName);
			stmt.executeUpdate();
		}
		finally
		{
			SQLUtils.close(stmt);
		}

		if(entries != null)
		{
			// Insert the new path.
			//
			stmt = SQLUtils
				.getDefaultConnection()
				.prepareStatement(
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
				SQLUtils.close(stmt);
			}
		}
		Loader.clearSchemaLoaders();
	}

	private static boolean assertInPath(String jarName,
		String[] originalSchemaAndPath) throws SQLException
	{
		String currentSchema = getCurrentSchema();
		String currentClasspath = getClassPath(currentSchema);
		originalSchemaAndPath[0] = currentSchema;
		originalSchemaAndPath[1] = currentClasspath;
		if(currentClasspath == null)
		{
			setClassPath(currentSchema, jarName);
			return true;
		}

		String[] elems = currentClasspath.split(":");
		int idx = elems.length;
		boolean found = false;
		while(--idx >= 0)
			if(elems[idx].equals(jarName))
			{
				found = true;
				break;
			}

		if(found)
			return false;

		setClassPath(currentSchema, jarName + ':' + currentClasspath);
		return true;
	}

	/**
	 * Throws an exception if the given name cannot be used as the name of a
	 * jar.
	 * 
	 * @param jarName The naem to check.
	 * @throws IOException
	 */
	private static void assertJarName(String jarName) throws SQLException
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
		throw new SQLException("The jar name '" + jarName
			+ "' is not a valid name");
	}

	private static void deployInstall(int jarId, String jarName)
	throws SQLException
	{
		SQLDeploymentDescriptor[] depDesc = getDeploymentDescriptors(jarId);

		String[] originalSchemaAndPath = new String[2];
		boolean classpathChanged = assertInPath(jarName, originalSchemaAndPath);
		for ( SQLDeploymentDescriptor dd : depDesc )
			dd.install(SQLUtils.getDefaultConnection());
		if(classpathChanged)
			setClassPath(originalSchemaAndPath[0], originalSchemaAndPath[1]);
	}

	private static void deployRemove(int jarId, String jarName)
	throws SQLException
	{
		SQLDeploymentDescriptor[] depDesc = getDeploymentDescriptors(jarId);

		String[] originalSchemaAndPath = new String[2];
		boolean classpathChanged = assertInPath(jarName, originalSchemaAndPath);
		for ( int i = depDesc.length ; i --> 0 ; )
			depDesc[i].remove(SQLUtils.getDefaultConnection());
		if(classpathChanged)
			setClassPath(originalSchemaAndPath[0], originalSchemaAndPath[1]);
	}

	private static SQLDeploymentDescriptor[] getDeploymentDescriptors(int jarId)
	throws SQLException
	{
		ResultSet rs = null;
		PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement(
				"SELECT e.entryImage"
					+ " FROM sqlj.jar_descriptor d INNER JOIN sqlj.jar_entry e"
					+ "   ON d.entryId = e.entryId"
					+ " WHERE d.jarId = ?"
					+ " ORDER BY d.ordinal");
		try
		{
			stmt.setInt(1, jarId);
			rs = stmt.executeQuery();
			ArrayList<SQLDeploymentDescriptor> sdds =
				new ArrayList<SQLDeploymentDescriptor>();
			while(rs.next())
			{
				byte[] bytes = rs.getBytes(1);
				// According to the SQLJ standard, this entry must be
				// UTF8 encoded.
				//
				sdds.add(
					new SQLDeploymentDescriptor(new String(bytes, "UTF8")));
			}
			return sdds.toArray( new SQLDeploymentDescriptor[sdds.size()]);
		}
		catch(UnsupportedEncodingException e)
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
			SQLUtils.close(rs);
			SQLUtils.close(stmt);
		}
	}

	private static String getFullSqlName(String sqlTypeName)
	throws SQLException
	{
		Oid typeId = Oid.forTypeName(sqlTypeName);
		s_logger.info("Type id = " + typeId.toString());

		ResultSet rs = null;
		PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement(
				"SELECT n.nspname, t.typname FROM pg_type t, pg_namespace n"
					+ " WHERE t.oid = ? AND n.oid = t.typnamespace");

		try
		{
			stmt.setObject(1, typeId);
			rs = stmt.executeQuery();
			if(!rs.next())
				throw new SQLException("Unable to obtain type info for "
					+ typeId);

			return rs.getString(1) + '.' + rs.getString(2);
		}
		finally
		{
			SQLUtils.close(rs);
			SQLUtils.close(stmt);
		}
	}

	private static int getJarId(PreparedStatement stmt, String jarName,
		AclId[] ownerRet) throws SQLException
	{
		stmt.setString(1, jarName);
		ResultSet rs = stmt.executeQuery();
		try
		{
			if(!rs.next())
				return -1;
			int id = rs.getInt(1);
			if(ownerRet != null)
			{
				String ownerName = rs.getString(2);
				ownerRet[0] = AclId.fromName(ownerName);
			}
			return id;
		}
		finally
		{
			SQLUtils.close(rs);
		}
	}

	/**
	 * Returns the primary key identifier for the given Jar.
	 * 
	 * @param conn The connection to use for the query.
	 * @param jarName The name of the jar.
	 * @return The primary key value of the given jar or <code>-1</code> if no
	 *         such jar is found.
	 * @throws SQLException
	 */
	private static int getJarId(String jarName, AclId[] ownerRet)
	throws SQLException
	{
		PreparedStatement stmt = SQLUtils
			.getDefaultConnection()
			.prepareStatement(
				"SELECT jarId, jarOwner FROM sqlj.jar_repository WHERE jarName = ?");
		try
		{
			return getJarId(stmt, jarName, ownerRet);
		}
		finally
		{
			SQLUtils.close(stmt);
		}
	}

	/**
	 * Returns the Oid for the given Schema.
	 * 
	 * @param conn The connection to use for the query.
	 * @param schemaName The name of the schema.
	 * @return The Oid of the given schema or <code>null</code> if no such
	 *         schema is found.
	 * @throws SQLException
	 */
	private static Oid getSchemaId(String schemaName) throws SQLException
	{
		ResultSet rs = null;
		PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement("SELECT oid FROM pg_namespace WHERE nspname = ?");
		try
		{
			stmt.setString(1, schemaName);
			rs = stmt.executeQuery();
			if(!rs.next())
				return null;
			return (Oid)rs.getObject(1);
		}
		finally
		{
			SQLUtils.close(rs);
			SQLUtils.close(stmt);
		}
	}

	private static void installJar(String urlString, String jarName,
		boolean deploy, byte[] image) throws SQLException
	{
		assertJarName(jarName);

		if(getJarId(jarName, null) >= 0)
			throw new SQLException("A jar named '" + jarName
					       + "' already exists",
					       "46002");

		PreparedStatement stmt = SQLUtils
			.getDefaultConnection()
			.prepareStatement(
				"INSERT INTO sqlj.jar_repository(jarName, jarOrigin, jarOwner) VALUES(?, ?, ?)");
		try
		{
			stmt.setString(1, jarName);
			stmt.setString(2, urlString);
			stmt.setString(3, AclId.getSessionUser().getName());
			if(stmt.executeUpdate() != 1)
				throw new SQLException(
					"Jar repository insert did not insert 1 row");
		}
		finally
		{
			SQLUtils.close(stmt);
		}

		AclId[] ownerRet = new AclId[1];
		int jarId = getJarId(jarName, ownerRet);
		if(jarId < 0)
			throw new SQLException("Unable to obtain id of '" + jarName + "'");

		if(image == null)
			Backend.addClassImages(jarId, urlString);
		else
		{
			InputStream imageStream = new ByteArrayInputStream(image);
			addClassImages(jarId, imageStream, image.length);
		}
		Loader.clearSchemaLoaders();
		if(deploy)
			deployInstall(jarId, jarName);
	}

	private static void replaceJar(String urlString, String jarName,
		boolean redeploy, byte[] image) throws SQLException
	{
		AclId[] ownerRet = new AclId[1];
		int jarId = getJarId(jarName, ownerRet);
		if(jarId < 0)
			throw new SQLException("No Jar named '" + jarName
					       + "' is known to the system",
					       "4600A");

		AclId user = AclId.getSessionUser();
		if(!(user.isSuperuser() || user.equals(ownerRet[0])))
			throw new SecurityException(
				"Only super user or owner can replace a jar");

		if(redeploy)
			deployRemove(jarId, jarName);

		PreparedStatement stmt = SQLUtils
			.getDefaultConnection()
			.prepareStatement(
				"UPDATE sqlj.jar_repository "
				+ "SET jarOrigin = ?, jarOwner = ?, jarManifest = NULL "
				+ "WHERE jarId = ?");
		try
		{
			stmt.setString(1, urlString);
			stmt.setString(2, user.getName());
			stmt.setInt(3, jarId);
			if(stmt.executeUpdate() != 1)
				throw new SQLException(
					"Jar repository update did not update 1 row");
		}
		finally
		{
			SQLUtils.close(stmt);
		}

		stmt = SQLUtils.getDefaultConnection().prepareStatement(
			"DELETE FROM sqlj.jar_entry WHERE jarId = ?");
		try
		{
			stmt.setInt(1, jarId);
			stmt.executeUpdate();
		}
		finally
		{
			SQLUtils.close(stmt);
		}
		if(image == null)
			Backend.addClassImages(jarId, urlString);
		else
		{
			InputStream imageStream = new ByteArrayInputStream(image);
			addClassImages(jarId, imageStream, image.length);
		}
		Loader.clearSchemaLoaders();
		if(redeploy)
			deployInstall(jarId, jarName);
	}
}
