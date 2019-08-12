/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   PostgreSQL Global Development Group
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.ClientInfoStatus;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData; // for javadoc link
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.BitSet;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.postgresql.pljava.internal.Oid;
import org.postgresql.pljava.internal.PgSavepoint;

/**
 * Provides access to the current connection (session) the Java stored
 * procedure is running in.  It is returned from the driver manager
 * with
 * <code>DriverManager.getConnection("jdbc:default:connection");</code>
 * and cannot be managed in any way since it's already running inside
 * a transaction.  This means the following methods cannot be used.
 * <ul>
 * <li><code>commit()</code></li>
 * <li><code>rollback()</code></li>
 * <li><code>setAutoCommit()</code></li>
 * <li><code>setTransactionIsolation()</code></li>
 * </ul>
 * @author Thomas Hallgren
 */
public class SPIConnection implements Connection
{
	/**
	 * The version number of the currently executing PostgreSQL
	 * server.
	 */
	private int[] VERSION_NUMBER = null;
	
	/**
	 * Client info properties for JDBC 4.
	 */
	private Properties _clientInfo;

	/**
	 * A map from Java classes to java.sql.Types integers.
	 *<p>
	 * This map is only used by the (non-API) getTypeForClass method,
	 * which, in turn, is only used for
	 * {@link PreparedStatement#setObject(int,Object)}.
	 */
	private static final HashMap<Class<?>,Integer> s_class2sqlType =
		new HashMap<Class<?>,Integer>(30);

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
		s_class2sqlType.put(clazz, sqlType);
	}

	/**
	 * Map a {@code Class} to a {@link Types} integer, as used in
	 * (and only in) {@link PreparedStatement#setObject(int,Object)}.
	 */
	static int getTypeForClass(Class c)
	{
		if(c.isArray() && !c.equals(byte[].class))
			return Types.ARRAY;

		Integer sqt = s_class2sqlType.get(c);
		if(sqt != null)
			return sqt;

		/*
		 * This is not a well known JDBC type.
		 */
		return Types.OTHER;
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
	 * Returns {@link ResultSet#CLOSE_CURSORS_AT_COMMIT}.
	 */
	@Override
	public int getHoldability()
	{
		return ResultSet.CLOSE_CURSORS_AT_COMMIT;
	}

	/**
	 * Returns {@link Connection#TRANSACTION_READ_COMMITTED}.
	 */
	@Override
	public int getTransactionIsolation()
	{
		return TRANSACTION_READ_COMMITTED;
	}

	/**
	 * Warnings are not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public void clearWarnings()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.clearWarnings");
	}

	/**
	 * This is a no-op. The default connection never closes.
	 */
	@Override
	public void close()
	{
	}

	/**
	 * It's not legal to do a commit within a call from SQL.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public void commit()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.commit");
	}

	/**
	 * It's not legal to do a rollback within a call from SQL.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public void rollback()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.rollback");
	}

	/**
	 * It is assumed that an SPI call is under transaction control. This method
	 * will always return <code>false</code>.
	 */
	@Override
	public boolean getAutoCommit()
	{
		return false;
	}

	/**
	 * Will always return false.
	 */
	@Override
	public boolean isClosed()
	{
		return false;
	}

	/**
	 * Returns <code>false</code>. The SPIConnection is not real-only.
	 */
	@Override
	public boolean isReadOnly()
	{
		return false;
	}

	/**
	 * Change of holdability is not supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public void setHoldability(int holdability)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.setHoldability");
	}

	/**
	 * Change of transaction isolation level is not supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
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
	@Override
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
	@Override
	public void setReadOnly(boolean readOnly)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.setReadOnly");
	}

	/**
	 * Returns the database in which we are running.
	 */
	@Override
	public String getCatalog()
	throws SQLException
	{
		ResultSet rs = createStatement().executeQuery("SELECT pg_catalog.current_database()");
		try {
			rs.next();
			return rs.getString(1);
		} finally {
			rs.close();
		}
	}

	/**
	 * The catalog name cannot be set.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public void setCatalog(String catalog)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.setCatalog");
	}

	/**
	 * Retrieves an instance of {@link SPIDatabaseMetaData}
	 * representing this <code>Connection</code> object.  The
	 * metadata includes information about the SQL grammar
	 * supported by PostgreSQL, the capabilities of PL/Java, as
	 * well as the tables and stored procedures for this
	 * connection and so on.
	 *
	 * @return an SPIDatabaseMetaData object for this
	 * <code>Connection</code> object
	 */
	@Override
	public DatabaseMetaData getMetaData()
	{
		return new SPIDatabaseMetaData(this);
	}

	/**
	 * Warnings are not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public SQLWarning getWarnings()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Connection.getWarnings");
	}

	@Override
	public void releaseSavepoint(Savepoint savepoint) throws SQLException
	{
		if(!(savepoint instanceof PgSavepoint))
			throw new IllegalArgumentException("Not a PL/Java Savepoint");

		PgSavepoint sp = (PgSavepoint)savepoint;
		sp.release();
		forgetSavepoint(sp);
	}

	@Override
	public void rollback(Savepoint savepoint) throws SQLException
	{
		if(!(savepoint instanceof PgSavepoint))
			throw new IllegalArgumentException("Not a PL/Java Savepoint");

		PgSavepoint sp = (PgSavepoint)savepoint;
		Invocation.clearErrorCondition();
		sp.rollback();
	}

	/**
	 * Creates a new instance of <code>SPIStatement</code>.
	 */
	@Override
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
	 *
	 *             if the <code>resultSetType</code> differs from
	 *             {@link ResultSet#TYPE_FORWARD_ONLY} or if the
	 *             <code>resultSetConcurrencty</code> differs from
	 *             {@link ResultSet#CONCUR_READ_ONLY}.
	 */
	@Override
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
	@Override
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
	@Override
	public Map<String,Class<?>> getTypeMap()
	throws SQLException
	{
		return null;
	}

	/**
	 * Type map is not yet implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public void setTypeMap(Map<String,Class<?>> map)
	throws SQLException
	{
		throw new UnsupportedOperationException("Type map is not yet implemented");
	}

	/**
	 * Parse the JDBC SQL into PostgreSQL.
	 */
	@Override
	public String nativeSQL(String sql)
	throws SQLException
	{
		return this.nativeSQL(sql, null);
	}
	
	/*
	 * An internal nativeSQL that returns a count of substitutable parameters
	 * detected, used in prepareStatement().
	 */
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
				else if(inQuote == 0)
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
	@Override
	public CallableStatement prepareCall(String sql) throws SQLException
	{
		throw new UnsupportedOperationException("Procedure calls are not yet implemented");
	}

	/**
	 * Procedure calls are not yet implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
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
	@Override
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
	@Override
	public PreparedStatement prepareStatement(String sql)
	throws SQLException
	{
		if(this.isClosed())
			throw new SQLException("Connection is closed");

		int[] pcount = new int[] { 0 };
		sql = this.nativeSQL(sql, pcount);
		PreparedStatement stmt = new SPIPreparedStatement(this, sql, pcount[0]);
		return stmt;
	}

	/**
	 * Return of auto generated keys is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
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
	@Override
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
	@Override
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
	@Override
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Auto generated key support not yet implemented");
	}

	/**
	 * Return of auto generated keys is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public PreparedStatement prepareStatement(String sql, String[] columnNames)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Auto generated key support not yet implemented");
	}

	@Override
	public Savepoint setSavepoint()
	throws SQLException
	{
		return this.rememberSavepoint(PgSavepoint.set(null));
	}

	@Override
	public Savepoint setSavepoint(String name)
	throws SQLException
	{
		return this.rememberSavepoint(PgSavepoint.set(name));
	}

	/*
	 * An implementation factor of setSavepoint() to ensure that all such
	 * savepoints are released when the function returns.
	 */
	private Savepoint rememberSavepoint(PgSavepoint sp)
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

	/*
	 * An implementation factor of releaseSavepoint()
	 * undoing the registration done by rememberSavepoint().
	 */
	private static void forgetSavepoint(PgSavepoint sp)
	throws SQLException
	{
		Invocation invocation = Invocation.current();
		if(invocation.getSavepoint() == sp)
			invocation.setSavepoint(null);
	}

    /**
	 * Return the server version number as a three-element {@code int} array
	 * (of which the third may be null), as used in the
	 * {@code getDatabase...Version} methods of {@link DatabaseMetaData}.
	 */
	public int[] getVersionNumber() throws SQLException
    {
        if (VERSION_NUMBER != null)
        	return VERSION_NUMBER;

        ResultSet rs = createStatement().executeQuery(
            "SELECT pg_catalog.version()");

        try
        {
            if (!rs.next())
                throw new SQLException(
                "Cannot retrieve product version number");

            String ver = rs.getString(1);
            Pattern p = Pattern.compile(
                "^PostgreSQL\\s+(\\d+)\\.(\\d+)(.\\d+)?.*");
            Matcher m = p.matcher(ver);
            if(m.matches())
            {
            	VERSION_NUMBER = new int[3];
            	VERSION_NUMBER[0] = Integer.parseInt(m.group(1));
            	VERSION_NUMBER[1] = Integer.parseInt(m.group(2));
            	String bugfix = m.group(3);
            	if(bugfix != null && bugfix.length() > 1)
            		VERSION_NUMBER[2] = Integer.parseInt(bugfix.substring(1));
                return VERSION_NUMBER;
            }
            throw new SQLException(
                "Unexpected product version string format: " +
                ver);
        }
        catch (PatternSyntaxException e)
        {
            throw new SQLException(
                "Error in product version string parsing: " +
                e.getMessage());
        }
        finally
        {
            rs.close();
        }
    }

	/**
	 * Convert a PostgreSQL type name to a {@link Types} integer, using the
	 * {@code JDBC_TYPE_NAMES}/{@code JDBC_TYPE_NUMBERS} arrays; used in
	 * {@link DatabaseMetaData} and {@link ResultSetMetaData}.
	 */
    public int getSQLType(String pgTypeName)
    {
        if (pgTypeName == null)
            return Types.OTHER;

        for (int i = 0;i < JDBC_TYPE_NAMES.length;i++)
            if (pgTypeName.equals(JDBC_TYPE_NAMES[i]))
                return JDBC_TYPE_NUMBERS[i];

        return Types.OTHER;
    }

	/**
	 * This returns the {@link Types} type for a PG type oid, by mapping it
	 * to a name using {@link #getPGType} and then to the result via
	 * {@link #getSQLType(String)}; used in {@link ResultSetMetaData} and
	 * five places in {@link DatabaseMetaData}.
	 *<p>
	 * This method is a bit goofy, as it first maps from Oid to type name, and
	 * then from name to JDBC type, all to accomplish the inverse of the JDBC
	 * type / Oid mapping that already exists in Oid.c, and so the mapping
	 * arrays in this file have to be updated in sync with that. Look into
	 * future consolidation....
     *
     * @param oid PostgreSQL type oid
     * @return the java.sql.Types type
     * @exception SQLException if a database access error occurs
     */
    public int getSQLType(Oid oid) throws SQLException
    {
        return getSQLType(getPGType(oid));
    }
 
	/**
	 * Map the Oid of a PostgreSQL type to its name (specifically, the
	 * {@code typname} attribute of {@code pg_type}. Used in
	 * {@link DatabaseMetaData} and {@link ResultSetMetaData}.
	 */
	public String getPGType(Oid oid) throws SQLException
    {
        String typeName = null;
        PreparedStatement query = null;
        ResultSet rs = null;

        try
        {
            query = prepareStatement("SELECT typname FROM pg_catalog.pg_type WHERE oid=?");
            query.setObject(1, oid);
            rs = query.executeQuery();

            if (rs.next())
            {
                typeName = rs.getString(1);
            }
            else
            {
                throw new SQLException("Cannot find PG type with oid=" + oid);
            }
        }
        finally
        {
            if (query != null)
            {
                query.close();
            }
        }

        return typeName;
    }

	/**
	 * Apply some hardwired coercions from an object to a desired class,
	 * where the class can be {@code String} or {@code URL}, as used in
	 * {@code ObjectResultSet} for retrieving values and
	 * {@code SingleRowWriter} for storing them, and also in
	 * {@code SQLInputFromTuple} for UDTs mapping composite types.
	 *<p>
	 * Some review may be in order to determine just what part of JDBC's
	 * type mapping rules this corresponds to. It seems strangely limited, and
	 * the use of the same coercion in both the retrieval and storage direction
	 * in {@code ResultSet}s seems a bit suspect, as does its use in UDT input
	 * but not output with composites.
	 */
	static <T> T basicCoercion(Class<T> cls, Object value)
	throws SQLException
	{
		if(value == null || cls.isInstance(value))
			return (T)value;

		if(cls == String.class)
		{
			if(value instanceof Number
			|| value instanceof Boolean
			|| value instanceof Timestamp
			|| value instanceof Date
			|| value instanceof Time)
				return (T)value.toString();
		}
		else if(cls == URL.class && value instanceof String)
		{
			try
			{
				return (T)new URL((String)value);
			}
			catch(MalformedURLException e)
			{
				throw new SQLException(e.toString());
			}
		}
		throw new SQLException("Cannot derive a value of class " +
				cls.getName() + " from an object of class " + value.getClass().getName());
	}

	/**
	 * Apply some hardwired coercions from an object to a desired class,
	 * one of Java's several numeric classes, when the value is an instance of
	 * {@code Number}, {@code String}, or {@code Boolean}, as used in
	 * {@code ObjectResultSet} for retrieving values and
	 * {@code SingleRowWriter} for storing them, and also in
	 * {@code SQLInputFromTuple} for UDTs mapping composite types.
	 *<p>
	 * Some review may be in order to determine just what part of JDBC's
	 * type mapping rules this corresponds to. It seems strangely limited, and
	 * the use of the same coercion in both the retrieval and storage direction
	 * in {@code ResultSet}s seems a bit suspect, as does its use in UDT input
	 * but not output with composites.
	 *<p>
	 * Oddly, this doesn't promise to return a subclass of its {@code cls}
	 * parameter: if {@code value} is a {@code Number}, it is returned directly
	 * no matter what {@code cls} was requested.
	 */
	static Number basicNumericCoercion(Class cls, Object value)
	throws SQLException
	{
		if(value == null || value instanceof Number)
			return (Number)value;

		if(cls == int.class  || cls == long.class || cls == short.class || cls == byte.class)
		{
			if(value instanceof String)
				return Long.valueOf((String)value);

			if(value instanceof Boolean)
				return new Long(((Boolean)value).booleanValue() ? 1 : 0);
		}
		else if(cls == BigDecimal.class)
		{
			if(value instanceof String)
				return new BigDecimal((String)value);

			if(value instanceof Boolean)
				return new BigDecimal(((Boolean)value).booleanValue() ? 1 : 0);
		}
		if(cls == double.class  || cls == float.class)
		{
			if(value instanceof String)
				return Double.valueOf((String)value);

			if(value instanceof Boolean)
				return new Double(((Boolean)value).booleanValue() ? 1 : 0);
		}
		throw new SQLException("Cannot derive a Number from an object of class " + value.getClass().getName());
	}

	/**
	 * Apply some hardwired coercions from an object to a desired class,
	 * where the class may be {@link Timestamp}, {@link Date}, or {@link Time}
	 * and the value one of those or {@code String}, as used in
	 * {@code ObjectResultSet} for retrieving values and
	 * {@code SingleRowWriter} for storing them, but <em>not</em> also in
	 * {@code SQLInputFromTuple} for UDTs mapping composite types.
	 *<p>
	 * Some review may be in order to determine just what part of JDBC's
	 * type mapping rules this corresponds to. It seems strangely limited, and
	 * the use of the same coercion in both the retrieval and storage direction
	 * in {@code ResultSet}s seems a bit suspect.
	 */
	static <T> T basicCalendricalCoercion(
		Class<T> cls, Object value, Calendar cal)
	throws SQLException
	{
		if(value == null)
			return null;

		if(cls.isInstance(value))
			return (T)value;

		if(cls == Timestamp.class)
		{
			if(value instanceof Date)
			{
				cal.setTime((Date)value);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.SECOND, 0);
				cal.set(Calendar.MILLISECOND, 0);
				return (T)new Timestamp(cal.getTimeInMillis());
			}
			else if(value instanceof Time)
			{
				cal.setTime((Date)value);
				cal.set(1970, 0, 1);
				return (T)new Timestamp(cal.getTimeInMillis());
			}
			else if(value instanceof String)
			{
				return (T)Timestamp.valueOf((String)value);
			}
		}
		else if(cls == Date.class)
		{
			if(value instanceof Timestamp)
			{
				Timestamp ts = (Timestamp)value;
				cal.setTime(ts);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.SECOND, 0);
				cal.set(Calendar.MILLISECOND, 0);
				return (T)new Date(cal.getTimeInMillis());
			}
			else if(value instanceof String)
			{
				return (T)Date.valueOf((String)value);
			}
		}
		else if(cls == Time.class)
		{
			if(value instanceof Timestamp)
			{
				Timestamp ts = (Timestamp)value;
				cal.setTime(ts);
				cal.set(1970, 0, 1);
				return (T)new Time(cal.getTimeInMillis());
			}
			else if(value instanceof String)
			{
				return (T)Time.valueOf((String)value);
			}
		}
		throw new SQLException("Cannot derive a value of class " +
			cls.getName() + " from an object of class " + value.getClass().getName());
	}

	/**
	 * Masquerade a no-time-zone PostgreSQL type as if it were UTC, for use with
	 * the {@code java.sql.{Date,Time,Timestamp}} types.
	 *<p>
	 * Those types are all implemented over {@code java.util.Date}, for which a
	 * time zone is always implicit. They can be interconverted freely with
	 * PostgreSQL types that have time zones. It's the PostgreSQL types without
	 * time zones that pose the problems. (That is why these {@code java.sql}
	 * types are not a great fit for the no-time-zone PostgreSQL types, and any
	 * code that can use the JSR 310 {@code java.time} types introduced in
	 * Java 8 should definitely use them instead.)
	 *<p>
	 * Converting a PostgreSQL no-time-zone type to a {@code java.sql} date/time
	 * type requires masquerading it as UTC, that is, finding a UTC instant that
	 * will appear to match PostgreSQL's human-readable rendering of the value,
	 * when rendered by Java according to the Java runtime's default time zone.
	 *<p>
	 * Likewise, when a Java value of {@code java.sql.{Date,Time,Timestamp}}
	 * type is to be assigned to a no-time-zone PostgreSQL type, it must be
	 * 'unmasked', which is done by this same method, with {@code unmask} true.
	 *<p>
	 * The mask and unmask operations simply move the value by the time zone
	 * offset between UTC and the Java runtime's default time zone. Time zones
	 * that observe summer time complicate the picture, because the offset
	 * depends on the date/time being converted. Specifically, it depends on the
	 * UTC instant being converted, which {@code TimeZone.getOffset} can look up
	 * in the zone's rules to determine what offset applies.
	 *<p>
	 * Therefore, the {@code unmask} operation is easy. The argument value is in
	 * UTC, so the correct offset is looked up in one step and the correct
	 * result is returned.
	 *<p>
	 * Not so for the mask operation. In that case, the argument value differs
	 * from the (unknown) UTC value, by exactly the (unknown) offset. But the
	 * tricky cases are only twice a year, at the transition points. Most of the
	 * year, the offset found by looking up the argument value <em>as if</em> it
	 * were UTC can be used to create a putative UTC value, and will match the
	 * offset authoritatively looked up for that putative result, which can then
	 * be returned as the correct result.
	 *<p>
	 * If the second offset differs from the first, the value is near a
	 * transition point, and the two different offsets now seen are both
	 * candidates for the correct one. A second putative UTC value can be
	 * constructed using the second choice of offset, and that UTC value used to
	 * look up the offset a third and final time. If the third offset matches
	 * the second one, the process has converged, and the latter candidate UTC
	 * value is the result.
	 *<p>
	 * If the third offset does not match the second, it might match the first,
	 * implying that the process, if continued, would clearly alternate and
	 * never converge. This reveals that the argument is a time that cannot be
	 * represented, as it falls in a gap (typically the spring transition to
	 * summer time). The later result will be returned: the one that falls after
	 * the transition gap.
	 *<p>
	 * That means that this mapping is irredeemably lossy in the neighborhood of
	 * gap transitions. Times that fall in the gap will not be rendered by Java
	 * the same as by PostgreSQL, but will be moved to just beyond the gap,
	 * where for the next hour (or other width of the gap) they will be
	 * indistinguishable from PostgreSQL values that really do fall in that same
	 * interval just beyond the gap. This is an inherent limitation of the early
	 * JDBC decision to use implicitly-zoned classes even to represent no-zone
	 * values. Again, the JSR 310 {@code java.time} types should be used in
	 * preference, wherever possible.
	 *<p>
	 * One final note: transitions can occur not just through the normal
	 * operation of summer time rules, but also through politicians changing
	 * those very rules, which happens less often, but also at less predictable
	 * times. To allow for that, the code might (vanishingly rarely) test a
	 * fourth offset before concluding it has pinpointed a gap; see the code
	 * comments for more.
	 * @param msecsFromEpoch a time in milliseconds from the Java epoch,
	 * 1 January 1970 00:00:00 either in UTC (when unmasking), or as a zoneless
	 * value taken to be in the Java runtime's default zone (when masking).
	 * @param unmask whether to 'mask' (convert a PostgreSQL value for Java's
	 * use by finding a UTC instant that corresponds to it), or 'unmask' (the
	 * reverse operation).
	 * @return milliseconds from the Java epoch, either in UTC (when masking),
	 * or in the Java runtime's default time zone (when unmasking).
	 */
	static long utcMasquerade(long msecsFromEpoch, boolean unmask)
	{
		TimeZone tz = TimeZone.getDefault();
		int msecOffset1 = tz.getOffset(msecsFromEpoch);
		if ( unmask  ||  0 == msecOffset1 )
			return msecsFromEpoch + msecOffset1;
		/*
		 * We are in the masking case, with msecOffset1 nonzero. It was a bit of
		 * a stab in the dark, being calculated from a local time rather than a
		 * value in UTC, but it gives us our first UTC value to try, and see
		 * what offset we get from that.
		 */
		long firstTry = msecsFromEpoch - msecOffset1;
		int msecOffset2 = tz.getOffset(firstTry);
		if ( msecOffset2 == msecOffset1 )
			return firstTry;
		long secondTry = msecsFromEpoch - msecOffset2;
		int msecOffset3 = tz.getOffset(secondTry);
		if ( msecOffset3 == msecOffset2 )
			return secondTry;
		/*
		 * We may be in a cycle where msecOffset3 == msecOffset1, in which case
		 * we'll never converge, because our target time falls in a gap. But
		 * keep in mind that msecOffset1 was a wild stab, computed from the
		 * wrong end of the UTC<->local conversion. Offset transitions can
		 * occur not just by summer time rules being triggered but also by
		 * politicians changing the rules. The difference between our original
		 * msecsFromEpoch and firstTry could be as much as fourteen hours under
		 * current rules (eighteen by limits in the code), potentially enough to
		 * have crossed more than one rule transition (as of this writing, no
		 * zone has two transitions within 18 hours, but future rules depend on
		 * future politicians). Therefore, best not to assume that
		 * offset3 != offset2 implies it must equal offset1. One more test is
		 * cheap insurance.
		 *
		 * If we are in a gap, and msecOffset1 was negative, subtracting it made
		 * firstTry strictly later than msecsFromEpoch, which means the change
		 * in offset resulted from a forward crossing of the transition, and
		 * firstTry is the later result. Otherwise, the subtraction made
		 * firstTry earlier, the first offset change resulted from a backward
		 * crossing, and secondTry is the later result.
		 */
		if ( msecOffset3 == msecOffset1 )
			return msecOffset1 < 0 ? firstTry : secondTry;
		/*
		 * If we're here (highly unlikely!), one more iteration will allow us to
		 * follow the same reasoning as above, but based exclusively on values
		 * computed from the UTC end of the conversion. That should make the
		 * chance vanishingly small of having hunted across more than one
		 * transition.
		 */
		long thirdTry = msecsFromEpoch - msecOffset3;
		int msecOffset4 = tz.getOffset(thirdTry);
		if ( msecOffset4 == msecOffset3 )
			return thirdTry;
		return msecOffset2 < 0 ? secondTry : thirdTry;
	}

    /*
     * This table holds the org.postgresql names for the types supported.
     * Any types that map to Types.OTHER (eg POINT) don't go into this table.
     * They default automatically to Types.OTHER
     *
     * Note: This must be in the same order as below.
	 *
	 * These arrays are not only used by getSQLType() in this file, but also
	 * directly accessed by getUDTs() in DatabaseMetaData.
     *
     * Tip: keep these grouped together by the Types. value
     */
    public static final String JDBC_TYPE_NAMES[] = {
                "int2",
                "int4", "oid",
                "int8",
                "cash", "money",
                "numeric",
                "float4",
                "float8",
                "bpchar", "char", "char2", "char4", "char8", "char16",
                "varchar", "text", "name", "filename",
                "bytea",
                "bool",
                "bit",
                "date",
                "time", "timetz",
                "abstime", "timestamp", "timestamptz",
				"xml",
                "_bool", "_char", "_int2", "_int4", "_text",
                "_oid", "_varchar", "_int8", "_float4", "_float8",
                "_abstime", "_date", "_time", "_timestamp", "_numeric",
                "_bytea"
            };

    /*
     * This table holds the JDBC type for each entry above.
     *
     * Note: This must be in the same order as above
     *
     * Tip: keep these grouped together by the Types. value
     */
    public static final int JDBC_TYPE_NUMBERS[];

	static
	{
		/*
		 * Try to get the JDBC 4.2 / Java 8 TIME*ZONE types reflectively.
		 * Once the Java back horizon advances to 8, just do this the easy way.
		 */
		int sqx  = Types.OTHER;      // don't just start saying SQLXML in 1.5.1
		int ttz  = Types.TIME;       // Use these values
		int tstz = Types.TIMESTAMP;  //         pre-Java 8
//		try    COMMENTED OUT FOR BACK-COMPATIBILITY REASONS IN PL/JAVA 1.5.x
//		{
//			ttz =
//				Types.class.getField("TIME_WITH_TIMEZONE")
//					.getInt(Types.class);
//			tstz =
//				Types.class.getField("TIMESTAMP_WITH_TIMEZONE")
//					.getInt(Types.class);
//		}
//		catch ( NoSuchFieldException nsfe ) { } // ok, not running in Java 8
//		catch ( IllegalAccessException iae )
//		{
//			throw new ExceptionInInitializerError(iae);
//		}
//		sqx = Types.SQLXML;

		JDBC_TYPE_NUMBERS = new int[]
		{
			Types.SMALLINT,
			Types.INTEGER, Types.INTEGER,
			Types.BIGINT,
			Types.DOUBLE, Types.DOUBLE,
			Types.NUMERIC,
			Types.REAL,
			Types.DOUBLE,
			Types.CHAR,Types.CHAR,Types.CHAR,Types.CHAR,Types.CHAR,Types.CHAR,
			Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
			Types.BINARY,
			Types.BOOLEAN,
			Types.BIT,
			Types.DATE,
			Types.TIME, ttz,
			Types.TIMESTAMP, Types.TIMESTAMP, tstz,
			sqx,
			Types.ARRAY, Types.ARRAY, Types.ARRAY, Types.ARRAY, Types.ARRAY,
			Types.ARRAY, Types.ARRAY, Types.ARRAY, Types.ARRAY, Types.ARRAY,
			Types.ARRAY, Types.ARRAY, Types.ARRAY, Types.ARRAY, Types.ARRAY,
			Types.ARRAY
        };
	}

	// ************************************************************
	// Implementation of JDBC 4 methods. Methods go here if they
	// don't throw SQLFeatureNotSupportedException; they can be
	// considered implemented even if they do nothing useful, as
	// long as that's an allowed behavior by the JDBC spec.
	// ************************************************************

	@Override
	public boolean isValid( int timeout )
	throws SQLException
	{
		return true; // The connection is always alive and
			     // ready, right?
	}

	@Override
	public boolean isWrapperFor(Class<?> iface)
	throws SQLException
	{
	    return iface.isInstance(this);
	}

	@Override
	public <T> T unwrap(Class<T> iface)
	throws SQLException
	{
	    if ( iface.isInstance(this) )
			return iface.cast(this);
		throw new SQLFeatureNotSupportedException
		( this.getClass().getSimpleName()
		  + " does not wrap " + iface.getName(),
		  "0A000" );
	}

	/*
	 * These ClientInfo implementations behave as if there are no known
	 * ClientInfo properties, which is an allowable implementation. However,
	 * there is a PostgreSQL notion corresponding to ApplicationName, so a
	 * later extension of these to recognize that property would not be amiss.
	 */

	@Override
	public void setClientInfo(String name, String value) throws SQLClientInfoException
	{
		Map<String, ClientInfoStatus> failures = new HashMap<String, ClientInfoStatus>();
		failures.put(name, ClientInfoStatus.REASON_UNKNOWN_PROPERTY);
		throw new SQLClientInfoException("ClientInfo property not supported.", failures);
	}


	@Override
	public void setClientInfo(Properties properties) 
		throws SQLClientInfoException
	{
		if (properties == null || properties.size() == 0)
			return;

		Map<String, ClientInfoStatus> failures = new HashMap<String, ClientInfoStatus>();

		Iterator<String> i = properties.stringPropertyNames().iterator();
		while (i.hasNext()) {
			failures.put(i.next(), ClientInfoStatus.REASON_UNKNOWN_PROPERTY);
		}
		throw new SQLClientInfoException("ClientInfo property not supported.", failures);
	}

	@Override
	public String getClientInfo(String name) throws SQLException
	{
		return null;
	}

	@Override
	public Properties getClientInfo() throws SQLException
	{
		if (_clientInfo == null) {
			_clientInfo = new Properties();
		}
		return _clientInfo;
	}

	@Override
	public SQLXML createSQLXML()
	throws SQLException
	{
		return SQLXMLImpl.newWritable();
	}

	// ************************************************************
	// Non-implementation of JDBC 4 methods.
	// ************************************************************

	@Override
	public Struct createStruct( String typeName, Object[] attributes )
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException(
			"SPIConnection.createStruct( String, Object[] ) not implemented yet.", "0A000" );
	}

	@Override
	public Array createArrayOf(String typeName, Object[] elements)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException(
			"SPIConnection.createArrayOf( String, Object[] ) not implemented yet.", "0A000" );
	}

	@Override
	public NClob createNClob()
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( "SPIConnection.createNClob() not implemented yet.",
			"0A000" );
	}

	@Override
	public Blob createBlob()
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( "SPIConnection.createBlob() not implemented yet.",
			"0A000" );
	}

	@Override
	public Clob createClob()
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( "SPIConnection.createClob() not implemented yet.",
			"0A000" );
	}

	// ************************************************************
	// Non-implementation of JDBC 4.1 methods.
	// ************************************************************

	// add @Override once Java 7 is back-support limit
	public void abort(Executor executor) throws SQLException
	{
		throw new SQLFeatureNotSupportedException(
			"SPIConnection.abort(Executor) not implemented yet.", "0A000" );
	}

	// add @Override once Java 7 is back-support limit
	public int getNetworkTimeout() throws SQLException
	{
		throw new SQLFeatureNotSupportedException(
			"SPIConnection.getNetworkTimeout() not implemented yet.", "0A000" );
	}

	// add @Override once Java 7 is back-support limit
	public void setNetworkTimeout(Executor executor, int milliseconds)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException(
			"SPIConnection.setNetworkTimeout(Executor,int) not implemented yet.", "0A000" );
	}

	// add @Override once Java 7 is back-support limit
	public String getSchema() throws SQLException
	{
		throw new SQLFeatureNotSupportedException(
			"SPIConnection.getSchema() not implemented yet.", "0A000" );
	}

	// add @Override once Java 7 is back-support limit
	public void setSchema(String schema) throws SQLException
	{
		throw new SQLFeatureNotSupportedException(
			"SPIConnection.setSchema(String) not implemented yet.", "0A000" );
	}
}	
