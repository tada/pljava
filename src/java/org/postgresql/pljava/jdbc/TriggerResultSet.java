/*
 * This file contains software that has been made available under The Mozilla
 * Public License 1.1. Use and distribution hereof are subject to the
 * restrictions set forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden All Rights Reserved
 */
package org.postgresql.pljava.jdbc;

import java.sql.SQLException;
import java.sql.Statement;
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
public class TriggerResultSet extends ObjectResultSet
{
	private final TupleDesc   m_tupleDesc;
	private final Tuple       m_tuple;
	private final boolean     m_readOnly;

	private ArrayList m_tupleChanges;
	private boolean   m_afterLast;

	public TriggerResultSet(TupleDesc tupleDesc, Tuple tuple, boolean readOnly)
	throws SQLException
	{
		m_tupleDesc = tupleDesc;
		m_tuple = tuple;
		m_readOnly = readOnly;
	}

	/**
	 * Returns the concurrency for this ResultSet.
	 * @see ResultSet#getConcurrency.
	 */
	public int getConcurrency() throws SQLException
	{
		return m_readOnly ? CONCUR_READ_ONLY : CONCUR_UPDATABLE;
	}

	public int getFetchDirection()
	throws SQLException
	{
		return FETCH_FORWARD;
	}

	public int getFetchSize()
	throws SQLException
	{
		return 1;
	}

	public int getRow()
	throws SQLException
	{
		return 1;
	}

	public int getType()
	throws SQLException
	{
		return TYPE_FORWARD_ONLY;
	}

	/**
	 * Cursor positoning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void afterLast()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Cursor positoning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void beforeFirst()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Cancel all changes made to the Tuple.
	 */
	public void cancelRowUpdates()
	throws SQLException
	{
		m_tupleChanges = null;
	}
	
	/**
	 * Cancel all changes made to the Tuple and closes this ResultSet.
	 */
	public void close()
	throws SQLException
	{
		m_tupleChanges = null;
		m_afterLast = true;
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean first() throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Returns <code>false</code> unless a call to <code>next</code> or
	 * <code>close</code> has been made.
	 */
	public boolean isAfterLast() throws SQLException
	{
		return m_afterLast;
	}

	/**
	 * Will always return <code>false</code> since a <code>TriggerResultSet
	 * </code> starts on the one and only row.
	 */
	public boolean isBeforeFirst() throws SQLException
	{
		return false;
	}

	/**
	 * Returns <code>true</code> unless a call to <code>next</code> or
	 * <code>close</code> has been made.
	 */
	public boolean isFirst() throws SQLException
	{
		return !m_afterLast;
	}

	/**
	 * Returns <code>true</code> unless a call to <code>next</code> or
	 * <code>close</code> has been made.
	 */
	public boolean isLast() throws SQLException
	{
		return !m_afterLast;
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean last()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Returns <code>false</code> and causes the ResultSet to be positioned
	 * &quot;after last&quot;
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean next()
	throws SQLException
	{
		m_afterLast = true;
		return false;
	}

	/**
	 * Reverse positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean previous()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Reverse positioning");
	}

	/**
	 * Only {@link ResultSet#FETCH_FORWARD is supported.
	 * @throws SQLException indicating that this feature is not supported
	 * for other values on <code>direction</code>.
	 */
	public void setFetchDirection(int direction)
	throws SQLException
	{
		if(direction != FETCH_FORWARD)
			throw new UnsupportedFeatureException("Non forward fetch direction");
	}

	/**
	 * Only permitted value for <code>fetchSize</code> is 1.
	 */
	public void setFetchSize(int fetchSize)
	throws SQLException
	{
		if(fetchSize != 1)
			throw new IllegalArgumentException("Illegal fetch size for TriggerResultSet");
	}

	/**
	 * Cursor positioning is not supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean absolute(int row)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Cursor positioning is not supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean relative(int rows)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Returns <code>null</code>.
	 */
	public String getCursorName()
	throws SQLException
	{
		return null;
	}

	public int findColumn(String columnName)
	throws SQLException
	{
		return m_tupleDesc.getColumnIndex(columnName);
	}

	/**
	 * The TriggerResultSet has no associated statement.
	 * @return <code>null</code>
	 */
	public Statement getStatement()
	throws SQLException
	{
		return null;
	}

	/**
	 * This feature is not supported on a <code>TriggerResultSet</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void deleteRow()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Deletes not supported on TriggerResultSet");
	}

	/**
	 * This feature is not supported on a <code>TriggerResultSet</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void insertRow()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Inserts not supported on TriggerResultSet");
	}

	/**
	 * This is a no-op since the <code>moveToInsertRow()</code> method is
	 * unsupported.
	 */
	public void moveToCurrentRow()
	throws SQLException
	{
	}

	/**
	 * This feature is not supported on a <code>TriggerResultSet</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void moveToInsertRow()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Inserts not supported on TriggerResultSet");
	}

	/**
	 * This is a noop. The actual update will be performed by the trigger
	 * manager upon return of the function call.
	 */
	public void updateRow()
	throws SQLException
	{
	}

	/**
	 * Will always return false.
	 */
	public boolean rowDeleted()
	throws SQLException
	{
		return false;
	}

	/**
	 * Will always return false.
	 */
	public boolean rowInserted()
	throws SQLException
	{
		return false;
	}

	/**
	 * Returns <code>true</code> if this row has been updated.
	 */
	public boolean rowUpdated()
	throws SQLException
	{
		return m_tupleChanges != null;
	}

	/**
	 * Store this change for later use
	 */
	public void updateObject(int columnIndex, Object x) throws SQLException
	{
		if(m_readOnly)
			throw new UnsupportedFeatureException("ResultSet is read-only");

		if(m_afterLast)
			throw new SQLException("ResultSet is not positioned on a valid row");

		if(m_tupleChanges == null)
			m_tupleChanges = new ArrayList();
		m_tupleChanges.add(new Integer(columnIndex));
		m_tupleChanges.add(x);
	}

	/**
	 * The scale is not really supported. This method just strips it off and
	 * calls {@link #updateObject(int, Object)}
	 */
	public void updateObject(int columnIndex, Object x, int scale)
	throws SQLException
	{
		// Simply drop the scale.
		//
		this.updateObject(columnIndex, x);
	}
	
	/**
	 * Return a 2 element array describing the changes that has been made to
	 * the contained Tuple. The first element is an <code>int[]</code> containing
	 * the index of each changed value. The second element is an <code>Object[]
	 * </code> with containing the corresponding values.
	 * 
	 * @return The 2 element array or <code>null</code> if no change has been made.
	 */
	public Object[] getChangeIndexesAndValues()
	{
		if(m_tupleChanges == null)
			return null;

		int top = m_tupleChanges.size();
		int[] indexes = new int[top / 2];
		Object[] values = new Object[top / 2];
		for(int idx = 0; idx < top;)
		{	
			indexes[idx] = ((Integer)m_tupleChanges.get(idx)).intValue();
			++idx;
			values[idx] = m_tupleChanges.get(idx);
			++idx;
		}
		return new Object[] { m_tuple, indexes, values };
	}

	protected Object getObjectValue(int columnIndex)
	throws SQLException
	{
		if(m_afterLast)
			throw new SQLException("ResultSet is not positioned on a valid row");

		if(m_tupleChanges != null)
		{
			// Check if this value has been changed.
			//
			int top = m_tupleChanges.size();
			for(int idx = 0; idx < top; idx += 2)
				if(columnIndex == ((Integer)m_tupleChanges.get(idx)).intValue())
					return m_tupleChanges.get(idx + 1);
		}
		return m_tuple.getObject(m_tupleDesc, columnIndex);
	}
}
