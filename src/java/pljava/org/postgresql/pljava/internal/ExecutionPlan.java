/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;
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
    static final int DEFAULT_INITIAL_CAPACITY = 29;
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
	    		synchronized(Backend.THREADLOCK)
				{
	    			((ExecutionPlan)eldest.getValue())._invalidate();
				}
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

	private static final PlanCache s_planCache;

	private final Object m_key;

	static
	{
		int cacheSize = Backend.getStatementCacheSize();
		s_planCache = new PlanCache(cacheSize < 11 ? 11 : cacheSize);
	}

	private ExecutionPlan(Object key)
	{
		m_key = key;
	}

	/**
	 * Close the plan.
	 */
	public void close()
	{
		s_planCache.put(m_key, this);
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
	 * Checks if this <code>ExecutionPlan</code> can create a <code>Portal
	 * </code> using {@link #cursorOpen}. This is true if the
	 * plan contains only one regular <code>SELECT</code> query.
	 * @return <code>true</code> if the plan can create a <code>Portal</code> 
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
	public int execute(Object[] parameters, int rowCount)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._execute(parameters, rowCount);
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
		Object key = (argTypes == null)
			? (Object)statement
			: (Object)new PlanKey(statement, argTypes);

		ExecutionPlan plan = (ExecutionPlan)s_planCache.remove(key);
		if(plan == null)
		{
			plan = new ExecutionPlan(key);
			synchronized(Backend.THREADLOCK)
			{
				plan._prepare(statement, argTypes);
			}
		}
		return plan;
	}

	private native Portal _cursorOpen(String cursorName, Object[] parameters)
	throws SQLException;

	private native boolean _isCursorPlan()
	throws SQLException;

	private native int _execute(Object[] parameters, int rowCount)
	throws SQLException;

	private native void _prepare(String statement, Oid[] argTypes)
	throws SQLException;

	private native void _invalidate();
}
