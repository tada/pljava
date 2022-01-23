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

import java.lang.invoke.MethodHandle;
import static java.lang.invoke.MethodHandles.lookup;
import java.lang.invoke.SwitchPoint;

import java.sql.SQLException;

import java.util.List;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.DATABASEOID; // syscache

import org.postgresql.pljava.pg.adt.EncodingAdapter;
import org.postgresql.pljava.pg.adt.GrantAdapter;
import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.NameAdapter.AS_STRING_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.BOOLEAN_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.INT4_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

import static org.postgresql.pljava.internal.UncheckedException.unchecked;

class DatabaseImpl extends Addressed<Database>
implements
	Shared<Database>, Named<Simple>, Owned,
	AccessControlled<CatalogObject.Grant.OnDatabase>, Database
{
	private static UnaryOperator<MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<Database> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return DATABASEOID;
	}

	/* Implementation of Named, Owned, AccessControlled */

	private static Simple name(DatabaseImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(t.descriptor().get("datname"), SIMPLE_INSTANCE);
	}

	private static RegRole owner(DatabaseImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(t.descriptor().get("datdba"), REGROLE_INSTANCE);
	}

	private static List<CatalogObject.Grant> grants(DatabaseImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(t.descriptor().get("datacl"), GrantAdapter.LIST_INSTANCE);
	}

	/* Implementation of Database */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	DatabaseImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_ENCODING;
	static final int SLOT_COLLATE;
	static final int SLOT_CTYPE;
	static final int SLOT_TEMPLATE;
	static final int SLOT_ALLOWCONNECTION;
	static final int SLOT_CONNECTIONLIMIT;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(DatabaseImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(DatabaseImpl.class.getDeclaredMethods())

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent(     "owner", SLOT_OWNER)
			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withDependent(    "grants", SLOT_ACL)

			.withReceiverType(null)
			.withDependent(       "encoding", SLOT_ENCODING        = i++)
			.withDependent(        "collate", SLOT_COLLATE         = i++)
			.withDependent(          "ctype", SLOT_CTYPE           = i++)
			.withDependent(       "template", SLOT_TEMPLATE        = i++)
			.withDependent("allowConnection", SLOT_ALLOWCONNECTION = i++)
			.withDependent("connectionLimit", SLOT_CONNECTIONLIMIT = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer)::apply;
		NSLOTS = i;
	}

	/* computation methods */

	private static CharsetEncoding encoding(DatabaseImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("encoding"), EncodingAdapter.INSTANCE);
	}

	private static String collate(DatabaseImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("datcollate"), AS_STRING_INSTANCE);
	}

	private static String ctype(DatabaseImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("datctype"), AS_STRING_INSTANCE);
	}

	private static boolean template(DatabaseImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("datistemplate"), BOOLEAN_INSTANCE);
	}

	private static boolean allowConnection(DatabaseImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("datallowconn"), BOOLEAN_INSTANCE);
	}

	private static int connectionLimit(DatabaseImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("datconnlimit"), INT4_INSTANCE);
	}

	/* API methods */

	@Override
	public CharsetEncoding encoding()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ENCODING];
			return (CharsetEncoding)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public String collate()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_COLLATE];
			return (String)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public String ctype()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CTYPE];
			return (String)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean template()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TEMPLATE];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean allowConnection()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ALLOWCONNECTION];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public int connectionLimit()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CONNECTIONLIMIT];
			return (int)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}
}
