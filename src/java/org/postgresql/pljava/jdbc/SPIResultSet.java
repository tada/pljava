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

import org.postgresql.pljava.Server;
import org.postgresql.pljava.internal.Portal;
import org.postgresql.pljava.internal.SPI;
import org.postgresql.pljava.internal.SPITupleTable;
import org.postgresql.pljava.internal.Tuple;
import org.postgresql.pljava.internal.TupleDesc;

/**
 * A Read-only ResultSet that provides direct access to a {@link
 * org.postgresql.pljava.internal.Portal Portal}. At present, only
 * forward positioning is implemented. Attempts to use reverse or
 * absolute positioning will fail.
 *
 * @author Thomas Hallgren
 */
public class SPIResultSet extends ReadOnlyResultSet
{
	private final Statement m_statement;
	private final Portal    m_portal;
	private final TupleDesc m_tupleDesc;
	private final int       m_maxRows;

	private int m_fetchSize;
	private int m_row;

	private Tuple m_currentRow;
	private Tuple m_nextRow;

	private SPITupleTable m_table;
	private int m_tableRow;

	SPIResultSet(Statement statement, Portal portal, int maxRows)
	throws SQLException
	{
		m_statement = statement;
		m_portal = portal;
		m_maxRows = maxRows;
		m_tupleDesc = portal.getTupleDesc();

		m_fetchSize = statement.getFetchSize();
		m_row = 0;	// First row is 1 so 0 is on undefined position.
		m_tableRow = -1;
	}

	public int getFetchDirection()
	throws SQLException
	{
		return FETCH_FORWARD;
	}

	public int getFetchSize()
	throws SQLException
	{
		return m_fetchSize;
	}

	public int getRow()
	throws SQLException
	{
		return m_row;
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

	public void close()
	throws SQLException
	{
		if(m_portal.isValid())
		{
			m_portal.close();
			m_table      = null;
			m_row        = -1;
			m_tableRow   = -1;
			m_currentRow = null;
			m_nextRow    = null;
		}
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean first() throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	public boolean isAfterLast() throws SQLException
	{
		return m_row < 0;
	}

	public boolean isBeforeFirst() throws SQLException
	{
		return m_row == 0;
	}

	public boolean isFirst() throws SQLException
	{
		return m_row == 1;
	}

	public boolean isLast() throws SQLException
	{
		return m_currentRow != null && this.peekNext() == null;
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

	public boolean next()
	throws SQLException
	{
		m_currentRow = this.peekNext();
		m_nextRow = null;
		boolean result = (m_currentRow != null);
		if(result)
			m_row++;
		else
			m_row = -1;	// After last
		return result;
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

	public void setFetchSize(int fetchSize)
	throws SQLException
	{
		if(fetchSize <= 0)
			throw new IllegalArgumentException("Illegal fetch size for ResultSet");
		m_fetchSize = fetchSize;
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean absolute(int row)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	/**
	 * Cursor positioning is not implemented yet.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean relative(int rows)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Cursor positioning");
	}

	public String getCursorName()
	throws SQLException
	{
		return this.getPortal().getName();
	}

	public int findColumn(String columnName)
	throws SQLException
	{
		return m_tupleDesc.getColumnIndex(columnName);
	}

	public Statement getStatement()
	throws SQLException
	{
		return m_statement;
	}

	protected final Portal getPortal()
	throws SQLException
	{
		if(!m_portal.isValid())
			throw new SQLException("ResultSet is closed");
		return m_portal;
	}

	protected final SPITupleTable getTupleTable()
	throws SQLException
	{
		if(m_table == null)
		{
			Portal portal = this.getPortal();
			if(portal.isAtEnd())
				return null;

			int mx;
			if(m_maxRows > 0)
			{
				mx = m_maxRows - portal.getPortalPos();
				if(mx <= 0)
					return null;
				if(mx > m_fetchSize)
					mx = m_fetchSize;
			}
			else
				mx = m_fetchSize;

			int result = portal.fetch(true, mx);
			Server.log("SPIResultSet fetch table with " + result + " rows");
			if(result > 0)
				m_table = SPI.getTupTable();
			m_tableRow = -1;
		}
		return m_table;
	}

	protected final Tuple getCurrentRow()
	throws SQLException
	{
		if(m_currentRow == null)
			throw new SQLException("ResultSet is not positioned on a valid row");
		return m_currentRow;
	}

	protected final Tuple peekNext()
	throws SQLException
	{
		if(m_nextRow != null)
			return m_nextRow;

		SPITupleTable table = this.getTupleTable();
		if(table == null)
			return null;

		Server.log("ResulSet table count = " + table.getCount());
		Server.log("tableRow = " + m_tableRow);
		if(m_tableRow >= table.getCount() - 1)
		{
			// Current table is exhaused, get the next
			// one.
			//
			m_table.invalidate();
			m_table = null;
			table = this.getTupleTable();
			if(table == null)
				return null;
		}
		m_nextRow = table.getSlot(++m_tableRow);
		return m_nextRow;
	}

	protected Object getObjectValue(int columnIndex)
	throws SQLException
	{
		return this.getCurrentRow().getObject(m_tupleDesc, columnIndex);
	}
}
