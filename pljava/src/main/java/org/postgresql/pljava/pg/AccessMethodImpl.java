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

import java.util.function.Function;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import org.postgresql.pljava.internal.SwitchPointCache.SwitchPoint;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.AMOID; // syscache

import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGPROCEDURE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.INT1_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

import static org.postgresql.pljava.internal.UncheckedException.unchecked;

class AccessMethodImpl extends Addressed<AccessMethod>
implements Nonshared<AccessMethod>, Named<Simple>, AccessMethod
{
	private static final Function<MethodHandle[],MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<AccessMethod> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return AMOID;
	}

	/* Implementation of Named */

	private static Simple name(AccessMethodImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.AMNAME, SIMPLE_INSTANCE);
	}

	/* Implementation of AccessMethod */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	AccessMethodImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_HANDLER;
	static final int SLOT_TYPE;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(AccessMethodImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(AccessMethodImpl.class.getDeclaredMethods())

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(   "name", SLOT_NAME)
			.withReturnType(null)

			.withReceiverType(null)
			.withDependent("handler", SLOT_HANDLER = i++)
			.withDependent(   "type", SLOT_TYPE    = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer);
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute AMNAME;
		static final Attribute AMHANDLER;
		static final Attribute AMTYPE;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"amname",
				"amhandler",
				"amtype"
			).iterator();

			AMNAME    = itr.next();
			AMHANDLER = itr.next();
			AMTYPE    = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	private static RegProcedure<AMHandler> handler(AccessMethodImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked")
		RegProcedure<AMHandler> p = (RegProcedure<AMHandler>)
			s.get(Att.AMHANDLER, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static Type type(AccessMethodImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return typeFromCatalog(s.get(Att.AMTYPE, INT1_INSTANCE));
	}

	/* API methods */

	@Override
	public RegProcedure<AMHandler> handler()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HANDLER];
			return (RegProcedure<AMHandler>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Type type()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TYPE];
			return (Type)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	private static Type typeFromCatalog(byte b)
	{
		switch ( b )
		{
		case (byte)'t': return Type.TABLE;
		case (byte)'i': return Type.INDEX;
		}
		throw unchecked(new SQLException(
			"unrecognized Type '" + (char)b + "' in catalog",
			"XX000"));
	}
}
