/*
 * Copyright (c) 2004-2018 Tada AB and other contributors, as listed below.
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
import java.sql.Statement;
import java.sql.ResultSetMetaData;

import org.postgresql.pljava.internal.Portal;
import org.postgresql.pljava.internal.SPI;
import org.postgresql.pljava.internal.TupleTable;
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
public class SPIResultSet extends ResultSetBase
{
	private final SPIStatement m_statement;
	private final Portal    m_portal;
	private final TupleDesc m_tupleDesc;
	private final long      m_maxRows;

	private Tuple m_currentRow;
	private Tuple m_nextRow;

	private TupleTable m_table;
	private int m_tableRow;

	private boolean m_open;

	SPIResultSet(SPIStatement statement, Portal portal, long maxRows)
	throws SQLException
	{
		super(statement.getFetchSize());
		m_statement = statement;
		m_portal = portal;
		m_maxRows = maxRows;
		m_tupleDesc = portal.getTupleDesc();
		m_tableRow = -1;
		m_open = true;
	}

	@Override
	public void close()
	throws SQLException
	{
		if(m_open)
		{
			m_open = false;
			m_portal.close();
			m_statement.resultSetClosed(this);
			m_table      = null;
			m_tableRow   = -1;
			m_currentRow = null;
			m_nextRow    = null;
			super.close();
		}
	}

	@Override
	public boolean isLast() throws SQLException
	{
		return m_currentRow != null && this.peekNext() == null;
	}

	@Override
	public boolean next()
	throws SQLException
	{
		m_currentRow = this.peekNext();
		m_nextRow = null;
		boolean result = (m_currentRow != null);
		this.setRow(result ? this.getRow() + 1 : -1);
		return result;
	}

	/**
	 * This method does return the name of the portal, but beware of attempting
	 * positioned update/delete, because rows are read from the portal in
	 * {@link #getFetchSize} batches.
	 */
	@Override
	public String getCursorName()
	throws SQLException
	{
		return this.getPortal().getName();
	}

	@Override
	public int findColumn(String columnName)
	throws SQLException
	{
		return m_tupleDesc.getColumnIndex(columnName);
	}

	@Override
	public Statement getStatement()
	throws SQLException
	{
		return m_statement;
	}

	/**
	 * Return the {@code Portal} associated with this {@code ResultSet}.
	 */
	protected final Portal getPortal()
	throws SQLException
	{
		if(!m_open)
			throw new SQLException("ResultSet is closed");
		return m_portal;
	}

	/**
	 * Get a(nother) table of {@link #getFetchSize} rows from the
	 * {@link Portal}.
	 */
	protected final TupleTable getTupleTable()
	throws SQLException
	{
		if(m_table == null)
		{
			Portal portal = this.getPortal();
			if(portal.isAtEnd())
				return null;

			long mx;
			int fetchSize = this.getFetchSize();
			if(m_maxRows > 0)
			{
				mx = m_maxRows - portal.getPortalPos();
				if(mx <= 0)
					return null;
				if(mx > fetchSize)
					mx = fetchSize;
			}
			else
				mx = fetchSize;

			try
			{
				long result = portal.fetch(true, mx);
				if(result > 0)
					m_table = SPI.getTupTable(m_tupleDesc);
				m_tableRow = -1;
			}
			finally
			{
				SPI.freeTupTable();
			}
		}
		return m_table;
	}

	/**
	 * Return the {@link Tuple} most recently returned by {@link #next}.
	 */
	protected final Tuple getCurrentRow()
	throws SQLException
	{
		if(m_currentRow == null)
			throw new SQLException("ResultSet is not positioned on a valid row");
		return m_currentRow;
	}

	/**
	 * Get another {@link Tuple} from the {@link TupleTable}, refreshing the
	 * table as needed.
	 */
	protected final Tuple peekNext()
	throws SQLException
	{
		if(m_nextRow != null)
			return m_nextRow;

		TupleTable table = this.getTupleTable();
		if(table == null)
			return null;

		if(m_tableRow >= table.getCount() - 1)
		{
			// Current table is exhausted, get the next
			// one.
			//
			m_table = null;
			table = this.getTupleTable();
			if(table == null)
				return null;
		}
		m_nextRow = table.getSlot(++m_tableRow);
		return m_nextRow;
	}

	/**
	 * Implemented over
	 * {@link Tuple#getObject Tuple.getObject(TupleDesc,int,Class)}.
	 */
	@Override // defined in ObjectResultSet
	protected Object getObjectValue(int columnIndex, Class<?> type)
	throws SQLException
	{
		return this.getCurrentRow().getObject(m_tupleDesc, columnIndex, type);
	}

	/**
	 * Returns an {@link SPIResultSetMetaData} instance.
	 */
	@Override
	public ResultSetMetaData getMetaData()
	throws SQLException
	{
		return new SPIResultSetMetaData(m_tupleDesc);
	}
}
