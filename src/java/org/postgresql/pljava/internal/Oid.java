/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.internal;

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
	private static final HashMap s_class2typeId = new HashMap();

	static
	{
		System.loadLibrary("pljava");
		registerType(Oid.class, Oid.getTypeId());
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
	public native static Oid forSqlType(int sqlType);
	
	/**
	 * Returns the PostgreSQL type id for the Oid type.
	 */
	public native static Oid getTypeId();
}
