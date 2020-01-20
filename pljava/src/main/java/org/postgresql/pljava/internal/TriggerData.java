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

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.postgresql.pljava.internal.Backend.doInPG;

import org.postgresql.pljava.TriggerException;
import org.postgresql.pljava.jdbc.TriggerResultSet;

/**
 * The <code>TriggerData</code> correspons to the internal PostgreSQL <code>TriggerData</code>.
 * 
 * @author Thomas Hallgren
 */
public class TriggerData implements org.postgresql.pljava.TriggerData
{
	private Relation m_relation;
	private TriggerResultSet m_old = null;
	private TriggerResultSet m_new = null;
	private Tuple m_newTuple;
	private Tuple m_triggerTuple;
	private boolean m_suppress = false;
	private final State m_state;

	TriggerData(DualState.Key cookie, long resourceOwner, long pointer)
	{
		m_state = new State(cookie, this, resourceOwner, pointer);
	}

	private static class State
	extends DualState.SingleGuardedLong<TriggerData>
	{
		private State(
			DualState.Key cookie, TriggerData td, long ro, long hth)
		{
			super(cookie, td, ro, hth);
		}

		/**
		 * Return the TriggerData pointer.
		 *<p>
		 * This is a transitional implementation: ideally, each method requiring
		 * the native state would be moved to this class, and hold the pin for
		 * as long as the state is being manipulated. Simply returning the
		 * guarded value out from under the pin, as here, is not great practice,
		 * but as long as the value is only used in instance methods of
		 * TriggerData, or subclasses, or something with a strong reference
		 * to this TriggerData, and only on a thread for which
		 * {@code Backend.threadMayEnterPG()} is true, disaster will not strike.
		 * It can't go Java-unreachable while an instance method's on the call
		 * stack, and the {@code Invocation} marking this state's native scope
		 * can't be popped before return of any method using the value.
		 */
		private long getTriggerDataPtr() throws SQLException
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

	private long getNativePointer() throws SQLException
	{
		return m_state.getTriggerDataPtr();
	}

	@Override
	public void suppress() throws SQLException
	{
		if ( isFiredForStatement() )
			throw new TriggerException(this,
				"Attempt to suppress operation in a STATEMENT trigger");
		if ( isFiredAfter() )
			throw new TriggerException(this,
				"Attempt to suppress operation in an AFTER trigger");
		m_suppress = true;
	}

	/**
	 * Returns the ResultSet that represents the new row. This ResultSet will
	 * be null for delete triggers and for triggers that was fired for
	 * statement. <br>
	 * The returned set will be updateable and positioned on a
	 * valid row.
	 * 
	 * @return An updateable <code>ResultSet</code> containing one row or
	 *         <code>null</code>.
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public ResultSet getNew() throws SQLException
	{
		if (m_new != null)
			return m_new;

		if (this.isFiredByDelete() || this.isFiredForStatement())
			return null;

		// PostgreSQL uses the trigger tuple as the new tuple for inserts.
		//
		Tuple tuple =
			this.isFiredByInsert()
				? this.getTriggerTuple()
				: this.getNewTuple();
				
		// Triggers fired after will always have a read-only row
		//
		m_new = new TriggerResultSet(this.getRelation().getTupleDesc(), tuple, this.isFiredAfter());
		return m_new;
	}

	/**
	 * Returns the ResultSet that represents the old row. This ResultSet will
	 * be null for insert triggers and for triggers that was fired for
	 * statement. <br>
	 * The returned set will be read-only and positioned on a
	 * valid row.
	 * 
	 * @return A read-only <code>ResultSet</code> containing one row or
	 *         <code>null</code>.
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public ResultSet getOld() throws SQLException
	{
		if (m_old != null)
			return m_old;

		if (this.isFiredByInsert() || this.isFiredForStatement())
			return null;
		m_old = new TriggerResultSet(this.getRelation().getTupleDesc(), this.getTriggerTuple(), true);
		return m_old;
	}

	/**
	 * Commits the changes made on the <code>ResultSet</code> representing
	 * <code>new</code> and returns the native pointer of new tuple. This
	 * method is called automatically by the trigger handler and should not
	 * be called in any other way.
	 *<p>
	 * Note: starting with PostgreSQL 10, this method can fail if SPI is not
	 * connected; it is the <em>caller's</em> responsibility in PG 10 and up
	 * to ensure that SPI is connected <em>and</em> that a longer-lived memory
	 * context than SPI's has been selected, if the caller wants the result of
	 * this call to survive {@code SPI_finish}.
	 * 
	 * @return The modified tuple, or if no modifications have been made, the
	 *         original tuple.
	 */
	public long getTriggerReturnTuple() throws SQLException
	{
		if(this.isFiredForStatement() || this.isFiredAfter() || m_suppress)
			//
			// Only triggers fired for each row, and not AFTER, can have a
			// nonzero return value. If such a trigger does return zero, it
			// tells PostgreSQL to silently suppress the row operation involved.
			//
			return 0;

		if (m_new != null)
		{
			Object[] changes = m_new.getChangeIndexesAndValues();
			if (changes != null)
			{
				Tuple original = (Tuple)changes[0];
				int[] indexes = (int[])changes[1];
				Object[] values = (Object[])changes[2];
				return this.getRelation().modifyTuple(original, indexes, values).getNativePointer();
			}
		}

		// Return the original tuple.
		//
		return (this.isFiredByUpdate() ? this.getNewTuple() : this.getTriggerTuple()).getNativePointer();
	}

	public String getTableName()
	throws SQLException
	{
		return this.getRelation().getName();
	}

	public String getSchemaName() throws SQLException {
		return this.getRelation().getSchema();
	}

	/**
	 * Returns a descriptor for the Tuples exposed by this trigger.
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public Relation getRelation()
	throws SQLException
	{
		if(m_relation == null)
		{
			m_relation = doInPG(() -> _getRelation(this.getNativePointer()));
		}
		return m_relation;
	}

	/**
	 * Returns a <code>Tuple</code> reflecting the row for which the trigger
	 * was fired. This is the row being inserted, updated, or deleted. If this
	 * trigger was fired for an <code>
	 * INSERT</code> or <code>DELETE</code>
	 * then this is what you should return to from the method if you don't want
	 * to replace the row with a different one (in the case of <code>INSERT
	 * </code>)
	 * or skip the operation.
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public Tuple getTriggerTuple()
	throws SQLException
	{
		if(m_triggerTuple == null)
		{
			m_triggerTuple =
				doInPG(() -> _getTriggerTuple(this.getNativePointer()));
		}
		return m_triggerTuple;
	}

	/**
	 * Returns a <code>Tuple</code> reflecting the new version of the row, if
	 * the trigger was fired for an <code>UPDATE</code>, and <code>null</code>
	 * if it is for an <code>INSERT</code> or a <code>DELETE</code>. This
	 * is what you have to return from the function if the event is an <code>UPDATE</code>
	 * and you don't want to replace this row by a different one or skip the
	 * operation.
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public Tuple getNewTuple()
	throws SQLException
	{
		if(m_newTuple == null)
		{
			m_newTuple = doInPG(() -> _getNewTuple(this.getNativePointer()));
		}
		return m_newTuple;
	}

	/**
	 * Returns the arguments for this trigger (as declared in the <code>CREATE TRIGGER</code>
	 * statement. If the trigger has no arguments, this method will return an
	 * array with size 0.
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public String[] getArguments()
	throws SQLException
	{
		return doInPG(() -> _getArguments(this.getNativePointer()));
	}

	/**
	 * Returns the name of the trigger (as declared in the <code>CREATE TRIGGER</code>
	 * statement).
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public String getName()
	throws SQLException
	{
		return doInPG(() -> _getName(this.getNativePointer()));
	}

	/**
	 * Returns <code>true</code> if the trigger was fired after the statement
	 * or row action that it is associated with.
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public boolean isFiredAfter()
	throws SQLException
	{
		return doInPG(() -> _isFiredAfter(this.getNativePointer()));
	}

	/**
	 * Returns <code>true</code> if the trigger was fired before the
	 * statement or row action that it is associated with.
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public boolean isFiredBefore()
	throws SQLException
	{
		return doInPG(() -> _isFiredBefore(this.getNativePointer()));
	}

	/**
	 * Returns <code>true</code> if this trigger is fired once for each row
	 * (as opposed to once for the entire statement).
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public boolean isFiredForEachRow()
	throws SQLException
	{
		return doInPG(() -> _isFiredForEachRow(this.getNativePointer()));
	}

	/**
	 * Returns <code>true</code> if this trigger is fired once for the entire
	 * statement (as opposed to once for each row).
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public boolean isFiredForStatement()
	throws SQLException
	{
		return doInPG(() -> _isFiredForStatement(this.getNativePointer()));
	}

	/**
	 * Returns <code>true</code> if this trigger was fired by a <code>DELETE</code>.
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public boolean isFiredByDelete()
	throws SQLException
	{
		return doInPG(() -> _isFiredByDelete(this.getNativePointer()));
	}

	/**
	 * Returns <code>true</code> if this trigger was fired by an <code>INSERT</code>.
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public boolean isFiredByInsert()
	throws SQLException
	{
		return doInPG(() -> _isFiredByInsert(this.getNativePointer()));
	}

	/**
	 * Returns <code>true</code> if this trigger was fired by an <code>UPDATE</code>.
	 * 
	 * @throws SQLException
	 *             if the contained native buffer has gone stale.
	 */
	public boolean isFiredByUpdate()
	throws SQLException
	{
		return doInPG(() -> _isFiredByUpdate(this.getNativePointer()));
	}

	private static native Relation _getRelation(long pointer) throws SQLException;
	private static native Tuple _getTriggerTuple(long pointer) throws SQLException;
	private static native Tuple _getNewTuple(long pointer) throws SQLException;
	private static native String[] _getArguments(long pointer) throws SQLException;
	private static native String _getName(long pointer) throws SQLException;
	private static native boolean _isFiredAfter(long pointer) throws SQLException;
	private static native boolean _isFiredBefore(long pointer) throws SQLException;
	private static native boolean _isFiredForEachRow(long pointer) throws SQLException;
	private static native boolean _isFiredForStatement(long pointer) throws SQLException;
	private static native boolean _isFiredByDelete(long pointer) throws SQLException;
	private static native boolean _isFiredByInsert(long pointer) throws SQLException;
	private static native boolean _isFiredByUpdate(long pointer) throws SQLException;
}
