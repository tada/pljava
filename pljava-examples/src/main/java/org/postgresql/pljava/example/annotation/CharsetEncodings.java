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
package org.postgresql.pljava.example.annotation;

import java.nio.charset.Charset;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Iterator;

import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.annotation.Function;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;
import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import org.postgresql.pljava.annotation.SQLAction;

import org.postgresql.pljava.model.CharsetEncoding;
import static org.postgresql.pljava.model.CharsetEncoding.SERVER_ENCODING;
import static org.postgresql.pljava.model.CharsetEncoding.clientEncoding;

/**
 * Example using the {@link CharsetEncoding CharsetEncoding} interface.
 */
public class CharsetEncodings implements ResultSetProvider.Large
{
	/**
	 * Enumerate PostgreSQL's known character set encodings, indicating for
	 * each one whether it is the server encoding, whether it's the client
	 * encoding, its PostgreSQL name, its corresponding Java
	 * {@link Charset Charset} name, and the Java module that provides it.
	 */
	@Function(
		schema = "javatest",
		out = {
			"server boolean", "client boolean", "server_usable boolean",
			"ordinal int", "pg_name text", "icu_name text",
			"java_name text", "module text"
		}
	)
	public static ResultSetProvider charsets()
	{
		return new CharsetEncodings();
	}

	/**
	 * Enumerate Java's known character set encodings, trying to map them to
	 * PostgreSQL encodings, and indicating for
	 * each one whether it is the server encoding, whether it's the client
	 * encoding, its PostgreSQL name, its corresponding Java
	 * {@link Charset Charset} name, and the Java module that provides it.
	 */
	@Function(
		schema = "javatest",
		out = {
			"server boolean", "client boolean", "server_usable boolean",
			"ordinal int", "pg_name text", "icu_name text",
			"java_name text", "module text"
		}
	)
	public static ResultSetProvider java_charsets(boolean try_aliases)
	{
		return new JavaEncodings(try_aliases);
	}

	@Override
	public void close()
	{
	}

	@Override
	public boolean assignRowValues(ResultSet receiver, long currentRow)
	throws SQLException
	{
		/*
		 * Shamelessly exploit the fact that currentRow will be passed as
		 * consecutive values starting at zero and that's the same way PG
		 * encodings are numbered.
		 */

		CharsetEncoding cse;

		try
		{
			cse = CharsetEncoding.fromOrdinal((int)currentRow);
		}
		catch ( IllegalArgumentException e )
		{
			return false;
		}

		if ( SERVER_ENCODING == cse )
			receiver.updateBoolean("server", true);
		if ( clientEncoding() == cse )
			receiver.updateBoolean("client", true);
		if ( cse.usableOnServer() )
			receiver.updateBoolean("server_usable", true);
		receiver.updateInt("ordinal", cse.ordinal());
		receiver.updateString("pg_name", cse.name());
		receiver.updateString("icu_name", cse.icuName());

		Charset cs = cse.charset();
		if ( null == cs )
			return true;

		receiver.updateString("java_name", cs.name());
		receiver.updateString("module", cs.getClass().getModule().getName());

		return true;
	}

	static class JavaEncodings implements ResultSetProvider.Large
	{
		final Iterator<Charset> iter =
			Charset.availableCharsets().values().iterator();
		final boolean tryAliases;

		JavaEncodings(boolean tryAliases)
		{
			this.tryAliases = tryAliases;
		}

		@Override
		public void close()
		{
		}

		@Override
		public boolean assignRowValues(ResultSet receiver, long currentRow)
		throws SQLException
		{
			if ( ! iter.hasNext() )
				return false;

			Charset cs = iter.next();

			receiver.updateString("java_name", cs.name());
			receiver.updateString("module",
				cs.getClass().getModule().getName());

			CharsetEncoding cse = null;

			try
			{
				cse = CharsetEncoding.fromName(cs.name());
			}
			catch ( IllegalArgumentException e )
			{
			}

			/*
			 * If the canonical Java name didn't match up with a PG encoding,
			 * try the first match found for any of the Java charset's aliases.
			 * This is not an especially dependable idea: the aliases are a Set,
			 * so they don't enumerate in a reproducible order, and some Java
			 * aliases are PG aliases for different charsets.
			 */
			if ( null == cse  &&  tryAliases )
			{
				for ( String alias : cs.aliases() )
				{
					try
					{
						cse = CharsetEncoding.fromName(alias);
						break;
					}
					catch ( IllegalArgumentException e )
					{
					}
				}
			}

			if ( null == cse )
				return true;

			if ( SERVER_ENCODING == cse )
				receiver.updateBoolean("server", true);
			if ( clientEncoding() == cse )
				receiver.updateBoolean("client", true);
			if ( cse.usableOnServer() )
				receiver.updateBoolean("server_usable", true);
			receiver.updateInt("ordinal", cse.ordinal());
			receiver.updateString("pg_name", cse.name());
			receiver.updateString("icu_name", cse.icuName());

			return true;
		}
	}
}
