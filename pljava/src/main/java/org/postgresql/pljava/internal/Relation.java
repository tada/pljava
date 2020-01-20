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
 * The <code>Relation</code> correspons to the internal PostgreSQL
 * <code>Relation</code>.
 *
 * @author Thomas Hallgren
 */
public class Relation
{
	private TupleDesc m_tupleDesc;
	private final State m_state;

	Relation(DualState.Key cookie, long resourceOwner, long pointer)
	{
		m_state = new State(cookie, this, resourceOwner, pointer);
	}

	private static class State
	extends DualState.SingleGuardedLong<Relation>
	{
		private State(
			DualState.Key cookie, Relation r, long ro, long hth)
		{
			super(cookie, r, ro, hth);
		}

		/**
		 * Return the Relation pointer.
		 *<p>
		 * This is a transitional implementation: ideally, each method requiring
		 * the native state would be moved to this class, and hold the pin for
		 * as long as the state is being manipulated. Simply returning the
		 * guarded value out from under the pin, as here, is not great practice,
		 * but as long as the value is only used in instance methods of
		 * Relation, or subclasses, or something with a strong reference to
		 * this Relation, and only on a thread for which
		 * {@code Backend.threadMayEnterPG()} is true, disaster will not strike.
		 * It can't go Java-unreachable while an instance method's on the call
		 * stack, and the {@code Invocation} marking this state's native scope
		 * can't be popped before return of any method using the value.
		 */
		private long getRelationPtr() throws SQLException
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
	 * Returns the name of this <code>Relation</code>.
	 * @throws SQLException
	 */
	public String getName()
	throws SQLException
	{
		return doInPG(() -> _getName(m_state.getRelationPtr()));
	}

	/**
	 * Returns the schema name of this <code>Relation</code>.
	 * @throws SQLException
	 */
	public String getSchema()
	throws SQLException
	{
		return doInPG(() -> _getSchema(m_state.getRelationPtr()));
	}

	/**
	 * Returns a descriptor that describes tuples in this <code>Relation</code>.
	 * @throws SQLException
	 */
	public TupleDesc getTupleDesc()
	throws SQLException
	{
		if(m_tupleDesc == null)
		{
			m_tupleDesc = doInPG(() -> _getTupleDesc(m_state.getRelationPtr()));
		}
		return m_tupleDesc;
	}

	/**
	 * Creates a new {@code Tuple} by substituting new values for selected
	 * columns copying the columns of the original {@code Tuple} at other
	 * positions. The original {@code Tuple} is not modified.
	 *<p>
	 * Note: starting with PostgreSQL 10, this method can fail if SPI is not
	 * connected; it is the <em>caller's</em> responsibility in PG 10 and up
	 * to ensure that SPI is connected <em>and</em> that a longer-lived memory
	 * context than SPI's has been selected, if the caller wants the result of
	 * this call to survive {@code SPI_finish}.
	 *
	 * @param original The tuple that serves as the source.
	 * @param fieldNumbers An array of one based indexes denoting the positions that
	 * are to receive modified values.
	 * @param values The array of new values. Each value in this array corresponds to
	 * an index in the <code>fieldNumbers</code> array.
	 * @return A copy of the original with modifications.
	 * @throws SQLException if indexes are out of range or the values illegal.
	 */
	public Tuple modifyTuple(Tuple original, int[] fieldNumbers, Object[] values)
	throws SQLException
	{
		return doInPG(() ->
			_modifyTuple(m_state.getRelationPtr(),
				original.getNativePointer(), fieldNumbers, values));
	}

	private static native String _getName(long pointer)
	throws SQLException;

	private static native String _getSchema(long pointer)
	throws SQLException;

	private static native TupleDesc _getTupleDesc(long pointer)
	throws SQLException;

	private static native Tuple _modifyTuple(long pointer, long original, int[] fieldNumbers, Object[] values)
	throws SQLException;
}
