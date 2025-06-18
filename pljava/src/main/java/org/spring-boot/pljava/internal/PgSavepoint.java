/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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

import static org.postgresql.pljava.internal.Backend.doInPG;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.SQLNonTransientException;
import java.util.Iterator;
import java.util.WeakHashMap;
import java.util.logging.Logger;

/**
 * Implementation of {@link Savepoint} for the SPI connection.
 *<p>
 * It is an historical oddity that this is in the {@code .internal} package
 * rather than {@code .jdbc}.
 * @author Thomas Hallgren
 */
public class PgSavepoint implements Savepoint
{
	/*
	 * Instances that might be live are tracked here in a WeakHashMap. The
	 * first one created (i.e., outermost) also has a strong reference held by
	 * the Invocation, so it can be zapped if lingering around at function exit.
	 * That automatically takes care of any later/inner ones, so it is not as
	 * critical to track those. Any instance that disappears from this
	 * WeakHashMap (because the application code has let go of all its live
	 * references) is an instance we don't have to fuss with. (That can mean,
	 * though, if any SavepointListeners are registered, and a reportable event
	 * happens to such a savepoint, listeners will be called with null instead
	 * of a Savepoint instance. That hasn't been changed, but is now documented
	 * over at SavepointListener, where it wasn't before.)
	 *
	 * Manipulations of this map take place only on the PG thread.
	 */
	private static final WeakHashMap<PgSavepoint,Boolean> s_knownSavepoints =
		new WeakHashMap<>();

	private static void forgetNestLevelsGE(int nestLevel)
	{
		assert Backend.threadMayEnterPG();
		Iterator<PgSavepoint> it = s_knownSavepoints.keySet().iterator();
		while ( it.hasNext() )
		{
			PgSavepoint sp = it.next();
			if ( sp.m_nestLevel < nestLevel )
				continue;
			it.remove();
			sp.m_nestLevel = 0; // force exception on future attempts to use
		}
	}

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
	 * onInvocationExit that nothing remains to do. Always manipulated and
	 * checked on "the PG thread".
	 */
	private int m_nestLevel;

	/*
	 * JDBC requires the rollback operation not "use up" the savepoint that is
	 * rolled back to (though it does discard any that were created after it).
	 * PL/Java has historically gotten that wrong. Changing that behavior could
	 * lead to unexpected warnings at function exit, if code written to the
	 * prior behavior has rolled back to a savepoint and then forgotten it,
	 * expecting it not to be found unreleased when the function exits.
	 *
	 * The behavior at function exit has historically been governed by the
	 * pljava.release_lingering_savepoints GUC: true => savepoint released,
	 * false => savepoint rolled back, with a warning either way. To accommodate
	 * savepoints that are still alive after rollback, a situation that formerly
	 * did not arise, create a third behavior: if such a 'reborn' savepoint is
	 * found "lingering" at function exit, it will be silently released.
	 */
	private boolean m_reborn = false;

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
		return doInPG(() ->
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
		});
	}

	static PgSavepoint forId(int savepointId)
	{
		if(savepointId == 0)
			return null;
		return doInPG(() ->
		{
			if ( null != s_nursery ) // can only be the Savepoint being set
			{
				PgSavepoint sp = s_nursery;
				sp.m_xactId = savepointId;
				s_nursery = null;
				return sp;
			}
			for ( PgSavepoint sp : s_knownSavepoints.keySet() )
			{
				if(savepointId == sp.m_xactId)
					return sp;
			}
			return null;
		});
	}

	@Override
	public int hashCode()
	{
		return System.identityHashCode(this);
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
		doInPG(() ->
		{
			if ( 0 == m_nestLevel )
				throw new SQLNonTransientException(
					"attempt to release savepoint " +
					(null != m_name ? ('"' + m_name + '"') : m_xactId) +
					" that is no longer valid", "3B001");

			_release(m_xactId, m_nestLevel);
			forgetNestLevelsGE(m_nestLevel);
		});
	}

	/**
	 * Roll back a savepoint; only to be called by
	 * {@link Connection#rollback(Savepoint) Connection.rollback}.
	 *<p>
	 * JDBC's rollback-to-savepoint operation discards all more-deeply-nested
	 * savepoints, but not the one that is the target of the rollback. That one
	 * remains active and can be used again. That behavior matches PostgreSQL's
	 * SQL-level savepoints, but here it has to be built on top of the
	 * "internal subtransaction" layer, and made to work the right way.
	 */
	public void rollback()
	throws SQLException
	{
		doInPG(() ->
		{
			if ( 0 == m_nestLevel )
				throw new SQLNonTransientException(
					"attempt to roll back to savepoint " +
					(null != m_name ? ('"' + m_name + '"') : m_xactId) +
					" that is no longer valid", "3B001");

			_rollback(m_xactId, m_nestLevel);

			/* Forget only more-deeply-nested savepoints, NOT this one */
			forgetNestLevelsGE(1 + m_nestLevel);

			/*
			 * The "internal subtransaction" was used up by rolling back. To
			 * provide the correct JDBC behavior, where a savepoint is not
			 * used up by a rollback, transparently set a new internal one.
			 */
			try
			{
				s_nursery = this;
				m_xactId = _set(m_name);
				m_reborn = true;
			}
			finally
			{
				s_nursery = null;
			}
		});
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

	public void onInvocationExit(boolean withError)
	throws SQLException
	{
		assert Backend.threadMayEnterPG();
		if(m_nestLevel == 0)
			return;

		Logger logger = Logger.getAnonymousLogger();
		if(!withError && (m_reborn || Backend.isReleaseLingeringSavepoints()))
		{
			if ( ! m_reborn )
				logger.warning("Releasing savepoint '" + m_xactId +
					"' since its lifespan exceeds that of the function where " +
					"it was set");
			/*
			 * Perform release directly, not through Connection, which does
			 * other bookkeeping that's unnecessary on invocation exit.
			 */
			_release(m_xactId, m_nestLevel);
			forgetNestLevelsGE(m_nestLevel);
		}
		else
		{
			if ( ! withError  ||  ! m_reborn )
				logger.warning("Rolling back to savepoint '" + m_xactId +
					"' since its lifespan exceeds that of the function where " +
					"it was set");
			/*
			 * Perform rollback directly, without Connection's unnecessary
			 * bookkeeping, and without resurrecting the savepoint this time.
			 */
			_rollback(m_xactId, m_nestLevel);
			forgetNestLevelsGE(m_nestLevel);
		}
	}

	private native int _set(String name)
	throws SQLException;

	private static native void _release(int xid, int nestLevel)
	throws SQLException;

	private static native void _rollback(int xid, int nestLevel)
	throws SQLException;
}
