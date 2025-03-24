/*
 * Copyright (c) 2004-2025 TADA AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLNonTransientException;
import java.sql.SQLOutput;
import java.sql.SQLXML;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;

import static org.postgresql.pljava.internal.Backend.doInPG;

import static org.postgresql.pljava.jdbc.SQLChunkIOOrder.MIRROR_J2P;
import static org.postgresql.pljava.jdbc.SQLChunkIOOrder.SCALAR_J2P;

/**
 * The SQLOutputToChunk uses JNI to build a PostgreSQL StringInfo buffer in
 * memory. A user should never make an attempt to create an instance of this
 * class. Only internal JNI routines can do that. An instance is propagated
 * in a call from the internal JNI layer to the Java layer will only survive
 * during that single call. The handle of the instance will be invalidated when
 * the call returns and subsequent use of the instance will yield a
 * SQLException with the message "Stream is closed".
 *
 * @author Thomas Hallgren
 */
public class SQLOutputToChunk implements SQLOutput
{
	private static final byte[] s_byteBuffer = new byte[8];

	private long m_handle;
	private ByteBuffer m_bb;

	public SQLOutputToChunk(long handle, ByteBuffer bb,
		boolean isJavaBasedScalar)
		throws SQLException
	{
		m_handle = handle;
		m_bb = bb.order(isJavaBasedScalar ? SCALAR_J2P : MIRROR_J2P);
	}

	@Override
	public void writeArray(Array value) throws SQLException
	{
		throw unsupportedOperationException("writeArray");
	}

	@Override
	public void writeAsciiStream(InputStream value) throws SQLException
	{
		throw unsupportedOperationException("writeAsciiStream");
	}

	@Override
	public void writeBigDecimal(BigDecimal value) throws SQLException
	{
		this.writeString(value.toString());
	}

	@Override
	public void writeBinaryStream(InputStream value) throws SQLException
	{
		byte[] buf = new byte[65536]; /* one more than max representable */
		int got;
		try
		{
			got = value.read(buf);
		}
		catch ( IOException e )
		{
			throw new SQLException(
				"Error making binary form of user-defined type from " +
				"input stream", "58030", e);
		}
		if ( -1 == got )
			got = 0;
		if ( 65535 < got )
			throw badRepresentation("writeBinaryStream");
		ensureCapacity(2 + got);
		m_bb.putShort((short)got).put(buf, 0, got);
	}

	@Override
	public void writeBlob(Blob value) throws SQLException
	{
		throw unsupportedOperationException("writeBlob");
	}

	@Override
	public void writeBoolean(boolean value) throws SQLException
	{
		this.writeByte(value ? (byte)1 : (byte)0);
	}

	@Override
	public void writeByte(byte value) throws SQLException
	{
		try
		{
			m_bb.put(value);
		}
		catch ( Exception e )
		{
			throwOrRetry(e, 1, "writeByte");
			m_bb.put(value);
		}
	}

	@Override
	public void writeBytes(byte[] buffer) throws SQLException
	{
		if ( 65535 < buffer.length )
			throw badRepresentation("writeBytes");
		ensureCapacity(2 + buffer.length);
		m_bb.putShort((short)buffer.length).put(buffer);
	}

	@Override
	public void writeCharacterStream(Reader value) throws SQLException
	{
		ByteBuffer bb = ByteBuffer.allocate(65535);
		CharBuffer cb = CharBuffer.allocate(1024);
		CharsetEncoder enc = UTF_8.newEncoder();
		CoderResult cr;

		try
		{
			while ( -1 != value.read(cb) )
			{
				cb.flip();
				cr = enc.encode(cb, bb, false);
				if ( ! cr.isUnderflow() )
					cr.throwException();
				cb.clear();
			}
			cb.flip();
			cr = enc.encode(cb, bb, true);
			if ( cr.isUnderflow() )
				cr = enc.flush(bb);
			if ( ! cr.isUnderflow() )
				cr.throwException();
			bb.flip();
			ensureCapacity(2 + bb.limit());
			m_bb.putShort((short)bb.limit()).put(bb);
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public void writeClob(Clob value) throws SQLException
	{
		throw unsupportedOperationException("writeClob");
	}

	@Override
	public void writeDate(Date value) throws SQLException
	{
		long v = value.getTime();
		try
		{
			m_bb.putLong(v);
		}
		catch ( Exception e )
		{
			throwOrRetry(e, 8, "writeDate");
			m_bb.putLong(v);
		}
	}

	@Override
	public void writeDouble(double value) throws SQLException
	{
		try
		{
			m_bb.putDouble(value);
		}
		catch ( Exception e )
		{
			throwOrRetry(e, 8, "writeDouble");
			m_bb.putDouble(value);
		}
	}

	@Override
	public void writeFloat(float value) throws SQLException
	{
		try
		{
			m_bb.putFloat(value);
		}
		catch ( Exception e )
		{
			throwOrRetry(e, 4, "writeFloat");
			m_bb.putFloat(value);
		}
	}

	@Override
	public void writeInt(int value) throws SQLException
	{
		try
		{
			m_bb.putInt(value);
		}
		catch ( Exception e )
		{
			throwOrRetry(e, 4, "writeInt");
			m_bb.putInt(value);
		}
	}

	@Override
	public void writeLong(long value) throws SQLException
	{
		try
		{
			m_bb.putLong(value);
		}
		catch ( Exception e )
		{
			throwOrRetry(e, 8, "writeLong");
			m_bb.putLong(value);
		}
	}

	@Override
	public void writeObject(SQLData value) throws SQLException
	{
		throw unsupportedOperationException("writeObject");
	}

	@Override
	public void writeRef(Ref value) throws SQLException
	{
		throw unsupportedOperationException("writeRef");
	}

	@Override
	public void writeShort(short value) throws SQLException
	{
		try
		{
			m_bb.putShort(value);
		}
		catch ( Exception e )
		{
			throwOrRetry(e, 2, "writeShort");
			m_bb.putShort(value);
		}
	}

	@Override
	public void writeString(String value) throws SQLException
	{
		CharBuffer cb = CharBuffer.wrap(value);
		try
		{
			CharsetEncoder enc = UTF_8.newEncoder();
			ByteBuffer bb = enc.encode(cb);
			int len = bb.limit();
			if ( 65535 < len )
				throw badRepresentation("writeString");
			ensureCapacity(2 + len);
			m_bb.putShort((short)len).put(bb);
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public void writeStruct(Struct value) throws SQLException
	{
		throw unsupportedOperationException("writeStruct");
	}

	@Override
	public void writeTime(Time value) throws SQLException
	{
		long v = value.getTime();
		try
		{
			m_bb.putLong(v);
		}
		catch ( Exception e )
		{
			throwOrRetry(e, 8, "writeTime");
			m_bb.putLong(v);
		}
	}

	@Override
	public void writeTimestamp(Timestamp value) throws SQLException
	{
		long v = value.getTime();
		try
		{
			m_bb.putLong(v);
		}
		catch ( Exception e )
		{
			throwOrRetry(e, 8, "writeTimestamp");
			m_bb.putLong(v);
		}
	}

	@Override
	public void writeURL(URL value) throws SQLException
	{
		this.writeString(value.toString());
	}

	void close() throws SQLException
	{
		if ( 0 == m_handle )
			return;
		ensureCapacity(0); /* propagate final position to native stringinfo */
		m_handle = 0;
		m_bb = null;
	}

	private void throwOrRetry(Exception e, int needed, String fn)
		throws SQLException
	{
		if ( e instanceof BufferOverflowException )
		{
			ensureCapacity(needed);
			return;
		}
		throw badRepresentation(e);
	}

	private SQLException unsupportedOperationException(String op)
	{
		return new SQLFeatureNotSupportedException(
			this.getClass() + "." + op + " not implemented yet.", "0A000");
	}

	private SQLException badRepresentation(String fn)
	{
		return new SQLNonTransientException(
			"Limit of 65535 bytes exceeded in " + fn + "for user-defined type",
			"54000");
	}

	private SQLException badRepresentation(Exception e)
	{
		if ( e instanceof NullPointerException )
			return new SQLNonTransientException(
				"attempted write via SQLOutput after closing it", "55000", e);
		if ( e instanceof BufferOverflowException )
			return new SQLNonTransientException(
				"Byte limit exceeded for user-defined type",
				"54000");
		if ( e instanceof UnmappableCharacterException )
			/*
			 * As long as the string encoding is unconditionally UTF-8, this
			 * shouldn't really be possible, but if the encoding is ever made
			 * selectable, this could happen.
			 */
			 return new SQLDataException(
				"Character not available in destination encoding",
				"22P05", e);
		if ( e instanceof MalformedInputException )
			/*
			 * This actually CAN happen ... as the input arrives in UTF-16,
			 * not codepoints.
			 */
			 return new SQLDataException(
				"Input that does not encode a valid character",
				"22021", e);
		return new SQLDataException(
			"Could not form binary representation of user-defined type",
			"22P03", e);
	}

	// ************************************************************
	// Non-implementation of JDBC 4 methods.
	// ************************************************************

	@Override
	public void writeNClob(NClob x)
                throws SQLException
	{
		throw unsupportedOperationException("writeNClob( NClob )");
	}
	
	@Override
	public void writeNString(String x)
		throws SQLException
		{
		throw unsupportedOperationException("writeNString( String )");
		}

	@Override
	public void writeRowId(RowId x)
                throws SQLException
	{
		throw unsupportedOperationException("writeRowId( RowId )");
	}
	
	@Override
	public void writeSQLXML(SQLXML x)
		throws SQLException
	{
		throw unsupportedOperationException("writeSQLXML( SQLXML )");
	}

	// ************************************************************
	// End of non-implementation of JDBC 4 methods.
	// ************************************************************


	private void ensureCapacity(int c) throws SQLException
	{
		doInPG(() ->
		{
			if(m_handle == 0)
				throw new SQLException("Stream is closed");
			ByteBuffer oldbb = m_bb;
			m_bb = _ensureCapacity(m_handle, m_bb, m_bb.position(), c);
			if ( m_bb != oldbb )
				m_bb.order(oldbb.order());
		});
	}

	private static native ByteBuffer _ensureCapacity(long handle,
		ByteBuffer bb, int pos, int needed);
}
