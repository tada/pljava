/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 * Copyright (c) 2010, 2011 PostgreSQL Global Development Group
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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLOutput;
import java.sql.SQLXML;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;

import org.postgresql.pljava.internal.Tuple;
import org.postgresql.pljava.internal.TupleDesc;

/**
 * Implementation of {@link SQLOutput} for the case of a composite data type.
 * @author Thomas Hallgren
 */
public class SQLOutputToTuple implements SQLOutput
{
	private final Object[] m_values;
	private final TupleDesc m_tupleDesc;
	private int m_index;
	private Tuple m_tuple;

	public SQLOutputToTuple(TupleDesc tupleDesc)
	{
		m_tupleDesc = tupleDesc;
		m_values = new Object[tupleDesc.size()];
		m_index = 0;
	}

	/**
	 * Creates a tuple from the written values and returns its native pointer.
	 * All values must have been written. This method is called automatically by
	 * the trigger handler and should not be called in any other way.
	 * 
	 * @return The Tuple reflecting the current row values.
	 * @throws SQLException
	 */
	public long getTuple()
	throws SQLException
	{
		if(m_tuple != null)
			return m_tuple.getNativePointer();

		if(m_index < m_values.length)
			throw new SQLException("Too few values have been written");

		m_tuple = m_tupleDesc.formTuple(m_values);
		return m_tuple.getNativePointer();
	}

	public void writeArray(Array value) throws SQLException
	{
		writeValue(value);
	}

	public void writeAsciiStream(InputStream value) throws SQLException
	{
		Reader rdr = new BufferedReader(new InputStreamReader(value, US_ASCII));
		writeClob(new ClobValue(rdr, ClobValue.getReaderLength(rdr)));
	}

	public void writeBigDecimal(BigDecimal value) throws SQLException
	{
		writeValue(value);
	}

	public void writeBinaryStream(InputStream value) throws SQLException
	{
		if(!value.markSupported())
			value = new BufferedInputStream(value);
		writeBlob(new BlobValue(value, BlobValue.getStreamLength(value)));
	}

	public void writeBlob(Blob value) throws SQLException
	{
		writeValue(value);
	}

	public void writeBoolean(boolean value) throws SQLException
	{
		writeValue(value);
	}

	public void writeByte(byte value) throws SQLException
	{
		writeValue(value);
	}

	public void writeBytes(byte[] value) throws SQLException
	{
		writeValue(value);
	}

	public void writeCharacterStream(Reader value) throws SQLException
	{
		if(!value.markSupported())
			value = new BufferedReader(value);
		writeClob(new ClobValue(value, ClobValue.getReaderLength(value)));
	}

	public void writeClob(Clob value) throws SQLException
	{
		writeValue(value);
	}

	public void writeDate(Date value) throws SQLException
	{
		writeValue(value);
	}

	public void writeDouble(double value) throws SQLException
	{
		writeValue(value);
	}

	public void writeFloat(float value) throws SQLException
	{
		writeValue(value);
	}

	public void writeInt(int value) throws SQLException
	{
		writeValue(value);
	}

	public void writeLong(long value) throws SQLException
	{
		writeValue(value);
	}

	public void writeObject(SQLData value) throws SQLException
	{
		writeValue(value);
	}

	public void writeRef(Ref value) throws SQLException
	{
		writeValue(value);
	}

	public void writeShort(short value) throws SQLException
	{
		writeValue(value);
	}

	public void writeString(String value) throws SQLException
	{
		writeValue(value);
	}

	public void writeStruct(Struct value) throws SQLException
	{
		writeValue(value);
	}

	public void writeTime(Time value) throws SQLException
	{
		writeValue(value);
	}

	public void writeTimestamp(Timestamp value) throws SQLException
	{
		writeValue(value);
	}

	public void writeURL(URL value) throws SQLException
	{
		writeValue(value.toString());
	}

	// ************************************************************
	// Implementation of JDBC 4 methods. Methods go here if they
	// don't throw SQLFeatureNotSupportedException; they can be
	// considered implemented even if they do nothing useful, as
	// long as that's an allowed behavior by the JDBC spec.
	// ************************************************************

	public void writeSQLXML(SQLXML x)
		throws SQLException
	{
		writeValue(x);
	}

	// ************************************************************
	// Non-implementation of JDBC 4 methods.
	// ************************************************************

	public void writeNClob(NClob x)
                throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".writeNClob( NClob ) not implemented yet.",
			  "0A000" );
	}
	
	public void writeNString(String x)
		throws SQLException
		{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".writeNString( String ) not implemented yet.",
		  "0A000" );
		}

	public void writeRowId(RowId x)
                throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".writeRowId( RowId ) not implemented yet.",
			  "0A000" );
	}

	// ************************************************************
	// End of non-implementation of JDBC 4 methods.
	// ************************************************************

	private void writeValue(Object value) throws SQLException
	{
		if(m_index >= m_values.length)
			throw new SQLException("Tuple cannot take more values");
		TypeBridge<?>.Holder vAlt = TypeBridge.wrap(value);
		m_values[m_index++] = null == vAlt ? value : vAlt;
	}
}
