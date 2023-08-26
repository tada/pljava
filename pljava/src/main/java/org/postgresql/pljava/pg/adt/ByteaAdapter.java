/*
 * Copyright (c) 2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.pg.adt;

import java.io.InputStream;
import java.io.IOException;

import java.nio.ByteBuffer;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.sql.SQLException;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.adt.spi.Datum;
import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.RegType;

/**
 * PostgreSQL {@code bytea}.
 */
public abstract class ByteaAdapter extends Adapter.Container
{
	private ByteaAdapter() // no instances
	{
	}

	public static final Bytes   ARRAY_INSTANCE;
	public static final Stream STREAM_INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration[] configs = AccessController.doPrivileged(
			(PrivilegedAction<Configuration[]>)() -> new Configuration[]
			{
				configure( Bytes.class, Via.DATUM),
				configure(Stream.class, Via.DATUM)
			});

		 ARRAY_INSTANCE = new  Bytes(configs[0]);
		STREAM_INSTANCE = new Stream(configs[1]);
	}

	/**
	 * Adapter producing a Java byte array.
	 */
	public static class Bytes extends Adapter.As<byte[],Void>
	{
		private Bytes(Configuration c)
		{
			super(c, null, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.BYTEA == pgType;
		}

		public byte[] fetch(Attribute a, Datum.Input in)
		throws SQLException
		{
			in.pin();
			try
			{
				ByteBuffer b = in.buffer();
				byte[] array = new byte [ b.limit() ];
				// Java >= 13: b.get(0, array)
				b.rewind().get(array);
				return array;
			}
			finally
			{
				in.unpin();
			}
		}
	}

	/**
	 * Adapter producing an {@code InputStream}.
	 */
	public static class Stream extends Adapter.As<InputStream,Void>
	{
		private Stream(Configuration c)
		{
			super(c, null, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.BYTEA == pgType;
		}

		public InputStream fetch(Attribute a, Datum.Input in)
		throws SQLException
		{
			return in.inputStream();
		}
	}
}
