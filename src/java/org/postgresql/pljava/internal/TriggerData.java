/*
 * This file contains software that has been made available under The Mozilla
 * Public License 1.1. Use and distribution hereof are subject to the
 * restrictions set forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden All Rights Reserved
 */
package org.postgresql.pljava.internal;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.jdbc.TriggerResultSet;

/**
 * The <code>TriggerData</code> correspons to the internal PostgreSQL <code>TriggerData</code>.
 * 
 * @author Thomas Hallgren
 */
public class TriggerData extends NativeStruct implements org.postgresql.pljava.TriggerData
{
	private Relation m_relation;
	private TriggerResultSet m_old = null;
	private TriggerResultSet m_new = null;

	/**
	 * Returns the ResultSet that represents the new row. This ResultSet will
	 * be null for delete triggers and for triggers that was fired for
	 * statement. <br/>The returned set will be updateable and positioned on a
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
		m_new = new TriggerResultSet(this.relation().getTupleDesc(), tuple, false);
		return m_new;
	}

	/**
	 * Returns the ResultSet that represents the old row. This ResultSet will
	 * be null for insert triggers and for triggers that was fired for
	 * statement. <br/>The returned set will be read-only and positioned on a
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
		m_old = new TriggerResultSet(this.relation().getTupleDesc(), this.getTriggerTuple(), true);
		return m_old;
	}

	/**
	 * Commits the changes made on the <code>ResultSet</code> representing
	 * <code>new</code> and returns the new tuple. This method is called
	 * automatically by the trigger handler and should not be called in any
	 * other way.
	 * 
	 * @return The modified tuple, or if no modifications have been made, the
	 *         original tuple.
	 */
	public Tuple getTriggerReturnTuple() throws SQLException
	{
		if(this.isFiredForStatement() || this.isFiredAfter())
			//
			// Only triggers fired before each row can have a return
			// value.
			//
			return null;

		if (m_new != null)
		{
			Object[] changes = m_new.getChangeIndexesAndValues();
			if (changes != null)
			{
				Tuple original = (Tuple)changes[0];
				int[] indexes = (int[])changes[1];
				Object[] values = (Object[])changes[2];
				return this.relation().modifyTuple(original, indexes, values);
			}
		}

		// Return the original tuple.
		//
		return this.isFiredByUpdate()
			? this.getNewTuple()
			: this.getTriggerTuple();
	}

	public String getTableName()
	throws SQLException
	{
		return this.relation().getName();
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
		synchronized(Backend.THREADLOCK)
		{
			return this._getRelation();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._getTriggerTuple();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._getNewTuple();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._getArguments();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._getName();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._isFiredAfter();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._isFiredBefore();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._isFiredForEachRow();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._isFiredForStatement();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._isFiredByDelete();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._isFiredByInsert();
		}
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
		synchronized(Backend.THREADLOCK)
		{
			return this._isFiredByUpdate();
		}
	}

	private final Relation relation()
	throws SQLException
	{
		if(m_relation == null)
			m_relation = this.getRelation();
		return m_relation;
	}

	private native Relation _getRelation() throws SQLException;
	private native Tuple _getTriggerTuple() throws SQLException;
	private native Tuple _getNewTuple() throws SQLException;
	private native String[] _getArguments() throws SQLException;
	private native String _getName() throws SQLException;
	private native boolean _isFiredAfter() throws SQLException;
	private native boolean _isFiredBefore() throws SQLException;
	private native boolean _isFiredForEachRow() throws SQLException;
	private native boolean _isFiredForStatement() throws SQLException;
	private native boolean _isFiredByDelete() throws SQLException;
	private native boolean _isFiredByInsert() throws SQLException;
	private native boolean _isFiredByUpdate() throws SQLException;
}
