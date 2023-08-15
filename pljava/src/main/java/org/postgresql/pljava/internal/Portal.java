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

import static org.postgresql.pljava.internal.Backend.doInPG;
import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;
import org.postgresql.pljava.internal.SPI;

import org.postgresql.pljava.Lifespan;

import org.postgresql.pljava.model.TupleDescriptor;
import org.postgresql.pljava.model.TupleTableSlot;

import org.postgresql.pljava.pg.ResourceOwnerImpl;
import org.postgresql.pljava.pg.TupleTableSlotImpl;

import java.sql.SQLException;

import java.util.List;

/**
 * The <code>Portal</code> correspons to the internal PostgreSQL
 * <code>Portal</code> type.
 *
 * @author Thomas Hallgren
 */
public class Portal implements org.postgresql.pljava.model.Portal
{
	/*
	 * Hold a reference to the Java ExecutionPlan object as long as we might be
	 * using it, just to make sure Java unreachability doesn't cause it to
	 * mop up its native plan state while the portal might still be using it.
	 */
	private ExecutionPlan m_plan;

	private TupleDescriptor m_tupdesc;

	private TupleTableSlotImpl m_slot;

	private final State m_state;

	private static final int  FETCH_FORWARD  = 0;
	private static final int  FETCH_BACKWARD = 1;
	private static final int  FETCH_ABSOLUTE = 2;
	private static final int  FETCH_RELATIVE = 3;
	private static final long FETCH_ALL = ALL;

	static
	{
		assert FETCH_FORWARD  == Direction.FORWARD .ordinal();
		assert FETCH_BACKWARD == Direction.BACKWARD.ordinal();
		assert FETCH_ABSOLUTE == Direction.ABSOLUTE.ordinal();
		assert FETCH_RELATIVE == Direction.RELATIVE.ordinal();
	}

	Portal(long ro, long pointer, ExecutionPlan plan)
	{
		m_state = new State(this, ResourceOwnerImpl.fromAddress(ro), pointer);
		m_plan = plan;
	}

	private static class State
	extends DualState.SingleSPIcursorClose<Portal>
	{
		private State(
			Portal referent, Lifespan span, long portal)
		{
			super(referent, span, portal);
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
	}

	/**
	 * Invalidates this structure and frees up memory using the
	 * internal function <code>SPI_cursor_close</code>
	 */
	public void close()
	{
		doInPG(() ->
		{
			m_state.releaseFromJava();
			m_plan = null;
			m_tupdesc = null;
			m_slot = null;
		});
	}

	/**
	 * Returns the {@link TupleDescriptor} that describes the row tuples for
	 * this {@code Portal}.
	 * @throws SQLException if the handle to the native structure is stale.
	 */
	@Override
	public TupleDescriptor tupleDescriptor()
	throws SQLException
	{
		return doInPG(() ->
		{
			if ( null == m_tupdesc )
				m_tupdesc = _getTupleDescriptor(m_state.getPortalPtr());
			return m_tupdesc;
		});
	}

	private TupleTableSlotImpl slot() throws SQLException
	{
		assert threadMayEnterPG(); // only call slot() on PG thread
		if ( null == m_slot )
			m_slot = _makeTupleTableSlot(
				m_state.getPortalPtr(), tupleDescriptor());
		return m_slot;
	}

	@Override
	public List<TupleTableSlot> fetch(Direction dir, long count)
	throws SQLException
	{
		boolean forward;
		switch ( dir )
		{
		case FORWARD : forward = true ; break;
		case BACKWARD: forward = false; break;
		default:
			throw new UnsupportedOperationException(
				dir + " Portal mode not yet supported");
		}

		return doInPG(() ->
		{
			fetch(forward, count); // for now; it's already implemented
			return SPI.getTuples(slot());
		});
	}

	@Override
	public long move(Direction dir, long count)
	throws SQLException
	{
		boolean forward;
		switch ( dir )
		{
		case FORWARD : forward = true ; break;
		case BACKWARD: forward = false; break;
		default:
			throw new UnsupportedOperationException(
				dir + " Portal mode not yet supported");
		}

		return move(forward, count); // for now; it's already implemented
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

	private static native TupleDescriptor _getTupleDescriptor(long pointer)
	throws SQLException;

	private static native TupleTableSlotImpl
		_makeTupleTableSlot(long pointer, TupleDescriptor td)
	throws SQLException;

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
