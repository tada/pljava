/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;
import java.util.ArrayList;

/**
 * The <code>ExecutionPlan</code> correspons to the execution plan obtained
 * using an internal PostgreSQL <code>SPI_prepare</code> call.
 *
 * @author Thomas Hallgren
 */
public class ExecutionPlan extends NativeStruct
{
	private static final ArrayList s_deathRow = new ArrayList();

	private boolean m_isDurable = false;

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
		synchronized(Backend.THREADLOCK)
		{
			return _prepare(statement, argTypes);
		}
	}

	/**
	 * Invalidates this structure and frees up memory using the
	 * internal function <code>SPI_freeplan</code>
	 */
	public void invalidate()
	{
		synchronized(Backend.THREADLOCK)
		{
			this._invalidate();
		}
	}

	/**
	 * Make this plan durable. This means that the plan will survive until
	 * it is explicitly invalidated.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public void makeDurable()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			if(m_isDurable)
				return;
			this._savePlan();
			m_isDurable = true;
		}
	}

	/**
	 * Finalizers might run by a thread other then the main thread. If
	 * that is the case, the pointer will be stored on a &quot;death
	 * row&quot; that will be investegated by the main thread later
	 * on.
	 */
	public void finalize()
	{
		if(m_isDurable && this.isValid())
		{
			long nativePtr = this.getNative();
			if(Backend.isCallingJava())
				this.invalidate();
			else
			{
				synchronized(s_deathRow)
				{
					s_deathRow.add(new Long(nativePtr));
					setDeathRowFlag(true);
				}
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

	private native static ExecutionPlan _prepare(String statement, Oid[] argTypes)
	throws SQLException;

	private native void _invalidate();
}
