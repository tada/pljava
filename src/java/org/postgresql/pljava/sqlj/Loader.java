/*
 * This file contains software that has been made available under The Mozilla
 * Public License 1.1. Use and distribution hereof are subject to the
 * restrictions set forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden All Rights Reserved
 */
package org.postgresql.pljava.sqlj;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * @author Thomas Hallgren
 */
public class Loader extends ClassLoader
{
	private static final Map s_schemaLoaders = new HashMap();

	private final Map m_entries;

	/**
	 * This implementation reads everything into memory. It needs to be improved
	 * so that it utilizes an index and the fact that we actually have a database
	 * that is far better suited to handle the images.
	 * @param map Map that maps name of jar file entry to its binary image.
	 * @param jarImage The image of the complete jar file.
	 * @throws SQLException
	 */
	public static void addClassImages(Map map, byte[] jarImage)
	throws SQLException
	{
		ByteArrayOutputStream img = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		try
		{
			JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarImage));
			for(;;)
			{
				JarEntry je = jis.getNextJarEntry();
				if(je == null)
					break;

				if(je.isDirectory())
					continue;

				int nBytes;
				img.reset();
				while((nBytes = jis.read(buf)) > 0)
					img.write(buf, 0, nBytes);
				jis.closeEntry();
				map.put(je.getName(), img.toByteArray());
			}
		}
		catch(IOException e)
		{
			throw new SQLException("I/O exception reading jar file: " + e.getMessage());
		}
	}

	/**
	 * Invalidates the loader for the given schema. This method is called from
	 * the {@link org.postgresql.pljava.management.Commands Commands} class when
	 * things are changed that might have an effect on the loader.
	 * @param schemaName The name of the schema
	 */
	public static void invalidateSchemaLoader(String schemaName)
	{
		// Simply drop the loader. A new one will be created the next time
		// a loader is wanted for the given schema.
		//
		s_schemaLoaders.remove(schemaName);
	}

	/**
	 * Obtain a loader that has been configured for the class path of the
	 * schema named <code>schemaName</code>. Class paths are defined using the
	 * SQL procedure <code>sqlj.set_classpath</code>.
	 * @param schemaName The name of the schema.
	 * @return A loader.
	 */
	public static ClassLoader getSchemaLoader(String schemaName)
	throws SQLException
	{
		if(schemaName == null || schemaName.length() == 0)
			schemaName = "public";
		else
			schemaName = schemaName.toLowerCase();

		ClassLoader loader = (ClassLoader)s_schemaLoaders.get(schemaName);
		if(loader != null)
			return loader;

		Map classImages = new HashMap();
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		try
		{
			// Read the entries so that the one with highest prio is read last.
			//
			PreparedStatement stmt = conn.prepareStatement(
				"SELECT r.jarImage" +
				" FROM sqlj.jar_repository r INNER JOIN sqlj.classpath_entry c ON r.jarId = c.jarId" +
				" WHERE c.schemaName = ? ORDER BY c.ordinal DESC");

			try
			{
				stmt.setString(1, schemaName);
				ResultSet rs = stmt.executeQuery();
				try
				{
					while(rs.next())
						addClassImages(classImages, rs.getBytes(1));
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

		ClassLoader parent = schemaName.equals("public")
			? ClassLoader.getSystemClassLoader()
			: getSchemaLoader("public");

		loader = (classImages.size() > 0)
			? new Loader(classImages, parent)
			: parent;

		s_schemaLoaders.put(schemaName, loader);
		return loader;
	}

	/**
	 * @param entries
	 * @param parent
	 */
	Loader(Map entries, ClassLoader parent)
	{
		super(parent);
		m_entries = entries;
	}

	protected Class findClass(final String name)
	throws ClassNotFoundException
	{
		String path = name.replace('.', '/').concat(".class");
		byte[] img = (byte[])m_entries.get(path);
		if(img == null)
			throw new ClassNotFoundException(name);
		
		return this.defineClass(name, img, 0, img.length);
	}
}
