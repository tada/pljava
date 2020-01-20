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
package org.postgresql.pljava.internal;

import static org.postgresql.pljava.internal.Backend.doInPG;

import java.sql.SQLData;
import java.sql.SQLException;
import java.util.HashMap;

/**
 * The <code>Oid</code> correspons to the internal PostgreSQL <code>Oid</code>.
 * Should the size of that change from 32 bit, this class must change too.
 * In Java, the InvalidOid is represented as <code>null</code>.
 *
 * @author Thomas Hallgren
 */
public class Oid extends Number
{
	private static final HashMap<Class<?>,Oid> s_class2typeId =
		new HashMap<>();

	private static final HashMap<Oid,Class<?>> s_typeId2class =
		new HashMap<>();

	/**
	 * Finds the PostgreSQL well known Oid for the given Java object.
	 * @param obj The object.
	 * @return The well known Oid or null if no such Oid could be found.
	 */
	public static Oid forJavaObject(Object obj) throws SQLException
	{
		if ( obj instanceof SQLData )
			return forTypeName(((SQLData)obj).getSQLTypeName());
		return s_class2typeId.get(obj.getClass());
	}

	/**
	 * Finds the PostgreSQL well known Oid for a type name.
	 * @param typeString The name of the type, optionally qualified with a namespace.
	 * @return The well known Oid.
	 * @throws SQLException if the type could not be found
	 */
	public static Oid forTypeName(String typeString)
	throws SQLException
	{
		return doInPG(() -> new Oid(_forTypeName(typeString)));
	}

	/**
	 * Finds the PostgreSQL well known Oid for the XOPEN Sql type.
	 * @param sqlType The XOPEN type code.
	 * @throws SQLException if the type could not be found
	 */
	public static Oid forSqlType(int sqlType)
	throws SQLException
	{
		return doInPG(() -> new Oid(_forSqlType(sqlType)));
	}

	/**
	 * Returns the PostgreSQL type id for the Oid type.
	 */
	public static Oid getTypeId()
	{
		return doInPG(Oid::_getTypeId);
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

	public double doubleValue()
	{
		return m_native;
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

	public float floatValue()
	{
		return m_native;
	}

	public Class getJavaClass()
	throws SQLException
	{
		Class c = s_typeId2class.get(this);
		if(c != null)
			return c;
		return doInPG(() ->
		{
			String className = _getJavaClassName(m_native);
			ClassLoader loader = _getCurrentLoader();
			Class cc;
			try
			{
				String canonName = getCanonicalClassName(className, 0);
				if ( null == loader )
					loader = getClass().getClassLoader();
				cc = Class.forName(canonName, true, loader);
			}
			catch(ClassNotFoundException e)
			{
				throw new SQLException(e.getMessage());
			}
			s_typeId2class.put(this, cc);
			s_class2typeId.put(cc, this);
			return cc;
		});
	}

	/**
	 * The native value is used as the hash code.
	 * @return The hashCode for this <code>Oid</code>.
	 */
	public int hashCode()
	{
		return m_native;
	}

	public int intValue()
	{
		return m_native;
	}

	public long longValue()
	{
		return m_native;
	}

	/**
	 * Returns a string representation of this OID.
	 */
	public String toString()
	{
		return "OID(" + m_native + ')';
	}

	private static String getCanonicalClassName(String name, int nDims)
	{
		if(name.endsWith("[]"))
			return getCanonicalClassName(name.substring(0, name.length() - 2), nDims + 1);

		boolean primitive = true;
		if(name.equals("boolean"))
			name = "Z";
		else if(name.equals("byte"))
			name = "B";
		else if(name.equals("char"))
			name = "C";
		else if(name.equals("double"))
			name = "D";
		else if(name.equals("float"))
			name = "F";
		else if(name.equals("int"))
			name = "I";
		else if(name.equals("long"))
			name = "J";
		else if(name.equals("short"))
			name = "S";
		else
			primitive = false;
		
		if(nDims > 0)
		{
			StringBuffer bld = new StringBuffer();
			while(--nDims >= 0)
				bld.append('[');
			if(primitive)
				bld.append(name);
			else
			{
				bld.append('L');
				bld.append(name);
				bld.append(';');
			}
			name = bld.toString();
		}
		return name;
	}

	private native static int _forTypeName(String typeString)
	throws SQLException;

	private native static int _forSqlType(int sqlType)
	throws SQLException;

	private native static Oid _getTypeId();

	private native static String _getJavaClassName(int nativeOid)
	throws SQLException;

	/**
	 * Return the (initiating, "schema") ClassLoader of the innermost
	 * currently-executing PL/Java function, or null if there is none or the
	 * schema loaders have since been cleared and the loader is gone.
	 */
	private native static ClassLoader _getCurrentLoader()
	throws SQLException;
}
