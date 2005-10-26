/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * @author Thomas Hallgren
 */
public class Savepoint
{
	private long m_pointer;

	private Savepoint(long pointer)
	{
		m_pointer = pointer;
	}

	public static Savepoint set(String name)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return new Savepoint(_set(name));
		}
	}

	public void release()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			_release(m_pointer);
			m_pointer = 0;
		}
	}

	public void rollback()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			_rollback(m_pointer);
			m_pointer = 0;
		}
	}

	public String getName()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getName(m_pointer);
		}
	}

	private static native long _set(String name)
	throws SQLException;

	private static native void _release(long pointer)
	throws SQLException;

	private static native void _rollback(long pointer)
	throws SQLException;

	private static native String _getName(long pointer);
}
