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
import org.postgresql.pljava.adt.spi.Datum;
import org.postgresql.pljava.model.Attribute;
import static org.postgresql.pljava.model.CharsetEncoding.SERVER_ENCODING;

import org.postgresql.pljava.model.RegType;

import static org.postgresql.pljava.pg.DatumUtils.mapCString;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

/**
 * PostgreSQL {@code name} type represented as
 * {@code Lexicals.Identifier.Simple} or {@code Lexicals.Identifier.Operator}.
 */
public abstract class NameAdapter<T extends Unqualified>
extends Adapter.As<T,Void>
{
	public static final Simple      SIMPLE_INSTANCE;
	public static final Operator  OPERATOR_INSTANCE;
	public static final AsString AS_STRING_INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration[] configs = AccessController.doPrivileged(
			(PrivilegedAction<Configuration[]>)() -> new Configuration[]
			{
				configure(  Simple.class, Via.DATUM),
				configure(Operator.class, Via.DATUM),
				configure(AsString.class, Via.DATUM)
			});

		SIMPLE_INSTANCE    = new   Simple(configs[0]);
		OPERATOR_INSTANCE  = new Operator(configs[1]);
		AS_STRING_INSTANCE = new AsString(configs[2]);
	}

	NameAdapter(Configuration c)
	{
		super(c, null, null);
	}

	@Override
	public boolean canFetch(RegType pgType)
	{
		return RegType.NAME == pgType;
	}

	/**
	 * Adapter for the {@code name} type, returning an
	 * {@link Identifier.Simple Identifier.Simple}.
	 */
	public static class Simple extends NameAdapter<Identifier.Simple>
	{
		private Simple(Configuration c)
		{
			super(c);
		}

		public Identifier.Simple fetch(Attribute a, Datum.Input in)
		throws SQLException, IOException
		{
			return Identifier.Simple.fromCatalog(decoded(in));
		}
	}

	/**
	 * Adapter for the {@code name} type, returning an
	 * {@link Identifier.Operator Identifier.Operator}.
	 */
	public static class Operator extends NameAdapter<Identifier.Operator>
	{
		private Operator(Configuration c)
		{
			super(c);
		}

		public Identifier.Operator fetch(Attribute a, Datum.Input in)
		throws SQLException, IOException
		{
			return Identifier.Operator.from(decoded(in));
		}
	}

	/**
	 * Adapter for the {@code name} type, returning a Java {@code String}.
	 *<p>
	 * This may be convenient for some casual uses, but a Java string will not
	 * observe any of the peculiar case-sensitivity rules of SQL identifiers.
	 */
	public static class AsString extends Adapter.As<String,Void>
	{
		private AsString(Configuration c)
		{
			super(c, null, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.NAME == pgType;
		}

		public String fetch(Attribute a, Datum.Input in)
		throws SQLException, IOException
		{
			return decoded(in);
		}
	}

	static final String decoded(Datum.Input in) throws SQLException, IOException
	{
		in.pin();
		try
		{
			ByteBuffer bnew = mapCString(in.buffer(), 0);
			return SERVER_ENCODING.decode(bnew).toString();
		}
		finally
		{
			in.unpin();
			in.close();
		}
	}
}
