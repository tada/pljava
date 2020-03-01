/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 * Copyright (c) 2010, 2011 PostgreSQL Global Development Group
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
package org.postgresql.pljava.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;

import org.postgresql.pljava.internal.Tuple;
import org.postgresql.pljava.internal.TupleDesc;

/**
 * A single row, updateable ResultSet specially made for triggers. The
 * changes made to this ResultSet are remembered and converted to a
 * SPI_modify_tuple call prior to function return.
 *
 * @author Thomas Hallgren
 */
public class TriggerResultSet extends SingleRowResultSet
{
	private ArrayList<Object> m_tupleChanges;
	private final TupleDesc m_tupleDesc;
	private final Tuple     m_tuple;
	private final boolean   m_readOnly;

	public TriggerResultSet(TupleDesc tupleDesc, Tuple tuple, boolean readOnly)
	throws SQLException
	{
		m_tupleDesc = tupleDesc;
		m_tuple = tuple;
		m_readOnly = readOnly;
	}

	/**
	 * Cancel all changes made to the Tuple.
	 */
	@Override
	public void cancelRowUpdates()
	throws SQLException
	{
		m_tupleChanges = null;
	}

	/**
	 * Cancels all changes but doesn't really close the set.
	 */
	@Override
	public void close()
	throws SQLException
	{
		m_tupleChanges = null;
	}

	/**
	 * Returns the concurrency for this ResultSet.
	 * @see java.sql.ResultSet#getConcurrency
	 */
	@Override
	public int getConcurrency() throws SQLException
	{
		return m_readOnly ? CONCUR_READ_ONLY : CONCUR_UPDATABLE;
	}

	/**
	 * Returns <code>true</code> if this row has been updated.
	 */
	@Override
	public boolean rowUpdated()
	throws SQLException
	{
		return m_tupleChanges != null;
	}

	/**
	 * Store this change for later use
	 */
	@Override
	public void updateObject(int columnIndex, Object x)
	throws SQLException
	{
		if(m_readOnly)
			throw new UnsupportedFeatureException("ResultSet is read-only");

		if(m_tupleChanges == null)
			m_tupleChanges = new ArrayList<>();

		m_tupleChanges.add(columnIndex);
		m_tupleChanges.add(x);
	}

	
	/**
	 * Return a 3 element array describing the changes that have been made to
	 * the contained Tuple. The first element the original Tuple, the second
	 * an {@code int[]} containing
	 * the index of each changed value, and the third an {@code Object[]}
	 * containing the corresponding values.
	 * 
	 * @return The 3 element array or <code>null</code> if no change has
	 * been made.
	 */
	public Object[] getChangeIndexesAndValues()
	{
		ArrayList<Object> changes = m_tupleChanges;
		if(changes == null)
			return null;

		int top = changes.size();
		if(changes.size() == 0)
			return null;

		top /= 2;
		int[] indexes = new int[top];
		Object[] values = new Object[top];
		int vIdx = 0;
		for(int idx = 0; idx < top; ++idx)
		{	
			indexes[idx] = ((Integer)changes.get(vIdx++)).intValue();
			Object v = changes.get(vIdx++);
			TypeBridge<?>.Holder vAlt = TypeBridge.wrap(v);
			values[idx] = null == vAlt ? v : vAlt;
		}
		return new Object[] { m_tuple, indexes, values };
	}

	/**
	 * If the value has not been changed, forwards to
	 * {@link Tuple#getObject(TupleDesc,int,Class) Tuple.getObject}, with the
	 * usual behavior for type coercion; if it has been changed, returns the
	 * exact object that was supplied with the change.
	 *<p>
	 * When the caller is the JDBC 4.1 {@link #getObject(int,Class)}, the caller
	 * will check and complain if the returned object is not of the right class.
	 */
	@Override // defined in ObjectResultSet
	protected Object getObjectValue(int columnIndex, Class<?> type)
	throws SQLException
	{
		// Check if this value has been changed.
		//
		ArrayList changes = m_tupleChanges;
		if(changes != null)
		{	
			int top = changes.size();
			for(int idx = 0; idx < top; idx += 2)
				if(columnIndex == ((Integer)changes.get(idx)).intValue())
					return changes.get(idx + 1);
		}
		return m_tuple.getObject(this.getTupleDesc(), columnIndex, type);
	}

	@Override // defined in SingleRowResultSet
	protected final TupleDesc getTupleDesc()
	{
		return m_tupleDesc;
	}


	// ************************************************************
	// Implementation of JDBC 4 methods.
	// ************************************************************

	
	@Override
	public boolean isClosed()
		throws SQLException
	{
		return m_tupleChanges == null;
	}

	// ************************************************************
	// End of implementation of JDBC 4 methods.
	// ************************************************************
}
