/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.jdbc;

import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.Ref;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Time;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Calendar;
import java.util.Map;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;


/**
 * @author Thomas Hallgren
 */
public abstract class ObjectResultSet extends AbstractResultSet
{
	private boolean m_wasNull = false;


	/**
	 * This is a noop since warnings are not supported.
	 */
	public void clearWarnings()
	throws SQLException
	{
	}

	public Array getArray(int columnIndex)
	throws SQLException
	{
		return (Array)this.getValue(columnIndex, Array.class);
	}

	public InputStream getAsciiStream(int columnIndex)
	throws SQLException
	{
		Clob c = this.getClob(columnIndex);
		return (c == null) ? null : c.getAsciiStream();
	}

	public BigDecimal getBigDecimal(int columnIndex)
	throws SQLException
	{
		return (BigDecimal)this.getValue(columnIndex, BigDecimal.class);
	}

	/**
	 * @deprecated
	 */
	public BigDecimal getBigDecimal(int columnIndex, int scale)
	throws SQLException
	{
		throw new UnsupportedFeatureException("getBigDecimal(int, int)");
	}

	public InputStream getBinaryStream(int columnIndex)
	throws SQLException
	{
		Blob b = this.getBlob(columnIndex);
		return (b == null) ? null : b.getBinaryStream();
	}


	public Blob getBlob(int columnIndex)
	throws SQLException
	{
		byte[] bytes = this.getBytes(columnIndex);
		return (bytes == null) ? null :  new BlobValue(bytes);
	}

	public boolean getBoolean(int columnIndex)
	throws SQLException
	{
		Boolean b = (Boolean)this.getValue(columnIndex, Boolean.class);
		return (b == null) ? false : b.booleanValue();
	}

	public byte getByte(int columnIndex)
	throws SQLException
	{
		Number b = this.getNumber(columnIndex, byte.class);
		return (b == null) ? 0 : b.byteValue();
	}

	public byte[] getBytes(int columnIndex)
	throws SQLException
	{
		return (byte[])this.getValue(columnIndex, byte[].class);
	}

	public Reader getCharacterStream(int columnIndex)
	throws SQLException
	{
		Clob c = this.getClob(columnIndex);
		return (c == null) ? null : c.getCharacterStream();
	}

	public Clob getClob(int columnIndex)
	throws SQLException
	{
		String str = this.getString(columnIndex);
		return (str == null) ? null :  new ClobValue(str);
	}
	
	public Date getDate(int columnIndex)
	throws SQLException
	{
		return (Date)this.getValue(columnIndex, Date.class);
	}

	public Date getDate(int columnIndex, Calendar cal)
	throws SQLException
	{
		return (Date)this.getValue(columnIndex, Date.class, cal);
	}

	public double getDouble(int columnIndex)
	throws SQLException
	{
		Number d = this.getNumber(columnIndex, double.class);
		return (d == null) ? 0 : d.doubleValue();
	}

	public float getFloat(int columnIndex)
	throws SQLException
	{
		Number f = this.getNumber(columnIndex, float.class);
		return (f == null) ? 0 : f.floatValue();
	}

	public int getInt(int columnIndex)
	throws SQLException
	{
		Number i = this.getNumber(columnIndex, int.class);
		return (i == null) ? 0 : i.intValue();
	}

	public long getLong(int columnIndex)
	throws SQLException
	{
		Number l = this.getNumber(columnIndex, long.class);
		return (l == null) ? 0 : l.longValue();
	}

	/**
	 * ResultSetMetaData is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public ResultSetMetaData getMetaData()
	throws SQLException
	{
		throw new UnsupportedFeatureException("ResultSet meta data is not yet implemented");
	}

	public final Object getObject(int columnIndex)
	throws SQLException
	{
		Object value = this.getObjectValue(columnIndex);
		m_wasNull = (value == null);
		return value;
	}

	public final Object getObject(int columnIndex, Map map)
	throws SQLException
	{
		Object value = this.getObjectValue(columnIndex, map);
		m_wasNull = (value == null);
		return value;
	}

	public Ref getRef(int columnIndex)
	throws SQLException
	{
		return (Ref)this.getValue(columnIndex, Ref.class);
	}

	public short getShort(int columnIndex)
	throws SQLException
	{
		Number s = this.getNumber(columnIndex, short.class);
		return (s == null) ? 0 : s.shortValue();
	}

	public String getString(int columnIndex)
	throws SQLException
	{
		return (String)this.getValue(columnIndex, String.class);
	}

	public Time getTime(int columnIndex)
	throws SQLException
	{
		return (Time)this.getValue(columnIndex, Time.class);
	}

	public Time getTime(int columnIndex, Calendar cal)
	throws SQLException
	{
		return (Time)this.getValue(columnIndex, Time.class, cal);
	}

	public Timestamp getTimestamp(int columnIndex)
	throws SQLException
	{
		return (Timestamp)this.getValue(columnIndex, Timestamp.class);
	}

	public Timestamp getTimestamp(int columnIndex, Calendar cal)
	throws SQLException
	{
		return (Timestamp)this.getValue(columnIndex, Timestamp.class, cal);
	}

	/**
	 * @deprecated
	 */
	public InputStream getUnicodeStream(int columnIndex)
	throws SQLException
	{
		throw new UnsupportedFeatureException("ResultSet.getUnicodeStream");
	}

	public URL getURL(int columnIndex) throws SQLException
	{
		return (URL)this.getValue(columnIndex, URL.class);
	}

	public SQLWarning getWarnings()
	throws SQLException
	{
		return null;
	}

	/**
	 * Refresh row is not yet implemented.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void refreshRow()
	throws SQLException
	{
		throw new UnsupportedFeatureException("Refresh row");
	}

	public void updateArray(int columnIndex, Array x) throws SQLException
	{
		this.updateObject(columnIndex, x);
	}

	public void updateAsciiStream(int columnIndex, InputStream x, int length)
	throws SQLException
	{
		try
		{
			this.updateObject(columnIndex,
					new ClobValue(new InputStreamReader(x, "US-ASCII"), length));
		}
		catch(UnsupportedEncodingException e)
		{
			throw new SQLException("US-ASCII encoding is not supported by this JVM");
		}
	}

	public void updateBigDecimal(int columnIndex, BigDecimal x)
	throws SQLException
	{
		this.updateObject(columnIndex, x);
	}

	public void updateBinaryStream(int columnIndex, InputStream x, int length)
	throws SQLException
	{
		this.updateBlob(columnIndex, new BlobValue(x, length));
	}

	public void updateBlob(int columnIndex, Blob x)
	throws SQLException
	{
		this.updateObject(columnIndex, x);
	}

	public void updateBoolean(int columnIndex, boolean x)
	throws SQLException
	{
		this.updateObject(columnIndex, x ? Boolean.TRUE : Boolean.FALSE);
	}

	public void updateByte(int columnIndex, byte x)
	throws SQLException
	{
		this.updateObject(columnIndex, new Byte(x));
	}

	public void updateBytes(int columnIndex, byte[] x)
	throws SQLException
	{
		this.updateObject(columnIndex, x);
	}

	public void updateCharacterStream(int columnIndex, Reader x, int length)
	throws SQLException
	{
		this.updateClob(columnIndex, new ClobValue(x, length));
	}

	public void updateClob(int columnIndex, Clob x)
	throws SQLException
	{
		this.updateObject(columnIndex, x);
	}

	public void updateDate(int columnIndex, Date x)
	throws SQLException
	{
		this.updateObject(columnIndex, x);
	}

	public void updateDouble(int columnIndex, double x)
	throws SQLException
	{
		this.updateObject(columnIndex, new Double(x));
	}

	public void updateFloat(int columnIndex, float x)
	throws SQLException
	{
		this.updateObject(columnIndex, new Float(x));
	}

	public void updateInt(int columnIndex, int x)
	throws SQLException
	{
		this.updateObject(columnIndex, new Integer(x));
	}

	public void updateLong(int columnIndex, long x)
	throws SQLException
	{
		this.updateObject(columnIndex, new Long(x));
	}

	public void updateNull(int columnIndex)
	throws SQLException
	{
		this.updateObject(columnIndex, null);
	}

	public void updateRef(int columnIndex, Ref x)
	throws SQLException
	{
		this.updateObject(columnIndex, x);
	}

	public void updateShort(int columnIndex, short x)
	throws SQLException
	{
		this.updateObject(columnIndex, new Short(x));
	}

	public void updateString(int columnIndex, String x)
	throws SQLException
	{
		this.updateObject(columnIndex, x);
	}

	public void updateTime(int columnIndex, Time x)
	throws SQLException
	{
		this.updateObject(columnIndex, x);
	}

	public void updateTimestamp(int columnIndex, Timestamp x)
	throws SQLException
	{
		this.updateObject(columnIndex, x);
	}

	public boolean wasNull()
	{
		return m_wasNull;
	}

	protected final Number getNumber(int columnIndex, Class cls)
	throws SQLException
	{
		Object value = this.getObjectValue(columnIndex);
		m_wasNull = (value == null);
		if(m_wasNull || value instanceof Number)
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

	protected final Object getValue(int columnIndex, Class cls)
	throws SQLException
	{
		Object value = this.getObject(columnIndex);
		if(value == null || cls.isInstance(value))
			return value;

		if(cls == String.class)
		{
			if(value instanceof Number
			|| value instanceof Boolean
			|| value instanceof Timestamp
			|| value instanceof Date
			|| value instanceof Time)
				return value.toString();
		}
		throw new SQLException("Cannot derive a value of class " +
				cls.getName() + " from an object of class " + value.getClass().getName());
	}

	protected Object getValue(int columnIndex, Class cls, Calendar cal)
	throws SQLException
	{
		Object value = this.getObject(columnIndex);
		if(value == null)
			return value;

		if(cls.isInstance(value))
			return value;

		if(cls == Timestamp.class)
		{
			if(value instanceof Date)
			{
				cal.setTime((Date)value);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.SECOND, 0);
				cal.set(Calendar.MILLISECOND, 0);
				return new Timestamp(cal.getTimeInMillis());
			}
			else if(value instanceof Time)
			{
				cal.setTime((Date)value);
				cal.set(1970, 0, 1);
				return new Timestamp(cal.getTimeInMillis());
			}
			else if(value instanceof String)
			{
				return Timestamp.valueOf((String)value);
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
				return new Date(cal.getTimeInMillis());
			}
			else if(value instanceof String)
			{
				return Date.valueOf((String)value);
			}
		}
		else if(cls == Time.class)
		{
			if(value instanceof Timestamp)
			{
				Timestamp ts = (Timestamp)value;
				cal.setTime(ts);
				cal.set(1970, 0, 1);
				return new Time(cal.getTimeInMillis());
			}
			else if(value instanceof String)
			{
				return Time.valueOf((String)value);
			}
		}
		throw new SQLException("Cannot derive a value of class " +
			cls.getName() + " from an object of class " + value.getClass().getName());
	}

	protected Object getObjectValue(int columnIndex, Map typeMap)
	throws SQLException
	{
		if(typeMap == null)
			return this.getObjectValue(columnIndex);
		throw new UnsupportedFeatureException("Obtaining values using explicit Map");
	}

	protected abstract Object getObjectValue(int columnIndex)
	throws SQLException;
}
