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
	public Savepoint setSavepoint()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.setSavepoint");
	}

	/**
	 * Savepoints are not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void releaseSavepoint(Savepoint savepoint) throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.releaseSavepoint");
	}

	/**
	 * Savepoints are not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void rollback(Savepoint savepoint) throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.rollback(Savepoint)");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createStatement()
	 */
	public Statement createStatement() throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createStatement(int, int)
	 */
	public Statement createStatement(
		int resultSetType,
		int resultSetConcurrency)
		throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#createStatement(int, int, int)
	 */
	public Statement createStatement(
		int resultSetType,
		int resultSetConcurrency,
		int resultSetHoldability)
		throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#getTypeMap()
	 */
	public Map getTypeMap() throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setTypeMap(java.util.Map)
	 */
	public void setTypeMap(Map map) throws SQLException
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#nativeSQL(java.lang.String)
	 */
	public String nativeSQL(String sql) throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#prepareCall(java.lang.String)
	 */
	public CallableStatement prepareCall(String sql) throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#prepareCall(java.lang.String, int, int)
	 */
	public CallableStatement prepareCall(
		String sql,
		int resultSetType,
		int resultSetConcurrency)
		throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#prepareCall(java.lang.String, int, int, int)
	 */
	public CallableStatement prepareCall(
		String sql,
		int resultSetType,
		int resultSetConcurrency,
		int resultSetHoldability)
		throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#prepareStatement(java.lang.String)
	 */
	public PreparedStatement prepareStatement(String sql) throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#prepareStatement(java.lang.String, int)
	 */
	public PreparedStatement prepareStatement(
		String sql,
		int autoGeneratedKeys)
		throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#prepareStatement(java.lang.String, int, int)
	 */
	public PreparedStatement prepareStatement(
		String sql,
		int resultSetType,
		int resultSetConcurrency)
		throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#prepareStatement(java.lang.String, int, int,
	 *      int)
	 */
	public PreparedStatement prepareStatement(
		String sql,
		int resultSetType,
		int resultSetConcurrency,
		int resultSetHoldability)
		throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#prepareStatement(java.lang.String, int[])
	 */
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
		throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#setSavepoint(java.lang.String)
	 */
	public Savepoint setSavepoint(String name) throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.sql.Connection#prepareStatement(java.lang.String,
	 *      java.lang.String[])
	 */
	public PreparedStatement prepareStatement(String sql, String[] columnNames)
		throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}
}
