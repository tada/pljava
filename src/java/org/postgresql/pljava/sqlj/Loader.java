/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.sqlj;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Thomas Hallgren
 */
public class Loader extends ClassLoader
{
	private static final String PUBLIC_SCHEMA = "public";
	private static final Map s_schemaLoaders = new HashMap();

	private final Map m_entries;
	private PreparedStatement m_getImageStmt = null;

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
		Logger log = Logger.getAnonymousLogger();
		log.info("Hey, I got this far");
		if(schemaName == null || schemaName.length() == 0)
			schemaName = PUBLIC_SCHEMA;
		else
			schemaName = schemaName.toLowerCase();

		ClassLoader loader = (ClassLoader)s_schemaLoaders.get(schemaName);
		if(loader != null)
			return loader;

		Map classImages = new HashMap();
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
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
							Integer entryId  = new Integer(rs2.getInt(1));
							String entryName = rs2.getString(2);
							classImages.put(entryName, entryId);
						}
					}
					finally
					{
						try { rs2.close(); } catch(SQLException e) { /* ignore */ }
					}
				}
			}
			finally
			{
				try { rs.close(); } catch(SQLException e) { /* ignore */ }
			}
		}
		finally
		{
			if(outer != null)
				try { outer.close(); } catch(SQLException e) { /* ignore */ }
			if(inner != null)
				try { inner.close(); } catch(SQLException e) { /* ignore */ }
			try { conn.close(); } catch(SQLException e) { /* ignore */ }
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
		Integer entryId = (Integer)m_entries.get(path);
		if(entryId != null)
		{	
			try
			{
				// This code rely heavily on the fact that the connection
				// is a singleton and that the prepared statement will live
				// for the duration of the loader.
				//
				if(m_getImageStmt == null)
				{
					Connection conn = DriverManager.getConnection("jdbc:default:connection");
					m_getImageStmt = conn.prepareStatement(
						"SELECT entryImage FROM sqlj.jar_entry WHERE entryId = ?");
				}
				m_getImageStmt.setInt(1, entryId.intValue());
				ResultSet rs = m_getImageStmt.executeQuery();
				try
				{
					if(rs.next())
					{
						byte[] img = rs.getBytes(1);
						rs.close();
						rs = null;
						return this.defineClass(name, img, 0, img.length);
					}
				}
				finally
				{
					try
					{
						if(rs != null)
							rs.close();
					}
					catch(SQLException e) { /* ignore */ }
				}
			}
			catch(SQLException e)
			{
			}
		}
	throw new ClassNotFoundException(name);
	}
}
