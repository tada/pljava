/*
 * Copyright (c) 2018 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.io.InputStream;
import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;

/**
 * Wrap a readable {@link ByteBuffer} as an {@link InputStream}.
 *<p>
 * An implementing class must provide a {@link #buffer} method that returns the
 * {@code ByteBuffer}, and the method is responsible for knowing when the memory
 * region windowed by the {@code ByteBuffer} is no longer to be accessed, and
 * throwing an exception in that case.
 *<p>
 * The implementing class may supply an object that the {@code InputStream}
 * operations will be {@code synchronized} on.
 *<p>
 * The underlying buffer's
 * {@link ByteBuffer#position() position} and
 * {@link ByteBuffer#mark() mark} are used to maintain the corresponding values
 * for the input stream.
 */
public abstract class ByteBufferInputStream extends InputStream
{
	/**
	 * The object on which the {@code InputStream} operations will synchronize.
	 */
	protected final Object m_lock;

	/**
	 * Whether this stream is open; initially true.
	 */
	protected boolean m_open;

	/**
	 * Construct an instance whose critical sections will synchronize on the
	 * instance itself.
	 */
	protected ByteBufferInputStream()
	{
		m_lock = this;
		m_open = true;
	}

	/**
	 * Construct an instance, given an object on which to synchronize.
	 * @param lock The Object to synchronize on.
	 */
	protected ByteBufferInputStream(Object lock)
	{
		m_lock = lock;
		m_open = true;
	}

	/**
	 * Pin resources if necessary during a reading operation.
	 *<p>
	 * This default implementation does nothing. A subclass should override it
	 * if (in addition to synchronizing on {@code m_lock}), some pinning of a
	 * resource is needed during access operations.
	 */
	protected void pin() throws IOException
	{
	}

	/**
	 * Unpin resources if necessary after a reading operation.
	 *<p>
	 * This default implementation does nothing.
	 */
	protected void unpin()
	{
	}

	/**
	 * Return the {@link ByteBuffer} being wrapped, or throw an exception if the
	 * memory windowed by the buffer should no longer be accessed.
	 *<p>
	 * The monitor on {@link #m_lock} is held when this method is called.
	 *<p>
	 * This method also should throw an exception if {@link #m_open} is false.
	 * It is called everywhere that should happen, so it is the perfect place
	 * for the test, and allows the implementing class to use a customized
	 * message in the exception.
	 *<p>
	 * All uses of the buffer in this class are preceded by {@code pin()} and
	 * followed by {@code unpin()} (whose default implementations in this class
	 * do nothing). If a subclass overrides {@code pin} with a version that
	 * throws the appropriate exception in either case or both, it is then
	 * redundant and unnecessary for {@code buffer} to check the same
	 * conditions.
	 */
	protected abstract ByteBuffer buffer() throws IOException;

	@Override
	public int read() throws IOException
	{
		pin();
		try
		{
			synchronized ( m_lock )
			{
				ByteBuffer src = buffer();
				if ( 0 < src.remaining() )
					return src.get();
				return -1;
			}
		}
		finally
		{
			unpin();
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException
	{
		pin();
		try
		{
			synchronized ( m_lock )
			{
				ByteBuffer src = buffer();
				int has = src.remaining();
				if ( len > has )
				{
					if ( 0 == has )
						return -1;
					len = has;
				}
				src.get(b, off, len);
				return len;
			}
		}
		finally
		{
			unpin();
		}
	}

	@Override
	public long skip(long n) throws IOException
	{
		pin();
		try
		{
			synchronized ( m_lock )
			{
				ByteBuffer src = buffer();
				int has = src.remaining();
				if ( n > has )
					n = has;
				src.position(src.position() + (int)n);
				return n;
			}
		}
		finally
		{
			unpin();
		}
	}

	@Override
	public int available() throws IOException
	{
		pin();
		try
		{
			synchronized ( m_lock )
			{
				return buffer().remaining();
			}
		}
		finally
		{
			unpin();
		}
	}

	@Override
	public void close() throws IOException
	{
		synchronized ( m_lock )
		{
			if ( ! m_open )
				return;
			m_open = false;
		}
	}

	@Override
	public void mark(int readlimit)
	{
		synchronized ( m_lock )
		{
			if ( ! m_open )
				return;
			boolean gotPin = false; // Kludge to get pin() inside the try block
			try
			{
				pin();
				gotPin = true;
				buffer().mark();
			}
			catch ( IOException e )
			{
				/*
				 * The contract is for mark to throw no checked exception.
				 * An exception caught here probably means the state's no longer
				 * live, which will be signaled to the caller if another,
				 * throwing, method is then called. If not, no harm no foul.
				 */
			}
			finally
			{
				if ( gotPin )
					unpin();
			}
		}
	}

	@Override
	public void reset() throws IOException
	{
		synchronized ( m_lock )
		{
			if ( ! m_open )
				return;
			pin();
			try
			{
				buffer().reset();
			}
			catch ( InvalidMarkException e )
			{
				throw new IOException("reset attempted when mark not set");
			}
			finally
			{
				unpin();
			}
		}
	}

	/**
	 * Return {@code true}; this class does support {@code mark} and
	 * {@code reset}.
	 */
	@Override
	public boolean markSupported()
	{
		return true;
	}
}
