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

import java.nio.ByteBuffer;

import java.sql.SQLException;

import java.util.Iterator;
import java.util.List;

import java.util.function.UnaryOperator;

import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;
import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.TRFOID; // syscache
import static org.postgresql.pljava.pg.ModelConstants.TRFTYPELANG; // syscache
import static org.postgresql.pljava.pg.TupleTableSlotImpl.heapTupleGetLightSlot;

import static org.postgresql.pljava.pg.adt.OidAdapter.PLANG_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGPROCEDURE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGTYPE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.TRANSFORM_INSTANCE;

class TransformImpl extends Addressed<Transform>
implements Nonshared<Transform>, Transform
{
	private static final UnaryOperator<MethodHandle[]> s_initializer;

	/**
	 * Looks up a single {@code Transform} given a type and procedural language.
	 *<p>
	 * Only to be called "on the PG thread".
	 * @return a {@code Transform} if found, otherwise null.
	 */
	static Transform fromTypeLang(RegType type, ProceduralLanguage lang)
	{
		assert threadMayEnterPG() : "Transform.fromTypeLang thread";

		/*
		 * All we need here is the transform's oid, which a custom native
		 * method could obtain more cheaply without copying the tuple, but
		 * _searchSysCacheCopy2 can do the job without adding yet another JNI
		 * method. We will allocate in the current context, assumed to be
		 * short-lived, context and use heapTupleGetLightSlot(..., false) to let
		 * the context take care of cleanup, as no reference to this slot will
		 * escape this call.
		 */
		ByteBuffer heapTuple =
			_searchSysCacheCopy2(TRFTYPELANG, type.oid(), lang.oid());
		if ( null == heapTuple )
				return null;

		TupleDescImpl td = (TupleDescImpl)CLASSID.tupleDescriptor();
		TupleTableSlot s = heapTupleGetLightSlot(td, heapTuple, null, false);
		return s.get(Att.OID, TRANSFORM_INSTANCE);
	}

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<Transform> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return TRFOID;
	}

	/* Implementation of Transform */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	TransformImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_TYPE;
	static final int SLOT_LANG;
	static final int SLOT_FROMSQL;
	static final int SLOT_TOSQL;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(TransformImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(TransformImpl.class.getDeclaredMethods())

			.withDependent(    "type", SLOT_TYPE	= i++)
			.withDependent("language", SLOT_LANG    = i++)
			.withDependent( "fromSQL", SLOT_FROMSQL = i++)
			.withDependent(   "toSQL", SLOT_TOSQL	= i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer)::apply;
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute OID; // used in fromTypeLang() above
		static final Attribute TRFTYPE;
		static final Attribute TRFLANG;
		static final Attribute TRFFROMSQL;
		static final Attribute TRFTOSQL;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"oid",
				"trftype",
				"trflang",
				"trffromsql",
				"trftosql"
			).iterator();

			OID        = itr.next();
			TRFTYPE    = itr.next();
			TRFLANG    = itr.next();
			TRFFROMSQL = itr.next();
			TRFTOSQL   = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	private static RegType type(TransformImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.TRFTYPE, REGTYPE_INSTANCE);
	}

	private static ProceduralLanguage language(TransformImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.TRFLANG, PLANG_INSTANCE);
	}

	private static RegProcedure<FromSQL> fromSQL(TransformImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked")
		RegProcedure<FromSQL> p =
			(RegProcedure<FromSQL>)s.get(Att.TRFFROMSQL, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<ToSQL> toSQL(TransformImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked")
		RegProcedure<ToSQL> p =
			(RegProcedure<ToSQL>)s.get(Att.TRFTOSQL, REGPROCEDURE_INSTANCE);
		return p;
	}

	/* API methods */

	@Override
	public RegType type()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TYPE];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public ProceduralLanguage language()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_LANG];
			return (ProceduralLanguage)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<FromSQL> fromSQL()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_FROMSQL];
			return (RegProcedure<FromSQL>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<ToSQL> toSQL()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TOSQL];
			return (RegProcedure<ToSQL>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}
}
