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
		m_paramList.add(new ParamEntry(columnIndex, sqlType, null));
	}

	public void setBoolean(int columnIndex, boolean value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.BOOLEAN, value ? Boolean.TRUE : Boolean.FALSE));
	}

	public void setByte(int columnIndex, byte value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.TINYINT, new Byte(value)));
	}

	public void setShort(int columnIndex, short value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.SMALLINT, new Short(value)));
	}

	public void setInt(int columnIndex, int value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.INTEGER, new Integer(value)));
	}

	public void setLong(int columnIndex, long value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.BIGINT, new Long(value)));
	}

	public void setFloat(int columnIndex, float value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.FLOAT, new Float(value)));
	}

	public void setDouble(int columnIndex, double value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.DOUBLE, new Double(value)));
	}

	public void setBigDecimal(int columnIndex, BigDecimal value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.DECIMAL, value));
	}

	public void setString(int columnIndex, String value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.CHAR, value));
	}

	public void setBytes(int columnIndex, byte[] value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.BINARY, value));
	}

	public void setDate(int columnIndex, Date value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.DATE, value));
	}

	public void setTime(int columnIndex, Time value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.TIME, value));
	}

	public void setTimestamp(int columnIndex, Timestamp value) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.TIMESTAMP, value));
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setAsciiStream(int, java.io.InputStream, int)
	 */
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

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setUnicodeStream(int, java.io.InputStream, int)
	 */
	public void setUnicodeStream(int columnIndex, InputStream value, int arg2) throws SQLException
	{
	}

	public void setBinaryStream(int columnIndex, InputStream value, int arg2) throws SQLException
	{
		m_paramList.add(new ParamEntry(columnIndex, Types.BLOB, new BlobValue(x, length)));
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#clearParameters()
	 */
	public void clearParameters() throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setObject(int, java.lang.Object, int, int)
	 */
	public void setObject(int columnIndex, Object value, int arg2, int arg3) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setObject(int, java.lang.Object, int)
	 */
	public void setObject(int columnIndex, Object value, int arg2) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setObject(int, java.lang.Object)
	 */
	public void setObject(int columnIndex, Object value) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#execute()
	 */
	public boolean execute() throws SQLException
	{
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#addBatch()
	 */
	public void addBatch() throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, int)
	 */
	public void setCharacterStream(int columnIndex, Reader value, int arg2) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setRef(int, java.sql.Ref)
	 */
	public void setRef(int columnIndex, Ref value) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setBlob(int, java.sql.Blob)
	 */
	public void setBlob(int columnIndex, Blob value) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setClob(int, java.sql.Clob)
	 */
	public void setClob(int columnIndex, Clob value) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setArray(int, java.sql.Array)
	 */
	public void setArray(int columnIndex, Array value) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#getMetaData()
	 */
	public ResultSetMetaData getMetaData() throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setDate(int, java.sql.Date, java.util.Calendar)
	 */
	public void setDate(int columnIndex, Date value, Calendar arg2) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setTime(int, java.sql.Time, java.util.Calendar)
	 */
	public void setTime(int columnIndex, Time value, Calendar arg2) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setTimestamp(int, java.sql.Timestamp, java.util.Calendar)
	 */
	public void setTimestamp(int columnIndex, Timestamp value, Calendar arg2) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setNull(int, int, java.lang.String)
	 */
	public void setNull(int columnIndex, int value, String arg2) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#setURL(int, java.net.URL)
	 */
	public void setURL(int columnIndex, URL value) throws SQLException
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see java.sql.PreparedStatement#getParameterMetaData()
	 */
	public ParameterMetaData getParameterMetaData() throws SQLException
	{
		// TODO Auto-generated method stub
		return null;
	}
}