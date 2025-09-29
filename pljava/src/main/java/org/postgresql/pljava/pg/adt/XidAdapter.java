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
package org.postgresql.pljava.pg.adt;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.sql.SQLException;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.adt.Internal;
import org.postgresql.pljava.adt.spi.Datum;
import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.RegType;
import static org.postgresql.pljava.model.RegNamespace.PG_CATALOG;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * PostgreSQL {@code cid}, {@code tid}, {@code xid}, and {@code xid8} types.
 */
public abstract class XidAdapter extends Adapter.Container
{
	private XidAdapter() // no instances
	{
	}

	private static final Configuration s_tid_config;

	public static final  CidXid  CID_INSTANCE;
	public static final  CidXid  XID_INSTANCE;
	public static final    Xid8 XID8_INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration[] configs = AccessController.doPrivileged(
			(PrivilegedAction<Configuration[]>)() -> new Configuration[]
			{
				configure( CidXid.class, Via.INT32ZX),
				configure(   Xid8.class, Via.INT64ZX),
				configure(    Tid.class, Via.DATUM  )
			});

		CID_INSTANCE  = new CidXid(configs[0], "cid");
		XID_INSTANCE  = new CidXid(configs[0], "xid");
		XID8_INSTANCE = new   Xid8(configs[1]);

		s_tid_config = configs[2];
	}

	/**
	 * Adapter for the {@code cid} or {@code xid} type, returned as
	 * a primitive {@code int}.
	 */
	public static class CidXid extends Adapter.AsInt.Unsigned<Void>
	{
		private final Simple m_typeName;
		private RegType m_type;

		private CidXid(Configuration c, String typeName)
		{
			super(c, null);
			m_typeName = Simple.fromJava(typeName);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			RegType myType = m_type;
			if ( null != myType )
				return myType == pgType;
			if ( ! m_typeName.equals(pgType.name())
				|| PG_CATALOG != pgType.namespace() )
				return false;
			/*
			 * Reference writes are atomic and RegTypes are singletons,
			 * so this race isn't evil.
			 */
			m_type = pgType;
			return true;
		}

		public int fetch(Attribute a, int in)
		{
			return in;
		}
	}

	/**
	 * Adapter for the {@code xid8} type, returned as a primitive {@code long}.
	 */
	public static class Xid8 extends Adapter.AsLong.Unsigned<Void>
	{
		private static final Simple s_typeName = Simple.fromJava("xid8");
		private static RegType s_type;

		private Xid8(Configuration c)
		{
			super(c, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			RegType myType = s_type;
			if ( null != myType )
				return myType == pgType;
			if ( ! s_typeName.equals(pgType.name())
				|| PG_CATALOG != pgType.namespace() )
				return false;
			/*
			 * Reference writes are atomic and RegTypes are singletons,
			 * so this race isn't evil.
			 */
			s_type = pgType;
			return true;
		}

		public long fetch(Attribute a, long in)
		{
			return in;
		}
	}

	/**
	 * Adapter for the {@code tid} type using the functional interface
	 * {@link Internal.Tid Internal.Tid}.
	 */
	public static class Tid<T> extends Adapter.As<T,Void>
	{
		private static final Simple s_typeName = Simple.fromJava("tid");
		private static RegType s_type;
		private Internal.Tid<T> m_ctor;

		public Tid(Configuration c, Internal.Tid<T> ctor)
		{
			super(ctor, null, c);
			m_ctor = ctor;
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			RegType myType = s_type;
			if ( null != myType )
				return myType == pgType;
			if ( ! s_typeName.equals(pgType.name())
				|| PG_CATALOG != pgType.namespace() )
				return false;
			/*
			 * Reference writes are atomic and RegTypes are singletons,
			 * so this race isn't evil.
			 */
			s_type = pgType;
			return true;
		}

		public T fetch(Attribute a, Datum.Input in)
		throws IOException, SQLException
		{
			try
			{
				in.pin();
				ByteBuffer bb = in.buffer();
				/*
				 * The following read could be unaligned; the C code declares
				 * BlockIdData trickily to allow it to be short-aligned.
				 * Java ByteBuffers will break up unaligned accesses as needed.
				 */
				int        blockId = bb.getInt();
				short offsetNumber = bb.getShort();
				return m_ctor.construct(blockId, offsetNumber);
			}
			finally
			{
				in.unpin();
				in.close();
			}
		}
	}
}
