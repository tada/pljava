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

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.sql.SQLException;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.adt.spi.Datum;
import org.postgresql.pljava.model.Attribute;
import static org.postgresql.pljava.model.CharsetEncoding.SERVER_ENCODING;
import org.postgresql.pljava.model.RegType;

/**
 * PostgreSQL {@code text}, {@code varchar}, and similar types represented as
 * Java {@code String}.
 */
public class TextAdapter extends Adapter.As<String,Void>
{
	public static final TextAdapter INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration config = AccessController.doPrivileged(
			(PrivilegedAction<Configuration>)() ->
				configure(TextAdapter.class, Via.DATUM));

		INSTANCE = new TextAdapter(config);
	}

	private TextAdapter(Configuration c)
	{
		super(c, null, null);
	}

	@Override
	public boolean canFetch(RegType pgType)
	{
		if ( RegType.TEXT == pgType || RegType.CSTRING == pgType )
			return true;

		pgType = pgType.withoutModifier();

		return RegType.VARCHAR == pgType
			|| RegType.BPCHAR  == pgType;

     /* [comment re: typmod copied from upstream utils/adt/varchar.c:]
      * For largely historical reasons, the typmod is VARHDRSZ plus the number
      * of characters; there is enough client-side code that knows about that
      * that we'd better not change it.
      */
	}

	public String fetch(Attribute a, Datum.Input in)
	throws SQLException, IOException
	{
		return SERVER_ENCODING.decode(in, /* close */ true).toString();
	}
}
