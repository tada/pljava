/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.pg;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;

import java.nio.ByteBuffer;

import java.sql.SQLException;

import org.postgresql.pljava.adt.spi.Datum;
import org.postgresql.pljava.adt.spi.Verifier;

import org.postgresql.pljava.internal.ByteBufferInputStream;
import org.postgresql.pljava.internal.VarlenaWrapper; // javadoc

/**
 * Contains implementation for {@link Datum Datum}.
 *<p>
 * This is also implemented by {@link VarlenaWrapper VarlenaWrapper}, which is
 * carried over from PL/Java 1.5.x, where it could originally be constructed
 * only from native code. It has been minimally adapted to fit into this new
 * scheme, and in future should fit more cleanly.
 */
public interface DatumImpl extends Datum
{
	/**
	 * Dissociate the datum from Java and return its address to native code.
	 */
	long adopt() throws SQLException;

	/**
	 * Implementation of {@link Datum.Input Datum.Input}.
	 */
	abstract class Input implements Datum.Input, DatumImpl
	{
		/**
		 * A Datum.Input copied onto the Java heap to depend on no native state,
		 * so {@code pin} and {@code unpin} are no-ops.
		 */
		static class JavaCopy extends DatumImpl.Input
		{
			private ByteBuffer m_buffer;

			public JavaCopy(ByteBuffer b)
			{
				assert ! b.isDirect() :
				  "ByteBuffer passed to a JavaCopy Datum constructor is direct";
				m_buffer = b;
			}

			@Override
			public ByteBuffer buffer() throws SQLException
			{
				ByteBuffer b = m_buffer;
				if ( b == null )
					throw new SQLException("Datum used after close", "55000");
				return b;
			}

			@Override
			public void close() throws IOException
			{
				m_buffer = null;
			}

			@Override
			public long adopt() throws SQLException
			{
				throw new UnsupportedOperationException(
					"XXX Datum JavaCopy.adopt");
			}
		}

		@Override @SuppressWarnings("unchecked")
		public <T extends InputStream & Datum> T inputStream()
		throws SQLException
		{
			return (T) new Stream<>(this);
		}

		/**
		 * {@link InputStream InputStream} view of a {@code Datum.Input}.
		 */
		public static class Stream<T extends DatumImpl.Input>
		extends ByteBufferInputStream implements DatumImpl
		{
			protected final T m_datum;

			/**
			 * A duplicate of the {@code Datum.Input}'s byte buffer,
			 * so its {@code position} and {@code mark} can be updated by the
			 * {@code InputStream} operations without affecting the original
			 * (therefore multiple {@code Stream}s may read one {@code Input}).
			 */
			private final ByteBuffer m_movingBuffer;

			protected Stream(T datum) throws SQLException
			{
				m_datum = datum;
				ByteBuffer b = datum.buffer();
				m_movingBuffer = b.duplicate().order(b.order());
			}

			@Override
			protected void pin() throws IOException
			{
				if ( ! m_open )
					throw new IOException("Read from closed Datum");
				try
				{
					m_datum.pin();
				}
				catch ( SQLException e )
				{
					throw new IOException(e.getMessage(), e);
				}
			}

			@Override
			protected void unpin()
			{
				m_datum.unpin();
			}

			@Override
			protected ByteBuffer buffer()
			{
				return m_movingBuffer;
			}

			@Override
			public void close() throws IOException
			{
				if ( m_datum.pinUnlessReleased() )
					return;
				try
				{
					super.close();
					m_datum.close();
				}
				finally
				{
					unpin();
				}
			}

			@Override
			public long adopt() throws SQLException
			{
				m_datum.pin();
				try
				{
					if ( ! m_open )
						throw new SQLException(
							"Cannot adopt VarlenaWrapper.Input after " +
							"it is closed", "55000");
					return m_datum.adopt();
				}
				finally
				{
					m_datum.unpin();
				}
			}

			/**
			 * Apply a {@code Verifier} to the input data.
			 *<p>
			 * This should only be necessary if the input wrapper is being used
			 * directly as an output item, and needs verification that it
			 * conforms to the format of the target type.
			 *<p>
			 * The current position must be at the beginning of the stream. The
			 * verifier must leave it at the end to confirm the entire stream
			 * was examined. There should be no need to reset the position here,
			 * as the only anticipated use is during an {@code adopt}, and the
			 * native code will only care about the varlena's address.
			 */
			public void verify(Verifier.OfStream v) throws SQLException
			{
				/*
				 * This is only called from some client code's adopt() method,
				 * calls to which are serialized through Backend.THREADLOCK
				 * anyway, so holding a pin here for the duration doesn't
				 * further limit concurrency. Hold m_lock's monitor also to
				 * block any extraneous reading interleaved with the verifier.
				 */
				m_datum.pin();
				try
				{
					ByteBuffer buf = buffer();
					synchronized ( m_lock )
					{
						if ( 0 != buf.position() )
							throw new SQLException(
								"Variable-length input data to be verified " +
								" not positioned at start",
								"55000");
						InputStream dontCloseMe = new FilterInputStream(this)
						{
							@Override
							public void close() throws IOException { }
						};
						v.verify(dontCloseMe);
						if ( 0 != buf.remaining() )
							throw new SQLException(
								"Verifier finished prematurely");
					}
				}
				catch ( SQLException | RuntimeException e )
				{
					throw e;
				}
				catch ( Exception e )
				{
					throw new SQLException(
						"Exception verifying variable-length data: " +
						e.getMessage(), "XX000", e);
				}
				finally
				{
					m_datum.unpin();
				}
			}
		}
	}
}
