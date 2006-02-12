/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root directory of this distribution or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLOutput;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * @author Thomas Hallgren
 */
public class SQLOutputToResultSet implements SQLOutput
{
	private int m_columnIndex;
	private final ResultSet m_resultSet;

	public SQLOutputToResultSet(ResultSet resultSet)
	{
		m_resultSet = resultSet;
		m_columnIndex = 0;
	}

	public void writeArray(Array value) throws SQLException
	{
		m_resultSet.updateArray(++m_columnIndex, value);
	}

	public void writeAsciiStream(InputStream value) throws SQLException
	{
		m_resultSet.updateAsciiStream(++m_columnIndex, value, this.getStreamLength(value));
	}

	public void writeBigDecimal(BigDecimal value) throws SQLException
	{
		m_resultSet.updateBigDecimal(++m_columnIndex, value);
	}

	public void writeBinaryStream(InputStream value) throws SQLException
	{
		m_resultSet.updateBinaryStream(++m_columnIndex, value, this.getStreamLength(value));
	}

	public void writeBlob(Blob value) throws SQLException
	{
		m_resultSet.updateBlob(++m_columnIndex, value);
	}

	public void writeBoolean(boolean value) throws SQLException
	{
		m_resultSet.updateBoolean(++m_columnIndex, value);
	}

	public void writeByte(byte value) throws SQLException
	{
		m_resultSet.updateByte(++m_columnIndex, value);
	}

	public void writeBytes(byte[] value) throws SQLException
	{
		m_resultSet.updateBytes(++m_columnIndex, value);
	}

	public void writeCharacterStream(Reader value) throws SQLException
	{
		if(!value.markSupported())
			throw new UnsupportedOperationException("Unable to determine stream length");

		try
		{
			value.mark(Integer.MAX_VALUE);
			long length = value.skip(Long.MAX_VALUE);
			if(length > Integer.MAX_VALUE)
				throw new SQLException("stream content too large");
			value.reset();
			m_resultSet.updateCharacterStream(++m_columnIndex, value, (int)length);
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public void writeClob(Clob value) throws SQLException
	{
		m_resultSet.updateClob(++m_columnIndex, value);
	}

	public void writeDate(Date value) throws SQLException
	{
		m_resultSet.updateDate(++m_columnIndex, value);
	}

	public void writeDouble(double value) throws SQLException
	{
		m_resultSet.updateDouble(++m_columnIndex, value);
	}

	public void writeFloat(float value) throws SQLException
	{
		m_resultSet.updateFloat(++m_columnIndex, value);
	}

	public void writeInt(int value) throws SQLException
	{
		m_resultSet.updateInt(++m_columnIndex, value);
	}

	public void writeLong(long value) throws SQLException
	{
		m_resultSet.updateLong(++m_columnIndex, value);
	}

	public void writeObject(SQLData value) throws SQLException
	{
		m_resultSet.updateObject(++m_columnIndex, value);
	}

	public void writeRef(Ref value) throws SQLException
	{
		m_resultSet.updateRef(++m_columnIndex, value);
	}

	public void writeShort(short value) throws SQLException
	{
		m_resultSet.updateShort(++m_columnIndex, value);
	}

	public void writeString(String value) throws SQLException
	{
		m_resultSet.updateString(++m_columnIndex, value);
	}

	public void writeStruct(Struct value) throws SQLException
	{
		m_resultSet.updateObject(++m_columnIndex, value);
	}

	public void writeTime(Time value) throws SQLException
	{
		m_resultSet.updateTime(++m_columnIndex, value);
	}

	public void writeTimestamp(Timestamp value) throws SQLException
	{
		m_resultSet.updateTimestamp(++m_columnIndex, value);
	}

	public void writeURL(URL value) throws SQLException
	{
		m_resultSet.updateObject(++m_columnIndex, value);
	}

	private int getStreamLength(InputStream value) throws SQLException
	{
		if(!value.markSupported())
			throw new UnsupportedOperationException("Unable to determine stream length");

		try
		{
			value.mark(Integer.MAX_VALUE);
			long length = value.skip(Long.MAX_VALUE);
			if(length > Integer.MAX_VALUE)
				throw new SQLException("stream content too large");
			value.reset();
			return (int)length;
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

}
