/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root directory of this distribution or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.jdbc;

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
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * @author Thomas Hallgren
 */
public class SQLInputOnResultSet implements SQLInput
{
	private int m_columnIndex;
	private final ResultSet m_resultSet;

	SQLInputOnResultSet(ResultSet resultSet)
	{
		m_resultSet = resultSet;
		m_columnIndex = 0;
	}

	public String readString() throws SQLException
	{
		return m_resultSet.getString(++m_columnIndex);
	}

	public boolean readBoolean() throws SQLException
	{
		return m_resultSet.getBoolean(++m_columnIndex);
	}

	public byte readByte() throws SQLException
	{
		return m_resultSet.getByte(++m_columnIndex);
	}

	public short readShort() throws SQLException
	{
		return m_resultSet.getShort(++m_columnIndex);
	}

	public int readInt() throws SQLException
	{
		return m_resultSet.getInt(++m_columnIndex);
	}

	public long readLong() throws SQLException
	{
		return m_resultSet.getLong(++m_columnIndex);
	}

	public float readFloat() throws SQLException
	{
		return m_resultSet.getFloat(++m_columnIndex);
	}

	public double readDouble() throws SQLException
	{
		return m_resultSet.getDouble(++m_columnIndex);
	}

	public BigDecimal readBigDecimal() throws SQLException
	{
		return m_resultSet.getBigDecimal(++m_columnIndex);
	}

	public byte[] readBytes() throws SQLException
	{
		return m_resultSet.getBytes(++m_columnIndex);
	}

	public Date readDate() throws SQLException
	{
		return m_resultSet.getDate(++m_columnIndex);
	}

	public Time readTime() throws SQLException
	{
		return m_resultSet.getTime(++m_columnIndex);
	}

	public Timestamp readTimestamp() throws SQLException
	{
		return m_resultSet.getTimestamp(++m_columnIndex);
	}

	public Reader readCharacterStream() throws SQLException
	{
		return m_resultSet.getCharacterStream(++m_columnIndex);
	}

	public InputStream readAsciiStream() throws SQLException
	{
		return m_resultSet.getAsciiStream(++m_columnIndex);
	}

	public InputStream readBinaryStream() throws SQLException
	{
		return m_resultSet.getBinaryStream(++m_columnIndex);
	}

	public Object readObject() throws SQLException
	{
		return m_resultSet.getObject(++m_columnIndex);
	}

	public Ref readRef() throws SQLException
	{
		return m_resultSet.getRef(++m_columnIndex);
	}

	public Blob readBlob() throws SQLException
	{
		return m_resultSet.getBlob(++m_columnIndex);
	}

	public Clob readClob() throws SQLException
	{
		return m_resultSet.getClob(++m_columnIndex);
	}

	public Array readArray() throws SQLException
	{
		return m_resultSet.getArray(++m_columnIndex);
	}

	public boolean wasNull() throws SQLException
	{
		return m_resultSet.wasNull();
	}

	public URL readURL() throws SQLException
	{
		return m_resultSet.getURL(++m_columnIndex);
	}
}
