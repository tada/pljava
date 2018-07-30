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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;

import java.sql.SQLException;

/**
 * Interface that wraps a PostgreSQL native variable-length ("varlena") datum;
 * implementing classes present an existing one to Java as a readable
 * {@code InputStream}, or allow a new one to be constructed by presenting a
 * writable {@code OutputStream}.
 *<p>
 * Common to both is a method {@link #adopt adopt()}, allowing native
 * code to reassert control over the varlena (for the writable variety, after
 * Java code has written and closed it), after which it is no longer accessible
 * from Java.
 */
public interface VarlenaWrapper extends Closeable
{
	/**
	 * Return the varlena address to native code and dissociate the varlena
	 * from Java.
	 * @param cookie Capability held by native code.
	 */
	long adopt(DualState.Key cookie) throws SQLException;

	/**
	 * Return a string describing this object in a way useful for debugging,
	 * prefixed with the name (abbreviated for comfort) of the class of the
	 * object passed in (the normal Java {@code toString()} method should pass
	 * {@code this}).
	 *<p>
	 * Subclasses or consumers are encouraged to call this method and append
	 * further details specific to the subclass or consumer. The convention
	 * should be that the recursion will stop at some class that will actually
	 * construct the abbreviated class name of {@code o} and use it to prefix
	 * the returned value.
	 * @param o An object whose class name (possibly abbreviated) should be used
	 * to prefix the returned string.
	 * @return Description of this object.
	 */
	String toString(Object o);



	/**
	 * A class by which Java reads the content of a varlena as an InputStream.
	 *
	 * Associated with a {@code ResourceOwner} to bound the lifetime of
	 * the native reference; the chosen resource owner must be one that will be
	 * released no later than the memory context containing the varlena.
	 */
	public static class Input extends InputStream implements VarlenaWrapper
	{
		private State m_state;
		private boolean m_open = true;

		/**
		 * Construct a {@code VarlenaWrapper.Input}.
		 * @param cookie Capability held by native code.
		 * @param resourceOwner Resource owner whose release will indicate that the
		 * underlying varlena is no longer valid.
		 * @param context Memory context in which the varlena is allocated.
		 * @param varlenaPtr Pointer value to the underlying varlena, to be
		 * {@code pfree}d when Java code closes or reclaims this object.
		 * @param buf Readable direct {@code ByteBuffer} constructed over the
		 * varlena's data bytes.
		 */
		private Input(DualState.Key cookie, long resourceOwner,
			long context, long varlenaPtr, ByteBuffer buf)
		{
			m_state = new State(
				cookie, this, resourceOwner,
				context, varlenaPtr, buf.asReadOnlyBuffer());
		}

		private ByteBuffer buf() throws IOException
		{
			if ( ! m_open )
				throw new IOException("Read from closed VarlenaWrapper");
			try
			{
				return m_state.buffer();
			}
			catch ( SQLException sqe )
			{
				throw new IOException("Read from varlena failed", sqe);
			}
		}

		@Override
		public int read() throws IOException
		{
			synchronized ( m_state )
			{
				ByteBuffer src = buf();
				if ( 0 < src.remaining() )
					return src.get();
				return -1;
			}
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException
		{
			synchronized ( m_state )
			{
				ByteBuffer src = buf();
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

		@Override
		public long skip(long n) throws IOException
		{
			synchronized ( m_state )
			{
				ByteBuffer src = buf();
				int has = src.remaining();
				if ( n > has )
					n = has;
				src.position(src.position() + (int)n);
				return n;
			}
		}

		@Override
		public int available() throws IOException
		{
			synchronized ( m_state )
			{
				return buf().remaining();
			}
		}

		@Override
		public void close() throws IOException
		{
			synchronized ( m_state )
			{
				if ( ! m_open )
					return;
				m_open = false;
				m_state.enqueue();
			}
		}

		@Override
		public void mark(int readlimit)
		{
			synchronized ( m_state )
			{
				if ( ! m_open )
					return;
				try
				{
					buf().mark();
				}
				catch ( IOException e )
				{
					/*
					 * The contract is for mark to throw no checked exception.
					 * An exception caught here would mean the state's no longer
					 * live, which will be signaled to the caller if another,
					 * throwing, method is then called. If not, no harm no foul.
					 */
				}
			}
		}

		@Override
		public void reset() throws IOException
		{
			synchronized ( m_state )
			{
				if ( ! m_open )
					return;
				try
				{
					buf().reset();
				}
				catch ( InvalidMarkException e )
				{
					throw new IOException("reset attempted when mark not set");
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

		@Override
		public long adopt(DualState.Key cookie) throws SQLException
		{
			synchronized ( m_state )
			{
				if ( ! m_open )
					throw new SQLException(
						"Cannot adopt VarlenaWrapper.Input after it is closed",
						"55000");
				return m_state.adopt(cookie);
			}
		}

		@Override
		public String toString()
		{
			return toString(this);
		}
		
		@Override
		public String toString(Object o)
		{
			return String.format("%s %s", m_state.toString(o),
				m_open ? "open" : "closed");
		}



		private static class State
		extends DualState.SingleMemContextDelete<Input>
		{
			private ByteBuffer m_buf;
			private long m_varlena;

			private State(
				DualState.Key cookie, Input vr, long resourceOwner,
				long memContext, long varlenaPtr, ByteBuffer buf)
			{
				super(cookie, vr, resourceOwner, memContext);
				m_varlena = varlenaPtr;
				m_buf = buf;
			}

			private ByteBuffer buffer() throws SQLException
			{
				assertNativeStateIsValid();
				return m_buf;
			}

			private long adopt(DualState.Key cookie) throws SQLException
			{
				checkCookie(cookie);
				assertNativeStateIsValid();
				long varlena = m_varlena;
				nativeStateReleased();
				return varlena;
			}

			@Override
			protected void nativeStateReleased()
			{
				m_buf = null;
				super.nativeStateReleased();
			}

			@Override
			protected void javaStateReleased()
			{
				m_buf = null;
				super.javaStateReleased();
			}

			@Override
			public String toString(Object o)
			{
				return String.format("%s varlena:%x %s",
					super.toString(o), m_varlena,
					String.valueOf(m_buf).replace("java.nio.", ""));
			}
		}
	}

	/**
	 * A class by which Java writes the content of a varlena as an OutputStream.
	 *
	 * Associated with a {@code ResourceOwner} to bound the lifetime of
	 * the native reference; the chosen resource owner must be one that will be
	 * released no later than the memory context containing the varlena.
	 */
	public class Output extends OutputStream implements VarlenaWrapper
	{
		private State m_state;
		private boolean m_open = true;

		/**
		 * Construct a {@code VarlenaWrapper.Output}.
		 * @param cookie Capability held by native code.
		 * @param resourceOwner Resource owner whose release will indicate that
		 * the underlying varlena is no longer valid.
		 * @param context Pointer to memory context containing the underlying
		 * varlena; subject to {@code MemoryContextDelete} if Java code frees or
		 * reclaims this object.
		 * @param varlenaPtr Pointer value to the underlying varlena.
		 * @param buf Writable direct {@code ByteBuffer} constructed over (an
		 * initial region of) the varlena's data bytes.
		 */
		private Output(DualState.Key cookie, long resourceOwner,
			long context, long varlenaPtr, ByteBuffer buf)
		{
			m_state = new State(
				cookie, this, resourceOwner, context, varlenaPtr, buf);
		}

		/**
		 * Return a ByteBuffer to write into.
		 *<p>
		 * It will be the existing buffer if it has any remaining capacity to
		 * write into (even if it is less than desiredCapacity), otherwise a new
		 * buffer allocated with desiredCapacity as a hint (it may still be
		 * smaller than the hint, or larger). Call with desiredCapacity zero to
		 * indicate that writing is finished and make the varlena available for
		 * native code to adopt.
		 */
		private ByteBuffer buf(int desiredCapacity) throws IOException
		{
			if ( ! m_open )
				throw new IOException("Write on closed VarlenaWrapper.Output");
			try
			{
				ByteBuffer buf = m_state.buffer();
				if ( 0 < buf.remaining()  &&  0 < desiredCapacity )
					return buf;
				return m_state.nextBuffer(desiredCapacity);
			}
			catch ( SQLException sqe )
			{
				throw new IOException("Write on varlena failed", sqe);
			}
		}

		@Override
		public void write(int b) throws IOException
		{
			synchronized ( m_state )
			{
				ByteBuffer dst = buf(1);
				dst.put((byte)(b & 0xff));
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException
		{
			synchronized ( m_state )
			{
				while ( 0 < len )
				{
					ByteBuffer dst = buf(len);
					int can = dst.remaining();
					if ( can > len )
						can = len;
					dst.put(b, off, can);
					off += can;
					len -= can;
				}
			}
		}

		@Override
		public void close() throws IOException
		{
			synchronized ( m_state )
			{
				if ( ! m_open )
					return;
				buf(0);
				m_open = false;
			}
		}

		/**
		 * Actually free a {@code VarlenaWrapper.Output}.
		 *<p>
		 * {@code close()} does not do so, because the typical use of this class
		 * is to write to an instance, close it, then let some native code adopt
		 * it. If it turns out one won't be adopted and must be freed, use this
		 * method.
		 */
		public void free() throws IOException
		{
			close();
			m_state.javaStateReleased();
		}

		@Override
		public long adopt(DualState.Key cookie) throws SQLException
		{
			synchronized ( m_state )
			{
				if ( m_open )
					throw new SQLException(
						"Writing of VarlenaWrapper.Output not yet complete",
						"55000");
				return m_state.adopt(cookie);
			}
		}

		@Override
		public String toString()
		{
			return toString(this);
		}

		@Override
		public String toString(Object o)
		{
			return String.format("%s %s", m_state.toString(o),
				m_open ? "open" : "closed");
		}



		private static class State
		extends DualState.SingleMemContextDelete<Output>
		{
			private ByteBuffer m_buf;
			private long m_varlena;

			private State(
				DualState.Key cookie, Output vr,
				long resourceOwner,	long memContext, long varlenaPtr,
				ByteBuffer buf)
			{
				super(cookie, vr, resourceOwner, memContext);
				m_varlena = varlenaPtr;
				m_buf = buf;
			}

			private ByteBuffer buffer() throws SQLException
			{
				assertNativeStateIsValid();
				return m_buf;
			}

			private ByteBuffer nextBuffer(int desiredCapacity)
			throws SQLException
			{
				assertNativeStateIsValid();
				if ( 0 < m_buf.remaining()  &&  0 < desiredCapacity )
					throw new SQLException(
						"VarlenaWrapper.Output buffer management error",
						"XX000");
				synchronized ( Backend.THREADLOCK )
				{
					m_buf =	_nextBuffer(m_varlena, m_buf.position(),
								desiredCapacity);
				}
				return m_buf;
			}

			private long adopt(DualState.Key cookie) throws SQLException
			{
				checkCookie(cookie);
				assertNativeStateIsValid();
				long varlena = m_varlena;
				nativeStateReleased();
				return varlena;
			}

			@Override
			public String toString(Object o)
			{
				return String.format("%s varlena:%x %s",
					super.toString(o), m_varlena,
					String.valueOf(m_buf).replace("java.nio.", ""));
			}

			@Override
			protected void nativeStateReleased()
			{
				m_buf = null;
				super.nativeStateReleased();
			}

			@Override
			protected void javaStateReleased()
			{
				m_buf = null;
				super.javaStateReleased();
			}

			private native ByteBuffer _nextBuffer(
				long varlenaPtr, int currentBufPosition, int desiredCapacity);
		}
	}
}
