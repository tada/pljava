/*
 * This file contains software that has been made available under The Mozilla
 * Public License 1.1. Use and distribution hereof are subject to the
 * restrictions set forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden All Rights Reserved
 */
package org.postgresql.pljava.jdbc;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Map;


/**
 * @author Thomas Hallgren
 */
public class SPIConnection implements Connection
{
	private boolean m_valid;

	SPIConnection()
	{
		m_valid = true;
	}

	/**
	 * Returns {@link ResultSet#CLOSE_CURSORS_AT_COMMIT}. Cursors are actually
	 * closed when a function returns to SQL.
	 */
	public int getHoldability() throws SQLException
	{
		return ResultSet.CLOSE_CURSORS_AT_COMMIT;
	}

	/**
	 * Returns {@link Connection#TRANSACTION_READ_COMMITTED}.
	 */
	public int getTransactionIsolation() throws SQLException
	{
		return TRANSACTION_READ_COMMITTED;
	}

	/**
	 * Warnings are not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void clearWarnings()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.clearWarnings");
	}

	/**
	 * Close the connection and free up any resources attached to it.
	 */
	public void close()
	throws SQLException
	{
		m_valid = false;
	}

	/**
	 * It's not legal to do a commit within a call from SQL.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void commit()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.commit");
	}

	/**
	 * It's not legal to do a rollback within a call from SQL.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void rollback()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.rollback");
	}

	/**
	 * It is assumed that an SPI call is under transaction control. This method
	 * will always return <code>false</code>.
	 */
	public boolean getAutoCommit()
	throws SQLException
	{
		return false;
	}

	/**
	 * Returns true if this conneciton has been closed.
	 */
	public boolean isClosed()
	throws SQLException
	{
		return !m_valid;
	}

	/**
	 * Returns <code>false</code>. The SPIConnection is not real-only.
	 */
	public boolean isReadOnly()
	throws SQLException
	{
		return false;
	}

	/**
	 * Change of holdability is not supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void setHoldability(int holdability)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.setHoldability");
	}

	/**
	 * Change of transaction isolation level is not supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void setTransactionIsolation(int level)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.setTransactionIsolation");
	}

	/**
	 * It is assumed that an SPI call is under transaction control. Changing
	 * that is not supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void setAutoCommit(boolean autoCommit)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.setAutoCommit");
	}

	/**
	 * It is assumed that an inserts and updates can be performed using and
	 * SPIConnection. Changing that is not supported. 
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void setReadOnly(boolean readOnly)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.setReadOnly");
	}

	/**
	 * Returns <code>null</code>.
	 */
	public String getCatalog()
	throws SQLException
	{
		return null;
	}

	/**
	 * The catalog name cannot be set.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void setCatalog(String catalog)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.setCatalog");
	}

	/**
	 * DatabaseMetaData is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public DatabaseMetaData getMetaData()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.getMetaData");
	}

	/**
	 * Warnings are not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public SQLWarning getWarnings()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.getWarnings");
	}

	/**
	 * Savepoints are not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void releaseSavepoint(Savepoint savepoint) throws SQLException
	{
		throw new UnsupportedFeatureException("Savepoints");
	}

	/**
	 * Savepoints are not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void rollback(Savepoint savepoint) throws SQLException
	{
		throw new UnsupportedFeatureException("Savepoints");
	}

	/**
	 * Creates a new instance of <code>SPIStatement</code>.
	 */
	public Statement createStatement()
	throws SQLException
	{
		if(this.isClosed())
			throw new SQLException("Connection is closed");
		return new SPIStatement(this);
	}

	/**
	 * Creates a new instance of <code>SPIStatement</code>.
	 * 
	 * @throws SQLException
	 *             if the <code>resultSetType</code> differs from {@link
	 *             ResultSet#TYPE_FORWARD_ONLY} or if the <code>resultSetConcurrencty</code>
	 *             differs from {@link ResultSet#CONCUR_READ_ONLY}.
	 */
	public Statement createStatement(
		int resultSetType,
		int resultSetConcurrency)
		throws SQLException
	{
		if(resultSetType != ResultSet.TYPE_FORWARD_ONLY)
			throw new UnsupportedOperationException("TYPE_FORWARD_ONLY supported ResultSet type");

		if(resultSetConcurrency != ResultSet.CONCUR_READ_ONLY)
			throw new UnsupportedOperationException("CONCUR_READ_ONLY is the supported ResultSet concurrency");
		return this.createStatement();
	}

	/**
	 * Creates a new instance of <code>SPIStatement</code>.
	 * 
	 * @throws SQLException
	 *             if the <code>resultSetType</code> differs from {@link
	 *             ResultSet#TYPE_FORWARD_ONLY}, if the <code>resultSetConcurrencty</code>
	 *             differs from {@link ResultSet#CONCUR_READ_ONLY}, or if the
	 *             resultSetHoldability differs from {@link ResultSet#CLOSE_CURSORS_AT_COMMIT}.
	 */
	public Statement createStatement(
		int resultSetType,
		int resultSetConcurrency,
		int resultSetHoldability)
		throws SQLException
	{
		if(resultSetHoldability != ResultSet.CLOSE_CURSORS_AT_COMMIT)
			throw new UnsupportedOperationException(
					"CLOSE_CURSORS_AT_COMMIT is the only supported ResultSet holdability");
		return this.createStatement(resultSetType, resultSetConcurrency);
	}

	/**
	 * Returns <code>null</code>. Type map is not yet imlemented.
	 */
	public Map getTypeMap()
	throws SQLException
	{
		return null;
	}

	/**
	 * Type map is not yet implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void setTypeMap(Map map)
	throws SQLException
	{
		throw new UnsupportedOperationException("Type map is not yet implemented");
	}

	/**
	 * Parse the JDBC SQL into PostgreSQL.
	 */
	public String nativeSQL(String sql)
	throws SQLException
	{
		StringBuffer buf = new StringBuffer();
		int len = sql.length();
		char inQuote = 0;
		int paramIndex = 1;
		for(int idx = 0; idx < len; ++len)
		{
			char c = sql.charAt(idx);
			switch(c)
			{
			case '\\':
				// Next character is escaped. Keep both
				// escape and the character.
				//
				buf.append(c);
				if(++idx == len)
					break;
				c = sql.charAt(idx);
				break;

			case '\'':
			case '"':
				// Strings within quotes should not be subject
				// to '?' -> '$n' substitution.
				//
				if(inQuote == c)
					inQuote = 0;
				else
					inQuote = c;
				break;
			
			case '?':
				if(inQuote == 0)
				{
					buf.append('$');
					c = (char)('0' + paramIndex++);
				}
				break;
			
			default:
				if(inQuote == 0 && Character.isWhitespace(c))
				{
					// Strip of multiple whitespace outside of
					// strings.
					//
					++idx;
					while(idx < len && Character.isWhitespace(sql.charAt(idx)))
						++idx;
					--idx;
					c = ' ';
				}
			}
			buf.append(c);
		}
		return buf.toString();
	}

	/**
	 * Procedure calls are not yet implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public CallableStatement prepareCall(String sql) throws SQLException
	{
		throw new UnsupportedOperationException("Procedure calls are not yet implemented");
	}

	/**
	 * Procedure calls are not yet implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public CallableStatement prepareCall(
		String sql,
		int resultSetType,
		int resultSetConcurrency)
		throws SQLException
	{
		throw new UnsupportedOperationException("Procedure calls are not yet implemented");
	}

	/**
	 * Procedure calls are not yet implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public CallableStatement prepareCall(
		String sql,
		int resultSetType,
		int resultSetConcurrency,
		int resultSetHoldability)
		throws SQLException
	{
		throw new UnsupportedOperationException("Procedure calls are not yet implemented");
	}

	/**
	 * Creates a new instance of <code>SPIPreparedStatement</code>.
	 */
	public PreparedStatement prepareStatement(String sql)
	throws SQLException
	{
		if(this.isClosed())
			throw new SQLException("Connection is closed");
		return new SPIPreparedStatement(this, this.nativeSQL(sql));
	}

	/**
	 * Return of auto generated keys is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Auto generated key support not yet implemented");
	}

	/**
	 * Creates a new instance of <code>SPIPreparedStatement</code>.
	 * 
	 * @throws SQLException
	 *             if the <code>resultSetType</code> differs from {@link
	 *             ResultSet#TYPE_FORWARD_ONLY} or if the <code>resultSetConcurrencty</code>
	 *             differs from {@link ResultSet#CONCUR_READ_ONLY}.
	 */
	public PreparedStatement prepareStatement(
		String sql,
		int resultSetType,
		int resultSetConcurrency)
		throws SQLException
	{
		if(resultSetType != ResultSet.TYPE_FORWARD_ONLY)
			throw new UnsupportedOperationException("TYPE_FORWARD_ONLY supported ResultSet type");

		if(resultSetConcurrency != ResultSet.CONCUR_READ_ONLY)
			throw new UnsupportedOperationException("CONCUR_READ_ONLY is the supported ResultSet concurrency");
		return prepareStatement(sql);
	}

	/**
	 * Creates a new instance of <code>SPIPreparedStatement</code>.
	 * 
	 * @throws SQLException
	 *             if the <code>resultSetType</code> differs from {@link
	 *             ResultSet#TYPE_FORWARD_ONLY}, if the <code>resultSetConcurrencty</code>
	 *             differs from {@link ResultSet#CONCUR_READ_ONLY}, or if the
	 *             resultSetHoldability differs from {@link ResultSet#CLOSE_CURSORS_AT_COMMIT}.
	 */
	public PreparedStatement prepareStatement(
		String sql,
		int resultSetType,
		int resultSetConcurrency,
		int resultSetHoldability)
		throws SQLException
	{
		if(resultSetHoldability != ResultSet.CLOSE_CURSORS_AT_COMMIT)
			throw new UnsupportedOperationException(
			"CLOSE_CURSORS_AT_COMMIT is the only supported ResultSet holdability");
		return this.prepareStatement(sql, resultSetType, resultSetConcurrency);
	}

	/**
	 * Return of auto generated keys is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Auto generated key support not yet implemented");
	}

	/**
	 * Return of auto generated keys is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public PreparedStatement prepareStatement(String sql, String[] columnNames)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Auto generated key support not yet implemented");
	}

	/**
	 * Savepoints are not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public Savepoint setSavepoint()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Savepoints");
	}

	/**
	 * Savepoints are not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public Savepoint setSavepoint(String name) throws SQLException
	{
		throw new UnsupportedFeatureException("Savepoints");
	}
}
