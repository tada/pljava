/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.WeakHashMap;
import java.util.logging.Logger;

/**
 * @author Thomas Hallgren
 */
public class PgSavepoint implements java.sql.Savepoint
{
	private static final WeakHashMap<PgSavepoint,Boolean> s_knownSavepoints =
		new WeakHashMap<PgSavepoint,Boolean>();

	/*
	 * PostgreSQL allows an internal subtransaction to have a name, though it
	 * isn't used for much, and is allowed to be null. The JDBC Savepoint API
	 * also allows a name, so we will pass it along to PG if provided, and save
	 * it here for the API method getSavepointName.
	 */
	private final String m_name;

	/*
	 * The transaction ID assigned during BeginInternalSubTransaction is really
	 * the identifier that matters. An instance will briefly have the default
	 * value zero when constructed; the real value will be filled in during the
	 * native _set method.
	 */
	private int m_xactId;

	/*
	 * The nesting level will also have its default value briefly at
	 * construction, with the real value filled in by _set. Real values will
	 * be positive, so setting this back to zero can be used to signal
	 * onInvocationExit that nothing remains to do.
	 */
	private int m_nestLevel;

	/*
	 * A complication arises if a savepoint listener has been registered:
	 * PostgreSQL will make the callback before BeginInternalSubTransaction
	 * has returned. The correct xactId will be passed to the callback, but it
	 * won't have been set in this object yet. The forId() method below can
	 * handle that by finding the object in this static and setting its m_xactId
	 * so it's fully initialized by the time it is passed to the listener.
	 *
	 * As all of this action (constructing and setting a new savepoint, calling
	 * a savepoint listener) can only happen on the PG thread, this static is
	 * effectively confined to one thread.
	 */
	private static PgSavepoint s_nursery;

	private PgSavepoint(String name)
	{
		m_name = name;
	}

	/**
	 * Establish a savepoint; only to be called by
	 * {@link Connection#setSavepoint Connection.setSavepoint}, which makes the
	 * arrangements for {@link #onInvocationExit onInvocationExit} to be
	 * called.
	 */
	public static PgSavepoint set(String name)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			PgSavepoint sp = new PgSavepoint(name);
			s_nursery = sp;
			try
			{
				/*
				 * This assignment of m_xactId will be redundant if a listener
				 * already was called and filled in the ID, but harmless.
				 */
				sp.m_xactId = sp._set(name);
			}
			finally
			{
				s_nursery = null;
			}
			s_knownSavepoints.put(sp, Boolean.TRUE);
			return sp;
		}
	}

	static PgSavepoint forId(int savepointId)
	{
		if(savepointId != 0)
		{
			assert Backend.threadMayEnterPG(); // this only happens on PG thread
			if ( null != s_nursery ) // can only be the Savepoint being set
			{
				s_nursery.m_xactId = savepointId;
				return s_nursery;
			}
			for ( PgSavepoint sp : s_knownSavepoints.keySet() )
			{
				if(savepointId == sp.m_xactId)
					return sp;
			}
		}
		return null;
	}

	@Override
	public int hashCode()
	{
		return this.getSavepointId();
	}

	@Override
	public boolean equals(Object o)
	{
		return (this == o);
	}

	/**
	 * Release (commit) a savepoint; only to be called by
	 * {@link Connection#releaseSavepoint Connection.releaseSavepoint}.
	 */
	public void release()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			_release(m_xactId, m_nestLevel);
			s_knownSavepoints.remove(this);
			m_nestLevel = 0;
		}
	}

	/**
	 * Roll back a savepoint; only to be called by
	 * {@link Connection#rollback(Savepoint) Connection.rollback}.
	 */
	public void rollback()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			_rollback(m_xactId, m_nestLevel);
			s_knownSavepoints.remove(this);
			m_nestLevel = 0;
		}
	}

	@Override
	public String getSavepointName() throws SQLException
	{
		// XXX per JDBC, this should throw an exception rather than return null
		return m_name;
	}

	@Override
	public int getSavepointId()
	{
		// XXX per JDBC, this should throw an exception if m_name ISN'T null
		return m_xactId;
	}

	public void onInvocationExit(Connection conn)
	throws SQLException
	{
		if(m_nestLevel == 0)
			return;

		Logger logger = Logger.getAnonymousLogger();
		if(Backend.isReleaseLingeringSavepoints())
		{
			logger.warning("Releasing savepoint '" + m_xactId +
				"' since its lifespan exceeds that of the function where " +
				"it was set");
			conn.releaseSavepoint(this);
		}
		else
		{
			logger.warning("Rolling back to savepoint '" + m_xactId +
				"' since its lifespan exceeds that of the function where " +
				"it was set");
			conn.rollback(this);
		}
	}

	private native int _set(String name)
	throws SQLException;

	private static native void _release(int xid, int nestLevel)
	throws SQLException;

	private static native void _rollback(int xid, int nestLevel)
	throws SQLException;
}
