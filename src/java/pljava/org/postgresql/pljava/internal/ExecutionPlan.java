/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * The <code>ExecutionPlan</code> correspons to the execution plan obtained
 * using an internal PostgreSQL <code>SPI_prepare</code> call.
 *
 * @author Thomas Hallgren
 */
public class ExecutionPlan extends NativeStruct
{
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * MRU cache for prepared plans.
     */
    static final class PlanCache extends LinkedHashMap
	{
    	private final int m_cacheSize;

		public PlanCache(int cacheSize)
		{
			super(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, true);
			m_cacheSize = cacheSize;
		}

		protected boolean removeEldestEntry(Map.Entry eldest)
		{
	        if(this.size() > m_cacheSize)
	        {
	        	((ExecutionPlan)eldest.getValue()).invalidate();
	        	return true;
	        }
	        return false;
	    }
	};

	static final class PlanKey
	{
		private final int    m_hashCode;
		private final String m_stmt;
		private final Oid[]  m_argTypes;

		PlanKey(String stmt, Oid[] argTypes)
		{
			m_stmt     = stmt;
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

	private static final ArrayList s_deathRow = new ArrayList();
	private static final PlanCache s_planCache;
	
	static
	{
		int cacheSize = _getCacheSize();
		s_planCache = (cacheSize > 0)
			? new PlanCache(cacheSize)
			: null;
	}

	/**
	 * Set up a cursor that will execute the plan using the internal
	 * <code>SPI_cursor_open</code> function
	 * @param cursorName Name of the cursor or <code>null</code> for a
	 * system generated name.
	 * @param parameters Values for the parameters.
	 * @return The <code>Portal</code> that represents the opened cursor.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public Portal cursorOpen(String cursorName, Object[] parameters)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._cursorOpen(cursorName, parameters);
		}
	}
	
	/**
	 * Returns <code>true</code> if this <code>ExecutionPlan</code> can create
	 * a <code>Portal</code> using {@link #cursorOpen}. This is true if the
	 * plan contains only one regular <code>SELECT</code> query.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public boolean isCursorPlan()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._isCursorPlan();
		}
	}
	
	/**
	 * Execute the plan using the internal <code>SPI_execp</code> function.
	 * @param parameters Values for the parameters.
	 * @param rowCount The maximum number of tuples to create. A value
	 * of <code>rowCount</code> of zero is interpreted as no limit, i.e.,
	 * run to completion.
	 * @return One of the status codes declared in class {@link SPI}.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public int execp(Object[] parameters, int rowCount)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._execp(parameters, rowCount);
		}
	}

	/**
	 * Create an execution plan for a statement to be executed later using
	 * the internal <code>SPI_prepare</code> function.
	 * @param statement The command string.
	 * @param argTypes SQL types of argument types.
	 * @return An execution plan for the prepared statement.
	 * @throws SQLException
	 * @see java.sql.Types
	 */
	public static ExecutionPlan prepare(String statement, Oid[] argTypes)
	throws SQLException
	{
		if(s_planCache != null)
		{
			Object key = (argTypes == null)
				? (Object)statement
				: (Object)new PlanKey(statement, argTypes);

			ExecutionPlan plan = (ExecutionPlan)s_planCache.get(key);
			if(plan == null)
			{
				synchronized(Backend.THREADLOCK)
				{
					plan = _prepare(statement, argTypes);
				}
				s_planCache.put(key, plan);
			}
			return plan;
		}
		synchronized(Backend.THREADLOCK)
		{
			return _prepare(statement, argTypes);
		}
	}

	/**
	 * Invalidates this structure and frees up memory using the
	 * internal function <code>SPI_freeplan</code>
	 */
	public final void invalidate()
	{
		synchronized(Backend.THREADLOCK)
		{
			this._invalidate();
		}
	}

	/**
	 * Finalizers might run by a thread other then the main thread so
	 * instead of calling invalidate here we store the pointer on a
	 * &quot;death row&quot; that will be investegated by the main
	 * thread later on.
	 */
	public void finalize()
	{
		long nativePtr = this.getNative();
		if(nativePtr != 0)
		{
			synchronized(s_deathRow)
			{
				s_deathRow.add(new Long(nativePtr));
				setDeathRowFlag(true);
			}
		}
	}

	/**
	 * Makes this plan durable. Failure to invalidate the plan after this
	 * will result in resource leaks.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	private native void _savePlan()
	throws SQLException;
	
	/**
	 * Returns an array of native pointers that originates from instances
	 * finalized by another thread than the main thread. This method is
	 * called by the main thread when it sees the hasDeathRowFlag. The
	 * death row is cleared by this call.
	 */
	static long[] getDeathRow()
	{
		synchronized(s_deathRow)
		{
			int top = s_deathRow.size();
			long[] dr = new long[top];
			while(--top >= 0)
				dr[top] = ((Long)s_deathRow.get(top)).longValue();
			s_deathRow.clear();
			setDeathRowFlag(false);
			return dr;
		}
	}
	
	/**
	 * Sets or clears the flag that tells the main thread that there
	 * are candidates waiting on the death row.
	 * @param flag
	 */
	private native static void setDeathRowFlag(boolean flag);

	private native Portal _cursorOpen(String cursorName, Object[] parameters)
	throws SQLException;

	private native boolean _isCursorPlan()
	throws SQLException;

	private native int _execp(Object[] parameters, int rowCount)
	throws SQLException;

	private native static int _getCacheSize();

	private native static ExecutionPlan _prepare(String statement, Oid[] argTypes)
	throws SQLException;

	private native void _invalidate();
}
