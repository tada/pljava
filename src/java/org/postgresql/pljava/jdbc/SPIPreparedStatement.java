/*
 * This file contains software that has been made available under The Mozilla
 * Public License 1.1. Use and distribution hereof are subject to the
 * restrictions set forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden All Rights Reserved
 */
package org.postgresql.pljava.jdbc;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ParameterMetaData;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.postgresql.pljava.internal.ExecutionPlan;
import org.postgresql.pljava.internal.Portal;
import org.postgresql.pljava.internal.SPI;
import org.postgresql.pljava.internal.SPIException;

/**
 *
 * @author Thomas Hallgren
 */
public class SPIPreparedStatement extends SPIStatement implements PreparedStatement
{
	private static class ParamEntry
	{
		final int    m_columnIndex;
		final int    m_sqlType;
		final Object m_value;
		
		ParamEntry(int columnIndex, int sqlType, Object value)
		{
			m_columnIndex = columnIndex;
			m_sqlType = sqlType;
			m_value = value;
		}
	}

	private final String m_statement;
	private final ArrayList m_paramList = new ArrayList();

	public SPIPreparedStatement(SPIConnection conn, String statement)
	{
		super(conn);
		m_statement = statement;
	}

	public ResultSet executeQuery()
	throws SQLException
	{
		this.execute();
		return this.getResultSet();
	}

	public int executeUpdate()
	throws SQLException
	{
		this.execute();
		return this.getUpdateCount();
	}

	public void setNull(int columnIndex, int sqlType)
	throws SQLException
	{
		this.setObject(columnIndex, null, sqlType);
	}

	public void setBoolean(int columnIndex, boolean value) throws SQLException
	{
		this.setObject(columnIndex, value ? Boolean.TRUE : Boolean.FALSE, Types.BOOLEAN);
	}

	public void setByte(int columnIndex, byte value) throws SQLException
	{
		this.setObject(columnIndex, new Byte(value), Types.TINYINT);
	}

	public void setShort(int columnIndex, short value) throws SQLException
	{
		this.setObject(columnIndex, new Short(value), Types.SMALLINT);
	}

	public void setInt(int columnIndex, int value) throws SQLException
	{
		this.setObject(columnIndex, new Integer(value), Types.INTEGER);
	}

	public void setLong(int columnIndex, long value) throws SQLException
	{
		this.setObject(columnIndex, new Long(value), Types.BIGINT);
	}

	public void setFloat(int columnIndex, float value) throws SQLException
	{
		this.setObject(columnIndex, new Float(value), Types.FLOAT);
	}

	public void setDouble(int columnIndex, double value) throws SQLException
	{
		this.setObject(columnIndex, new Double(value), Types.DOUBLE);
	}

	public void setBigDecimal(int columnIndex, BigDecimal value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.DECIMAL);
	}

	public void setString(int columnIndex, String value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.CHAR);
	}

	public void setBytes(int columnIndex, byte[] value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.BINARY);
	}

	public void setDate(int columnIndex, Date value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.DATE);
	}

	public void setTime(int columnIndex, Time value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.TIME);
	}

	public void setTimestamp(int columnIndex, Timestamp value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.TIMESTAMP);
	}

	public void setAsciiStream(int columnIndex, InputStream value, int length) throws SQLException
	{
		try
		{
			m_paramList.add(new ParamEntry(columnIndex, Types.CLOB,
					new ClobValue(new InputStreamReader(value, "US-ASCII"), length)));
		}
		catch(UnsupportedEncodingException e)
		{
			throw new SQLException("US-ASCII encoding is not supported by this JVM");
		}
	}

	/**
	 * @deprecated
	 */
	public void setUnicodeStream(int columnIndex, InputStream value, int arg2) throws SQLException
	{
		throw new UnsupportedFeatureException("PreparedStatement.setUnicodeStream");
	}

	public void setBinaryStream(int columnIndex, InputStream value, int length) throws SQLException
	{
		this.setObject(columnIndex, new BlobValue(value, length), Types.BLOB);
	}

	public void clearParameters() throws SQLException
	{
		m_paramList.clear();
	}

	public void setObject(int columnIndex, Object value, int sqlType, int scale)
	throws SQLException
	{
		this.setObject(columnIndex, value, sqlType);
	}

	public void setObject(int columnIndex, Object value, int sqlType)
	throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, sqlType, value));
	}

	public void setObject(int columnIndex, Object value)
	throws SQLException
	{
		this.setObject(columnIndex, value, Types.JAVA_OBJECT);
	}

	public boolean execute()
	throws SQLException
	{
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * The prepared statement cannot be used for executing oter statements.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean execute(String statement)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Can't execute other statements using a prepared statement");
	}

	public void addBatch()
	throws SQLException
	{
		this.internalAddBatch(m_paramList.clone());
		m_paramList.clear();
	}

	/**
	 * The prepared statement cannot have other statements added too it.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void addBatch(String statement)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Can't add batch statements to a prepared statement");
	}

	public void setCharacterStream(int columnIndex, Reader value, int length)
	throws SQLException
	{
		this.setObject(columnIndex, new ClobValue(value, length), Types.CLOB);
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setRef(int, java.sql.Ref)
	 */
	public void setRef(int columnIndex, Ref value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.REF);
	}

	public void setBlob(int columnIndex, Blob value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.BLOB);
	}

	public void setClob(int columnIndex, Clob value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.CLOB);
	}

	public void setArray(int columnIndex, Array value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.ARRAY);
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#getMetaData()
	 */
	public ResultSetMetaData getMetaData() throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public void setDate(int columnIndex, Date value, Calendar cal)
	throws SQLException
	{
		if(cal == null || cal == Calendar.getInstance())
			this.setObject(columnIndex, value, Types.DATE);
		throw new UnsupportedFeatureException("Setting date using explicit Calendar");
	}

	public void setTime(int columnIndex, Time value, Calendar cal)
	throws SQLException
	{
		if(cal == null || cal == Calendar.getInstance())
			this.setObject(columnIndex, value, Types.TIME);
		throw new UnsupportedFeatureException("Setting time using explicit Calendar");
	}

	public void setTimestamp(int columnIndex, Timestamp value, Calendar cal)
	throws SQLException
	{
		if(cal == null || cal == Calendar.getInstance())
			this.setObject(columnIndex, value, Types.TIMESTAMP);
		throw new UnsupportedFeatureException("Setting time using explicit Calendar");
	}

	public void setNull(int columnIndex, int sqlType, String typeName)
	throws SQLException
	{
		this.setNull(columnIndex, sqlType);
	}

	public void setURL(int columnIndex, URL value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.DATALINK);
	}

	public ParameterMetaData getParameterMetaData() throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	protected int executeBatchEntry(Object batchEntry)
	throws SQLException
	{
		this.clearParameters();
		m_paramList.addAll((ArrayList)batchEntry);
		if(!this.execute())
		{
			int updCount = this.getUpdateCount();
			if(updCount >= 0)
				return updCount;
		}
		return SUCCESS_NO_INFO;
	}
}