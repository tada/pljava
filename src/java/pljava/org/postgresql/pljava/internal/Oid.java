/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;
import java.util.HashMap;

/**
 * The <code>Oid</code> correspons to the internal PostgreSQL <code>Oid</code>.
 * Should the size of that change from 32 bit, this class must change too.
 * In Java, the InvalidOid is represented as <code>null</code>.
 *
 * @author Thomas Hallgren
 */
public class Oid
{
	static
	{
		try
		{
			// Ensure that the SPI JDBC driver is loaded and registered
			// with the java.sql.DriverManager.
			//
			Class.forName("org.postgresql.pljava.jdbc.SPIDriver");
		}
		catch(ClassNotFoundException e)
		{
			throw new ExceptionInInitializerError(e);
		}
	}

	private static final HashMap s_class2typeId = new HashMap();
	private static final HashMap s_typeId2class = new HashMap();

	/*
	 * The native Oid represented as a 32 bit quantity.
	 * See definition in file &quot;include/postgres_ext&quot; of the
	 * PostgreSQL distribution.
	 */
	private final int m_native;

	public Oid(int value)
	{
		m_native = value;
	}

	/**
	 * Checks to see if the other object is an <code>Oid</code>, and if so,
	 * if the native value of that <code>Oid</code> equals the native value
	 * of this <code>Oid</code>.
	 * @return true if the objects are equal.
	 */
	public boolean equals(Object o)
	{
		return (o == this) || ((o instanceof Oid) && ((Oid)o).m_native == m_native);
	}

	public Class getJavaClass()
	throws SQLException
	{
		Class c = (Class)s_typeId2class.get(this);
		if(c == null)
		{
			String className;
			synchronized(Backend.THREADLOCK)
			{
				className = this._getJavaClassName();
			}
			try
			{
				c = Class.forName(className);
			}
			catch(ClassNotFoundException e)
			{
				throw new SQLException(e.getMessage());
			}
			s_typeId2class.put(this, c);
			s_class2typeId.put(c, this);
		}
		return c;
	}

	/**
	 * The native value is used as the hash code.
	 * @return The hashCode for this <code>Oid</code>.
	 */
	public int hashCode()
	{
		return m_native;
	}

	/**
	 * A Type well known to PostgreSQL but not known as a standard XOPEN
	 * SQL type can be registered here. This includes types like the Oid
	 * itself and all the geometry related types.
	 * @param clazz The Java class that corresponds to the type id.
	 * @param typeId The well known type id.
	 */
	public static void registerType(Class clazz, Oid typeId)
	{
		s_class2typeId.put(clazz, typeId);
		if(!s_typeId2class.containsKey(typeId))
			s_typeId2class.put(typeId, clazz);
	}

	/**
	 * Finds the PostgreSQL well known Oid for the given class.
	 * @param clazz The class.
	 * @return The well known Oid or null if no such Oid could be found.
	 */
	public static Oid forJavaClass(Class clazz)
	{
		return (Oid)s_class2typeId.get(clazz);
	}

	/**
	 * Finds the PostgreSQL well known Oid for the XOPEN Sql type.
	 * @param sqlType The XOPEN type code.
	 * @return The well known Oid or null if no such Oid could be found.
	 */
	public static Oid forSqlType(int sqlType)
	{
		synchronized(Backend.THREADLOCK)
		{
			return _forSqlType(sqlType);
		}
	}

	/**
	 * Returns the PostgreSQL type id for the Oid type.
	 */
	public static Oid getTypeId()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getTypeId();
		}
	}

	/**
	 * Returns a string representation of this OID.
	 */
	public String toString()
	{
		return "OID(" + m_native + ')';
	}

	private native static Oid _forSqlType(int sqlType);
	private native static Oid _getTypeId();

	private native String _getJavaClassName()
	throws SQLException;
}
