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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * The {@code AbstractResultSet} serves as a base class for implementations
 * of the {@link java.sql.ResultSet} interface. All calls using columnNames are
 * translated into the corresponding call with index position computed using
 * a call to {@link java.sql.ResultSet#findColumn(String) findColumn}.
 *
 * @author Thomas Hallgren
 */
public abstract class AbstractResultSet implements ResultSet
{
	// ************************************************************
	// Pre-JDBC 4
	// Getters-by-columnName mapped to getters-by-columnIndex
	// ************************************************************

	@Override
	public Array getArray(String columnName)
	throws SQLException
	{
		return this.getArray(this.findColumn(columnName));
	}

	@Override
	public InputStream getAsciiStream(String columnName)
	throws SQLException
	{
		return this.getAsciiStream(this.findColumn(columnName));
	}

	@Override
	public BigDecimal getBigDecimal(String columnName)
	throws SQLException
	{
		return this.getBigDecimal(this.findColumn(columnName));
	}

	@SuppressWarnings("deprecation") @Override
	public BigDecimal getBigDecimal(String columnName, int scale)
	throws SQLException
	{
		return this.getBigDecimal(this.findColumn(columnName), scale);
	}

	@Override
	public InputStream getBinaryStream(String columnName)
	throws SQLException
	{
		return this.getBinaryStream(this.findColumn(columnName));
	}

	@Override
	public Blob getBlob(String columnName)
	throws SQLException
	{
		return this.getBlob(this.findColumn(columnName));
	}

	@Override
	public boolean getBoolean(String columnName)
	throws SQLException
	{
		return this.getBoolean(this.findColumn(columnName));
	}

	@Override
	public byte getByte(String columnName)
	throws SQLException
	{
		return this.getByte(this.findColumn(columnName));
	}

	@Override
	public byte[] getBytes(String columnName)
	throws SQLException
	{
		return this.getBytes(this.findColumn(columnName));
	}

	@Override
	public Reader getCharacterStream(String columnName)
	throws SQLException
	{
		return this.getCharacterStream(this.findColumn(columnName));
	}

	@Override
	public Clob getClob(String columnName)
	throws SQLException
	{
		return this.getClob(this.findColumn(columnName));
	}

	@Override
	public Date getDate(String columnName)
	throws SQLException
	{
		return this.getDate(this.findColumn(columnName));
	}

	@Override
	public Date getDate(String columnName, Calendar cal)
	throws SQLException
	{
		return this.getDate(this.findColumn(columnName), cal);
	}

	@Override
	public double getDouble(String columnName)
	throws SQLException
	{
		return this.getDouble(this.findColumn(columnName));
	}

	@Override
	public float getFloat(String columnName)
	throws SQLException
	{
		return this.getFloat(this.findColumn(columnName));
	}

	@Override
	public int getInt(String columnName)
	throws SQLException
	{
		return this.getInt(this.findColumn(columnName));
	}

	@Override
	public long getLong(String columnName)
	throws SQLException
	{
		return this.getLong(this.findColumn(columnName));
	}

	@Override
	public Object getObject(String columnName)
	throws SQLException
	{
		return this.getObject(this.findColumn(columnName));
	}

	@Override
	public Object getObject(String columnName, Map<String,Class<?>> map)
	throws SQLException
	{
		return this.getObject(this.findColumn(columnName), map);
	}

	@Override
	public Ref getRef(String columnName)
	throws SQLException
	{
		return this.getRef(this.findColumn(columnName));
	}

	@Override
	public short getShort(String columnName)
	throws SQLException
	{
		return this.getShort(this.findColumn(columnName));
	}

	@Override
	public String getString(String columnName)
	throws SQLException
	{
		return this.getString(this.findColumn(columnName));
	}

	@Override
	public Time getTime(String columnName)
	throws SQLException
	{
		return this.getTime(this.findColumn(columnName));
	}

	@Override
	public Time getTime(String columnName, Calendar cal)
	throws SQLException
	{
		return this.getTime(this.findColumn(columnName), cal);
	}

	@Override
	public Timestamp getTimestamp(String columnName)
	throws SQLException
	{
		return this.getTimestamp(this.findColumn(columnName));
	}

	@Override
	public Timestamp getTimestamp(String columnName, Calendar cal)
	throws SQLException
	{
		return this.getTimestamp(this.findColumn(columnName), cal);
	}

	@SuppressWarnings("deprecation") @Override
	public InputStream getUnicodeStream(String columnName)
	throws SQLException
	{
		return this.getUnicodeStream(this.findColumn(columnName));
	}

	@Override
	public URL getURL(String columnName)
	throws SQLException
	{
		return this.getURL(this.findColumn(columnName));
	}

	// ************************************************************
	// Pre-JDBC 4
	// Updaters-by-columnName mapped to updaters-by-columnIndex
	// ************************************************************

	@Override
	public void updateArray(String columnName, Array x)
	throws SQLException
	{
		this.updateArray(this.findColumn(columnName), x);
	}

	@Override
	public void updateAsciiStream(String columnName, InputStream x, int length)
	throws SQLException
	{
		this.updateAsciiStream(this.findColumn(columnName), x, length);
	}

	@Override
	public void updateBigDecimal(String columnName, BigDecimal x)
	throws SQLException
	{
		this.updateBigDecimal(this.findColumn(columnName), x);
	}

	@Override
	public void updateBinaryStream(String columnName, InputStream x, int length)
	throws SQLException
	{
		this.updateBinaryStream(this.findColumn(columnName), x, length);
	}

	@Override
	public void updateBlob(String columnName, Blob x)
	throws SQLException
	{
		this.updateBlob(this.findColumn(columnName), x);
	}

	@Override
	public void updateBoolean(String columnName, boolean x)
	throws SQLException
	{
		this.updateBoolean(this.findColumn(columnName), x);
	}

	@Override
	public void updateByte(String columnName, byte x)
	throws SQLException
	{
		this.updateByte(this.findColumn(columnName), x);
	}

	@Override
	public void updateBytes(String columnName, byte x[])
	throws SQLException
	{
		this.updateBytes(this.findColumn(columnName), x);
	}

	@Override
	public void updateCharacterStream(String columnName, Reader x, int length)
	throws SQLException
	{
		this.updateCharacterStream(this.findColumn(columnName), x, length);
	}

	@Override
	public void updateClob(String columnName, Clob x)
	throws SQLException
	{
		this.updateClob(this.findColumn(columnName), x);
	}

	@Override
	public void updateDate(String columnName, Date x)
	throws SQLException
	{
		this.updateDate(this.findColumn(columnName), x);
	}

	@Override
	public void updateDouble(String columnName, double x)
	throws SQLException
	{
		this.updateDouble(this.findColumn(columnName), x);
	}

	@Override
	public void updateFloat(String columnName, float x)
	throws SQLException
	{
		this.updateFloat(this.findColumn(columnName), x);
	}

	@Override
	public void updateInt(String columnName, int x)
	throws SQLException
	{
		this.updateInt(this.findColumn(columnName), x);
	}

	@Override
	public void updateLong(String columnName, long x)
	throws SQLException
	{
		this.updateLong(this.findColumn(columnName), x);
	}

	@Override
	public void updateNull(String columnName)
	throws SQLException
	{
		this.updateNull(this.findColumn(columnName));
	}

	@Override
	public void updateObject(String columnName, Object x)
	throws SQLException
	{
		this.updateObject(this.findColumn(columnName), x);
	}

	@Override
	public void updateObject(String columnName, Object x, int scale)
	throws SQLException
	{
		this.updateObject(this.findColumn(columnName), x, scale);
	}

	@Override
	public void updateRef(String columnName, Ref x)
	throws SQLException
	{
		this.updateRef(this.findColumn(columnName), x);
	}

	@Override
	public void updateShort(String columnName, short x)
	throws SQLException
	{
		this.updateShort(this.findColumn(columnName), x);
	}

	@Override
	public void updateString(String columnName, String x)
	throws SQLException
	{
		this.updateString(this.findColumn(columnName), x);
	}

	@Override
	public void updateTime(String columnName, Time x)
	throws SQLException
	{
		this.updateTime(this.findColumn(columnName), x);
	}

	@Override
	public void updateTimestamp(String columnName, Timestamp x)
	throws SQLException
	{
		this.updateTimestamp(this.findColumn(columnName), x);
	}

	// ************************************************************
	// Pre-JDBC 4
	// Trivial default implementations for some methods inquiring
	// ResultSet status.
	// ************************************************************

	/**
	 * Returns null if not overridden in a subclass.
	 */
	@Override
	public String getCursorName()
	throws SQLException
	{
		return null;
	}

	/**
	 * Returns null if not overridden in a subclass.
	 */
	@Override
	public Statement getStatement()
	throws SQLException
	{
		return null;
	}

	// ************************************************************
	// Implementation of JDBC 4 methods. Methods go here if they
	// don't throw SQLFeatureNotSupportedException; they can be
	// considered implemented even if they do nothing useful, as
	// long as that's an allowed behavior by the JDBC spec.
	// ************************************************************

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

	@Override
	public SQLXML getSQLXML(int columnIndex)
	throws SQLException
	{
		return getObject(columnIndex, SQLXML.class);
	}

	@Override
	public SQLXML getSQLXML(String columnLabel)
	throws SQLException
	{
		return getObject(columnLabel, SQLXML.class);
	}

	@Override
	public void updateSQLXML(int columnIndex, SQLXML xmlObject)
	throws SQLException
	{
		updateObject(columnIndex, xmlObject);
	}

	@Override
	public void updateSQLXML(String columnLabel, SQLXML xmlObject)
	throws SQLException
	{
		updateObject(columnLabel, xmlObject);
	}

	// ************************************************************
	// Non-implementation of JDBC 4 get methods.
	// ************************************************************

	@Override
	public Reader getNCharacterStream(String columnLabel)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".getNCharacterStream( String ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public Reader getNCharacterStream(int columnIndex)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".gett( int ) not implemented yet.", "0A000" );
	}

	@Override
	public NClob getNClob(String columnLabel)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".getNClob( String ) not implemented yet.", "0A000" );
	}

	@Override
	public NClob getNClob(int columnIndex)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".getNClob( int ) not implemented yet.", "0A000" );
	}

	@Override
	public String getNString(String columnLabel)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".getNString( String ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public String getNString(int columnIndex)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".getNString( int ) not implemented yet.", "0A000" );
	}

	@Override
	public RowId getRowId(String columnLabel)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".getRowId( String ) not implemented yet.", "0A000" );
	}

	@Override
	public RowId getRowId(int columnIndex)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			"getRowId( int ) not implemented yet.", "0A000" );
	}

	// ************************************************************
	// Non-implementation of JDBC 4 update methods.
	// ************************************************************

	@Override
	public void updateAsciiStream(String columnLabel, InputStream x)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateAsciiStream( String, InputStream ) not implemented yet.", "0A000" );
	}

	@Override
	public void updateAsciiStream(String columnLabel, InputStream x, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateAsciiStream( String, InputStream, long ) not implemented yet.", "0A000" );
	}

	@Override
	public void updateAsciiStream(int columnIndex, InputStream x)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateAsciiStream( int, InputStream ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateAsciiStream(int columnIndex, InputStream x, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateAsciiStream( int, InputStream, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateBinaryStream(String columnLabel, InputStream x)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateBinaryStream( String, InputStream ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateBinaryStream(String columnLabel, InputStream x, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateBinaryStream( String, InputStream, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateBinaryStream(int columnIndex, InputStream x)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateBinaryStream( int, InputStream ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateBinaryStream(int columnIndex, InputStream x, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateBinaryStream( int, InputStream, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateBlob(String columnLabel, InputStream inputStream)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateBlob( String, InputStream ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateBlob(String columnLabel, InputStream inputStream, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateBlob( String, InputStream, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateBlob(int columnIndex, InputStream inputStream)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateBlob( int, InputStream ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateBlob(int columnIndex, InputStream inputStream, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateBlob( int, InputStream, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateCharacterStream(String ColumnLabel, Reader x)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateCharacterStream( String, Reader ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateCharacterStream(String ColumnLabel, Reader x, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateCharacterStream( String, Reader, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateCharacterStream(int columnIndex, Reader x)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateCharacterStream( int, Reader ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateCharacterStream(int columnIndex, Reader x, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateCharacterStream( int, Reader, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateClob(String columnLabel, Reader reader)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateClob( String, Reader ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateClob(String columnLabel, Reader reader, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateClob( String, Reader, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateClob(int columnIndex, Reader reader)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateClob( int, Reader ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateClob(int columnIndex, Reader reader, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateClob( int, Reader, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNCharacterStream(String columnLabel, Reader reader)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNCharacterStream( String, Reader ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNCharacterStream(String columnLabel, Reader reader, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNCharacterStream( String, Reader, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNCharacterStream(int columnIndex, Reader reader)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNCharacterStream( int, Reader ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNCharacterStream(int columnIndex, Reader reader, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNCharaterStream( int, Reader, long] ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNClob(String columnLabel, NClob nClob)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNClob( String, NClob ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNClob(String columnLabel, Reader reader)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNClob( String, Reader ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNClob(String columnLabel, Reader reader, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNClob( String, Reader, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNClob(int columnIndex, NClob nClob)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNClob( int, NClob ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNClob(int columnIndex, Reader reader)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNClob( int, Reader ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNClob(int columnIndex, Reader reader, long length)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNClob( int, Reader, long ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNString(String columnLabel, String nString)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNString( String, String ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateNString(int columnIndex, String nString)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateNString( String, Object[] ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateRowId(String columnLabel, RowId x)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateRowId( String, RowId ) not implemented yet.",
							   "0A000" );
	}

	@Override
	public void updateRowId(int columnIndex, RowId x)
	throws SQLException
	{
		throw new SQLFeatureNotSupportedException( this.getClass() +
			".updateRowId( int, RowId ) not implemented yet.",
							   "0A000" );
	}

	// ************************************************************
	// Implementation of JDBC 4.1 methods.
	// ************************************************************

	@Override
	public <T> T getObject(String columnName, Class<T> type)
	throws SQLException
	{
		return this.getObject(this.findColumn(columnName), type);
	}
}
