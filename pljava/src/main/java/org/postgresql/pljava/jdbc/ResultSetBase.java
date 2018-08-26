/*
 * Copyright (c) 2004-2018 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Filip Hrbek
 *   PostgreSQL Global Development Group
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Provides methods that are common both for
 * a SyntheticResultSet (which is not associated with a
 * statement) and SPIResultSet.
 *
 * @author Filip Hrbek
 */
public abstract class ResultSetBase extends ReadOnlyResultSet
{
	private int m_fetchSize;
	private int m_row;

	/**
	 * Records a fetch size, and an initial position before the first row.
	 */
	ResultSetBase(int fetchSize)
	{
		m_fetchSize = fetchSize;
		m_row = 0;	// First row is 1 so 0 is on undefined position.
	}

	/**
	 * Always returns {@link #FETCH_FORWARD} if not overridden.
	 */
	@Override
	public int getFetchDirection()
	throws SQLException
	{
		return FETCH_FORWARD;
	}

	/**
	 * Returns the fetch size set by the constructor or with
	 * {@link #setFetchSize}.
	 */
	@Override
	public final int getFetchSize()
	throws SQLException
	{
		return m_fetchSize;
	}

	/**
	 * Returns the row set by the constructor or with
	 * {@link #setRow}.
	 */
	@Override
	public final int getRow()
	throws SQLException
	{
		return m_row;
	}

	/**
	 * Always returns {@link #TYPE_FORWARD_ONLY} if not overridden.
	 */
	@Override
	public int getType()
	throws SQLException
	{
		return TYPE_FORWARD_ONLY;
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public void afterLast()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public void beforeFirst()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	@Override
	public void close()
	throws SQLException
	{
		m_row = -1;
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public boolean first() throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	@Override
	public boolean isAfterLast() throws SQLException
	{
		return m_row < 0;
	}

	@Override
	public boolean isBeforeFirst() throws SQLException
	{
		return m_row == 0;
	}

	@Override
	public boolean isFirst() throws SQLException
	{
		return m_row == 1;
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public boolean last()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Reverse positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public boolean previous()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Reverse positioning");
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public boolean absolute(int row)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public boolean relative(int rows)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Only {@link java.sql.ResultSet#FETCH_FORWARD} is supported.
	 * @throws SQLException indicating that this feature is not supported
	 * for other values on <code>direction</code>.
	 */
	@Override
	public void setFetchDirection(int direction)
	throws SQLException
	{
		if(direction != FETCH_FORWARD)
			throw new UnsupportedFeatureException("Non forward fetch direction");
	}

	// ************************************************************
	// Implementation of JDBC 4 methods.
	// ************************************************************


	@Override
	public boolean isClosed()
		throws SQLException
	{
		return m_row == -1;
	}

	/**
	 * Returns {@link ResultSet#CLOSE_CURSORS_AT_COMMIT}.
	 */
	public int getHoldability()
		throws SQLException
	{
		return ResultSet.CLOSE_CURSORS_AT_COMMIT;
	}

	// ************************************************************
	// End of implementation of JDBC 4 methods.
	// ************************************************************

	/**
	 * Sets the fetch size maintained in this class.
	 */
	@Override
	public void setFetchSize(int fetchSize)
	throws SQLException
	{
		if(fetchSize <= 0)
			throw new IllegalArgumentException("Illegal fetch size for ResultSet");
		m_fetchSize = fetchSize;
	}

	/**
	 * Sets the row reported by this class; should probably have
	 * {@code protected} access.
	 */
	final void setRow(int row)
	{
		m_row = row;
	}
}
