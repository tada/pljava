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

/**
 * The <code>Tuple</code> correspons to the internal PostgreSQL
 * <code>HeapTuple</code>.
 *
 * @author Thomas Hallgren
 */
public class Tuple
{
	private final State m_state;

	Tuple(DualState.Key cookie, long resourceOwner, long pointer)
	{
		m_state = new State(cookie, this, resourceOwner, pointer);
	}

	private static class State
	extends DualState.SingleHeapFreeTuple<Tuple>
	{
		private State(
			DualState.Key cookie, Tuple t, long ro, long ht)
		{
			super(cookie, t, ro, ht);
		}

		/**
		 * Return the HeapTuple pointer.
		 *<p>
		 * This is a transitional implementation: ideally, each method requiring
		 * the native state would be moved to this class, and hold the pin for
		 * as long as the state is being manipulated. Simply returning the
		 * guarded value out from under the pin, as here, is not great practice,
		 * but as long as the value is only used in instance methods of
		 * Tuple, or subclasses, or something with a strong reference
		 * to this Tuple, and only on a thread for which
		 * {@code Backend.threadMayEnterPG()} is true, disaster will not strike.
		 * It can't go Java-unreachable while an instance method's on the call
		 * stack, and the {@code Invocation} marking this state's native scope
		 * can't be popped before return of any method using the value.
		 */
		private long getHeapTuplePtr() throws SQLException
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
	 * Return pointer to native HeapTuple structure as a long; use only while
	 * a reference to this class is live and the THREADLOCK is held.
	 */
	public final long getNativePointer() throws SQLException
	{
		return m_state.getHeapTuplePtr();
	}

	/**
	 * Obtains a value from the underlying native <code>HeapTuple</code>
	 * structure.
	 *<p>
	 * Conversion to a JDBC 4.1 specified class is best effort, if the native
	 * type system knows how to do so; otherwise, the return value can be
	 * whatever would have been returned in the legacy case. Caller beware!
	 * @param tupleDesc The Tuple descriptor for this instance.
	 * @param index Index of value in the structure (one based).
	 * @param type Desired Java class of the result, if the JDBC 4.1 version
	 * of {@code getObject} has been called; null in all the legacy cases.
	 * @return The value or <code>null</code>.
	 * @throws SQLException If the underlying native structure has gone stale.
	 */
	public Object getObject(TupleDesc tupleDesc, int index, Class<?> type)
	throws SQLException
	{
		return doInPG(() ->
			_getObject(this.getNativePointer(),
				tupleDesc.getNativePointer(), index, type));
	}

	private static native Object _getObject(
		long pointer, long tupleDescPointer, int index, Class<?> type)
	throws SQLException;
}
