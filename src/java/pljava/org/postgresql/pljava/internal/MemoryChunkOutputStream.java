/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root directory of this distribution or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Thomas Hallgren
 */
public class MemoryChunkOutputStream extends OutputStream
{
	private long m_handle;
	public MemoryChunkOutputStream(long handle)
	{
		m_handle = handle;
	}

    public void close() throws IOException
    {
    	m_handle = 0;
    }

	public void write(byte[] buffer, int off, int len) throws IOException
	{
		int top = buffer.length;
		if(off < 0 || len < 0 || off + len > top)
			throw new IndexOutOfBoundsException();

		if(len > 0)
		{
			synchronized(Backend.THREADLOCK)
			{
				if(m_handle == 0)
					throw new EOFException("Stream is closed");
				_writeBytes(m_handle, buffer, off, len);
			}
		}
	}

	public void write(int b) throws IOException
	{
		synchronized(Backend.THREADLOCK)
		{
			if(m_handle == 0)
				throw new EOFException("Stream is closed");
			_writeByte(m_handle, b);
		}
	}

	private static native void _writeByte(long handle, int theByte);
	private static native void _writeBytes(long handle, byte[] src, int off, int len);
}
