/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root directory of this distribution or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Thomas Hallgren
 */
public class MemoryChunkInputStream extends InputStream
{
	private final int m_chunkSize;

	private int m_position;

	private long m_handle;

	public MemoryChunkInputStream(long handle, int chunkSize)
	{
		m_handle = handle;
		m_chunkSize = chunkSize;
		m_position = 0;
	}

	public int available() throws IOException
	{
		return m_chunkSize - m_position;
	}

	public void close() throws IOException
	{
		m_handle = 0;
	}

	public int read() throws IOException
	{
		if(m_position < m_chunkSize)
		{
			synchronized(Backend.THREADLOCK)
			{
				if(m_handle == 0)
					throw new EOFException("Stream is closed");
				return _readByte(m_handle, m_position++);
			}
		}
		return -1;
	}

	public int read(byte[] buffer, int off, int len) throws IOException
	{
		int top = buffer.length;
		if(off < 0 || len < 0 || off + len > top)
			throw new IndexOutOfBoundsException();

		int maxRead = m_chunkSize - m_position;
		if(len > maxRead)
			len = maxRead;

		if(len <= 0)
			return 0;

		synchronized(Backend.THREADLOCK)
		{
			if(m_handle == 0)
				throw new EOFException("Stream is closed");
			_readBytes(m_handle, m_position, buffer, off, len);
		}
		m_position += len;
		return len;
	}

	public long skip(long n) throws IOException
	{
		if(n <= 0)
			return 0;

		int maxRead = m_chunkSize - m_position;
		if(n > maxRead)
			n = maxRead;
		m_position += n;
		return n;
	}

	private static native int _readByte(long handle, int position);
	private static native void _readBytes(long handle, int position, byte[] dest, int off, int len);
}
