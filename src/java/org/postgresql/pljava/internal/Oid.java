/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.internal;

/**
 * The <code>Oid</code> correspons to the internal PostgreSQL <code>Oid</code>.
 * Should the size of that change from 32 bit, this class must change too.
 *
 * @author Thomas Hallgren
 */
public class Oid
{
	static
	{
		System.loadLibrary("pljava");
	}

	/**
	 * The Oid representing the invalid Oid.
	 * See definition in file &quot;include/postgres_ext.h&quot; of the
	 * PostgreSQL distribution.
	 */
	private static final Oid s_invalidOid = new Oid(0);

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
	 * Returns the singleton that represents the invalid Oid.
	 * @return The one and only invalid Oid.
	 */
	public static Oid invalidOid()
	{
		return s_invalidOid;
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
	 * Checks to see if this <code>Oid</code> is valid.
	 * @return <code>true</code> if this <code>Oid</code> is valid.
	 */
	public boolean isValid()
	{
		return m_native == s_invalidOid.m_native;
	}
}
