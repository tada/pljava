/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>AclId</code> correspons to the internal PostgreSQL <code>AclId</code>.
 *
 * @author Thomas Hallgren
 */
public final class AclId
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
	 * Returns the integer value of this id.
	 */
	public int intValue()
	{
		return m_native;
	}

	/**
	 * Returns the hashCode of this id.
	 */
	public int hashCode()
	{
		return m_native;
	}

	/**
	 * Return the current <em>effective</em> database user id.
	 *<p>
	 * <a href=
'http://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/init/miscinit.c;h=f8cc2d85c18f4e3a21a3e22457ef78d286cd1330;hb=b196a71d88a325039c0bf2a9823c71583b3f9047#l291'
>Definition</a>:
	 * "The one to use for all normal permissions-checking purposes."
	 * Within {@code SECURITY DEFINER} functions and some specialized commands,
	 * it can be different from the {@linkplain #getOuterUser outer ID}.
	 */
	public static AclId getUser()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getUser();
		}
	}

	/**
	 * Return the <em>outer</em> database user id.
	 *<p>
	 * <a href=
'http://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/init/miscinit.c;h=f8cc2d85c18f4e3a21a3e22457ef78d286cd1330;hb=b196a71d88a325039c0bf2a9823c71583b3f9047#l286'
>Definition</a>:
	 * "the current user ID in effect at the 'outer level' (outside any
	 * transaction or function)." The session user id taking into account any
	 * {@code SET ROLE} in effect. This is the ID that a
	 * {@code SECURITY DEFINER} function should revert to if it needs to operate
	 * with the invoker's permissions.
	 * @since 1.5.0
	 */
	public static AclId getOuterUser()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getOuterUser();
		}
	}

	/**
	 * Deprecated synonym for {@link #getOuterUser getOuterUser}.
	 * @deprecated As of 1.5.0, this method is retained only for compatibility
	 * with old code, and returns the same value as
	 * {@link #getOuterUser getOuterUser}, which should be used instead.
	 * Previously, it returned the <em>session</em> ID unconditionally, which is
	 * incorrect for any PostgreSQL version newer than 8.0, because it was
	 * unaware of {@code SET ROLE} introduced in 8.1. Any actual use case for a
	 * method that ignores roles and reports only the session ID should be
	 * reported as an issue.
	 */
	@Deprecated
	public static AclId getSessionUser()
	{
		return getOuterUser();
	}

	/**
	 * Return the id of the named user.
	 * @throws SQLException if the user is unknown to the system.
	 */
	public static AclId fromName(String name) throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _fromName(name);
		}
	}

	/**
	 * Return the name that corresponds to this id.
	 */
	public String getName()
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getName();
		}
	}

	/**
	 * Return true if this AclId has the right to create new objects
	 * in the given schema.
	 */
	public boolean hasSchemaCreatePermission(Oid oid)
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._hasSchemaCreatePermission(oid);
		}
	}

	/**
	 * Returns true if this AclId represents a super user.
	 */
	public boolean isSuperuser()
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._isSuperuser();
		}
	}

	/**
	 * Returns the result of calling #getName().
	 */
	public String toString()
	{
		return this.getName();
	}

	private static native AclId _getUser();
	private static native AclId _getOuterUser();
	private static native AclId _fromName(String name);
	private native String _getName();
	private native boolean _hasSchemaCreatePermission(Oid oid);
	private native boolean _isSuperuser();
}
