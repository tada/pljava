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

import java.io.IOException;
import java.io.InputStream;

import java.nio.ByteBuffer;

import java.sql.SQLException;

public interface VarlenaWrapper
{
	/**
	 * A class by which Java reads the content of a varlena as an InputStream.
	 *
	 * Associated with a {@code ResourceOwner} to bound the lifetime of
	 * the native reference; the chosen resource owner must be one that will be
	 * released no later than the memory context containing the varlena.
	 */
	public static class Input extends InputStream
	{
		private State m_state;
		private boolean m_open = true;

		/**
		 * Construct a {@code VarlenaWrapper.Input}.
		 * @param cookie Capability held by native code.
		 * @param resourceOwner Resource owner whose release will indicate that the
		 * underlying varlena is no longer valid.
		 * @param varlenaPtr Pointer value to the underlying varlena, to be
		 * {@code pfree}d when Java code closes or reclaims this object.
		 * @param buf Readable direct {@code ByteBuffer} constructed over the
		 * varlena's data bytes.
		 */
		private Input(DualState.Key cookie, long resourceOwner,
			long varlenaPtr, ByteBuffer buf)
		{
			m_state = new State(
				cookie, this, resourceOwner, varlenaPtr, buf.asReadOnlyBuffer());
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



		private static class State extends DualState.SinglePfree<Input>
		{
			private ByteBuffer m_buf;

			private State(
				DualState.Key cookie, Input vr, long resourceOwner,
				long varlenaPtr, ByteBuffer buf)
			{
				super(cookie, vr, resourceOwner, varlenaPtr);
				m_buf = buf;
			}

			private ByteBuffer buffer() throws SQLException
			{
				assertNativeStateIsValid();
				return m_buf;
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
		}
	}
}
