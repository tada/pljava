/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root directory of this distribution or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.jdbc;

import java.io.DataInputStream;
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
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.Time;
import java.sql.Timestamp;

import org.postgresql.pljava.internal.MemoryChunkInputStream;

/**
 * @author Thomas Hallgren
 */
public class SQLInputOnStream implements SQLInput
{
	private final DataInputStream m_dataInput;

	public SQLInputOnStream(long chunkHandle, int chunkSize)
	{
		this(new MemoryChunkInputStream(chunkHandle, chunkSize));
	}

	public SQLInputOnStream(InputStream input)
	{
		m_dataInput = new DataInputStream(input);
	}

	public String readString() throws SQLException
	{
		try
		{
			return m_dataInput.readUTF();
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public boolean readBoolean() throws SQLException
	{
		try
		{
			return m_dataInput.readBoolean();
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public byte readByte() throws SQLException
	{
		try
		{
			return m_dataInput.readByte();
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public short readShort() throws SQLException
	{
		try
		{
			return m_dataInput.readShort();
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public int readInt() throws SQLException
	{
		try
		{
			return m_dataInput.readInt();
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public long readLong() throws SQLException
	{
		try
		{
			return m_dataInput.readLong();
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public float readFloat() throws SQLException
	{
		try
		{
			return m_dataInput.readFloat();
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public double readDouble() throws SQLException
	{
		try
		{
			return m_dataInput.readDouble();
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public BigDecimal readBigDecimal() throws SQLException
	{
		try
		{
			return new BigDecimal(m_dataInput.readUTF());
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public byte[] readBytes() throws SQLException
	{
		try
		{
			int len = m_dataInput.readInt();
			byte[] ba = new byte[len];
			m_dataInput.readFully(ba);
			return ba;
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public Date readDate() throws SQLException
	{
		try
		{
			return new Date(m_dataInput.readLong());
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public Time readTime() throws SQLException
	{
		try
		{
			return new Time(m_dataInput.readLong());
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public Timestamp readTimestamp() throws SQLException
	{
		try
		{
			return new Timestamp(m_dataInput.readLong());
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public Reader readCharacterStream() throws SQLException
	{
		throw new UnsupportedOperationException("readCharacterStream");
	}

	public InputStream readAsciiStream() throws SQLException
	{
		throw new UnsupportedOperationException("readAsciiStream");
	}

	public InputStream readBinaryStream() throws SQLException
	{
		throw new UnsupportedOperationException("readBinaryStream");
	}

	public Object readObject() throws SQLException
	{
		throw new UnsupportedOperationException("readObject");
	}

	public Ref readRef() throws SQLException
	{
		throw new UnsupportedOperationException("readRef");
	}

	public Blob readBlob() throws SQLException
	{
		throw new UnsupportedOperationException("readBlob");
	}

	public Clob readClob() throws SQLException
	{
		throw new UnsupportedOperationException("readClob");
	}

	public Array readArray() throws SQLException
	{
		throw new UnsupportedOperationException("readArray");
	}

	public boolean wasNull() throws SQLException
	{
		return false;
	}

	public URL readURL() throws SQLException
	{
		try
		{
			return new URL(m_dataInput.readUTF());
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}
}
