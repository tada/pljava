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

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;

import java.sql.SQLException;

import java.util.List;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.Anum_pg_class_reltype;
import static org.postgresql.pljava.pg.ModelConstants.RELOID; // syscache

import static org.postgresql.pljava.pg.adt.OidAdapter.REGTYPE_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import static org.postgresql.pljava.internal.UncheckedException.unchecked;

/*
 * Can get lots of information, including Form_pg_class rd_rel and
 * TupleDesc rd_att, from the relcache. See CacheRegisterRelcacheCallback().
 * However, the relcache copy of the class tuple is cut off at CLASS_TUPLE_SIZE.
 */

class RegClassImpl extends Addressed<RegClass>
implements
	Nonshared<RegClass>, Namespaced<Simple>, Owned,
	AccessControlled<CatalogObject.Grant.OnClass>, RegClass
{
	static class Known<T extends CatalogObject.Addressed<T>>
	extends RegClassImpl implements RegClass.Known<T>
	{
	}

	/**
	 * Per-instance switch point, to be invalidated selectively
	 * by a relcache callback.
	 */
	SwitchPoint m_cacheSwitchPoint;

	private static UnaryOperator<MethodHandle[]> s_initializer;

	@Override
	int cacheId()
	{
		return RELOID;
	}

	RegClassImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
		m_cacheSwitchPoint = new SwitchPoint();
	}

	/**
	 * Called from {@code Factory}'s {@code invalidateRelation} to set up
	 * the invalidation of this relation's metadata.
	 *<p>
	 * Adds this relation's {@code SwitchPoint} to the caller's list so that,
	 * if more than one is to be invalidated, that can be done in bulk. Adds to
	 * <var>postOps</var> any operations the caller should conclude with
	 * after invalidating the {@code SwitchPoint}.
	 */
	void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
	{
		TupleDescriptor.Interned[] oldTDH = m_tupDescHolder;
		sps.add(m_cacheSwitchPoint);

		/*
		 * Before invalidating the SwitchPoint, line up a new one (and a newly
		 * nulled tupDescHolder) for value-computing methods to find once the
		 * old SwitchPoint is invalidated.
		 */
		m_cacheSwitchPoint = new SwitchPoint();
		m_tupDescHolder = null;

		/*
		 * After the old SwitchPoint gets invalidated, the old tupDescHolder,
		 * if any, can have its element nulled so the old TupleDescriptor can
		 * be collected without having to wait for the 'guardWithTest's it is
		 * bound into to be recomputed.
		 */
		if ( null != oldTDH )
			postOps.add(() -> oldTDH[0] = null);
	}

	/**
	 * Associated tuple descriptor, redundantly kept accessible here as well as
	 * opaquely bound into a {@code SwitchPointCache} method handle.
	 *<p>
	 * This one-element array containing the descriptor is what gets bound into
	 * the handle, so the descriptor can be freed for GC at invalidation time
	 * (rather than waiting for the next tuple-descriptor request to replace
	 * the handle). Only accessed from {@code SwitchPointCache} computation
	 * methods or {@code TupleDescImpl} factory methods, all of which execute
	 * on the PG thread; no synchronization fuss needed.
	 *<p>
	 * When null, no computation method has run (or none since invalidation),
	 * and the state is not known. Otherwise, the single element is the result
	 * to be returned by the {@code tupleDescriptor()} API method.
	 */
	TupleDescriptor.Interned[] m_tupDescHolder;

	/**
	 * Holder for the {@code RegType} corresponding to {@code type()},
	 * only non-null during a call of {@code dualHandshake}.
	 */
	private RegType m_dual = null;

	/**
	 * Called by the corresponding {@code RegType} instance if it has just
	 * looked us up.
	 *<p>
	 * Because the {@code SwitchPointCache} recomputation methods always execute
	 * on the PG thread, plain access to an instance field suffices here.
	 */
	void dualHandshake(RegType dual)
	{
		try
		{
			m_dual = dual;
			dual = type();
			assert dual == m_dual : "RegType/RegClass handshake outcome";
		}
		finally
		{
			m_dual = null;
		}
	}

	static final int SLOT_TUPLEDESCRIPTOR;
	static final int SLOT_TYPE;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(RegClassImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> o.m_cacheSwitchPoint)
			.withSlots(o -> o.m_slots)
			.withCandidates(RegClassImpl.class.getDeclaredMethods())
			.withDependent( "tupleDescriptor", SLOT_TUPLEDESCRIPTOR  = i++)
			.withDependent(            "type", SLOT_TYPE             = i++)
			.build();
		NSLOTS = i;
	}

	/**
	 * Return the tuple descriptor for this relation, wrapped in a one-element
	 * array, which is also stored in {@code m_tupDescHolder}.
	 *<p>
	 * The tuple descriptor for a relation can be retrieved from the PostgreSQL
	 * {@code relcache} or {@code typcache}; it's the same descriptor, and the
	 * latter gets it from the former. Going through the {@code relcache} is
	 * fussier, involving the lock manager every time, while using the
	 * {@code typcache} can avoid that except in its cache-miss case.
	 *<p>
	 * Here, for every relation other than {@code pg_class} itself, we will
	 * rely on the corresponding {@code RegType} to do the work. There is a bit
	 * of incest involved; it will construct the descriptor to rely on our
	 * {@code SwitchPoint} for invalidation, and will poke the wrapper array
	 * into our {@code m_tupDescHolder}.
	 *<p>
	 * It does that last bit so that, even if the first query for a type's
	 * tuple descriptor is made through the {@code RegType}, we will also return
	 * it if a later request is made here, and all of the invalidation logic
	 * lives here; it is relation-cache invalidation that obsoletes a cataloged
	 * tuple descriptor.
	 *<p>
	 * However, when the relation <em>is</em> {@code pg_class} itself, we rely
	 * on a bespoke JNI method to get the descriptor from the {@code relcache}.
	 * The case occurs when we are looking up the descriptor to interpret our
	 * own cache tuples, and the normal case's {@code type()} call won't work
	 * before that's available.
	 */
	private static TupleDescriptor.Interned[] tupleDescriptor(RegClassImpl o)
	{
		TupleDescriptor.Interned[] r = o.m_tupDescHolder;

		/*
		 * If not null, r is a value placed here by an invocation of
		 * tupleDescriptor() on the associated RegType, and we have not seen an
		 * invalidation since that happened (invalidations run on the PG thread,
		 * as do computation methods like this, so we've not missed anything).
		 * It is the value to return.
		 */
		if ( null != r )
			return r;

		/*
		 * In any case other than looking up our own tuple descriptor, we can
		 * use type() to find the associated RegType and let it do the work.
		 */
		if ( CLASSID != o )
		{
			o.type().tupleDescriptor(); // side effect: writes o.m_tupDescHolder
			return o.m_tupDescHolder;
		}

		/*
		 * It is the bootstrap case, looking up the pg_class tuple descriptor.
		 * If we got here we need it, so we can call the Cataloged constructor
		 * directly, rather than fromByteBuffer (which would first check whether
		 * we need it, and bump its reference count only if so). Called
		 * directly, the constructor expects the count already bumped, which
		 * the _tupDescBootstrap method will have done for us.
		 */
		ByteBuffer bb = _tupDescBootstrap();
		bb.order(nativeOrder());
		r = new TupleDescriptor.Interned[] {new TupleDescImpl.Cataloged(bb, o)};
		return o.m_tupDescHolder = r;
	}

	private static RegType type(RegClassImpl o) throws SQLException
	{
		/*
		 * If this is a handshake occurring when the corresponding RegType
		 * has just looked *us* up, we are done.
		 */
		if ( null != o.m_dual )
			return o.m_dual;

		/*
		 * Otherwise, look up the corresponding RegType, and do the same
		 * handshake in reverse. Either way, the connection is set up
		 * bidirectionally with one cache lookup starting from either. That
		 * can avoid extra work in operations (like TupleDescriptor caching)
		 * that may touch both objects, without complicating their code.
		 *
		 * Because the fetching of pg_attribute's tuple descriptor
		 * necessarily passes through this point, and attributes don't know
		 * what their names are until it has, use the attribute number here.
		 */
		TupleTableSlot s = o.cacheTuple();
		RegType t = s.get(
			s.descriptor().sqlGet(Anum_pg_class_reltype), REGTYPE_INSTANCE);

		((RegTypeImpl)t).dualHandshake(o);
		return t;
	}

	@Override
	public TupleDescriptor.Interned tupleDescriptor()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TUPLEDESCRIPTOR];
			return ((TupleDescriptor.Interned[])h.invokeExact(this, h))[0];
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

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
	public RegType ofType()
	{
		throw notyet();
	}

	// am
	// filenode
	// tablespace

	/* Of limited interest ... estimates used by planner
	 *
	int pages();
	float tuples();
	int allVisible();
	 */

	@Override
	public RegClass toastRelation()
	{
		throw notyet();
	}

	@Override
	public boolean hasIndex()
	{
		throw notyet();
	}

	@Override
	public boolean isShared()
	{
		throw notyet();
	}

	// persistence
	// kind

	@Override
	public short nAttributes()
	{
		throw notyet();
	}

	@Override
	public short checks()
	{
		throw notyet();
	}

	@Override
	public boolean hasRules()
	{
		throw notyet();
	}

	@Override
	public boolean hasTriggers()
	{
		throw notyet();
	}

	@Override
	public boolean hasSubclass()
	{
		throw notyet();
	}

	@Override
	public boolean rowSecurity()
	{
		throw notyet();
	}

	@Override
	public boolean forceRowSecurity()
	{
		throw notyet();
	}

	@Override
	public boolean isPopulated()
	{
		throw notyet();
	}

	// replident

	@Override
	public boolean isPartition()
	{
		throw notyet();
	}

	// rewrite
	// frozenxid
	// minmxid

	@Override
	public List<String> options()
	{
		throw notyet();
	}

	// partbound
}
