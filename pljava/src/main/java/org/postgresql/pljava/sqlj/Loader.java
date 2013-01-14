/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.sqlj;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgresql.pljava.internal.Backend;
import org.postgresql.pljava.internal.Oid;
import org.postgresql.pljava.jdbc.SQLUtils;

/**
 * @author Thomas Hallgren
 */
public class Loader extends ClassLoader
{
	private final static Logger s_logger = Logger.getLogger(Loader.class.getName());

	static class EntryEnumeration implements Enumeration
	{
		private final int[] m_entryIds;
		private int m_top = 0;

		EntryEnumeration(int[] entryIds)
		{
			m_entryIds = entryIds;
		}

		public boolean hasMoreElements()
		{
			return (m_top < m_entryIds.length);
		}
		
		public Object nextElement()
		throws NoSuchElementException
		{
			if (m_top >= m_entryIds.length)
				throw new NoSuchElementException();
			return entryURL(m_entryIds[m_top++]);
		}
	}
	private static final String PUBLIC_SCHEMA = "public";

	private static final Map s_schemaLoaders = new HashMap();

	private static final Map s_typeMap = new HashMap();

	/**
	 * Removes all cached schema loaders, functions, and type maps. This
	 * method is called by the utility functions that manipulate the
	 * data that has been cached. It is not intended to be called
	 * from user code.
	 */
	public static void clearSchemaLoaders()
	{
		s_schemaLoaders.clear();
		s_typeMap.clear();
		Backend.clearFunctionCache();
	}

	/**
	 * Obtains the loader that is in effect for the current schema (i.e. the
	 * schema that is first in the search path).
	 * @return A loader
	 * @throws SQLException
	 */
	public static ClassLoader getCurrentLoader()
	throws SQLException
	{
		String schema;
		Statement stmt = SQLUtils.getDefaultConnection().createStatement();
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery("SELECT current_schema()");
			if(!rs.next())
				throw new SQLException("Unable to determine current schema");
			schema = rs.getString(1);
		}
		finally
		{
			SQLUtils.close(rs);
			SQLUtils.close(stmt);
		}
		return getSchemaLoader(schema);
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
			schemaName = PUBLIC_SCHEMA;
		else
			schemaName = schemaName.toLowerCase();

		ClassLoader loader = (ClassLoader)s_schemaLoaders.get(schemaName);
		if(loader != null)
			return loader;

		Map classImages = new HashMap();
		Connection conn = SQLUtils.getDefaultConnection();
		PreparedStatement outer = null;
		PreparedStatement inner = null;
		try
		{
			// Read the entries so that the one with highest prio is read last.
			//
			outer = conn.prepareStatement(
				"SELECT r.jarId" +
				" FROM sqlj.jar_repository r INNER JOIN sqlj.classpath_entry c ON r.jarId = c.jarId" +
				" WHERE c.schemaName = ? ORDER BY c.ordinal DESC");

			inner = conn.prepareStatement(
				"SELECT entryId, entryName FROM sqlj.jar_entry WHERE jarId = ?");

			outer.setString(1, schemaName);
			ResultSet rs = outer.executeQuery();
			try
			{
				while(rs.next())
				{
					inner.setInt(1, rs.getInt(1));
					ResultSet rs2 = inner.executeQuery();
					try
					{
						while(rs2.next())
						{
							int entryId = rs2.getInt(1);
							String entryName = rs2.getString(2);
							int[] oldEntry = (int[])classImages.get(entryName);
							if(oldEntry == null)
								classImages.put(entryName, new int[] { entryId });
							else
							{
								int last = oldEntry.length;
								int[] newEntry = new int[last + 1];
								newEntry[0] = entryId;
								System.arraycopy(oldEntry, 0, newEntry, 1, last);
								classImages.put(entryName, newEntry);
							}
						}
					}
					finally
					{
						SQLUtils.close(rs2);
					}
				}
			}
			finally
			{
				SQLUtils.close(rs);
			}
		}
		finally
		{
			SQLUtils.close(outer);
			SQLUtils.close(inner);
		}

		ClassLoader parent = ClassLoader.getSystemClassLoader();
		if(classImages.size() == 0)
			//
			// No classpath defined for the schema. Default to
			// classpath of public schema or to the system classloader if the
			// request already is for the public schema.
			//
			loader = schemaName.equals(PUBLIC_SCHEMA) ? parent : getSchemaLoader(PUBLIC_SCHEMA);
		else
			loader = new Loader(classImages, parent);

		s_schemaLoaders.put(schemaName, loader);
		return loader;
	}

	/**
	 * Returns the SQL type {@link Oid} to Java {@link Class} map that contains the
	 * Java UDT mappings for the given <code>schema</code>.
	 * This method is called by the function mapping mechanisms. Application code
	 * should never call this method.
	 *
	 * @param schema The schema
	 * @return The Map, possibly empty but never <code>null</code>.
	 */
	public static Map getTypeMap(final String schema) throws SQLException
	{
		Map typesForSchema = (Map)s_typeMap.get(schema);
		if(typesForSchema != null)
			return typesForSchema;

		s_logger.fine("Creating typeMappings for schema " + schema);
		typesForSchema = new HashMap()
		{
			public Object get(Object key)
			{
				s_logger.fine("Obtaining type mapping for OID " + key + " for schema " + schema);
				return super.get(key);
			}
		};
		ClassLoader loader = Loader.getSchemaLoader(schema);
		Statement stmt = SQLUtils.getDefaultConnection().createStatement();
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery("SELECT javaName, sqlName FROM sqlj.typemap_entry");
			while(rs.next())
			{
				try
				{
					String javaClassName = rs.getString(1);
					String sqlName = rs.getString(2);
					Class cls = loader.loadClass(javaClassName);
					if(!SQLData.class.isAssignableFrom(cls))
						throw new SQLException("Class " + javaClassName + " does not implement java.sql.SQLData");
					
					Oid typeOid = Oid.forTypeName(sqlName);
					typesForSchema.put(typeOid, cls);
					s_logger.fine("Adding type mapping for OID " + typeOid + " -> class " + cls.getName() + " for schema " + schema);
				}
				catch(ClassNotFoundException e)
				{
					// Ignore, type is not know to this schema and that is ok
				}
			}
			if(typesForSchema.isEmpty())
				typesForSchema = Collections.EMPTY_MAP;
			s_typeMap.put(schema, typesForSchema);
			return typesForSchema;
		}
		finally
		{
			SQLUtils.close(rs);
			SQLUtils.close(stmt);
		}
	}

	private static URL entryURL(int entryId)
	{
		try
		{
			return new URL(
					"dbf",
					"localhost",
					-1,
					"/" + entryId,
					EntryStreamHandler.getInstance());
		}
		catch(MalformedURLException e)
		{
			throw new RuntimeException(e);
		}
	}

	private final Map m_entries;

	/**
	 * Create a new Loader.
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
		int[] entryId = (int[])m_entries.get(path);
		if(entryId != null)
		{
			PreparedStatement stmt = null;
			ResultSet rs = null;
			try
			{
				// This code rely heavily on the fact that the connection
				// is a singleton and that the prepared statement will live
				// for the duration of the loader.
				//
				stmt = SQLUtils.getDefaultConnection().prepareStatement(
					"SELECT entryImage FROM sqlj.jar_entry WHERE entryId = ?");

				stmt.setInt(1, entryId[0]);
				rs = stmt.executeQuery();
				if(rs.next())
				{
					byte[] img = rs.getBytes(1);
					rs.close();
					rs = null;
					return this.defineClass(name, img, 0, img.length);
				}
			}
			catch(SQLException e)
			{
				Logger.getAnonymousLogger().log(Level.INFO, "Failed to load class", e);
				throw new ClassNotFoundException(name + " due to: " + e.getMessage());
			}
			finally
			{
				SQLUtils.close(rs);
				SQLUtils.close(stmt);
			}
		}
	throw new ClassNotFoundException(name);
	}

	protected URL findResource(String name)
	{
		int[] entryIds = (int[])m_entries.get(name);
		if(entryIds == null)
			return null;
		
		return entryURL(entryIds[0]);
	}

	protected Enumeration findResources(String name)
    throws IOException
	{
		int[] entryIds = (int[])m_entries.get(name);
		if(entryIds == null)
			entryIds = new int[0];
		return new EntryEnumeration(entryIds);
	}
}
