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
import static java.nio.ByteOrder.nativeOrder;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.sql.SQLException;

import java.util.List;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.adt.Array.AsFlatList;
import org.postgresql.pljava.adt.spi.Datum;
import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.CatalogObject.Grant;
import org.postgresql.pljava.model.RegType;

import org.postgresql.pljava.pg.AclItem;

/**
 * PostgreSQL {@code aclitem} represented as {@link Grant Grant}.
 */
public class GrantAdapter extends Adapter.As<Grant,Void>
{
	public static final GrantAdapter INSTANCE;

	public static final ArrayAdapter<List<Grant>,?> LIST_INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration config = AccessController.doPrivileged(
			(PrivilegedAction<Configuration>)() ->
				configure(GrantAdapter.class, Via.DATUM));

		INSTANCE = new GrantAdapter(config);

		LIST_INSTANCE = new ArrayAdapter<>(
			AsFlatList.of(AsFlatList::nullsIncludedCopy), INSTANCE);
	}

	private GrantAdapter(Configuration c)
	{
		super(c, null, null);
	}

	@Override
	public boolean canFetch(RegType pgType)
	{
		return RegType.ACLITEM == pgType;
	}

	public Grant fetch(Attribute a, Datum.Input in)
	throws IOException, SQLException
	{
		in.pin();
		try
		{
			ByteBuffer b = in.buffer().order(nativeOrder());
			return new AclItem.NonRole(b);
		}
		finally
		{
			in.unpin();
			in.close();
		}
	}
}
