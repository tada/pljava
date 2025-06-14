/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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
import static java.nio.charset.StandardCharsets.US_ASCII;


/**
 * Implements most getters in terms of {@link #getValue}, {@link #getNumber},
 * or a few other {@code ResultSet} getters that are so implemented, tracks
 * {@link #wasNull wasNull}, and provides {@link #getObjectValue(int)} as the
 * chief method for subclasses to implement; turns most updaters into
 * {@link #updateObject(int,Object)}.
 * @author Thomas Hallgren
 */
public abstract class ObjectResultSet extends AbstractResultSet
{
	private boolean m_wasNull = false;

	/**
	 * Returns a private value updated by final methods in this class.
	 */
	@Override
	public boolean wasNull()
	{
		return m_wasNull;
	}

	/**
	 * This is a noop since warnings are not supported.
	 */
	@Override
	public void clearWarnings()
	throws SQLException
	{
	}

	/**
	 * Returns null if not overridden in a subclass.
	 */
	@Override
	public SQLWarning getWarnings()
	throws SQLException
	{
		return null;
	}

	/**
	 * Throws "unsupported" exception if not overridden in a subclass.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public ResultSetMetaData getMetaData()
	throws SQLException
	{
		throw new UnsupportedFeatureException(
			"ResultSet meta data is not yet implemented");
	}

	// ************************************************************
	// Pre-JDBC 4
	// Getters-by-columnIndex
	// ************************************************************

	/**
	 * Implemented over {@link #getValue getValue}.
	 */
	@Override
	public Array getArray(int columnIndex)
	throws SQLException
	{
		return getValue(columnIndex, Array.class);
	}

	/**
	 * Implemented over {@link #getClob(int) getClob}.
	 */
	@Override
	public InputStream getAsciiStream(int columnIndex)
	throws SQLException
	{
		Clob c = getClob(columnIndex);
		return (c == null) ? null : c.getAsciiStream();
	}

	/**
	 * Implemented over {@link #getValue getValue}.
	 */
	@Override
	public BigDecimal getBigDecimal(int columnIndex)
	throws SQLException
	{
		return getValue(columnIndex, BigDecimal.class);
	}

	/**
	 * Throws "unsupported" exception.
	 */
	@SuppressWarnings("deprecation") @Override
	public BigDecimal getBigDecimal(int columnIndex, int scale)
	throws SQLException
	{
		throw new UnsupportedFeatureException("getBigDecimal(int, int)");
	}

	/**
	 * Implemented over {@link #getBlob(int) getBlob}.
	 */
	@Override
	public InputStream getBinaryStream(int columnIndex)
	throws SQLException
	{
		Blob b = getBlob(columnIndex);
		return (b == null) ? null : b.getBinaryStream();
	}

	/**
	 * Implemented over {@link #getBytes(int) getBytes}.
	 */
	@Override
	public Blob getBlob(int columnIndex)
	throws SQLException
	{
		byte[] bytes = getBytes(columnIndex);
		return (bytes == null) ? null :  new BlobValue(bytes);
	}

	/**
	 * Implemented over {@link #getValue getValue}.
	 */
	@Override
	public boolean getBoolean(int columnIndex)
	throws SQLException
	{
		Boolean b = getValue(columnIndex, Boolean.class);
		return (b == null) ? false : b.booleanValue();
	}

	/**
	 * Implemented over {@link #getNumber getNumber}.
	 */
	@Override
	public byte getByte(int columnIndex)
	throws SQLException
	{
		Number b = getNumber(columnIndex, byte.class);
		return (b == null) ? 0 : b.byteValue();
	}

	/**
	 * Implemented over {@link #getValue getValue}.
	 */
	@Override
	public byte[] getBytes(int columnIndex)
	throws SQLException
	{
		return getValue(columnIndex, byte[].class);
	}

	/**
	 * Implemented over {@link #getClob(int) getClob}.
	 */
	@Override
	public Reader getCharacterStream(int columnIndex)
	throws SQLException
	{
		Clob c = getClob(columnIndex);
		return (c == null) ? null : c.getCharacterStream();
	}

	/**
	 * Implemented over {@link #getString(int) getString}.
	 */
	@Override
	public Clob getClob(int columnIndex)
	throws SQLException
	{
		String str = getString(columnIndex);
		return (str == null) ? null :  new ClobValue(str);
	}
	
	/**
	 * Implemented over {@link #getValue getValue}.
	 */
	@Override
	public Date getDate(int columnIndex)
	throws SQLException
	{
		return getValue(columnIndex, Date.class);
	}

	/**
	 * Implemented over {@link #getValue(int,Class,Calendar) getValue}.
	 */
	@Override
	public Date getDate(int columnIndex, Calendar cal)
	throws SQLException
	{
		return getValue(columnIndex, Date.class, cal);
	}

	/**
	 * Implemented over {@link #getNumber getNumber}.
	 */
	@Override
	public double getDouble(int columnIndex)
	throws SQLException
	{
		Number d = getNumber(columnIndex, double.class);
		return (d == null) ? 0 : d.doubleValue();
	}

	/**
	 * Implemented over {@link #getNumber getNumber}.
	 */
	@Override
	public float getFloat(int columnIndex)
	throws SQLException
	{
		Number f = getNumber(columnIndex, float.class);
		return (f == null) ? 0 : f.floatValue();
	}

	/**
	 * Implemented over {@link #getNumber getNumber}.
	 */
	@Override
	public int getInt(int columnIndex)
	throws SQLException
	{
		Number i = getNumber(columnIndex, int.class);
		return (i == null) ? 0 : i.intValue();
	}

	/**
	 * Implemented over {@link #getNumber getNumber}.
	 */
	@Override
	public long getLong(int columnIndex)
	throws SQLException
	{
		Number l = getNumber(columnIndex, long.class);
		return (l == null) ? 0 : l.longValue();
	}

	/**
	 * Implemented over {@link #getObjectValue(int) getObjectValue}.
	 * Final because it records {@code wasNull} for use by other methods.
	 */
	@Override
	public final Object getObject(int columnIndex)
	throws SQLException
	{
		Object value = getObjectValue(columnIndex);
		m_wasNull = (value == null);
		return value;
	}

	/**
	 * Implemented over {@link #getObjectValue(int,Map) getObjectValue}.
	 * Final because it records {@code wasNull} for use by other methods.
	 */
	@Override
	public final Object getObject(int columnIndex, Map map)
	throws SQLException
	{
		Object value = getObjectValue(columnIndex, map);
		m_wasNull = (value == null);
		return value;
	}

	/**
	 * Implemented over {@link #getValue getValue}.
	 */
	@Override
	public Ref getRef(int columnIndex)
	throws SQLException
	{
		return getValue(columnIndex, Ref.class);
	}

	/**
	 * Implemented over {@link #getNumber getNumber}.
	 */
	@Override
	public short getShort(int columnIndex)
	throws SQLException
	{
		Number s = getNumber(columnIndex, short.class);
		return (s == null) ? 0 : s.shortValue();
	}

	/**
	 * Implemented over {@link #getValue getValue}.
	 */
	@Override
	public String getString(int columnIndex)
	throws SQLException
	{
		return getValue(columnIndex, String.class);
	}

	/**
	 * Implemented over {@link #getValue getValue}.
	 */
	@Override
	public Time getTime(int columnIndex)
	throws SQLException
	{
		return getValue(columnIndex, Time.class);
	}

	/**
	 * Implemented over {@link #getValue(int,Class,Calendar) getValue}.
	 */
	@Override
	public Time getTime(int columnIndex, Calendar cal)
	throws SQLException
	{
		return getValue(columnIndex, Time.class, cal);
	}

	/**
	 * Implemented over {@link #getValue getValue}.
	 */
	@Override
	public Timestamp getTimestamp(int columnIndex)
	throws SQLException
	{
		return getValue(columnIndex, Timestamp.class);
	}

	/**
	 * Implemented over {@link #getValue(int,Class,Calendar) getValue}.
	 */
	@Override
	public Timestamp getTimestamp(int columnIndex, Calendar cal)
	throws SQLException
	{
		return getValue(columnIndex, Timestamp.class, cal);
	}

	/**
	 * Throws "unsupported" exception.
	 */
	@SuppressWarnings("deprecation") @Override
	public InputStream getUnicodeStream(int columnIndex)
	throws SQLException
	{
		throw new UnsupportedFeatureException("ResultSet.getUnicodeStream");
	}

	/**
	 * Implemented over {@link #getValue getValue}.
	 */
	@Override
	public URL getURL(int columnIndex) throws SQLException
	{
		return getValue(columnIndex, URL.class);
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

	// ************************************************************
	// Pre-JDBC 4
	// Updaters-by-columnIndex
	// ************************************************************

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateArray(int columnIndex, Array x) throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link ClobValue} and
	 * {@link #updateObject updateObject}.
	 */
	@Override
	public void updateAsciiStream(int columnIndex, InputStream x, int length)
	throws SQLException
	{
		updateObject(columnIndex,
			new ClobValue(new InputStreamReader(x, US_ASCII), length));
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateBigDecimal(int columnIndex, BigDecimal x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link BlobValue} and
	 * {@link #updateBlob updateBlob}.
	 */
	@Override
	public void updateBinaryStream(int columnIndex, InputStream x, int length)
	throws SQLException
	{
		updateBlob(columnIndex, (Blob) new BlobValue(x, length));
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateBlob(int columnIndex, Blob x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateBoolean(int columnIndex, boolean x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateByte(int columnIndex, byte x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateBytes(int columnIndex, byte[] x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link ClobValue} and
	 * {@link #updateClob updateClob}.
	 */
	@Override
	public void updateCharacterStream(int columnIndex, Reader x, int length)
	throws SQLException
	{
		updateClob(columnIndex, (Clob) new ClobValue(x, length));
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateClob(int columnIndex, Clob x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateDate(int columnIndex, Date x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateDouble(int columnIndex, double x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateFloat(int columnIndex, float x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateInt(int columnIndex, int x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateLong(int columnIndex, long x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateNull(int columnIndex)
	throws SQLException
	{
		updateObject(columnIndex, null);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateRef(int columnIndex, Ref x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateShort(int columnIndex, short x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateString(int columnIndex, String x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateTime(int columnIndex, Time x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	/**
	 * Implemented over {@link #updateObject updateObject}.
	 */
	@Override
	public void updateTimestamp(int columnIndex, Timestamp x)
	throws SQLException
	{
		updateObject(columnIndex, x);
	}

	// ************************************************************
	// JDBC 4.1
	// Getter-by-columnIndex
	// ************************************************************

	/**
	 * Implemented over {@link #getObjectValue(int,Class) getObjectValue}.
	 * Final because it records {@code wasNull} for use by other methods.
	 */
	@Override
	public final <T> T getObject(int columnIndex, Class<T> type)
	throws SQLException
	{
		Object value = getObjectValue(columnIndex, type);
		m_wasNull = (value == null);
		if ( m_wasNull  ||  type.isInstance(value) )
			return type.cast(value);
		throw new SQLException("Cannot convert " + value.getClass().getName() +
			" to " + type.getName());
	}

	// ************************************************************
	// Implementation methods
	// ************************************************************

	/**
	 * Implemented over {@link #getObjectValue}, tracks {@code wasNull},
	 * applies {@link SPIConnection#basicNumericCoercion} to {@code cls}.
	 */
	protected final Number getNumber(int columnIndex, Class cls)
	throws SQLException
	{
		Object value = getObjectValue(columnIndex);
		m_wasNull = (value == null);
		return SPIConnection.basicNumericCoercion(cls, value);
	}

	/**
	 * Implemented over {@link #getObject},
	 * applies {@link SPIConnection#basicCoercion} to {@code cls}.
	 */
	protected final <T> T getValue(int columnIndex, Class<T> cls)
	throws SQLException
	{
		return SPIConnection.basicCoercion(cls, getObject(columnIndex));
	}

	/**
	 * Implemented over {@link #getObject},
	 * applies {@link SPIConnection#basicCalendricalCoercion} to {@code cls}.
	 */
	protected <T> T getValue(int columnIndex, Class<T> cls, Calendar cal)
	throws SQLException
	{
		return SPIConnection.basicCalendricalCoercion(cls,
			getObject(columnIndex), cal);
	}

	/**
	 * Implemented over {@link #getObjectValue(int)}, complains if
	 * {@code typeMap} is non-null.
	 */
	protected Object getObjectValue(int columnIndex, Map typeMap)
	throws SQLException
	{
		if(typeMap == null)
			return getObjectValue(columnIndex);
		throw new UnsupportedFeatureException(
			"Obtaining values using explicit Map");
	}

	/**
	 * Implemented over {@link #getObjectValue(int,Class)}, passing null for
	 * the class.
	 *<p>
	 * To preserve back-compatible behavior in the 1.5.x branch, this is still
	 * what ends up getting called in all cases that do not explicitly use the
	 * JDBC 4.1 new {@link #getObject(int,Class)}.
	 */
	protected Object getObjectValue(int columnIndex)
	throws SQLException
	{
		return getObjectValue(columnIndex, (Class<?>)null);
	}

	/**
	 * Primary method for subclass to override to retrieve a value.
	 *<p>
	 * The signature does not constrain this to return an object of the
	 * requested class, so it can still be used as before by methods that may do
	 * additional coercions. When called by {@link #getObject(int,Class)}, that
	 * caller enforces the class of the result.
	 */
	protected abstract Object getObjectValue(int columnIndex, Class<?> type)
	throws SQLException;
}
