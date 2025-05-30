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
import static java.nio.ByteOrder.BIG_ENDIAN;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.sql.SQLException;

import java.util.UUID;

import org.postgresql.pljava.Adapter;

import org.postgresql.pljava.adt.spi.Datum;

import org.postgresql.pljava.model.Attribute;
import static org.postgresql.pljava.model.RegNamespace.PG_CATALOG;
import org.postgresql.pljava.model.RegType;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * PostgreSQL {@code uuid} type represented
 * as {@code java.util.UUID}.
 */
public class UUIDAdapter extends Adapter.As<UUID,Void>
{
	public static final UUIDAdapter INSTANCE;

	private static final Simple s_name_UUID = Simple.fromJava("uuid");

	private static RegType s_uuidType;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration config = AccessController.doPrivileged(
			(PrivilegedAction<Configuration>)() ->
				configure(UUIDAdapter.class, Via.DATUM));

		INSTANCE = new UUIDAdapter(config);
	}

	UUIDAdapter(Configuration c)
	{
		super(c, null, null);
	}

	@Override
	public boolean canFetch(RegType pgType)
	{
		/*
		 * Compare by name and namespace rather than requiring RegType to have
		 * a static field for the UUID type; more popular ones, sure, but a line
		 * has to be drawn somewhere.
		 */
		RegType uuidType = s_uuidType;
		if ( null != uuidType ) // have we matched it before and cached it?
			return uuidType == pgType;

		if ( ! s_name_UUID.equals(pgType.name())
			|| PG_CATALOG != pgType.namespace() )
			return false;

		/*
		 * Hang onto this matching RegType for faster future checks.
		 * Because RegTypes are singletons, and reference writes can't
		 * be torn, this isn't evil as data races go.
		 */
		s_uuidType = pgType;
		return true;
	}

	public UUID fetch(Attribute a, Datum.Input in)
	throws SQLException, IOException
	{
		try
		{
			in.pin();
			ByteBuffer bb = in.buffer();
			/*
			 * The storage is laid out byte by byte in the order PostgreSQL
			 * prints them (irrespective of architecture). Java's UUID type
			 * prints the MSB first.
			 */
			bb.order(BIG_ENDIAN);
			long high64 = bb.getLong();
			long low64  = bb.getLong();
			return new UUID(high64, low64);
		}
		finally
		{
			in.unpin();
			in.close();
		}
	}
}
