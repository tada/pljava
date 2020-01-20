/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
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
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLNonTransientException;
import java.sql.SQLInput;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;

import org.postgresql.pljava.internal.Backend;
import org.postgresql.pljava.internal.DualState;
import org.postgresql.pljava.internal.TupleDesc;

/**
 * Implements the {@code SQLInput} interface for a user-defined type (UDT)
 * implemented in Java, for the case where a composite type in PostgreSQL is
 * used as the UDT's representation, so it can be accessed as a PG tuple.
 *
 * @author Thomas Hallgren
 */
public class SQLInputFromTuple extends SingleRowReader implements SQLInput
{
	private int m_index;
	private final int m_columns;

	/**
	 * Construct an instance, given the (native) pointer to a PG
	 * {@code HeapTupleHeader}, as well as the TupleDesc (Java object this time)
	 * describing its structure.
	 */
	public SQLInputFromTuple(DualState.Key cookie, long resourceOwner,
		long heapTupleHeaderPointer, TupleDesc tupleDesc)
	throws SQLException
	{
		super(cookie, resourceOwner, heapTupleHeaderPointer, tupleDesc);
		m_index = 0;
		m_columns = tupleDesc.size();
	}

	protected int nextIndex() throws SQLException
	{
		if ( m_index >= m_columns )
			throw new SQLNonTransientException("Tuple has no more columns");
		return ++m_index;
	}

	/**
	 * Implemented over {@link #readValue}.
	 */
	@Override
	public Array readArray() throws SQLException
	{
		return readValue(Array.class);
	}

	/**
	 * Implemented over {@link #readClob}.
	 */
	@Override
	public InputStream readAsciiStream() throws SQLException
	{
		Clob c = readClob();
		return (c == null) ? null : c.getAsciiStream();
	}

	/**
	 * Implemented over {@link #readValue}.
	 */
	@Override
	public BigDecimal readBigDecimal() throws SQLException
	{
		return readValue(BigDecimal.class);
	}

	/**
	 * Implemented over {@link #readBlob}.
	 */
	@Override
	public InputStream readBinaryStream() throws SQLException
	{
		Blob b = readBlob();
		return (b == null) ? null : b.getBinaryStream();
	}

	/**
	 * Implemented over {@link #readBytes}.
	 */
	@Override
	public Blob readBlob() throws SQLException
	{
		byte[] bytes = readBytes();
		return (bytes == null) ? null :  new BlobValue(bytes);
	}

	/**
	 * Implemented over {@link #readValue}.
	 */
	@Override
	public boolean readBoolean() throws SQLException
	{
		Boolean b = readValue(Boolean.class);
		return (b == null) ? false : b.booleanValue();
	}

	/**
	 * Implemented over {@link #readNumber}.
	 */
	@Override
	public byte readByte() throws SQLException
	{
		Number b = readNumber(byte.class);
		return (b == null) ? 0 : b.byteValue();
	}

	/**
	 * Implemented over {@link #readValue}.
	 */
	@Override
	public byte[] readBytes() throws SQLException
	{
		return readValue(byte[].class);
	}

	/**
	 * Implemented over {@link #readClob}.
	 */
	public Reader readCharacterStream() throws SQLException
	{
		Clob c = readClob();
		return (c == null) ? null : c.getCharacterStream();
	}

	/**
	 * Implemented over {@link #readString}.
	 */
	public Clob readClob() throws SQLException
	{
		String str = readString();
		return (str == null) ? null :  new ClobValue(str);
	}

	/**
	 * Implemented over {@link #readValue}.
	 */
	@Override
	public Date readDate() throws SQLException
	{
		return readValue(Date.class);
	}

	/**
	 * Implemented over {@link #readNumber}.
	 */
	@Override
	public double readDouble() throws SQLException
	{
		Number d = readNumber(double.class);
		return (d == null) ? 0 : d.doubleValue();
	}

	/**
	 * Implemented over {@link #readNumber}.
	 */
	@Override
	public float readFloat() throws SQLException
	{
		Number f = readNumber(float.class);
		return (f == null) ? 0 : f.floatValue();
	}

	/**
	 * Implemented over {@link #readNumber}.
	 */
	@Override
	public int readInt() throws SQLException
	{
		Number i = readNumber(int.class);
		return (i == null) ? 0 : i.intValue();
	}

	/**
	 * Implemented over {@link #readNumber}.
	 */
	@Override
	public long readLong() throws SQLException
	{
		Number l = readNumber(long.class);
		return (l == null) ? 0 : l.longValue();
	}

	@Override
	public Object readObject() throws SQLException
	{
		return getObject(nextIndex());
	}

	/**
	 * Implemented over {@link #readValue}.
	 */
	@Override
	public Ref readRef() throws SQLException
	{
		return readValue(Ref.class);
	}

	/**
	 * Implemented over {@link #readNumber}.
	 */
	@Override
	public short readShort() throws SQLException
	{
		Number s = readNumber(short.class);
		return (s == null) ? 0 : s.shortValue();
	}

	/**
	 * Implemented over {@link #readValue}.
	 */
	@Override
	public String readString() throws SQLException
	{
		return readValue(String.class);
	}

	/**
	 * Implemented over {@link #readValue}.
	 */
	@Override
	public Time readTime() throws SQLException
	{
		return readValue(Time.class);
	}

	/**
	 * Implemented over {@link #readValue}.
	 */
	@Override
	public Timestamp readTimestamp() throws SQLException
	{
		return readValue(Timestamp.class);
	}

	/**
	 * Implemented over {@link #readValue}.
	 */
	@Override
	public URL readURL() throws SQLException
	{
		return readValue(URL.class);
	}

	// ************************************************************
	// Implementation of JDBC 4 methods. Methods go here if they
	// don't throw SQLFeatureNotSupportedException; they can be
	// considered implemented even if they do nothing useful, as
	// long as that's an allowed behavior by the JDBC spec.
	// ************************************************************

	@Override
	public SQLXML readSQLXML()
		throws SQLException
	{
		return readObject(SQLXML.class);
	}

	// ************************************************************
	// Non-implementation of JDBC 4 methods.
	// ************************************************************

	/** Not yet implemented. */
	@Override
	public RowId readRowId()
                throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".readRowId() not implemented yet.",
			  "0A000" );
	}

	/** Not yet implemented. */
	@Override
	public String readNString()
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".readNString() not implemented yet.",
			  "0A000" );
		
	}
	
	/** Not yet implemented. */
	@Override
	public NClob readNClob()
	       throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".readNClob() not implemented yet.",
		  "0A000" );
		
	}

	// ************************************************************
	// Implementation of JDBC 4.2 method.
	// ************************************************************

	@Override
	public <T> T readObject(Class<T> type) throws SQLException
	{
		return getObject(nextIndex(), type);
	}

	// ************************************************************
	// Implementation methods, over methods of ObjectResultSet.
	// ************************************************************

	private Number readNumber(Class numberClass) throws SQLException
	{
		return getNumber(nextIndex(), numberClass);
	}

	private <T> T readValue(Class<T> valueClass) throws SQLException
	{
		return getValue(nextIndex(), valueClass);
	}
}
