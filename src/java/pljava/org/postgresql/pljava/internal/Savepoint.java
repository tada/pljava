/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * @author Thomas Hallgren
 */
public class Savepoint extends NativeStruct
{
	public static Savepoint set(String name)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _set(name);
		}
	}

	public void release()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			this._release();
		}
	}

	public void rollback()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			this._rollback();
		}
	}

	public String getName()
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getName();
		}
	}

	private static native Savepoint _set(String name)
	throws SQLException;

	private native void _release()
	throws SQLException;

	private native void _rollback()
	throws SQLException;

	private native String _getName();
}
