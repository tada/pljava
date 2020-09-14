/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
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
import java.nio.ByteBuffer;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLNonTransientException;
import java.sql.SQLSyntaxErrorException;
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

import org.postgresql.pljava.Session;
import org.postgresql.pljava.SessionManager;

import org.postgresql.pljava.internal.AclId;
import org.postgresql.pljava.internal.Backend;
import org.postgresql.pljava.internal.Oid;
import org.postgresql.pljava.jdbc.SQLUtils;
import org.postgresql.pljava.sqlj.Loader;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import static org.postgresql.pljava.annotation.Function.Security.DEFINER;

/**
 * This methods of this class are implementations of SQLJ commands.
 * <h2>SQLJ functions</h2>
 * <h3>install_jar</h3>
 * The install_jar command loads a jar file from a location appointed by an URL
 * or a binary image that constitutes the contents of a jar file into the SQLJ
 * jar repository. It is an error if a jar with the given name already exists in
 * the repository.
 * <h4>Usage 1</h4>
 * <blockquote><code>SELECT sqlj.install_jar(&lt;jar_url&gt;, &lt;jar_name&gt;, &lt;deploy&gt;);</code>
 * </blockquote>
 * <h4>Parameters</h4>
 * <blockquote><table><caption>Parameters for sqlj.install_jar(url...)</caption>
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
 * <h4>Usage 2</h4>
 * <blockquote><code>SELECT sqlj.install_jar(&lt;jar_image&gt;, &lt;jar_name&gt;, &lt;deploy&gt;);</code>
 * </blockquote>
 * <h4>Parameters</h4>
 * <blockquote><table><caption>Parameters for
 * sqlj.install_jar(bytea...)</caption>
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
 * <h3>replace_jar</h3>
 * The replace_jar will replace a loaded jar with another jar. Use this command
 * to update already loaded files. It's an error if the jar is not found.
 * <h4>Usage 1</h4>
 * <blockquote><code>SELECT sqlj.replace_jar(&lt;jar_url&gt;, &lt;jar_name&gt;, &lt;redeploy&gt;);</code>
 * </blockquote>
 * <h4>Parameters</h4>
 * <blockquote><table><caption>Parameters for sqlj.replace_jar(url...)</caption>
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
 * <h4>Usage 2</h4>
 * <blockquote><code>SELECT sqlj.replace_jar(&lt;jar_image&gt;, &lt;jar_name&gt;, &lt;redeploy&gt;);</code>
 * </blockquote>
 * <h4>Parameters</h4>
 * <blockquote><table><caption>Parameters for
 * sqlj.replace_jar(bytea...)</caption>
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
 * <h3>remove_jar</h3>
 * The remove_jar will drop the jar from the jar repository. Any classpath that
 * references this jar will be updated accordingly. It's an error if the jar is
 * not found.
 * <h4>Usage</h4>
 * <blockquote><code>SELECT sqlj.remove_jar(&lt;jar_name&gt;, &lt;undeploy&gt;);</code>
 * </blockquote>
 * <h4>Parameters</h4>
 * <blockquote><table><caption>Parameters for sqlj.remove_jar</caption>
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
 * <h3>get_classpath</h3>
 * The get_classpath will return the classpath that has been defined for the
 * given schema or NULL if the schema has no classpath. It's an error if the
 * given schema does not exist.
 * <h4>Usage</h4>
 * <blockquote><code>SELECT sqlj.get_classpath(&lt;schema&gt;);</code>
 * </blockquote>
 * <h4>Parameters</h4>
 * <blockquote><table><caption>Parameters for sqlj.get_classpath</caption>
 * <tr>
 * <td><b>schema</b></td>
 * <td>The name of the schema</td>
 * </tr>
 * </table></blockquote>
 * <h3>set_classpath</h3>
 * The set_classpath will define a classpath for the given schema. A classpath
 * consists of a colon separated list of jar names. It's an error if the given
 * schema does not exist or if one or more jar names references non existent
 * jars.
 * <h4>Usage</h4>
 * <blockquote><code>SELECT sqlj.set_classpath(&lt;schema&gt;, &lt;classpath&gt;);</code>
 * </blockquote>
 * <h4>Parameters</h4>
 * <blockquote><table><caption>Parameters for sqlj.set_classpath</caption>
 * <tr>
 * <td><b>schema</b></td>
 * <td>The name of the schema</td>
 * </tr>
 * <tr>
 * <td><b>classpath</b></td>
 * <td>The colon separated list of jar names</td>
 * </tr>
 * </table></blockquote>
 * <h3>add_type_mapping</h3>
 * The add_type_mapping defines the mapping between an SQL type and a Java
 * class.
 * <h4>Usage</h4>
 * <blockquote><code>SELECT sqlj.add_type_mapping(&lt;sqlTypeName&gt;, &lt;className&gt;);</code>
 * </blockquote>
 * <h4>Parameters</h4>
 * <blockquote><table><caption>Parameters for sqlj.add_type_mapping</caption>
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
 * <h3>drop_type_mapping</h3>
 * The drop_type_mapping removes the mapping between an SQL type and a Java
 * class.
 * <h4>Usage</h4>
 * <blockquote><code>SELECT sqlj.drop_type_mapping(&lt;sqlTypeName&gt;);</code>
 * </blockquote>
 * <h4>Parameters</h4>
 * <blockquote><table><caption>Parameters for sqlj.drop_type_mapping</caption>
 * <tr>
 * <td><b>sqlTypeName</b></td>
 * <td>The name of the SQL type. The name can be qualified with a
 * schema (namespace). If the schema is omitted, it will be resolved according
 * to the current setting of the search_path.</td>
 * </tr>
 * </table></blockquote>
 * 
 * @author Thomas Hallgren
 * @author Chapman Flack
 */
/*
 * Attention: any evolution of the schema here needs to be reflected in
 * o.p.p.internal.InstallHelper.SchemaVariant and .recognizeSchema().
 *
 * Schema-qualification of a type with a typmod, e.g. pg_catalog.varchar(100),
 * is possible from PostgreSQL 8.3 onward, but not in 8.2. As a compromise, use
 * the two-word CHARACTER VARYING syntax, to evade capture by a user type.
 *
 * In this (1.5.0) incarnation of the schema, jar_repository and jar_entry are
 * both indexed by SERIAL columns. The replace_jar operation is an UPDATE to
 * jar_repository (so the jar's id is preserved), but deletes and reinserts to
 * jar_entry (so ALL classes get new ids). This makes the entryId sufficient as
 * a class-cache token to ensure old cached versions are recognized as invalid
 * (although at the cost of doing so for *every single class* in a jar even if
 * many are unchanged). It is used that way in the cache-token construction in
 * o.p.p.sqlj.Loader, which could need to be revisited if this behavior changes.
 */
@SQLAction(provides="sqlj.tables", install={
"	CREATE TABLE sqlj.jar_repository(" +
"		jarId       SERIAL PRIMARY KEY," +
"		jarName     CHARACTER VARYING(100) UNIQUE NOT NULL," +
"		jarOrigin   CHARACTER VARYING(500) NOT NULL," +
"		jarOwner    pg_catalog.NAME NOT NULL," +
"		jarManifest pg_catalog.TEXT" +
"	)",
"	COMMENT ON TABLE sqlj.jar_repository IS" +
"	'Information on jars loaded by PL/Java, one row per jar.'",
"	GRANT SELECT ON sqlj.jar_repository TO public",

"	CREATE TABLE sqlj.jar_entry(" +
"		entryId     SERIAL PRIMARY KEY," +
"		entryName   CHARACTER VARYING(200) NOT NULL," +
"		jarId       INT NOT NULL" +
"					REFERENCES sqlj.jar_repository ON DELETE CASCADE," +
"		entryImage  pg_catalog.BYTEA NOT NULL," +
"		UNIQUE(jarId, entryName)" +
"	)",
"	COMMENT ON TABLE sqlj.jar_entry IS" +
"	'Name and content of each entry in every jar loaded by PL/Java.'",
"	GRANT SELECT ON sqlj.jar_entry TO public",

"	CREATE TABLE sqlj.jar_descriptor(" +
"		jarId       INT REFERENCES sqlj.jar_repository ON DELETE CASCADE," +
"		ordinal     pg_catalog.INT2," +
"		PRIMARY KEY (jarId, ordinal)," +
"		entryId     INT NOT NULL REFERENCES sqlj.jar_entry ON DELETE CASCADE" +
"	)",
"	COMMENT ON TABLE sqlj.jar_descriptor IS" +
"	'Associates each jar with zero-or-more deployment descriptors (a row " +
	"for each), with ordinal indicating their order of mention in the " +
	"manifest.'",
"	GRANT SELECT ON sqlj.jar_descriptor TO public",

"	CREATE TABLE sqlj.classpath_entry(" +
"		schemaName  CHARACTER VARYING(30) NOT NULL," +
"		ordinal     pg_catalog.INT2 NOT NULL," +
"		jarId       INT NOT NULL" +
"					REFERENCES sqlj.jar_repository ON DELETE CASCADE," +
"		PRIMARY KEY(schemaName, ordinal)" +
"	)",
"	COMMENT ON TABLE sqlj.classpath_entry IS" +
"	'Associates each schema with zero-or-more jars (a row " +
	"for each), with ordinal indicating their order of precedence in the " +
	"classpath.'",
"	GRANT SELECT ON sqlj.classpath_entry TO public",

"	CREATE TABLE sqlj.typemap_entry(" +
"		mapId       SERIAL PRIMARY KEY," +
"		javaName    CHARACTER VARYING(200) NOT NULL," +
"		sqlName     pg_catalog.NAME NOT NULL" +
"	)",
"	COMMENT ON TABLE sqlj.typemap_entry IS" +
"	'A row for each SQL type <-> Java type custom mapping.'",
"	GRANT SELECT ON sqlj.typemap_entry TO public"
}, remove={
"	DROP TABLE sqlj.typemap_entry",
"	DROP TABLE sqlj.jar_repository CASCADE"
})
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
						"UPDATE sqlj.jar_repository SET jarManifest = ? " +
						"WHERE jarId OPERATOR(pg_catalog.=) ?");
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
							"SELECT entryId FROM sqlj.jar_entry " +
							"WHERE jarId OPERATOR(pg_catalog.=) ?" +
							"  AND entryName OPERATOR(pg_catalog.=) ?");
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
				+ e.getMessage(), "58030", e);
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
	    "(?<=[\\r\\n])Name: ((?:.|(?:\\r\\n?+|\\n) )++)(?:\\r\\n?+|\\n)" +
		"(?:[^\\r\\n]++(?:\\r\\n?+|\\n)(?![\\r\\n]))*" +
		"SQLJDeploymentDescriptor: (?:(?:\\r\\n?+|\\r) )*+TRUE(?!\\S)",
		Pattern.CASE_INSENSITIVE
	);

	private final static Pattern mfCont = Pattern.compile( "(?:\\r\\n?+|\\n) ");

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
				CharsetDecoder u8 = UTF_8.newDecoder();
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
	 *            {@code search_path}.
	 * @param javaClassName The name of the class. The class must be found in
	 *            the classpath in effect for the current schema
	 * @throws SQLException if the type or class cannot be found, or if the
	 *            invoking user does not own the type.
	 */
	@Function(schema="sqlj", name="add_type_mapping", security=DEFINER,
		requires="sqlj.tables")
	public static void addTypeMapping(String sqlTypeName, String javaClassName)
	throws SQLException
	{
		try(PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement(
				"INSERT INTO sqlj.typemap_entry(javaName, sqlName)" +
				" VALUES(?,?)"))
		{
			ClassLoader loader = Loader.getCurrentLoader();
			Class cls = loader.loadClass(javaClassName);
			if(!SQLData.class.isAssignableFrom(cls))
				throw new SQLException("Class " + javaClassName
					+ " does not implement java.sql.SQLData");

			sqlTypeName = getFullSqlNameOwned(sqlTypeName);
			stmt.setString(1, javaClassName);
			stmt.setString(2, sqlTypeName);
			stmt.executeUpdate();
		}
		catch(ClassNotFoundException e)
		{
			throw new SQLException(
				"No such class: " + javaClassName, "46103", e);
		}
		Loader.clearSchemaLoaders();
	}

	/**
	 * Drops the mapping between an SQL type and a Java class.
	 * 
	 * @param sqlTypeName The name of the SQL type. The name can be
	 *            qualified with a schema (namespace). If the schema is omitted,
	 *            it will be resolved according to the current setting of the
	 *            {@code search_path}.
	 * @throws SQLException if the type cannot be found, or if the
	 *            invoking user does not own the type.
	 */
	@Function(schema="sqlj", name="drop_type_mapping", security=DEFINER,
		requires="sqlj.tables")
	public static void dropTypeMapping(String sqlTypeName) throws SQLException
	{
		try(PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement(
				"DELETE FROM sqlj.typemap_entry " +
				"WHERE sqlName OPERATOR(pg_catalog.=) ?"))
		{
			sqlTypeName = getFullSqlNameOwned(sqlTypeName);
			stmt.setString(1, sqlTypeName);
			stmt.executeUpdate();
		}
		Loader.clearSchemaLoaders();
	}

	/**
	 * Return the classpath that has been defined for the schema named
	 * {@code schemaName}. This method is exposed in SQL as
	 * {@code sqlj.get_classpath(VARCHAR)}.
	 * 
	 * @param schemaName Name of the schema for which this path is valid.
	 * @return The defined classpath or {@code null} if this schema has
	 *         no classpath.
	 * @throws SQLException
	 */
	@Function(schema="sqlj", name="get_classpath", security=DEFINER,
		requires="sqlj.tables")
	public static String getClassPath(String schemaName) throws SQLException
	{
		try(PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement(
				"SELECT r.jarName" +
				" FROM" +
				"  sqlj.jar_repository r" +
				"  INNER JOIN sqlj.classpath_entry c" +
				"  ON r.jarId OPERATOR(pg_catalog.=) c.jarId" +
				" WHERE c.schemaName OPERATOR(pg_catalog.=) ?" +
				" ORDER BY c.ordinal"))
		{
			if(schemaName == null || schemaName.length() == 0)
				schemaName = "public";
			else
				schemaName = schemaName.toLowerCase();
			stmt.setString(1, schemaName);
			StringBuffer buf = null;
			try(ResultSet rs = stmt.executeQuery())
			{
				while(rs.next())
				{
					if(buf == null)
						buf = new StringBuffer();
					else
						buf.append(':');
					buf.append(rs.getString(1));
				}
			}
			return (buf == null) ? null : buf.toString();
		}
	}

	static String getCurrentSchema() throws SQLException
	{
		Session session = SessionManager.current();
		return ((org.postgresql.pljava.internal.Session)session)
			.getOuterUserSchema();
	}

	/**
	 * Installs a new Jar in the database jar repository under name
	 * {@code jarName}. Once installed classpaths can be defined that
	 * refrences this jar. This method is exposed in SQL as
	 * {@code sqlj.install_jar(BYTEA, VARCHAR, BOOLEAN)}.
	 * 
	 * @param image The byte array that constitutes the jar content.
	 * @param jarName The name by which the system will refer to this jar.
	 * @param deploy If set, execute install commands found in the deployment
	 *            descriptor.
	 * @throws SQLException if the {@code jarName} contains characters
	 *             that are invalid or if the named jar already exists in the
	 *             system.
	 * @see #setClassPath
	 */
	@Function(schema="sqlj", name="install_jar", security=DEFINER,
		requires="sqlj.tables")
	public static void installJar(byte[] image, String jarName, boolean deploy)
	throws SQLException
	{
		installJar("streamed byte image", jarName, deploy, image);
	}

	/**
	 * Installs a new Jar in the database jar repository under name
	 * {@code jarName}. Once installed classpaths can be defined that
	 * refrences this jar. This method is exposed in SQL as
	 * {@code sqlj.install_jar(VARCHAR, VARCHAR, BOOLEAN)}.
	 * 
	 * @param urlString The location of the jar that will be installed.
	 * @param jarName The name by which the system will refer to this jar.
	 * @param deploy If set, execute install commands found in the deployment
	 *            descriptor.
	 * @throws SQLException if the {@code jarName} contains characters
	 *             that are invalid or if the named jar already exists in the
	 *             system.
	 * @see #setClassPath
	 */
	@Function(schema="sqlj", name="install_jar", security=DEFINER,
		requires="sqlj.tables")
	public static void installJar(String urlString, String jarName,
		boolean deploy) throws SQLException
	{
		installJar(urlString, jarName, deploy, null);
	}

	/**
	 * Removes the jar named {@code jarName} from the database jar
	 * repository. Class path entries that references this jar will also be
	 * removed (just the entry, not the whole path). This method is exposed in
	 * SQL as {@code sqlj.remove_jar(VARCHAR, BOOLEAN)}.
	 * 
	 * @param jarName The name by which the system referes this jar.
	 * @param undeploy If set, execute remove commands found in the deployment
	 *            descriptor of the jar.
	 * @throws SQLException if the named jar cannot be found in the repository.
	 */
	@Function(schema="sqlj", name="remove_jar", security=DEFINER,
		requires="sqlj.tables")
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

		AclId user = AclId.getOuterUser();
		if(!(user.isSuperuser() || user.equals(ownerRet[0])))
			throw new SQLSyntaxErrorException(
				"Only super user or owner can remove a jar", "42501");

		if(undeploy)
			deployRemove(jarId, jarName);

		PreparedStatement stmt = SQLUtils
			.getDefaultConnection()
			.prepareStatement(
				"DELETE FROM sqlj.jar_repository " +
				"WHERE jarId OPERATOR(pg_catalog.=) ?");
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
	 * Replaces the image of jar named {@code jarName} in the database
	 * jar repository. This method is exposed in SQL as
	 * {@code sqlj.replace_jar(BYTEA, VARCHAR, BOOLEAN)}.
	 * 
	 * @param jarImage The byte array that constitutes the jar content.
	 * @param jarName The name by which the system referes this jar.
	 * @param redeploy If set, execute remove commands found in the deployment
	 *            descriptor of the old jar and install commands found in the
	 *            deployment descriptor of the new jar.
	 * @throws SQLException if the named jar cannot be found in the repository.
	 */
	@Function(schema="sqlj", name="replace_jar", security=DEFINER,
		requires="sqlj.tables")
	public static void replaceJar(byte[] jarImage, String jarName,
		boolean redeploy) throws SQLException
	{
		replaceJar("streamed byte image", jarName, redeploy, jarImage);
	}

	/**
	 * Replaces the image of jar named {@code jarName} in the database
	 * jar repository. This method is exposed in SQL as
	 * {@code sqlj.replace_jar(VARCHAR, VARCHAR, BOOLEAN)}.
	 * 
	 * @param urlString The location of the jar that will be installed.
	 * @param jarName The name by which the system referes this jar.
	 * @param redeploy If set, execute remove commands found in the deployment
	 *            descriptor of the old jar and install commands found in the
	 *            deployment descriptor of the new jar.
	 * @throws SQLException if the named jar cannot be found in the repository.
	 */
	@Function(schema="sqlj", name="replace_jar", security=DEFINER,
		requires="sqlj.tables")
	public static void replaceJar(String urlString, String jarName,
		boolean redeploy) throws SQLException
	{
		replaceJar(urlString, jarName, redeploy, null);
	}

	/**
	 * Define the class path to use for Java functions, triggers, and procedures
	 * that are created in the schema named {@code schemaName}. This
	 * method is exposed in SQL as
	 * {@code sqlj.set_classpath(VARCHAR, VARCHAR)}.
	 * 
	 * @param schemaName Name of the schema for which this path is valid.
	 * @param path Colon separated list of names. Each name must denote the name
	 *            of a jar that is present in the jar repository.
	 * @throws SQLException If no schema can be found with the givene name, or
	 *             if one or several names of the path denotes a nonexistant jar
	 *             file.
	 */
	@Function(schema="sqlj", name="set_classpath", security=DEFINER,
		requires="sqlj.tables")
	public static void setClassPath(String schemaName, String path)
	throws SQLException
	{
		if(schemaName == null || schemaName.length() == 0)
			schemaName = "public";

		schemaName = schemaName.toLowerCase();
		if("public".equals(schemaName))
		{
			if(!AclId.getOuterUser().isSuperuser())
				throw new SQLSyntaxErrorException( // yeah, for 42501, really
					"Permission denied. Only a super user can set the " +
					"classpath of the public schema", "42501");
		}
		else
		{
			Oid schemaId = getSchemaId(schemaName);
			if(schemaId == null)
				throw new SQLNonTransientException(
					"No such schema: " + schemaName, "3F000");
			if(!AclId.getOuterUser().hasSchemaCreatePermission(schemaId))
				throw new SQLSyntaxErrorException(
					"Permission denied. User must have create permission on " +
					"the target schema in order to set the classpath", "42501");
		}

		ArrayList<Integer> entries = null;
		if(path != null && path.length() > 0)
		{
			// Collect and verify that all entries in the path represents a
			// valid jar
			//
			entries = new ArrayList<>();
			try(PreparedStatement stmt = SQLUtils.getDefaultConnection()
				.prepareStatement(
					"SELECT jarId FROM sqlj.jar_repository " +
					"WHERE jarName OPERATOR(pg_catalog.=) ?"))
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
						throw new SQLNonTransientException(
							"No such jar: " + jarName, "46102");

					entries.add(jarId);
					if(colon < 0)
						break;
				}
			}
		}

		// Delete the old classpath
		//
		try(PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement(
				"DELETE FROM sqlj.classpath_entry " +
				"WHERE schemaName OPERATOR(pg_catalog.=) ?"))
		{
			stmt.setString(1, schemaName);
			stmt.executeUpdate();
		}

		if(entries != null)
		{
			// Insert the new path.
			//
			;
			try(PreparedStatement stmt = SQLUtils.getDefaultConnection()
				.prepareStatement(
					"INSERT INTO sqlj.classpath_entry("+
					" schemaName, ordinal, jarId) VALUES(?, ?, ?)"))
			{
				int top = entries.size();
				for(int idx = 0; idx < top; ++idx)
				{
					int jarId = entries.get(idx);
					stmt.setString(1, schemaName);
					stmt.setInt(2, idx + 1);
					stmt.setInt(3, jarId);
					stmt.executeUpdate();
				}
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
	 * @param jarName The name to check.
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
		throw new SQLNonTransientException("The jar name '" + jarName
			+ "' is not a valid name", "46002");
	}

	private static void deployInstall(int jarId, String jarName)
	throws SQLException
	{
		SQLDeploymentDescriptor[] depDesc = getDeploymentDescriptors(jarId);

		String[] originalSchemaAndPath = new String[2];
		boolean classpathChanged = assertInPath(jarName, originalSchemaAndPath);
		for ( SQLDeploymentDescriptor dd : depDesc )
			dd.install(SQLUtils.getDefaultConnection());
		if (classpathChanged)
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
		try
		{
			if (classpathChanged)
				setClassPath(originalSchemaAndPath[0],originalSchemaAndPath[1]);
		}
		catch ( SQLException sqle )
		{
			if ( ! "3F000".equals(sqle.getSQLState()) )
				throw sqle;
		}
	}

	private static SQLDeploymentDescriptor[] getDeploymentDescriptors(int jarId)
	throws SQLException
	{
		ResultSet rs = null;
		PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement(
				"SELECT e.entryImage"
					+ " FROM sqlj.jar_descriptor d INNER JOIN sqlj.jar_entry e"
					+ "   ON d.entryId OPERATOR(pg_catalog.=) e.entryId"
					+ " WHERE d.jarId OPERATOR(pg_catalog.=) ?"
					+ " ORDER BY d.ordinal");
		try
		{
			stmt.setInt(1, jarId);
			rs = stmt.executeQuery();
			ArrayList<SQLDeploymentDescriptor> sdds = new ArrayList<>();
			while(rs.next())
			{
				ByteBuffer bytes = ByteBuffer.wrap(rs.getBytes(1));
				// According to the SQLJ standard, this entry must be
				// UTF8 encoded.
				//
				sdds.add( new SQLDeploymentDescriptor(
					UTF_8.newDecoder().decode(bytes).toString()));
			}
			return sdds.toArray( new SQLDeploymentDescriptor[sdds.size()]);
		}
		catch(CharacterCodingException e)
		{
			throw new SQLDataException(
				"deployment descriptor is not well-formed UTF-8", "22021", e);
		}
		catch(ParseException e)
		{
			throw new SQLSyntaxErrorException(String.format(
				"%1$s at %2$s", e.getMessage(), e.getErrorOffset()),
				"42601", e);
		}
		finally
		{
			SQLUtils.close(rs);
			SQLUtils.close(stmt);
		}
	}

	/*
	 * In addition to resolving the type name to a fully qualified one, also
	 * make sure the current (outer) user is, or is granted, the type's owning
	 * role.
	 */
	private static String getFullSqlNameOwned(String sqlTypeName)
	throws SQLException
	{
		Oid typeId = Oid.forTypeName(sqlTypeName);
		s_logger.finer("Type id = " + typeId.toString());

		AclId invoker = AclId.getOuterUser();

		try(PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement(
				"SELECT n.nspname, t.typname,"
					+ " pg_catalog.pg_has_role(?, t.typowner, 'USAGE')"
					+ " FROM pg_catalog.pg_type t, pg_catalog.pg_namespace n"
					+ " WHERE t.oid OPERATOR(pg_catalog.=) ?"
					+ " AND n.oid OPERATOR(pg_catalog.=) t.typnamespace"))
		{
			stmt.setObject(1, invoker);
			stmt.setObject(2, typeId);
			try(ResultSet rs = stmt.executeQuery())
			{
				if(!rs.next())
					throw new SQLException("Unable to obtain type info for "
						+ typeId);

				if ( ! rs.getBoolean(3) )
					throw new SQLSyntaxErrorException( // yes, for 42501, really
						"Permission denied. Only superuser or type's owner " +
						"may add or drop a type mapping.", "42501");

				return rs.getString(1) + '.' + rs.getString(2);
			}
		}
	}

	private static int getJarId(PreparedStatement stmt, String jarName,
		AclId[] ownerRet) throws SQLException
	{
		stmt.setString(1, jarName);
		try(ResultSet rs = stmt.executeQuery())
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
	}

	/**
	 * Returns the primary key identifier for the given Jar.
	 * 
	 * @param jarName The name of the jar.
	 * @param ownerRet a caller-supplied array in which the jar's owner will
	 *	be returned.
	 * @return The primary key value of the given jar or <code>-1</code> if no
	 *         such jar is found.
	 * @throws SQLException
	 */
	private static int getJarId(String jarName, AclId[] ownerRet)
	throws SQLException
	{
		try(PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement(
				"SELECT jarId, jarOwner FROM sqlj.jar_repository"+
				" WHERE jarName OPERATOR(pg_catalog.=) ?"))
		{
			return getJarId(stmt, jarName, ownerRet);
		}
	}

	/**
	 * Returns the Oid for the given Schema.
	 * 
	 * @param schemaName The name of the schema.
	 * @return The Oid of the given schema or <code>null</code> if no such
	 *         schema is found.
	 * @throws SQLException
	 */
	private static Oid getSchemaId(String schemaName) throws SQLException
	{
		try(PreparedStatement stmt = SQLUtils.getDefaultConnection()
			.prepareStatement(
				"SELECT oid FROM pg_catalog.pg_namespace " +
				"WHERE nspname OPERATOR(pg_catalog.=) ?"))
		{
			stmt.setString(1, schemaName);
			try(ResultSet rs = stmt.executeQuery())
			{
				if(!rs.next())
					return null;
				return (Oid)rs.getObject(1);
			}
		}
	}

	private static void installJar(String urlString, String jarName,
		boolean deploy, byte[] image) throws SQLException
	{
		if ( Backend.isCreatingExtension() )
			throw new SQLFeatureNotSupportedException(
				"A jar cannot (yet) be installed as an extension in its " +
				"own right.", "0A000");

		assertJarName(jarName);

		if(getJarId(jarName, null) >= 0)
			throw new SQLNonTransientException("A jar named '" + jarName
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
			stmt.setString(3, AclId.getOuterUser().getName());
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
		if(!deploy)
			return;

		try
		{
			deployInstall(jarId, jarName);
		}
		catch ( Error | RuntimeException | SQLException e )
		{
			Loader.clearSchemaLoaders();
			throw e;
		}
	}

	private static void replaceJar(String urlString, String jarName,
		boolean redeploy, byte[] image) throws SQLException
	{
		AclId[] ownerRet = new AclId[1];
		int jarId = getJarId(jarName, ownerRet);
		if(jarId < 0)
			throw new SQLNonTransientException("No Jar named '" + jarName
					       + "' is known to the system",
					       "4600A");

		AclId user = AclId.getOuterUser();
		if(!(user.isSuperuser() || user.equals(ownerRet[0])))
			throw new SQLSyntaxErrorException(
				"Only super user or owner can replace a jar", "42501");

		if(redeploy)
			deployRemove(jarId, jarName);

		PreparedStatement stmt = SQLUtils
			.getDefaultConnection()
			.prepareStatement(
				"UPDATE sqlj.jar_repository "
				+ "SET jarOrigin = ?, jarOwner = ?, jarManifest = NULL "
				+ "WHERE jarId OPERATOR(pg_catalog.=) ?");
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
			"DELETE FROM sqlj.jar_entry WHERE jarId OPERATOR(pg_catalog.=) ?");
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
		if(!redeploy)
			return;

		try
		{
			deployInstall(jarId, jarName);
		}
		catch ( Error | RuntimeException | SQLException e )
		{
			Loader.clearSchemaLoaders();
			throw e;
		}
	}
}
