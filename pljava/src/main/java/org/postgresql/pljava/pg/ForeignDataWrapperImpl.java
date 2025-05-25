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
import static org.postgresql.pljava.pg.ModelConstants.FOREIGNDATAWRAPPEROID;

import org.postgresql.pljava.pg.adt.GrantAdapter;
import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGPROCEDURE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

import static org.postgresql.pljava.internal.UncheckedException.unchecked;

class ForeignDataWrapperImpl extends Addressed<ForeignDataWrapper>
implements
	Nonshared<ForeignDataWrapper>, Named<Simple>, Owned,
	AccessControlled<CatalogObject.USAGE>, ForeignDataWrapper
{
	private static final Function<MethodHandle[],MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<ForeignDataWrapper> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return FOREIGNDATAWRAPPEROID;
	}

	/* Implementation of Named, Owned, AccessControlled */

	private static Simple name(ForeignDataWrapperImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.FDWNAME, SIMPLE_INSTANCE);
	}

	private static RegRole owner(ForeignDataWrapperImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.FDWOWNER, REGROLE_INSTANCE);
	}

	private static List<CatalogObject.Grant> grants(ForeignDataWrapperImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.FDWACL, GrantAdapter.LIST_INSTANCE);
	}

	/* Implementation of ForeignDataWrapper */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	ForeignDataWrapperImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_HANDLER;
	static final int SLOT_VALIDATOR;
	static final int SLOT_OPTIONS;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(ForeignDataWrapperImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(ForeignDataWrapperImpl.class.getDeclaredMethods())

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent(     "owner", SLOT_OWNER)
			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withDependent(    "grants", SLOT_ACL)

			.withReceiverType(null)
			.withDependent(  "handler", SLOT_HANDLER   = i++)
			.withDependent("validator", SLOT_VALIDATOR = i++)
			.withDependent(  "options", SLOT_OPTIONS   = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer);
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute FDWNAME;
		static final Attribute FDWOWNER;
		static final Attribute FDWACL;
		static final Attribute FDWHANDLER;
		static final Attribute FDWVALIDATOR;
		static final Attribute FDWOPTIONS;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"fdwname",
				"fdwowner",
				"fdwacl",
				"fdwhandler",
				"fdwvalidator",
				"fdwoptions"
			).iterator();

			FDWNAME      = itr.next();
			FDWOWNER     = itr.next();
			FDWACL       = itr.next();
			FDWHANDLER   = itr.next();
			FDWVALIDATOR = itr.next();
			FDWOPTIONS   = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	private static RegProcedure<FDWHandler> handler(ForeignDataWrapperImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked")
		RegProcedure<FDWHandler> p = (RegProcedure<FDWHandler>)
			s.get(Att.FDWHANDLER, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<FDWValidator> validator(
		ForeignDataWrapperImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked")
		RegProcedure<FDWValidator> p = (RegProcedure<FDWValidator>)
			s.get(Att.FDWVALIDATOR, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static Map<Simple,String> options(ForeignDataWrapperImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.FDWOPTIONS, ArrayAdapters.RELOPTIONS_INSTANCE);
	}

	/* API methods */

	@Override
	public RegProcedure<FDWHandler> handler()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HANDLER];
			return (RegProcedure<FDWHandler>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<FDWValidator> validator()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_VALIDATOR];
			return (RegProcedure<FDWValidator>)h.invokeExact(this, h);
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
