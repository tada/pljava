/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.jdbc;

import java.io.BufferedInputStream;
import java.io.CharConversionException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.sql.Clob;
import java.sql.SQLException;

/**
 * @author Thomas Hallgren
 */
public class ClobValue extends Reader implements Clob
{
	private final Reader m_reader;
	private final long m_nChars;
	private long m_readerPos;
	private long m_markPos;

	public ClobValue(String value)
	{
		this(new StringReader(value), value.length());
	}

	public ClobValue(Reader reader, long nChars)
	{
		m_reader    = reader;
		m_nChars    = nChars;
		m_readerPos = 0L;
		m_markPos   = 0L;
	}

	//***************************************
	// Implementation of java.sql.Clob
	//***************************************
	public long length()
	{
		return m_nChars;
	}

	public InputStream getAsciiStream()
	{
		return new BufferedInputStream(new InputStream()
				{
			public int read()
			throws IOException
			{
				int nextChar = ClobValue.this.read();
				if(nextChar > 127)
					throw new CharConversionException("Non ascii character in Clob data");
				return nextChar;
			}
		});
	}

	public Reader getCharacterStream()
	{
		return this;
	}

	public String getSubString(long pos, int length)
	throws SQLException
	{
		if(pos < 0L || length < 0)
			throw new IllegalArgumentException();
		if(length == 0)
			return "";

		if(pos + length > m_nChars)
			throw new SQLException("Attempt to read beyond end of Clob data");

		long skip = pos - m_readerPos;
		if(skip < 0)
			throw new SQLException("Cannot position Clob stream backwards");

		try
		{
			if(skip > 0)
				this.skip(skip);

			char[] buf = new char[length];
			int nr = this.read(buf);
			if(nr < length)
				throw new SQLException("Clob data read not fulfilled");
			return new String(buf);
		}
		catch(IOException e)
		{
			throw new SQLException("Error reading Blob data: " + e.getMessage());
		}
	}

	/**
	 * In this method is not supported by <code>ClobValue</code>
	 */
	public long position(String pattern, long start)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * In Frameworx this method is not supported.
	 */
	public long position(Clob pattern, long start)
	{
		throw new UnsupportedOperationException();
	}

	//*************************************************************************
	// Implementation of java.sql.Clob JDK 1.4 methods
	//
	// Those method are intended to provide a channel to the underlying data
	// storage as an alternatvie to the setCharacterStream and setAsciiStream
	// on the preparedStatement and are not implemented by the ClobValue.
	//
	//*************************************************************************
	/**
	 * In this method is not supported by <code>ClobValue</code>
	 */
	public OutputStream setAsciiStream(long pos)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * In this method is not supported by <code>ClobValue</code>
	 */
	public Writer setCharacterStream(long pos)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * In this method is not supported by <code>ClobValue</code>
	 */
	public int setString(long pos, String str)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * In this method is not supported by <code>ClobValue</code>
	 */
	public int setString(long pos, String str, int offset, int len)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * In this method is not supported by <code>ClobValue</code>
	 */
	public void truncate(long len)
	{
		throw new UnsupportedOperationException();
	}

	//***************************************
	// Implementation of java.io.Reader
	//***************************************
	public void close()
	throws IOException
	{
		m_reader.close();
		m_readerPos = 0;
		m_markPos = 0;
	}

	public boolean markSupported()
	{
		return m_reader.markSupported();
	}

	public synchronized void mark(int readLimit)
	throws IOException
	{
		m_reader.mark(readLimit);
		m_markPos = m_readerPos;
	}

	public synchronized int read()
	throws IOException
	{
		int rs = m_reader.read();
		m_readerPos++;
		return rs;
	}

	public synchronized int read(char[] b)
	throws IOException
	{
		int rs = m_reader.read(b);
		m_readerPos += rs;
		return rs;
	}

	public synchronized int read(char[] b, int off,  int len)
	throws IOException
	{
		int rs = m_reader.read(b, off, len);
		m_readerPos += rs;
		return rs;
	}

	public synchronized long skip(long nBytes)
	throws IOException
	{
		long skipped = m_reader.skip(nBytes);
		m_readerPos += skipped;
		return skipped;
	}

	public synchronized boolean ready()
	throws IOException
	{
		return m_reader.ready();
	}

	public synchronized void reset()
	throws IOException
	{
		m_reader.reset();
		m_readerPos = m_markPos;
	}
}
