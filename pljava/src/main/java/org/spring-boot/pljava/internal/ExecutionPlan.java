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

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * The {@code ExecutionPlan} corresponds to the execution plan obtained
 * using an internal PostgreSQL {@code SPI_prepare} call.
 *<p>
 * The {@code ExecutionPlan} is distinct from {@code SPIPreparedStatement}
 * because of its greater specificity. The current {@code PreparedStatement}
 * behavior (though it may, in future, change) is that the types of parameters
 * are not inferred in a "PostgreSQL-up" manner (that is, by having PostgreSQL
 * parse the SQL and report what types the parameters would need to have), but
 * in a "Java-down" manner, driven by the types of the parameter values supplied
 * to the {@code PreparedStatement} before executing it. An
 * {@code ExecutionPlan} corresponds to a particular assignment of parameter
 * types; a subsequent use of the same {@code PreparedStatement} with different
 * parameter values may (depending on their types) lead to generation of a new
 * plan, with the former plan displaced from the {@code PreparedStatement} and
 * into the {@code PlanCache} implemented here. Another re-use of the same
 * {@code PreparedStatement} with the original parameter types will displace the
 * newer plan into the cache and retrieve the earlier one.
 *<p>
 * The native state of a plan is not held in a transient context, so it is not
 * subject to invalidation from the native side. The Java object is kept "live"
 * (garbage-collection prevented) by being referenced either from the
 * {@code Statement} that created it, or from the cache if it has been displaced
 * there. The {@code close} method does not deallocate a plan, but simply moves
 * it to the cache, where it may be found again if needed for the same SQL and
 * parameter types.
 *<p>
 * At no time (except in passing) is a plan referred to both by the cache and by
 * a {@code Statement}. It is cached when displaced out of its statement,
 * and removed from the cache if it is later found there and claimed again by
 * a statement, so that one {@code ExecutionPlan} does not end up getting
 * shared by multiple statement instances. (There is nothing, however,
 * thread-safe about these machinations.)
 *<p>
 * There are not many ways for an {@code ExecutionPlan} to actually be freed.
 * That will happen if it is evicted from the cache, either because it is oldest
 * and the cache limit is reached, or when another plan is cached for the same
 * SQL and parameter types; it will also happen if a {@code PreparedStatement}
 * using the plan becomes unreferenced and garbage-collected without
 * {@code close} being called (which would have moved the plan back to the
 * cache).
 * 
 * @author Thomas Hallgren
 */
public class ExecutionPlan
{
	static final int INITIAL_CACHE_CAPACITY = 29;

	static final float CACHE_LOAD_FACTOR = 0.75f;

	/* These three values must match those in ExecutionPlan.c */
	public static final short SPI_READONLY_DEFAULT = 0;
	public static final short SPI_READONLY_FORCED  = 1;
	public static final short SPI_READONLY_CLEARED = 2;

	private final State m_state;

	private static class State
	extends DualState.SingleSPIfreeplan<ExecutionPlan>
	{
		private State(
			DualState.Key cookie, ExecutionPlan jep, long ro, long ep)
		{
			super(cookie, jep, ro, ep);
		}

		/**
		 * Return the SPI execution-plan pointer.
		 *<p>
		 * This is a transitional implementation: ideally, each method requiring
		 * the native state would be moved to this class, and hold the pin for
		 * as long as the state is being manipulated. Simply returning the
		 * guarded value out from under the pin, as here, is not great practice,
		 * but as long as the value is only used in instance methods of
		 * ExecutionPlan, or subclasses, or something with a strong reference to
		 * this ExecutionPlan, and only on a thread for which
		 * {@code Backend.threadMayEnterPG()} is true, disaster will not strike.
		 * It can't go Java-unreachable while a reference is on the call stack,
		 * and as long as we're on the thread that's in PG, the saved plan won't
		 * be popped before we return.
		 */
		private long getExecutionPlanPtr() throws SQLException
		{
			pin();
			try
			{
				return guardedLong();
			}
			finally
			{
				unpin();
			}
		}
	}

	/**
	 * MRU cache for prepared plans.
	 * The key type is Object, not PlanKey, because for a statement with no
	 * parameters, the statement itself is used as the key, rather than
	 * constructing a PlanKey.
	 */
	static final class PlanCache extends LinkedHashMap<Object,ExecutionPlan>
	{
		private final int m_cacheSize;

		public PlanCache(int cacheSize)
		{
			super(INITIAL_CACHE_CAPACITY, CACHE_LOAD_FACTOR, true);
			m_cacheSize = cacheSize;
		}

		@Override
		protected boolean removeEldestEntry(
			Map.Entry<Object,ExecutionPlan> eldest)
		{
			if(this.size() <= m_cacheSize)
				return false;

			ExecutionPlan evicted = eldest.getValue();
			/*
			 * See close() below for why 'evicted' is not enqueue()d right here.
			 */
			return true;
		}
	};

	static final class PlanKey
	{
		private final int m_hashCode;

		private final String m_stmt;

		private final Oid[] m_argTypes;

		PlanKey(String stmt, Oid[] argTypes)
		{
			m_stmt = stmt;
			m_hashCode = stmt.hashCode() + 1;
			m_argTypes = argTypes;
		}

		public boolean equals(Object o)
		{
			if(!(o instanceof PlanKey))
				return false;

			PlanKey pk = (PlanKey)o;
			if(!pk.m_stmt.equals(m_stmt))
				return false;

			Oid[] pat = pk.m_argTypes;
			Oid[] mat = m_argTypes;
			int idx = pat.length;
			if(mat.length != idx)
				return false;

			while(--idx >= 0)
				if(!pat[idx].equals(mat[idx]))
					return false;

			return true;
		}

		public int hashCode()
		{
			return m_hashCode;
		}
	}

	private static final Map<Object,ExecutionPlan> s_planCache;

	private final Object m_key;

	static
	{
		int cacheSize = Backend.getStatementCacheSize();
		s_planCache = Collections.synchronizedMap(new PlanCache(cacheSize < 11
			? 11
			: cacheSize));
	}

	private ExecutionPlan(DualState.Key cookie, long resourceOwner,
		Object planKey, long spiPlan)
	{
		m_key = planKey;
		m_state = new State(cookie, this, resourceOwner, spiPlan);
	}

	/**
	 * Close the plan.
	 */
	public void close()
	{
		ExecutionPlan old = s_planCache.put(m_key, this);
		/*
		 * For now, do NOT immediately enqueue() a non-null 'old'. It could
		 * still be live via a Portal that is still retrieving results. Java
		 * reachability will determine when it isn't, in the natural course of
		 * things.
		 * If that turns out to keep plans around too long, something more
		 * elaborate can be done, involving coordination with the reachability
		 * of any referencing Portal.
		 */
	}

	/**
	 * Set up a cursor that will execute the plan using the internal
	 * <code>SPI_cursor_open</code> function
	 * 
	 * @param cursorName Name of the cursor or <code>null</code> for a system
	 *            generated name.
	 * @param parameters Values for the parameters.
	 * @param read_only One of the values {@code SPI_READONLY_DEFAULT},
	 *     {@code SPI_READONLY_FORCED}, or {@code SPI_READONLY_CLEARED} (in the
	 *     default case, the native code will defer to
	 *     {@code Function_isCurrentReadOnly}.
	 * @return The <code>Portal</code> that represents the opened cursor.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public Portal cursorOpen(
		String cursorName, Object[] parameters, short read_only)
	throws SQLException
	{
		return doInPG(() ->
			_cursorOpen(m_state.getExecutionPlanPtr(),
				cursorName, parameters, read_only));
	}

	/**
	 * Checks if this <code>ExecutionPlan</code> can create a <code>Portal
	 * </code>
	 * using {@link #cursorOpen}. This is true if the plan contains only one
	 * regular <code>SELECT</code> query.
	 * 
	 * @return <code>true</code> if the plan can create a <code>Portal</code>
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public boolean isCursorPlan() throws SQLException
	{
		return doInPG(() -> _isCursorPlan(m_state.getExecutionPlanPtr()));
	}

	/**
	 * Execute the plan using the internal <code>SPI_execp</code> function.
	 * 
	 * @param parameters Values for the parameters.
	 * @param read_only One of the values {@code SPI_READONLY_DEFAULT},
	 *     {@code SPI_READONLY_FORCED}, or {@code SPI_READONLY_CLEARED} (in the
	 *     default case, the native code will defer to
	 *     {@code Function_isCurrentReadOnly}.
	 * @param rowCount The maximum number of tuples to create. A value of
	 *            <code>rowCount</code> of zero is interpreted as no limit,
	 *            i.e., run to completion.
	 * @return One of the status codes declared in class {@link SPI}.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public int execute(Object[] parameters, short read_only, int rowCount)
	throws SQLException
	{
		return doInPG(() ->
			_execute(m_state.getExecutionPlanPtr(),
				parameters, read_only, rowCount));
	}

	/**
	 * Create an execution plan for a statement to be executed later using the
	 * internal <code>SPI_prepare</code> function.
	 * 
	 * @param statement The command string.
	 * @param argTypes SQL types of argument types.
	 * @return An execution plan for the prepared statement.
	 * @throws SQLException
	 * @see java.sql.Types
	 */
	public static ExecutionPlan prepare(String statement, Oid[] argTypes)
	throws SQLException
	{
		Object key = (argTypes == null)
			? (Object)statement
			: (Object)new PlanKey(statement, argTypes);

		ExecutionPlan plan = s_planCache.remove(key);
		if(plan == null)
			plan = doInPG(() -> _prepare(key, statement, argTypes));
		return plan;
	}

	/*
	 * Not static, so the Portal can hold a live reference to us in case we are
	 * evicted from the cache while it is still using the plan.
	 */
	private native Portal _cursorOpen(long pointer,
		String cursorName, Object[] parameters, short read_only)
		throws SQLException;

	private static native boolean _isCursorPlan(long pointer)
	throws SQLException;

	private static native int _execute(long pointer,
		Object[] parameters, short read_only, int rowCount) throws SQLException;

	private static native ExecutionPlan _prepare(
		Object key, String statement, Oid[] argTypes)
	throws SQLException;
}
