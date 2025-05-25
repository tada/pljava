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
import static java.nio.ByteOrder.nativeOrder;

import java.sql.SQLException;
import java.sql.SQLXML;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.util.function.Function;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import org.postgresql.pljava.internal.SwitchPointCache.SwitchPoint;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.Anum_pg_class_reltype;
import static org.postgresql.pljava.pg.ModelConstants.RELOID; // syscache
import static org.postgresql.pljava.pg.ModelConstants.CLASS_TUPLE_SIZE;

import org.postgresql.pljava.pg.adt.GrantAdapter;
import org.postgresql.pljava.pg.adt.NameAdapter;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGCLASS_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGNAMESPACE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGTYPE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.*;
import static org.postgresql.pljava.pg.adt.XMLAdapter.SYNTHETIC_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Qualified;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

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

	private static final Function<MethodHandle[],MethodHandle[]> s_initializer;

	/**
	 * Per-instance switch point, to be invalidated selectively
	 * by a relcache callback.
	 */
	private final SwitchPoint[] m_cacheSwitchPoint;

	final SwitchPoint cacheSwitchPoint()
	{
		return m_cacheSwitchPoint[0];
	}

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<RegClass> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return RELOID;
	}

	/* Implementation of Named, Namespaced, Owned, AccessControlled */

	private static Simple name(RegClassImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return
			t.get(Att.RELNAME, NameAdapter.SIMPLE_INSTANCE);
	}

	private static RegNamespace namespace(RegClassImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.RELNAMESPACE, REGNAMESPACE_INSTANCE);
	}

	private static RegRole owner(RegClassImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.RELOWNER, REGROLE_INSTANCE);
	}

	private static List<CatalogObject.Grant> grants(RegClassImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.RELACL, GrantAdapter.LIST_INSTANCE);
	}

	/* Implementation of RegClass */

	RegClassImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
		m_cacheSwitchPoint = new SwitchPoint[] { new SwitchPoint() };
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
		SwitchPoint sp = m_cacheSwitchPoint[0];
		if ( sp.unused() )
			return;
		TupleDescriptor.Interned[] oldTDH = m_tupDescHolder;
		sps.add(sp);

		/*
		 * Before invalidating the SwitchPoint, line up a new one (and a newly
		 * nulled tupDescHolder) for value-computing methods to find once the
		 * old SwitchPoint is invalidated.
		 */
		m_cacheSwitchPoint[0] = new SwitchPoint();
		m_tupDescHolder = null;

		/*
		 * After the old SwitchPoint gets invalidated, the old tupDescHolder,
		 * if any, can have its element nulled so the old TupleDescriptor can
		 * be collected without having to wait for the 'guardWithTest's it is
		 * bound into to be recomputed.
		 */
		if ( null != oldTDH )
		{
			postOps.add(() ->
			{
				TupleDescImpl td = (TupleDescImpl)oldTDH[0];
				if ( null == td )
					return;
				oldTDH[0] = null;
				td.invalidate();
			});
		}
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
	static final int SLOT_OFTYPE;
	static final int SLOT_TOASTRELATION;
	static final int SLOT_HASINDEX;
	static final int SLOT_ISSHARED;
	static final int SLOT_PERSISTENCE;
	static final int SLOT_KIND;
	static final int SLOT_NATTRIBUTES;
	static final int SLOT_CHECKS;
	static final int SLOT_HASRULES;
	static final int SLOT_HASTRIGGERS;
	static final int SLOT_HASSUBCLASS;
	static final int SLOT_ROWSECURITY;
	static final int SLOT_FORCEROWSECURITY;
	static final int SLOT_ISPOPULATED;
	static final int SLOT_REPLIDENT;
	static final int SLOT_ISPARTITION;
	static final int SLOT_OPTIONS;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(RegClassImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> o.m_cacheSwitchPoint[0])
			.withSlots(o -> o.m_slots)

			.withCandidates(
				CatalogObjectImpl.Addressed.class.getDeclaredMethods())
			.withReceiverType(CatalogObjectImpl.Addressed.class)
			.withDependent("cacheTuple", SLOT_TUPLE)

			.withCandidates(RegClassImpl.class.getDeclaredMethods())
			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReceiverType(CatalogObjectImpl.Namespaced.class)
			.withReturnType(null)
			.withDependent( "namespace", SLOT_NAMESPACE)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent(     "owner", SLOT_OWNER)
			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withDependent(    "grants", SLOT_ACL)

			.withReceiverType(null)
			.withDependent( "tupleDescriptor", SLOT_TUPLEDESCRIPTOR  = i++)
			.withDependent(            "type", SLOT_TYPE             = i++)
			.withDependent(          "ofType", SLOT_OFTYPE           = i++)
			.withDependent(   "toastRelation", SLOT_TOASTRELATION    = i++)
			.withDependent(        "hasIndex", SLOT_HASINDEX         = i++)
			.withDependent(        "isShared", SLOT_ISSHARED         = i++)
			.withDependent(     "persistence", SLOT_PERSISTENCE      = i++)
			.withDependent(            "kind", SLOT_KIND             = i++)
			.withDependent(     "nAttributes", SLOT_NATTRIBUTES      = i++)
			.withDependent(          "checks", SLOT_CHECKS           = i++)
			.withDependent(        "hasRules", SLOT_HASRULES         = i++)
			.withDependent(     "hasTriggers", SLOT_HASTRIGGERS      = i++)
			.withDependent(     "hasSubclass", SLOT_HASSUBCLASS      = i++)
			.withDependent(     "rowSecurity", SLOT_ROWSECURITY      = i++)
			.withDependent("forceRowSecurity", SLOT_FORCEROWSECURITY = i++)
			.withDependent(     "isPopulated", SLOT_ISPOPULATED      = i++)
			.withDependent( "replicaIdentity", SLOT_REPLIDENT        = i++)
			.withDependent(     "isPartition", SLOT_ISPARTITION      = i++)
			.withDependent(         "options", SLOT_OPTIONS          = i++)

			.build();
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute RELNAME;
		static final Attribute RELNAMESPACE;
		static final Attribute RELOWNER;
		static final Attribute RELACL;
		static final Attribute RELOFTYPE;
		static final Attribute RELTOASTRELID;
		static final Attribute RELHASINDEX;
		static final Attribute RELISSHARED;
		static final Attribute RELPERSISTENCE;
		static final Attribute RELKIND;
		static final Attribute RELNATTS;
		static final Attribute RELCHECKS;
		static final Attribute RELHASRULES;
		static final Attribute RELHASTRIGGERS;
		static final Attribute RELHASSUBCLASS;
		static final Attribute RELROWSECURITY;
		static final Attribute RELFORCEROWSECURITY;
		static final Attribute RELISPOPULATED;
		static final Attribute RELREPLIDENT;
		static final Attribute RELISPARTITION;
		static final Attribute RELOPTIONS;
		static final Attribute RELPARTBOUND;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"relname",
				"relnamespace",
				"relowner",
				"relacl",
				"reloftype",
				"reltoastrelid",
				"relhasindex",
				"relisshared",
				"relpersistence",
				"relkind",
				"relnatts",
				"relchecks",
				"relhasrules",
				"relhastriggers",
				"relhassubclass",
				"relrowsecurity",
				"relforcerowsecurity",
				"relispopulated",
				"relreplident",
				"relispartition",
				"reloptions",
				"relpartbound"
			).iterator();

			RELNAME             = itr.next();
			RELNAMESPACE        = itr.next();
			RELOWNER            = itr.next();
			RELACL              = itr.next();
			RELOFTYPE           = itr.next();
			RELTOASTRELID       = itr.next();
			RELHASINDEX         = itr.next();
			RELISSHARED         = itr.next();
			RELPERSISTENCE      = itr.next();
			RELKIND             = itr.next();
			RELNATTS            = itr.next();
			RELCHECKS           = itr.next();
			RELHASRULES         = itr.next();
			RELHASTRIGGERS      = itr.next();
			RELHASSUBCLASS      = itr.next();
			RELROWSECURITY      = itr.next();
			RELFORCEROWSECURITY = itr.next();
			RELISPOPULATED      = itr.next();
			RELREPLIDENT        = itr.next();
			RELISPARTITION      = itr.next();
			RELOPTIONS          = itr.next();
			RELPARTBOUND        = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	/**
	 * Return the tuple descriptor for this relation, wrapped in a one-element
	 * array, which is also stored in {@code m_tupDescHolder}.
	 *<p>
	 * The tuple descriptor for a relation can be retrieved from the PostgreSQL
	 * {@code relcache}, or from the {@code typcache} if the relation has an
	 * associated type; it's the same descriptor, and the
	 * latter gets it from the former. Going through the {@code relcache} is
	 * fussier, involving the lock manager every time, while using the
	 * {@code typcache} can avoid that except in its cache-miss case.
	 *<p>
	 * Here, for every relation other than {@code pg_class} itself, we will
	 * rely on the corresponding {@code RegType}, if there is one, to do
	 * the work. There is a bit
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
	 * However, when the relation <em>is</em> {@code pg_class} itself, or is one
	 * of the relation kinds without an associated type entry, we rely
	 * on a bespoke JNI method to get the descriptor from the {@code relcache}.
	 * The {@code pg_class} case occurs when we are looking up the descriptor to
	 * interpret our own cache tuples, and the normal case's {@code type()} call
	 * won't work before that's available.
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
		 * use type() to find the associated RegType and let it, if valid,
		 * do the work.
		 */
		if ( CLASSID != o )
		{
			RegType t = o.type();
			if ( t.isValid() )
			{
				t.tupleDescriptor(); // side effect: writes o.m_tupDescHolder
				return o.m_tupDescHolder;
			}
		}

		/*
		 * May be the bootstrap case, looking up the pg_class tuple descriptor,
		 * or just a relation kind that does not have an associate type entry.
		 * If we got here we need it, so we can call the Cataloged constructor
		 * directly, rather than fromByteBuffer (which would first check whether
		 * we need it, and bump its reference count only if so). Called
		 * directly, the constructor expects the count already bumped, which
		 * the _tupDescBootstrap method will have done for us.
		 */
		ByteBuffer bb = _tupDescBootstrap(o.oid());
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

		/*
		 * Regular relations have a valid reltype, but other kinds of RegClass
		 * (index, toast table) do not.
		 */
		if ( t.isValid() )
			((RegTypeImpl)t).dualHandshake(o);

		return t;
	}

	private static RegType ofType(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELOFTYPE, REGTYPE_INSTANCE);
	}

	private static RegClass toastRelation(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELTOASTRELID, REGCLASS_INSTANCE);
	}

	private static boolean hasIndex(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELHASINDEX, BOOLEAN_INSTANCE);
	}

	private static boolean isShared(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELISSHARED, BOOLEAN_INSTANCE);
	}

	private static Persistence persistence(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return persistenceFromCatalog(
			s.get(Att.RELPERSISTENCE, INT1_INSTANCE));
	}

	private static Kind kind(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return kindFromCatalog(
			s.get(Att.RELKIND, INT1_INSTANCE));
	}

	private static short nAttributes(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELNATTS, INT2_INSTANCE);
	}

	private static short checks(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELCHECKS, INT2_INSTANCE);
	}

	private static boolean hasRules(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELHASRULES, BOOLEAN_INSTANCE);
	}

	private static boolean hasTriggers(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELHASTRIGGERS, BOOLEAN_INSTANCE);
	}

	private static boolean hasSubclass(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELHASSUBCLASS, BOOLEAN_INSTANCE);
	}

	private static boolean rowSecurity(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELROWSECURITY, BOOLEAN_INSTANCE);
	}

	private static boolean forceRowSecurity(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(Att.RELFORCEROWSECURITY, BOOLEAN_INSTANCE);
	}

	private static boolean isPopulated(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELISPOPULATED, BOOLEAN_INSTANCE);
	}

	private static ReplicaIdentity replicaIdentity(RegClassImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return replicaIdentityFromCatalog(
			s.get(Att.RELREPLIDENT, INT1_INSTANCE));
	}

	private static boolean isPartition(RegClassImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELISPARTITION, BOOLEAN_INSTANCE);
	}

	private static Map<Simple,String> options(RegClassImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.RELOPTIONS, ArrayAdapters.RELOPTIONS_INSTANCE);
	}

	/* API methods */

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
		try
		{
			MethodHandle h = m_slots[SLOT_OFTYPE];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
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
		try
		{
			MethodHandle h = m_slots[SLOT_TOASTRELATION];
			return (RegClass)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean hasIndex()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HASINDEX];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean isShared()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ISSHARED];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Persistence persistence()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_PERSISTENCE];
			return (Persistence)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Kind kind()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_KIND];
			return (Kind)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public short nAttributes()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_NATTRIBUTES];
			return (short)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public short checks()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CHECKS];
			return (short)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean hasRules()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HASRULES];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean hasTriggers()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HASTRIGGERS];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean hasSubclass()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HASSUBCLASS];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean rowSecurity()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ROWSECURITY];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean forceRowSecurity()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_FORCEROWSECURITY];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean isPopulated()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ISPOPULATED];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public ReplicaIdentity replicaIdentity()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_REPLIDENT];
			return (ReplicaIdentity)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean isPartition()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ISPARTITION];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	// rewrite
	// frozenxid
	// minmxid

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

	@Override
	public SQLXML partitionBound()
	{
		/*
		 * Because of the JDBC rules that an SQLXML instance lasts no longer
		 * than one transaction and can only be read once, it is not a good
		 * candidate for caching. We will just fetch a new one from the cached
		 * tuple as needed.
		 */
		TupleTableSlot s = cacheTuple();
		return s.get(Att.RELPARTBOUND, SYNTHETIC_INSTANCE);
	}

	private static Persistence persistenceFromCatalog(byte b)
	{
		switch ( b )
		{
		case (byte)'p': return Persistence.PERMANENT;
		case (byte)'u': return Persistence.UNLOGGED;
		case (byte)'t': return Persistence.TEMPORARY;
		}
		throw unchecked(new SQLException(
			"unrecognized Persistence type '" + (char)b + "' in catalog",
			"XX000"));
	}

	private static Kind kindFromCatalog(byte b)
	{
		switch ( b )
		{
		case (byte)'r': return Kind.TABLE;
		case (byte)'i': return Kind.INDEX;
		case (byte)'S': return Kind.SEQUENCE;
		case (byte)'t': return Kind.TOAST;
		case (byte)'v': return Kind.VIEW;
		case (byte)'m': return Kind.MATERIALIZED_VIEW;
		case (byte)'c': return Kind.COMPOSITE_TYPE;
		case (byte)'f': return Kind.FOREIGN_TABLE;
		case (byte)'p': return Kind.PARTITIONED_TABLE;
		case (byte)'I': return Kind.PARTITIONED_INDEX;
		}
		throw unchecked(new SQLException(
			"unrecognized Kind type '" + (char)b + "' in catalog",
			"XX000"));
	}

	private static ReplicaIdentity replicaIdentityFromCatalog(byte b)
	{
		switch ( b )
		{
		case (byte)'d': return ReplicaIdentity.DEFAULT;
		case (byte)'n': return ReplicaIdentity.NOTHING;
		case (byte)'f': return ReplicaIdentity.ALL;
		case (byte)'i': return ReplicaIdentity.INDEX;
		}
		throw unchecked(new SQLException(
			"unrecognized ReplicaIdentity type '" + (char)b + "' in catalog",
			"XX000"));
	}
}
