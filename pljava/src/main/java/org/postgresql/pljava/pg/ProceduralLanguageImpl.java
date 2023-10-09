/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
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

import java.util.Iterator;
import java.util.List;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.PLPrincipal;

import org.postgresql.pljava.annotation.Function.Trust;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.LANGOID; // syscache

import org.postgresql.pljava.pg.adt.GrantAdapter;
import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGPROCEDURE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.BOOLEAN_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

class ProceduralLanguageImpl extends Addressed<ProceduralLanguage>
implements
	Nonshared<ProceduralLanguage>, Named<Simple>, Owned,
	AccessControlled<CatalogObject.USAGE>, ProceduralLanguage
{
	private static UnaryOperator<MethodHandle[]> s_initializer;

	private final SwitchPoint[] m_sp;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<ProceduralLanguage> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return LANGOID;
	}

	/* Implementation of Named, Owned, AccessControlled */

	private static Simple name(ProceduralLanguageImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return
			t.get(Att.LANNAME, SIMPLE_INSTANCE);
	}

	private static RegRole owner(ProceduralLanguageImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.LANOWNER, REGROLE_INSTANCE);
	}

	private static List<CatalogObject.Grant> grants(ProceduralLanguageImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.LANACL, GrantAdapter.LIST_INSTANCE);
	}

	/* Implementation of ProceduralLanguage */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	ProceduralLanguageImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
		m_sp = new SwitchPoint[] { new SwitchPoint() };
	}

	@Override
	void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
	{
		sps.add(m_sp[0]);
		m_sp[0] = new SwitchPoint();
	}

	static final int SLOT_PRINCIPAL;
	static final int SLOT_HANDLER;
	static final int SLOT_INLINEHANDLER;
	static final int SLOT_VALIDATOR;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(ProceduralLanguageImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> o.m_sp[0])
			.withSlots(o -> o.m_slots)

			.withCandidates(
				CatalogObjectImpl.Addressed.class.getDeclaredMethods())
			.withReceiverType(CatalogObjectImpl.Addressed.class)
			.withDependent("cacheTuple", SLOT_TUPLE)

			.withCandidates(ProceduralLanguageImpl.class.getDeclaredMethods())
			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent(     "owner", SLOT_OWNER)
			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withDependent(    "grants", SLOT_ACL)

			.withReceiverType(null)
			.withDependent(    "principal", SLOT_PRINCIPAL     = i++)
			.withDependent(      "handler", SLOT_HANDLER       = i++)
			.withDependent("inlineHandler", SLOT_INLINEHANDLER = i++)
			.withDependent(    "validator", SLOT_VALIDATOR     = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer)::apply;
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute LANNAME;
		static final Attribute LANOWNER;
		static final Attribute LANACL;
		static final Attribute LANPLTRUSTED;
		static final Attribute LANPLCALLFOID;
		static final Attribute LANINLINE;
		static final Attribute LANVALIDATOR;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"lanname",
				"lanowner",
				"lanacl",
				"lanpltrusted",
				"lanplcallfoid",
				"laninline",
				"lanvalidator"
			).iterator();

			LANNAME       = itr.next();
			LANOWNER      = itr.next();
			LANACL        = itr.next();
			LANPLTRUSTED  = itr.next();
			LANPLCALLFOID = itr.next();
			LANINLINE     = itr.next();
			LANVALIDATOR  = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	private static PLPrincipal principal(ProceduralLanguageImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		if ( s.get(Att.LANPLTRUSTED, BOOLEAN_INSTANCE) )
			return new PLPrincipal.Sandboxed(o.name());
		return new PLPrincipal.Unsandboxed(o.name());
	}

	private static RegProcedure<Handler> handler(ProceduralLanguageImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<Handler> p = (RegProcedure<Handler>)
			s.get(Att.LANPLCALLFOID, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<InlineHandler> inlineHandler(
		ProceduralLanguageImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<InlineHandler> p = (RegProcedure<InlineHandler>)
			s.get(Att.LANINLINE, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<Validator> validator(ProceduralLanguageImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<Validator> p = (RegProcedure<Validator>)
			s.get(Att.LANVALIDATOR, REGPROCEDURE_INSTANCE);
		return p;
	}

	/* API methods */

	@Override
	public PLPrincipal principal()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_PRINCIPAL];
			return (PLPrincipal)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<Handler> handler()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HANDLER];
			return (RegProcedure<Handler>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<InlineHandler> inlineHandler()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_INLINEHANDLER];
			return (RegProcedure<InlineHandler>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<Validator> validator()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_VALIDATOR];
			return (RegProcedure<Validator>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}
}
