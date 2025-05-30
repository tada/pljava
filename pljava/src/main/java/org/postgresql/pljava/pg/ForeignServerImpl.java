/*
 * Copyright (c) 2022-2025 Tada AB and other contributors, as listed below.
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

import java.sql.SQLException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.util.function.Function;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import org.postgresql.pljava.internal.SwitchPointCache.SwitchPoint;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.FOREIGNSERVEROID;

import org.postgresql.pljava.pg.adt.GrantAdapter;
import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.FDW_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;
import org.postgresql.pljava.pg.adt.TextAdapter;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

import static org.postgresql.pljava.internal.UncheckedException.unchecked;

/**
 * Implementation of the {@link ForeignServer ForeignServer} interface.
 */
class ForeignServerImpl extends Addressed<ForeignServer>
implements
	Nonshared<ForeignServer>, Named<Simple>, Owned,
	AccessControlled<CatalogObject.USAGE>, ForeignServer
{
	private static final Function<MethodHandle[],MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<ForeignServer> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return FOREIGNSERVEROID;
	}

	/* Implementation of Named, Owned, AccessControlled */

	private static Simple name(ForeignServerImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.SRVNAME, SIMPLE_INSTANCE);
	}

	private static RegRole owner(ForeignServerImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.SRVOWNER, REGROLE_INSTANCE);
	}

	private static List<CatalogObject.Grant> grants(ForeignServerImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.SRVACL, GrantAdapter.LIST_INSTANCE);
	}

	/* Implementation of ForeignServer */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	ForeignServerImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_FDW;
	static final int SLOT_TYPE;
	static final int SLOT_VERSION;
	static final int SLOT_OPTIONS;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(ForeignServerImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(ForeignServerImpl.class.getDeclaredMethods())

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent(     "owner", SLOT_OWNER)
			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withDependent(    "grants", SLOT_ACL)

			.withReceiverType(null)
			.withDependent(    "fdw", SLOT_FDW     = i++)
			.withDependent(   "type", SLOT_TYPE    = i++)
			.withDependent("version", SLOT_VERSION = i++)
			.withDependent("options", SLOT_OPTIONS = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer);
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute SRVNAME;
		static final Attribute SRVOWNER;
		static final Attribute SRVACL;
		static final Attribute SRVFDW;
		static final Attribute SRVTYPE;
		static final Attribute SRVVERSION;
		static final Attribute SRVOPTIONS;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"srvname",
				"srvowner",
				"srvacl",
				"srvfdw",
				"srvtype",
				"srvversion",
				"srvoptions"
			).iterator();

			SRVNAME    = itr.next();
			SRVOWNER   = itr.next();
			SRVACL     = itr.next();
			SRVFDW     = itr.next();
			SRVTYPE    = itr.next();
			SRVVERSION = itr.next();
			SRVOPTIONS = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	private static ForeignDataWrapper fdw(ForeignServerImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.SRVFDW, FDW_INSTANCE);
	}

	private static String type(ForeignServerImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.SRVTYPE, TextAdapter.INSTANCE);
	}

	private static String version(ForeignServerImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.SRVVERSION, TextAdapter.INSTANCE);
	}

	private static Map<Simple,String> options(ForeignServerImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.SRVOPTIONS, ArrayAdapters.RELOPTIONS_INSTANCE);
	}

	/* API methods */

	@Override
	public ForeignDataWrapper fdw()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_FDW];
			return (ForeignDataWrapper)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public String type()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TYPE];
			return (String)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public String version()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_VERSION];
			return (String)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Map<Simple,String> options()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_OPTIONS];
			return (Map<Simple,String>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}
}
