/*
 * This file contains software that has been made available under The Mozilla
 * Public License 1.1. Use and distribution hereof are subject to the
 * restrictions set forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden All Rights Reserved
 */
package org.postgresql.pljava.jdbc;

import java.io.InputStream;
import java.io.Reader;

import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Calendar;
import java.util.Map;

/**
 * The <code>AbstractResultSet</code> serves as a base class for implementations
 * of the{@link java.sql.ResultSet} interface. All calls using columnNames are
 * translated into the corresponding call with index position computed using
 * a call to {@link java.sql.ResultSet#findColumn(String) findColumn}.
 *
 * @author Thomas Hallgren
 */
public abstract class AbstractResultSet implements ResultSet
{
	public Array getArray(String columnName)
	throws SQLException
	{
		return this.getArray(this.findColumn(columnName));
	}

	public InputStream getAsciiStream(String columnName)
	throws SQLException
	{
		return this.getAsciiStream(this.findColumn(columnName));
	}

	public BigDecimal getBigDecimal(String columnName)
	throws SQLException
	{
		return this.getBigDecimal(this.findColumn(columnName));
	}

	/**
	 * @deprecated
	 */
	public BigDecimal getBigDecimal(String columnName, int scale)
	throws SQLException
	{
		return this.getBigDecimal(this.findColumn(columnName), scale);
	}

	public InputStream getBinaryStream(String columnName)
	throws SQLException
	{
		return this.getBinaryStream(this.findColumn(columnName));
	}

	public Blob getBlob(String columnName)
	throws SQLException
	{
		return this.getBlob(this.findColumn(columnName));
	}

	public boolean getBoolean(String columnName)
	throws SQLException
	{
		return this.getBoolean(this.findColumn(columnName));
	}

	public byte getByte(String columnName)
	throws SQLException
	{
		return this.getByte(this.findColumn(columnName));
	}

	public byte[] getBytes(String columnName)
	throws SQLException
	{
		return this.getBytes(this.findColumn(columnName));
	}

	public Reader getCharacterStream(String columnName)
	throws SQLException
	{
		return this.getCharacterStream(this.findColumn(columnName));
	}

	public Clob getClob(String columnName)
	throws SQLException
	{
		return this.getClob(this.findColumn(columnName));
	}

	public Date getDate(String columnName)
	throws SQLException
	{
		return this.getDate(this.findColumn(columnName));
	}

	public Date getDate(String columnName, Calendar cal)
	throws SQLException
	{
		return this.getDate(this.findColumn(columnName), cal);
	}

	public double getDouble(String columnName)
	throws SQLException
	{
		return this.getDouble(this.findColumn(columnName));
	}

	public float getFloat(String columnName)
	throws SQLException
	{
		return this.getFloat(this.findColumn(columnName));
	}

	public int getInt(String columnName)
	throws SQLException
	{
		return this.getInt(this.findColumn(columnName));
	}

	public long getLong(String columnName)
	throws SQLException
	{
		return this.getLong(this.findColumn(columnName));
	}

	public Object getObject(String columnName)
	throws SQLException
	{
		return this.getObject(this.findColumn(columnName));
	}

	public Object getObject(String columnName, Map map)
	throws SQLException
	{
		return this.getObject(this.findColumn(columnName), map);
	}

	public Ref getRef(String columnName)
	throws SQLException
	{
		return this.getRef(this.findColumn(columnName));
	}

	public short getShort(String columnName)
	throws SQLException
	{
		return this.getShort(this.findColumn(columnName));
	}

	public String getString(String columnName)
	throws SQLException
	{
		return this.getString(this.findColumn(columnName));
	}

	public Time getTime(String columnName)
	throws SQLException
	{
		return this.getTime(this.findColumn(columnName));
	}

	public Time getTime(String columnName, Calendar cal)
	throws SQLException
	{
		return this.getTime(this.findColumn(columnName), cal);
	}

	public Timestamp getTimestamp(String columnName)
	throws SQLException
	{
		return this.getTimestamp(this.findColumn(columnName));
	}

	public Timestamp getTimestamp(String columnName, Calendar cal)
	throws SQLException
	{
		return this.getTimestamp(this.findColumn(columnName), cal);
	}

	/**
	 * @deprecated
	 */
	public InputStream getUnicodeStream(String columnName)
	throws SQLException
	{
		return this.getUnicodeStream(this.findColumn(columnName));
	}

	public URL getURL(String columnName)
	throws SQLException
	{
		return this.getURL(this.findColumn(columnName));
	}

	public void updateArray(String columnName, Array x)
	throws SQLException
	{
		this.updateArray(this.findColumn(columnName), x);
	}

	public void updateAsciiStream(String columnName, InputStream x, int length)
	throws SQLException
	{
		this.updateAsciiStream(this.findColumn(columnName), x, length);
	}

	public void updateBigDecimal(String columnName, BigDecimal x)
	throws SQLException
	{
		this.updateBigDecimal(this.findColumn(columnName), x);
	}

	public void updateBinaryStream(String columnName, InputStream x, int length)
	throws SQLException
	{
		this.updateBinaryStream(this.findColumn(columnName), x, length);
	}

	public void updateBlob(String columnName, Blob x)
	throws SQLException
	{
		this.updateBlob(this.findColumn(columnName), x);
	}

	public void updateBoolean(String columnName, boolean x)
	throws SQLException
	{
		this.updateBoolean(this.findColumn(columnName), x);
	}

	public void updateByte(String columnName, byte x)
	throws SQLException
	{
		this.updateByte(this.findColumn(columnName), x);
	}

	public void updateBytes(String columnName, byte x[])
	throws SQLException
	{
		this.updateBytes(this.findColumn(columnName), x);
	}

	public void updateCharacterStream(String columnName, Reader x, int length)
	throws SQLException
	{
		this.updateCharacterStream(this.findColumn(columnName), x, length);
	}

	public void updateClob(String columnName, Clob x)
	throws SQLException
	{
		this.updateClob(this.findColumn(columnName), x);
	}

	public void updateDate(String columnName, Date x)
	throws SQLException
	{
		this.updateDate(this.findColumn(columnName), x);
	}

	public void updateDouble(String columnName, double x)
	throws SQLException
	{
		this.updateDouble(this.findColumn(columnName), x);
	}

	public void updateFloat(String columnName, float x)
	throws SQLException
	{
		this.updateFloat(this.findColumn(columnName), x);
	}

	public void updateInt(String columnName, int x)
	throws SQLException
	{
		this.updateInt(this.findColumn(columnName), x);
	}

	public void updateLong(String columnName, long x)
	throws SQLException
	{
		this.updateLong(this.findColumn(columnName), x);
	}

	public void updateNull(String columnName)
	throws SQLException
	{
		this.updateNull(this.findColumn(columnName));
	}

	public void updateObject(String columnName, Object x)
	throws SQLException
	{
		this.updateObject(this.findColumn(columnName), x);
	}

	public void updateObject(String columnName, Object x, int scale)
	throws SQLException
	{
		this.updateObject(this.findColumn(columnName), x, scale);
	}

	public void updateRef(String columnName, Ref x)
	throws SQLException
	{
		this.updateRef(this.findColumn(columnName), x);
	}

	public void updateShort(String columnName, short x)
	throws SQLException
	{
		this.updateShort(this.findColumn(columnName), x);
	}

	public void updateString(String columnName, String x)
	throws SQLException
	{
		this.updateString(this.findColumn(columnName), x);
	}

	public void updateTime(String columnName, Time x)
	throws SQLException
	{
		this.updateTime(this.findColumn(columnName), x);
	}

	public void updateTimestamp(String columnName, Timestamp x)
	throws SQLException
	{
		this.updateTimestamp(this.findColumn(columnName), x);
	}
}
