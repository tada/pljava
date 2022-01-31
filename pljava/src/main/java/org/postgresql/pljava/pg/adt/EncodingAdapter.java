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

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.CharsetEncoding;
import org.postgresql.pljava.model.RegType;

/**
 * PostgreSQL character set encoding ({@code int4} in the catalogs) represented
 * as {@code CharsetEncoding}.
 */
public class EncodingAdapter extends Adapter.As<CharsetEncoding,Void>
{
	public static final EncodingAdapter INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration config = AccessController.doPrivileged(
			(PrivilegedAction<Configuration>)() ->
				configure(EncodingAdapter.class, Via.INT32SX));

		INSTANCE = new EncodingAdapter(config);
	}

	EncodingAdapter(Configuration c)
	{
		super(c, null, null);
	}

	@Override
	public boolean canFetch(RegType pgType)
	{
		return RegType.INT4 == pgType;
	}

	public CharsetEncoding fetch(Attribute a, int in)
	throws SQLException, IOException
	{
		return -1 == in ? CharsetEncoding.ANY : CharsetEncoding.fromOrdinal(in);
	}
}
