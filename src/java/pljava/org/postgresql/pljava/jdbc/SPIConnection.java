/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.jdbc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;


/**
 * @author Thomas Hallgren
 */
public class SPIConnection implements Connection
{
	private static final HashMap s_sqlType2Class = new HashMap(30);

	static
	{
		addType(String.class, Types.VARCHAR);
		addType(Byte.class, Types.TINYINT);
		addType(Short.class, Types.SMALLINT);
		addType(Integer.class, Types.INTEGER);
		addType(Long.class, Types.BIGINT);
		addType(Float.class, Types.FLOAT);
		addType(Double.class, Types.DOUBLE);
		addType(BigDecimal.class, Types.DECIMAL);
		addType(BigInteger.class, Types.NUMERIC);
		addType(Boolean.class, Types.BOOLEAN);
		addType(Blob.class, Types.BLOB);
		addType(Clob.class, Types.CLOB);
		addType(Date.class, Types.DATE);
		addType(Time.class, Types.TIME);
		addType(Timestamp.class, Types.TIMESTAMP);
		addType(java.util.Date.class, Types.TIMESTAMP);
		addType(byte[].class, Types.VARBINARY);
		addType(BitSet.class, Types.BIT);
		addType(URL.class, Types.DATALINK);
	}

	private static final void addType(Class clazz, int sqlType)
	{
		s_sqlType2Class.put(clazz, new Integer(sqlType));
	}

	/**
	 * Returns a default connection instance. It is the callers responsability
	 * to close this instance.
	 */
	public static Connection getDefault()
	throws SQLException
	{
		return new SPIConnection();
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
	 * This is a no-op. The default connection never closes.
	 */
	public void close()
	throws SQLException
	{
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
	 * Will always return false.
	 */
	public boolean isClosed()
	throws SQLException
	{
		return false;
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

	public void releaseSavepoint(Savepoint savepoint) throws SQLException
	{
		if(!(savepoint instanceof SPISavepoint))
			throw new IllegalArgumentException("Not an SPISavepoint");

		SPISavepoint sp = (SPISavepoint)savepoint;
		sp.release();
		forgetSavepoint(sp);
	}

	public void rollback(Savepoint savepoint) throws SQLException
	{
		if(!(savepoint instanceof SPISavepoint))
			throw new IllegalArgumentException("Not an SPISavepoint");

		SPISavepoint sp = (SPISavepoint)savepoint;
		Invocation.clearErrorCondition();
		sp.rollback();
		forgetSavepoint(sp);
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
		return this.nativeSQL(sql, null);
	}
	
	public String nativeSQL(String sql, int[] paramCountRet)
	{
		StringBuffer buf = new StringBuffer();
		int len = sql.length();
		char inQuote = 0;
		int paramIndex = 1;
		for(int idx = 0; idx < len; ++idx)
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
					buf.append(paramIndex++);
					continue;
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
		if(paramCountRet != null)
			paramCountRet[0] = paramIndex - 1;
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

		int[] pcount = new int[] { 0 };
		sql = this.nativeSQL(sql, pcount);
		PreparedStatement stmt = new SPIPreparedStatement(this, sql, pcount[0]);
		Invocation.current().manageStatement(stmt);
		return stmt;
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

	public Savepoint setSavepoint()
	throws SQLException
	{
		return this.rememberSavepoint(new SPIAnonymousSavepoint());
	}

	public Savepoint setSavepoint(String name)
	throws SQLException
	{
		return this.rememberSavepoint(new SPINamedSavepoint(name));
	}

	static int getTypeForClass(Class c)
	{
		if(c.isArray() && !c.equals(byte[].class))
			return Types.ARRAY;

		Integer sqt = (Integer)s_sqlType2Class.get(c);
		if(sqt != null)
			return sqt.intValue();

		/*
		 * This is not a well known JDBC type.
		 */
		return Types.OTHER;
	}

	private Savepoint rememberSavepoint(SPISavepoint sp)
	throws SQLException
	{
		// Remember the first savepoint for each call-level so
		// that it can be released when the function call ends. Releasing
		// the first savepoint will release all subsequent savepoints.
		//
		Invocation invocation = Invocation.current();
		Savepoint old = invocation.getSavepoint();
		if(old == null)
			invocation.setSavepoint(sp);
		return sp;
	}

	private static void forgetSavepoint(SPISavepoint sp)
	throws SQLException
	{
		Invocation invocation = Invocation.current();
		if(invocation.getSavepoint() == sp)
			invocation.setSavepoint(null);
	}
}
