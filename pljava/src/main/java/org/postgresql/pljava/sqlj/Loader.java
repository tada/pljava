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
 *   Chapman Flack
 */
package org.postgresql.pljava.sqlj;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
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

/*
 * Import an interface (internal-only, at least for now) that allows overriding
 * the default derivation of the read_only parameter to SPI query execution
 * functions. The default behavior follows the recommendation in the SPI docs
 * to use read_only => true if the currently-executing PL function is declared
 * IMMUTABLE, and read_only => false otherwise.
 *
 * Several queries in this class will use this interface to force read_only to
 * be false, even though the queries clearly do nothing but reading. The reason
 * may not be obvious:
 *
 * One effect of the read_only parameter in SPI is the selection of the snapshot
 * used to evaluate the query. When read_only is true, a snapshot from the
 * beginning of the command is used, which cannot see even the modifications
 * made in this transaction since that point.
 *
 * Where that becomes a problem is during evaluation of a deployment descriptor
 * as part of install_jar or replace_jar. The command began by loading some new
 * or changed classes, and is now executing deployment commands, which may very
 * well need to load those classes. But some of the loading requests may happen
 * to come through functions that are declared IMMUTABLE (type IO functions, for
 * example), which, under the default behavior, would mean SPI gets passed
 * read_only => true and selects a snapshot from before the new classes were
 * there, and loading fails. That is why read_only is always forced false here.
 */
import org.postgresql.pljava.jdbc.SPIReadOnlyControl;

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

	private static final Map<String,ClassLoader>
		s_schemaLoaders = new HashMap<String,ClassLoader>();

	private static final Map<String,Map<Oid,Class<? extends SQLData>>>
		s_typeMap = new HashMap<String,Map<Oid,Class<? extends SQLData>>>();

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
			rs = stmt.executeQuery("SELECT pg_catalog.current_schema()");
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

		ClassLoader loader = s_schemaLoaders.get(schemaName);
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
				" FROM" +
				"  sqlj.jar_repository r" +
				"  INNER JOIN sqlj.classpath_entry c" +
				"  ON r.jarId OPERATOR(pg_catalog.=) c.jarId" +
				" WHERE c.schemaName OPERATOR(pg_catalog.=) ?" +
				" ORDER BY c.ordinal DESC");

			inner = conn.prepareStatement(
				"SELECT entryId, entryName FROM sqlj.jar_entry " +
				"WHERE jarId OPERATOR(pg_catalog.=) ?");

			outer.unwrap(SPIReadOnlyControl.class).clearReadOnly();
			inner.unwrap(SPIReadOnlyControl.class).clearReadOnly();
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
	public static Map<Oid,Class<? extends SQLData>> getTypeMap(
		final String schema)
		throws SQLException
	{
		Map<Oid,Class<? extends SQLData>> typesForSchema =
			s_typeMap.get(schema);
		if(typesForSchema != null)
			return typesForSchema;

		s_logger.finer("Creating typeMappings for schema " + schema);
		typesForSchema = new HashMap()
		{
			public Object get(Object key)
			{
				s_logger.finer("Obtaining type mapping for OID " + key + " for schema " + schema);
				return super.get(key);
			}
		};
		ClassLoader loader = Loader.getSchemaLoader(schema);
		Statement stmt = SQLUtils.getDefaultConnection().createStatement();
		stmt.unwrap(SPIReadOnlyControl.class).clearReadOnly();
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
					typesForSchema.put(typeOid, cls.asSubclass(SQLData.class));
					s_logger.finer("Adding type mapping for OID " + typeOid + " -> class " + cls.getName() + " for schema " + schema);
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
		m_j9Helper = ifJ9getHelper(); // null if not under OpenJ9 with sharing
	}

	protected Class<?> findClass(final String name)
	throws ClassNotFoundException
	{
		String path = name.replace('.', '/').concat(".class");
		int[] entryId = (int[])m_entries.get(path);
		if(entryId != null)
		{
			/*
			 * Check early whether running on OpenJ9 JVM and the shared cache
			 * has the class. It is possible this early because the entryId is
			 * being used to generate the token, and it is known before even
			 * doing the jar_entry query. It would be possible to use something
			 * like the row's xmin instead, in which case this test would have
			 * to be moved after retrieving the row.
			 *
			 * ifJ9findSharedClass can only return a byte[], a String, or null.
			 */
			Object o = ifJ9findSharedClass(name, entryId[0]);
			if ( o instanceof byte[] )
			{
				byte[] img = (byte[]) o;
				return defineClass(name, img, 0, img.length);
			}
			String ifJ9token = (String) o; // used below when storing class

			PreparedStatement stmt = null;
			ResultSet rs = null;
			try
			{
				// This code relies heavily on the fact that the connection
				// is a singleton and that the prepared statement will live
				// for the duration of the loader.
				//
				stmt = SQLUtils.getDefaultConnection().prepareStatement(
					"SELECT entryImage FROM sqlj.jar_entry " +
					"WHERE entryId OPERATOR(pg_catalog.=) ?");

				stmt.setInt(1, entryId[0]);
				stmt.unwrap(SPIReadOnlyControl.class).clearReadOnly();
				rs = stmt.executeQuery();
				if(rs.next())
				{
					byte[] img = rs.getBytes(1);
					rs.close();
					rs = null;

					Class<?> cls = this.defineClass(name, img, 0, img.length);

					ifJ9storeSharedClass(ifJ9token, cls); // noop for null token
					return cls;
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

	/*
	 * Detect and integrate with the OpenJ9 JVM class sharing facility.
	 * https://www.ibm.com/developerworks/library/j-class-sharing-openj9/#usingthehelperapi
	 * https://github.com/eclipse/openj9/blob/master/jcl/src/openj9.sharedclasses/share/classes/com/ibm/oti/shared/
	 */

	private static final Object s_j9HelperFactory;
	private static final Method s_j9GetTokenHelper;
	private static final Method s_j9FindSharedClass;
	private static final Method s_j9StoreSharedClass;
	private final Object m_j9Helper;

	/**
	 * Return an OpenJ9 {@code SharedClassTokenHelper} if running on an OpenJ9
	 * JVM with sharing enabled; otherwise return null.
	 */
	private Object ifJ9getHelper()
	{
		if ( null == s_j9HelperFactory )
			return null;
		try
		{
			return s_j9GetTokenHelper.invoke(s_j9HelperFactory, this);
		}
		catch ( IllegalAccessException e )
		{
			throw new SecurityException(e);
		}
		catch ( InvocationTargetException ite )
		{
			Throwable t = ite.getCause();
			if ( t instanceof Error )
				throw (Error)t;
			if ( t instanceof RuntimeException )
				throw (RuntimeException)t;
			throw new UndeclaredThrowableException(t, t.getMessage());
		}
	}

	/**
	 * Find a class definition in the OpenJ9 shared cache (if running under
	 * OpenJ9, and sharing is enabled, and the class is there).
	 * @param className name of the class to seek.
	 * @param tokenSource something passed by the caller from which we can
	 * generate a token that is sure to be different if the class has been
	 * updated. For now, just the int entryId, which is sufficient because that
	 * is a SERIAL column and entries are deleted/reinserted by replace_jar.
	 * There is just the one caller, so the type and usage of this parameter can
	 * be changed to whatever is appropriate should the schema evolve.
	 * @return null if not running under J9 with sharing; a {@code byte[]} if
	 * the class is found in the shared cache, or a {@code String} token that
	 * should be passed to {@code ifJ9storeSharedClass} later.
	 */
	private Object ifJ9findSharedClass(String className, int tokenSource)
	{
		if ( null == m_j9Helper )
			return null;

		String token = Integer.toString(tokenSource);

		try
		{
			byte[] cookie = (byte[])
				s_j9FindSharedClass.invoke(m_j9Helper, token, className);
			if ( null == cookie )
				return token;
			return cookie;
		}
		catch ( IllegalAccessException e )
		{
			throw new SecurityException(e);
		}
		catch ( InvocationTargetException ite )
		{
			Throwable t = ite.getCause();
			if ( t instanceof Error )
				throw (Error)t;
			if ( t instanceof RuntimeException )
				throw (RuntimeException)t;
			throw new UndeclaredThrowableException(t, t.getMessage());
		}
	}

	/**
	 * Store a newly-defined class in the OpenJ9 shared class cache if running
	 * under OpenJ9 with sharing enabled (implied if {@code token} is non-null,
	 * per the convention that its value came from {@code ifJ9findSharedClass}).
	 * @param token A token generated by {@code ifJ9findSharedClass}, non-null
	 * only if J9 sharing is active and the class is not already cached. This
	 * method is a noop if {@code token} is null.
	 * @param cls The newly-defined class.
	 */
	private void ifJ9storeSharedClass(String token, Class<?> cls)
	{
		if ( null == token )
			return;
		assert(null != m_j9Helper);

		try
		{
			s_j9StoreSharedClass.invoke(m_j9Helper, token, cls);
		}
		catch ( IllegalAccessException e )
		{
			throw new SecurityException(e);
		}
		catch ( InvocationTargetException ite )
		{
			Throwable t = ite.getCause();
			if ( t instanceof Error )
				throw (Error)t;
			if ( t instanceof RuntimeException )
				throw (RuntimeException)t;
			throw new UndeclaredThrowableException(t, t.getMessage());
		}
	}

	/*
	 * Detect if this is an OpenJ9 JVM with sharing enabled, setting the related
	 * static fields for later reflective access to its sharing helpers if so.
	 */
	static
	{
		Object factory = null;
		Method getHelper = null;
		Method findShared = null;
		Method storeShared = null;

		try
		{
			/* If this throws ClassNotFoundException, the JVM isn't OpenJ9. */
			Class<?> shared = ClassLoader.getSystemClassLoader().loadClass(
				"com.ibm.oti.shared.Shared");

			Method getFactory =
				shared.getMethod("getSharedClassHelperFactory", null);

			/* If getFactory returns null, sharing is not enabled. */
			factory = getFactory.invoke(null);
			if ( null != factory )
			{
				Class<?> factoryClass = getFactory.getReturnType();
				getHelper =
					factoryClass.getMethod("getTokenHelper", ClassLoader.class);
				Class<?> helperClass = getHelper.getReturnType();
				findShared =
					helperClass.getMethod(
						"findSharedClass", String.class, String.class);
				storeShared =
					helperClass.getMethod(
						"storeSharedClass", String.class, Class.class);
			}
		}
		catch ( ClassNotFoundException cnfe )
		{
			/* Not running on an OpenJ9 JVM. Leave all the statics null. */
		}
		catch ( RuntimeException rte )
		{
			throw rte;
		}
		catch ( Exception e )
		{
			throw new ExceptionInInitializerError(e);
		}
		finally
		{
			s_j9HelperFactory = factory;
			s_j9GetTokenHelper = getHelper;
			s_j9FindSharedClass = findShared;
			s_j9StoreSharedClass = storeShared;
		}
	}
}
