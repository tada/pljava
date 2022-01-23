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
import java.sql.SQLXML;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.adt.spi.Datum;

import org.postgresql.pljava.jdbc.SQLXMLImpl;
import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.RegType;

/**
 * PostgreSQL {@code xml} type represented as {@code java.sql.SQLXML}.
 */
public class XMLAdapter extends Adapter.As<SQLXML,Void>
{
	public static final XMLAdapter INSTANCE;
	public static final XMLAdapter SYNTHETIC_INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration[] configs = AccessController.doPrivileged(
			(PrivilegedAction<Configuration[]>)() -> new Configuration[]
			{
				configure(XMLAdapter.class, Via.DATUM),
				configure(Synthetic.class,  Via.DATUM)
			});

		INSTANCE           = new XMLAdapter(configs[0]);
		SYNTHETIC_INSTANCE = new  Synthetic(configs[1]);
	}

	XMLAdapter(Configuration c)
	{
		super(c, null, null);
	}

	/*
	 * This preserves the convention, since SQLXML came to PL/Java 1.5.1, that
	 * you can use the SQLXML API over text values (such as in a database built
	 * without the XML type, though who would do that nowadays?).
	 */
	@Override
	public boolean canFetch(RegType pgType)
	{
		return RegType.XML  == pgType
			|| RegType.TEXT == pgType;
	}

	public SQLXML fetch(Attribute a, Datum.Input in)
	throws SQLException, IOException
	{
		return SQLXMLImpl.newReadable(in, a.type(), false);
	}

	/**
	 * Adapter for use when the PostgreSQL type is not actually XML, but
	 * to be synthetically rendered as XML (such as {@code pg_node_tree}).
	 *<p>
	 * This is, for now, a very thin wrapper over
	 * {@code SQLXMLImpl.newReadable}, which (so far) is still where the
	 * type-specific rendering logic gets chosen, but that can be refactored
	 * eventually.
	 */
	public static class Synthetic extends XMLAdapter
	{
		Synthetic(Configuration c)
		{
			super(c);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.PG_NODE_TREE == pgType;
		}

		@Override
		public SQLXML fetch(Attribute a, Datum.Input in)
		throws SQLException, IOException
		{
			return SQLXMLImpl.newReadable(in, a.type(), true);
		}
	}
}
