/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.jdbc;

import java.sql.SQLException;
import java.sql.Statement;

import org.postgresql.pljava.internal.TupleDesc;

/**
 * A single row, updateable ResultSet
 *
 * @author Thomas Hallgren
 */
public abstract class SingleRowResultSet extends ObjectResultSet
{
	private final TupleDesc m_tupleDesc;

	public SingleRowResultSet(TupleDesc tupleDesc)
	throws SQLException
	{
		m_tupleDesc = tupleDesc;
	}

	public int findColumn(String columnName)
	throws SQLException
	{
		return m_tupleDesc.getColumnIndex(columnName);
	}

	public int getConcurrency()
	throws SQLException
	{
		return CONCUR_UPDATABLE;
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
	 * Cursor positoning is not implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void afterLast()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Cursor positoning is not implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void beforeFirst()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Cancels all changes but doesn't really close the set.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void close()
	throws SQLException
	{
		this.cancelRowUpdates();
	}

	/**
	 * Cursor positioning is not implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean first() throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Returns <code>false</code>.
	 */
	public boolean isAfterLast() throws SQLException
	{
		return false;
	}

	/**
	 * Will always return <code>false</code> since a <code>SingleRowWriter
	 * </code> starts on the one and only row.
	 */
	public boolean isBeforeFirst() throws SQLException
	{
		return false;
	}

	/**
	 * Returns <code>true</code>.
	 */
	public boolean isFirst() throws SQLException
	{
		return true;
	}

	/**
	 * Returns <code>true</code>.
	 */
	public boolean isLast() throws SQLException
	{
		return true;
	}

	/**
	 * Cursor positioning is not implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean last()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * This method will always return <code> false</code> but it will not change
	 * the state of the <code>ResultSet</code>.
	 */
	public boolean next()
	throws SQLException
	{
		return false;
	}

	/**
	 * This method will always return <code> false</code> but it will not change
	 * the state of the <code>ResultSet</code>.
	 */
	public boolean previous()
	throws SQLException
	{
		return false;
	}

	/**
	 * Only {@link java.sql.ResultSet#FETCH_FORWARD} is supported.
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
			throw new IllegalArgumentException("Illegal fetch size for single row set");
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
	 * This feature is not supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void deleteRow()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Deletes not supported on single row set");
	}

	/**
	 * This feature is not supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void insertRow()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Inserts not supported on single row set");
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
	 * This feature is not supported on a <code>SingleRowWriter</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void moveToInsertRow()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Inserts not supported on single row set");
	}

	/**
	 * This is a noop.
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

	protected final TupleDesc getTupleDesc()
	{
		return m_tupleDesc;
	}
}
