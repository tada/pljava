/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava;

/**
 * The <code>AclId</code> correspons to the internal PostgreSQL <code>AclId</code>.
 *
 * @author Thomas Hallgren
 */
public class AclId
{
	private final int m_native;

	/**
	 * Called from native code.
	 */
	public AclId(int nativeAclId)
	{
		m_native = nativeAclId;
	}

	/**
	 * Returns equal if other is an AclId that is equal to this id.
	 */
	public boolean equals(Object other)
	{
		return this == other || ((other instanceof AclId) && ((AclId)other).m_native == m_native);
	}

	/**
	 * Returns the hashCode of this id.
	 */
	public int hashCode()
	{
		return m_native;
	}

	/**
	 * Return the id of the current database user.
	 */
	public static native AclId getUser();

	/**
	 * Return the id of the session user.
	 */
	public static native AclId getSessionUser();

	/**
	 * Return the name that corresponds to this id.
	 */
	public native String getName();

	/**
	 * Returns the result of calling #getName().
	 */
	public String toString()
	{
		return this.getName();
	}
}
