/*
 * Copyright (c) 2004-2023 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.internal.SPI; // for javadoc
import static org.postgresql.pljava.internal.Backend.doInPG;

import java.sql.SQLException;

/**
 * The <code>Portal</code> correspons to the internal PostgreSQL
 * <code>Portal</code> type.
 *
 * @author Thomas Hallgren
 */
public class Portal
{
	private final State m_state;

	Portal(DualState.Key cookie, long ro, long pointer, ExecutionPlan plan)
	{
		m_state = new State(cookie, this, ro, pointer, plan);
	}

	private static class State
	extends DualState.SingleSPIcursorClose<Portal>
	{
		/*
		 * Hold a reference to the Java ExecutionPlan object as long as we might
		 * be using it, just to make sure Java unreachability doesn't cause it
		 * to mop up its native plan state while the portal might still want it.
		 */
		private ExecutionPlan m_plan;

		private State(
			DualState.Key cookie, Portal referent, long ro, long portal,
			ExecutionPlan plan)
		{
			super(cookie, referent, ro, portal);
			m_plan = plan;
		}

		/**
		 * Return the Portal pointer.
		 *<p>
		 * This is a transitional implementation: ideally, each method requiring
		 * the native state would be moved to this class, and hold the pin for
		 * as long as the state is being manipulated. Simply returning the
		 * guarded value out from under the pin, as here, is not great practice,
		 * but as long as the value is only used in instance methods of
		 * Portal, or subclasses, or something with a strong reference to
		 * this Portal, and only on a thread for which
		 * {@code Backend.threadMayEnterPG()} is true, disaster will not strike.
		 * It can't go Java-unreachable while a reference is on the call stack,
		 * and as long as we're on the thread that's in PG, the saved plan won't
		 * be popped before we return.
		 */
		private long getPortalPtr() throws SQLException
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

		@Override
		protected void javaStateReleased(boolean nativeStateLive)
		{
			super.javaStateReleased(nativeStateLive);
			m_plan = null;
		}
	}

	/**
	 * Invalidates this structure and frees up memory using the
	 * internal function <code>SPI_cursor_close</code>
	 */
	public void close()
	{
		m_state.releaseFromJava();
	}

	/**
	 * Returns the name of this Portal.
	 * @throws SQLException if the handle to the native structure is stale.
	 */
	public String getName()
	throws SQLException
	{
		return doInPG(() -> _getName(m_state.getPortalPtr()));
	}

	/**
	 * Returns the value of the <code>portalPos</code> attribute.
	 * @throws SQLException if the handle to the native structure is stale.
	 */
	public long getPortalPos()
	throws SQLException
	{
		long pos = doInPG(() -> _getPortalPos(m_state.getPortalPtr()));
		if ( pos < 0 )
			throw new ArithmeticException(
				"portal position too large to report " +
				"in a Java signed long");
		return pos;
	}

	/**
	 * Returns the TupleDesc that describes the row Tuples for this
	 * Portal.
	 * @throws SQLException if the handle to the native structure is stale.
	 */
	public TupleDesc getTupleDesc()
	throws SQLException
	{
		return doInPG(() -> _getTupleDesc(m_state.getPortalPtr()));
	}

	/**
	 * Performs an <code>SPI_cursor_fetch</code>.
	 *<p>
	 * The fetched rows are parked at the C global {@code SPI_tuptable}; see
	 * {@link SPI#getTupTable SPI.getTupTable} for retrieving them. (While
	 * faithful to the way the C API works, this seems a bit odd as a Java API,
	 * and suggests that calls to this method and then {@code SPI.getTupTable}
	 * would ideally be done inside a single {@code doInPG}.)
	 * @param forward Set to <code>true</code> for forward, <code>false</code> for backward.
	 * @param count Maximum number of rows to fetch.
	 * @return The actual number of fetched rows.
	 * @throws SQLException if the handle to the native structure is stale.
	 */
	public long fetch(boolean forward, long count)
	throws SQLException
	{
		long fetched =
			doInPG(() -> _fetch(m_state.getPortalPtr(), forward, count));
		if ( fetched < 0 )
			throw new ArithmeticException(
				"fetched too many rows to report in a Java signed long");
		return fetched;
	}

	/**
	 * Returns the value of the <code>atEnd</code> attribute.
	 * @throws SQLException if the handle to the native structure is stale.
	 */
	public boolean isAtEnd()
	throws SQLException
	{
		return doInPG(() -> _isAtEnd(m_state.getPortalPtr()));
	}

	/**
	 * Returns the value of the <code>atStart</code> attribute.
	 * @throws SQLException if the handle to the native structure is stale.
	 */
	public boolean isAtStart()
	throws SQLException
	{
		return doInPG(() -> _isAtStart(m_state.getPortalPtr()));
	}

	/**
	 * Performs an <code>SPI_cursor_move</code>.
	 * @param forward Set to <code>true</code> for forward, <code>false</code> for backward.
	 * @param count Maximum number of rows to fetch.
	 * @return The actual number of rows moved.
	 * @throws SQLException if the handle to the native structure is stale.
	 */
	public long move(boolean forward, long count)
	throws SQLException
	{
		long moved =
			doInPG(() -> _move(m_state.getPortalPtr(), forward, count));
		if ( moved < 0 )
			throw new ArithmeticException(
				"moved too many rows to report in a Java signed long");
		return moved;
	}

	private static native String _getName(long pointer)
	throws SQLException;

	private static native long _getPortalPos(long pointer)
	throws SQLException;

	private static native TupleDesc _getTupleDesc(long pointer)
	throws SQLException;

	private static native long _fetch(long pointer, boolean forward, long count)
	throws SQLException;

	private static native void _close(long pointer);

	private static native boolean _isAtEnd(long pointer)
	throws SQLException;

	private static native boolean _isAtStart(long pointer)
	throws SQLException;

	private static native long _move(long pointer, boolean forward, long count)
	throws SQLException;
}
