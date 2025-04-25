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
import static java.util.Objects.requireNonNull;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.internal.Checked;
import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import org.postgresql.pljava.internal.SwitchPointCache.SwitchPoint;
import static org.postgresql.pljava.internal.SwitchPointCache.setConstant;

import org.postgresql.pljava.model.*;
import static org.postgresql.pljava.model.MemoryContext.JavaMemoryContext;

import static org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.MemoryContextImpl.allocatingIn;
import static org.postgresql.pljava.pg.ModelConstants.*;
import static org.postgresql.pljava.pg.TupleDescImpl.Blessed;
import static org.postgresql.pljava.pg.TupleDescImpl.Ephemeral;
import static org.postgresql.pljava.pg.TupleTableSlotImpl.heapTupleGetLightSlot;

import org.postgresql.pljava.pg.adt.GrantAdapter;
import org.postgresql.pljava.pg.adt.NameAdapter;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGCOLLATION_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.*;

import org.postgresql.pljava.annotation.BaseUDT.Alignment;
import org.postgresql.pljava.annotation.BaseUDT.Storage;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

import static org.postgresql.pljava.internal.UncheckedException.unchecked;

abstract class AttributeImpl extends Addressed<RegClass>
implements
	Nonshared<RegClass>, Named<Simple>,
	AccessControlled<CatalogObject.Grant.OnAttribute>, Attribute
{
	// syscache id is ATTNUM; two key components: attrelid, attnum
	// remember to account for ATTRIBUTE_FIXED_PART_SIZE when from tupledesc

	abstract SwitchPoint cacheSwitchPoint();

	private static final UnaryOperator<MethodHandle[]> s_initializer;

	/* Implementation of CatalogObject */

	@Override
	public <T extends CatalogObject.Addressed<T>> T of(RegClass.Known<T> c)
	{
		throw new UnsupportedOperationException("of() on an Attribute");
	}

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<RegClass> classId()
	{
		return RegClass.CLASSID;
	}

	/**
	 * Overrides {@code cacheDescriptor} to correctly return the descriptor
	 * for {@code pg_attribute}.
	 *<p>
	 * Because of the unusual addressing scheme for attributes, where
	 * the {@code classId} refers to {@code pg_class}, the inherited method
	 * would return the wrong descriptor.
	 */
	@Override
	TupleDescriptor cacheDescriptor()
	{
		return CLASS.tupleDescriptor();
	}

	/**
	 * An attribute exists for as long as it has a non-invalidated containing
	 * tuple descriptor that can supply a byte buffer, whether or not it appears
	 * in the catalog.
	 */
	@Override
	public boolean exists()
	{
		try
		{
			return null != rawBuffer();
		}
		catch ( IllegalStateException e )
		{
			return false;
		}
	}

	/**
	 * Fetch the entire tuple for this attribute from the PG {@code syscache}.
	 *<p>
	 * The containing {@code TupleDescriptor} supplies a
	 * {@link #partialTuple partialTuple} covering the first
	 * {@code ATTRIBUTE_FIXED_PART_SIZE} bytes of this, where most often-needed
	 * properties are found, so this will be called only on requests for
	 * the properties that aren't found in that prefix.
	 */
	private static TupleTableSlot cacheTuple(AttributeImpl o)
	{
		ByteBuffer heapTuple;

		/*
		 * See this method in CatalogObjectImpl.Addressed for more on the choice
		 * of memory context and lifespan.
		 */
		try ( Checked.AutoCloseable<RuntimeException> ac =
			allocatingIn(JavaMemoryContext()) )
		{
			heapTuple = _searchSysCacheCopy2(ATTNUM, o.oid(), o.subId());
			if ( null == heapTuple )
				return null;
		}
		return heapTupleGetLightSlot(o.cacheDescriptor(), heapTuple, null);
	}

	/*
	 * The super implementation nulls the TUPLE slot permanently; this
	 * class has RAWBUFFER and PARTIALTUPLE slots used similarly, so null those
	 * too. Transient will in turn override this and null nothing at all; its
	 * instances have the invalid Oid as a matter of course.
	 *
	 * It may well be that no circumstances exist where this version is called.
	 */
	@Override
	void makeInvalidInstance(MethodHandle[] slots)
	{
		super.makeInvalidInstance(slots);
		setConstant(slots, SLOT_RAWBUFFER, null);
		setConstant(slots, SLOT_PARTIALTUPLE, null);
	}

	/* Implementation of Named and AccessControlled */

	private static Simple name(AttributeImpl o) throws SQLException
	{
		TupleTableSlot t = o.partialTuple();
		return
			t.get(t.descriptor().sqlGet(Anum_pg_attribute_attname),
				NameAdapter.SIMPLE_INSTANCE);
	}

	private static List<CatalogObject.Grant> grants(AttributeImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.ATTACL, GrantAdapter.LIST_INSTANCE);
	}

	/* Implementation of Attribute */

	AttributeImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_RAWBUFFER;
	static final int SLOT_PARTIALTUPLE;

	static final int SLOT_TYPE;
	static final int SLOT_LENGTH;
	static final int SLOT_DIMENSIONS;
	// static final int SLOT_CACHEDOFFSET; -- read fresh every time, no slot
	static final int SLOT_BYVALUE;
	static final int SLOT_ALIGNMENT;
	static final int SLOT_STORAGE;
	// static final int SLOT_COMPRESSION; -- add this
	static final int SLOT_NOTNULL;
	static final int SLOT_HASDEFAULT;
	static final int SLOT_HASMISSING;
	static final int SLOT_IDENTITY;
	static final int SLOT_GENERATED;
	static final int SLOT_DROPPED;
	static final int SLOT_LOCAL;
	static final int SLOT_INHERITANCECOUNT;
	static final int SLOT_COLLATION;
	// static final int SLOT_OPTIONS; -- add this
	// static final int SLOT_FDWOPTIONS; -- add this
	// static final int SLOT_MISSINGVALUE; -- add this

	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(AttributeImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(AttributeImpl::cacheSwitchPoint)
			.withSlots(o -> o.m_slots)
			.withCandidates(AttributeImpl.class.getDeclaredMethods())

			/*
			 * First declare some slots whose consuming API methods are found
			 * on inherited interfaces. This requires some adjustment of method
			 * types so that run-time adaptation isn't needed.
			 */
			.withReceiverType(CatalogObjectImpl.Addressed.class)
			.withDependent("cacheTuple", SLOT_TUPLE)

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)

			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withReturnType(null) // cancel adjustment from above
			.withDependent(    "grants", SLOT_ACL)

			/*
			 * Next come slots where the compute and API methods are here.
			 */
			.withReceiverType(null)
			.withDependent(   "rawBuffer", SLOT_RAWBUFFER    = i++)
			.withDependent("partialTuple", SLOT_PARTIALTUPLE = i++)

			.withDependent(            "type", SLOT_TYPE             = i++)
			.withDependent(          "length", SLOT_LENGTH           = i++)
			.withDependent(      "dimensions", SLOT_DIMENSIONS       = i++)
			.withDependent(         "byValue", SLOT_BYVALUE          = i++)
			.withDependent(       "alignment", SLOT_ALIGNMENT        = i++)
			.withDependent(         "storage", SLOT_STORAGE          = i++)
			.withDependent(         "notNull", SLOT_NOTNULL          = i++)
			.withDependent(      "hasDefault", SLOT_HASDEFAULT       = i++)
			.withDependent(      "hasMissing", SLOT_HASMISSING       = i++)
			.withDependent(        "identity", SLOT_IDENTITY         = i++)
			.withDependent(       "generated", SLOT_GENERATED        = i++)
			.withDependent(         "dropped", SLOT_DROPPED          = i++)
			.withDependent(           "local", SLOT_LOCAL            = i++)
			.withDependent("inheritanceCount", SLOT_INHERITANCECOUNT = i++)
			.withDependent(       "collation", SLOT_COLLATION        = i++)

			.build();
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute ATTACL;
		static final Attribute ATTNDIMS;
		static final Attribute ATTSTORAGE;
		static final Attribute ATTHASDEF;
		static final Attribute ATTHASMISSING;
		static final Attribute ATTIDENTITY;
		static final Attribute ATTGENERATED;
		static final Attribute ATTISLOCAL;
		static final Attribute ATTINHCOUNT;
		static final Attribute ATTCOLLATION;

		static
		{
			Iterator<Attribute> itr = CLASS.tupleDescriptor().project(
				"attacl",
				"attndims",
				"attstorage",
				"atthasdef",
				"atthasmissing",
				"attidentity",
				"attgenerated",
				"attislocal",
				"attinhcount",
				"attcollation"
			).iterator();

			ATTACL        = itr.next();
			ATTNDIMS      = itr.next();
			ATTSTORAGE    = itr.next();
			ATTHASDEF     = itr.next();
			ATTHASMISSING = itr.next();
			ATTIDENTITY   = itr.next();
			ATTGENERATED  = itr.next();
			ATTISLOCAL    = itr.next();
			ATTINHCOUNT   = itr.next();
			ATTCOLLATION  = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	/**
	 * Obtain the raw, heap-formatted readable byte buffer over this attribute.
	 *<p>
	 * Because this is the {@code AttributeImpl} class, a few of the critical
	 * properties will be read directly via ByteBuffer methods, rather than
	 * using the {@code TupleTableSlot.get} API where a working
	 * {@code Attribute} must be supplied.
	 *<p>
	 * The raw buffer is what the containing {@code TupleDescImpl} supplies, and
	 * it cuts off at {@code ATTRIBUTE_FIXED_PART_SIZE}. Retrieving properties
	 * beyond that point will require using {@code cacheTuple()} to fetch
	 * the whole tuple from the {@code syscache}.
	 */
	private static ByteBuffer rawBuffer(AttributeImpl o)
	{
		return
			((TupleDescImpl)o.containingTupleDescriptor()).slice(o.subId() - 1);
	}

	/**
	 * A {@code TupleTableSlot} formed over the {@link #rawBuffer rawBuffer},
	 * which holds only the first {@code ATTRIBUTE_FIXED_PART_SIZE} bytes of
	 * the full {@code pg_attribute} tuple.
	 *<p>
	 * Supports the regular {@code TupleTableSlot.get} API for most properties
	 * (the ones that appear in the first {@code ATTRIBUTE_FIXED_PART_SIZE}
	 * bytes, and aren't needed for {@code TupleTableSlot.get} itself to work).
	 */
	private static TupleTableSlot partialTuple(AttributeImpl o)
	{
		return new TupleTableSlotImpl.Heap(
			CLASS, o.cacheDescriptor(), o.rawBuffer(), null);
	}

	private static RegType type(AttributeImpl o)
	{
		ByteBuffer b = o.rawBuffer();
		assert 4 == SIZEOF_pg_attribute_atttypid : "sizeof atttypid changed";
		assert 4 == SIZEOF_pg_attribute_atttypmod : "sizeof atttypmod changed";
		return
			CatalogObjectImpl.Factory.formMaybeModifiedType(
				b.getInt(OFFSET_pg_attribute_atttypid),
				b.getInt(OFFSET_pg_attribute_atttypmod));
	}

	private static short length(AttributeImpl o)
	{
		ByteBuffer b = o.rawBuffer();
		assert 2 == SIZEOF_pg_attribute_attlen : "sizeof attlen changed";
		return b.getShort(OFFSET_pg_attribute_attlen);
	}

	private static int dimensions(AttributeImpl o) throws SQLException
	{
		TupleTableSlot s = o.partialTuple();
		return s.get(Att.ATTNDIMS, INT4_INSTANCE);
	}

	private static boolean byValue(AttributeImpl o)
	{
		ByteBuffer b = o.rawBuffer();
		assert 1 == SIZEOF_pg_attribute_attbyval : "sizeof attbyval changed";
		return 0 != b.get(OFFSET_pg_attribute_attbyval);
	}

	private static Alignment alignment(AttributeImpl o)
	{
		ByteBuffer b = o.rawBuffer();
		assert 1 == SIZEOF_pg_attribute_attalign : "sizeof attalign changed";
		return alignmentFromCatalog(b.get(OFFSET_pg_attribute_attalign));
	}

	private static Storage storage(AttributeImpl o) throws SQLException
	{
		TupleTableSlot s = o.partialTuple();
		return
			storageFromCatalog(
				s.get(Att.ATTSTORAGE, INT1_INSTANCE));
	}

	private static boolean notNull(AttributeImpl o)
	{
		ByteBuffer b = o.rawBuffer();
		assert
			1 == SIZEOF_pg_attribute_attnotnull : "sizeof attnotnull changed";
		return 0 != b.get(OFFSET_pg_attribute_attnotnull);
	}

	private static boolean hasDefault(AttributeImpl o) throws SQLException
	{
		TupleTableSlot s = o.partialTuple();
		return s.get(Att.ATTHASDEF, BOOLEAN_INSTANCE);
	}

	private static boolean hasMissing(AttributeImpl o)  throws SQLException
	{ // not 9.5
		TupleTableSlot s = o.partialTuple();
		return s.get(Att.ATTHASMISSING, BOOLEAN_INSTANCE);
	}

	private static Identity identity(AttributeImpl o)  throws SQLException
	{ // not 9.5
		TupleTableSlot s = o.partialTuple();
		byte v = s.get(Att.ATTIDENTITY, INT1_INSTANCE);
		return identityFromCatalog(v);
	}

	private static Generated generated(AttributeImpl o)  throws SQLException
	{ // not 9.5
		TupleTableSlot s = o.partialTuple();
		byte v = s.get(Att.ATTGENERATED, INT1_INSTANCE);
		return generatedFromCatalog(v);
	}

	private static boolean dropped(AttributeImpl o)
	{
		ByteBuffer b = o.rawBuffer();
		assert
			1 == SIZEOF_pg_attribute_attisdropped
			: "sizeof attisdropped changed";
		return 0 != b.get(OFFSET_pg_attribute_attisdropped);
	}

	private static boolean local(AttributeImpl o) throws SQLException
	{
		TupleTableSlot s = o.partialTuple();
		return s.get(Att.ATTISLOCAL, BOOLEAN_INSTANCE);
	}

	private static int inheritanceCount(AttributeImpl o) throws SQLException
	{
		TupleTableSlot s = o.partialTuple();
		return s.get(Att.ATTINHCOUNT, INT4_INSTANCE);
	}

	private static RegCollation collation(AttributeImpl o) throws SQLException
	{
		TupleTableSlot s = o.partialTuple();
		return s.get(Att.ATTCOLLATION, REGCOLLATION_INSTANCE);
	}

	/* private methods using cache slots like API methods do */

	private ByteBuffer rawBuffer()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_RAWBUFFER];
			return (ByteBuffer)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	private TupleTableSlot partialTuple()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_PARTIALTUPLE];
			return (TupleTableSlot)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
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
	public short length()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_LENGTH];
			return (short)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public int dimensions()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_DIMENSIONS];
			return (int)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public int cachedOffset() // perhaps useful for heap case?
	{
		ByteBuffer b = rawBuffer();
		assert 4 == SIZEOF_pg_attribute_attcacheoff
			: "sizeof attcacheoff changed";
		return b.getInt(OFFSET_pg_attribute_attcacheoff);
	}

	@Override
	public boolean byValue()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_BYVALUE];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Alignment alignment()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ALIGNMENT];
			return (Alignment)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Storage storage()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_STORAGE];
			return (Storage)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean notNull()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_NOTNULL];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean hasDefault()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HASDEFAULT];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean hasMissing() // not 9.5
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HASMISSING];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Identity identity() // not 9.5
	{
		try
		{
			MethodHandle h = m_slots[SLOT_IDENTITY];
			return (Identity)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Generated generated() // not 9.5
	{
		try
		{
			MethodHandle h = m_slots[SLOT_GENERATED];
			return (Generated)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean dropped()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HASMISSING];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean local()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_LOCAL];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public int inheritanceCount()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_INHERITANCECOUNT];
			return (int)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegCollation collation()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_COLLATION];
			return (RegCollation)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	// options
	// fdwoptions
	// missingValue

	@Override
	public TupleDescriptor containingTupleDescriptor()
	{
		return relation().tupleDescriptor();
	}

	/**
	 * An attribute that belongs to a full-fledged cataloged composite type.
	 *<p>
	 * It holds a reference to the relation that defines the composite type
	 * layout. While that can always be found from the class and object IDs
	 * of the object address, that is too much fuss for as often as
	 * {@code relation()} is called.
	 */
	static class Cataloged extends AttributeImpl
	{
		private final RegClassImpl m_relation;

		Cataloged(RegClassImpl relation)
		{
			m_relation = requireNonNull(relation);
		}

		@Override
		SwitchPoint cacheSwitchPoint()
		{
			return m_relation.cacheSwitchPoint();
		}

		@Override
		public RegClass relation()
		{
			return m_relation;
		}
	}

	/**
	 * An attribute that belongs to a transient {@code TupleDescriptor}, not
	 * to any relation in the catalog (and therefore isn't really
	 * a {@code CatalogObject}, though it still pretends to be one).
	 *<p>
	 * For now, this is simply a subclass of {@code AttributeImpl} to inherit
	 * most of the same machinery, and simply overrides and disables the methods
	 * of a real {@code CatalogObject}. In an alternative, it could be an
	 * independent implementation of the {@code Attribute} interface, but that
	 * could require more duplication of implementation. A cost of this
	 * implementation is that every instance will carry around one unused
	 * {@code CatalogObjectImpl.m_objectAddress} field.
	 */
	static class Transient extends AttributeImpl
	{
		private static final RegClass s_invalidClass =
			CatalogObjectImpl.Factory.staticFormObjectId(
				RegClass.CLASSID, InvalidOid);

		private final TupleDescriptor m_containingTupleDescriptor;
		private final int m_attnum;

		SwitchPoint cacheSwitchPoint()
		{
			return
				((RegTypeImpl)m_containingTupleDescriptor.rowType())
					.cacheSwitchPoint();
		}

		Transient(TupleDescriptor td, int attnum)
		{
			m_containingTupleDescriptor = requireNonNull(td);
			assert 0 < attnum : "nonpositive attnum in transient attribute";
			m_attnum = attnum;
		}

		/*
		 * Do no nulling of slots (not even what the superclass method does)
		 * when created with the invalid Oid. *All* Transient instances have
		 * the invalid Oid!
		 */
		@Override
		void makeInvalidInstance(MethodHandle[] slots)
		{
		}

		@Override
		public int oid()
		{
			return InvalidOid;
		}

		@Override
		public int classOid()
		{
			return RegClass.CLASSID.oid();
		}

		@Override
		public int subId()
		{
			return m_attnum;
		}

		/**
		 * Returns true for an attribute of a transient {@code TupleDescriptor},
		 * even though {@code oid()} will return {@code InvalidOid}.
		 *<p>
		 * It's not clear any other convention would be less weird.
		 */
		@Override
		public boolean isValid()
		{
			return true;
		}

		/**
		 * Equality test for {@code AttributeImpl.Transient}.
		 *<p>
		 * Instances of {@code Transient} are used both in
		 * {@link Ephemeral Ephemeral} tuple descriptors and in
		 * {@link Blessed Blessed} ones. This method will not treat as equal
		 * attributes belonging to any two distinct ephemeral descriptors, and
		 * naturally the attribute at this attribute's position in this
		 * attribute's containing descriptor can only be this attribute, so
		 * reference equality is necessary and sufficient.
		 *<p>
		 * Reference equality also suffices for the {@code Blessed} case, as
		 * PostgreSQL at present keeps such row types unique for the life of the
		 * backend and does not invalidate them; the first
		 * {@code TupleDescriptor} returned by the corresponding {@code RegType}
		 * will therefore be the only one it can return.
		 *<p>
		 * Should the (weakly cached) {@code RegType} instance be GC'd and a
		 * new one later instantiated for the same row type, a different tuple
		 * descriptor could result, but a {@code TupleDescriptorImpl.Blessed}
		 * holds a strong reference to its row type, which therefore can't go
		 * unreachable until the tuple descriptor has also; at any given time
		 * there can be no more than one in play.
		 */
		@Override
		public boolean equals(Object other)
		{
			return this == other;
		}

		/**
		 * Hash code for {@code AttributeImpl.Transient}.
		 *<p>
		 * As reference equality is used for the {@code equals} test,
		 * {@code System.identityHashCode} is used here.
		 */
		@Override
		public int hashCode()
		{
			return System.identityHashCode(this);
		}

		@Override
		public RegClass relation()
		{
			return s_invalidClass;
		}

		@Override
		public TupleDescriptor containingTupleDescriptor()
		{
			return m_containingTupleDescriptor;
		}
	}

	/**
	 * A transient attribute belonging to a synthetic tuple descriptor with
	 * one element of a specified {@code RegType}.
	 *<p>
	 * Such a singleton tuple descriptor allows the {@code TupleTableSlot} API
	 * to be used as-is for related applications like array element access.
	 *<p>
	 * Most methods simply delegate to the associated RegType.
	 */
	static class OfType extends Transient
	{
		private final RegType m_type;

		OfType(TupleDescriptor td, RegType type)
		{
			super(td, 1);
			m_type = requireNonNull(type);
		}

		@Override
		public boolean exists()
		{
			return true;
		}

		@Override
		public Simple name()
		{
			return Simple.None.INSTANCE;
		}

		@Override
		public RegType type()
		{
			return m_type;
		}

		@Override
		public short length()
		{
			return m_type.length();
		}

		@Override
		public int dimensions()
		{
			return m_type.dimensions();
		}

		@Override
		public int cachedOffset() // perhaps useful for heap case?
		{
			return -1;
		}

		@Override
		public boolean byValue()
		{
			return m_type.byValue();
		}

		@Override
		public Alignment alignment()
		{
			return m_type.alignment();
		}

		@Override
		public Storage storage()
		{
			return m_type.storage();
		}

		@Override
		public boolean notNull()
		{
			return m_type.notNull();
		}

		@Override
		public boolean hasDefault()
		{
			return false;
		}

		@Override
		public boolean hasMissing() // not 9.5
		{
			return false;
		}

		@Override
		public Identity identity() // not 9.5
		{
			return Identity.INAPPLICABLE;
		}

		@Override
		public Generated generated() // not 9.5
		{
			return Generated.INAPPLICABLE;
		}

		@Override
		public boolean dropped()
		{
			return false;
		}

		@Override
		public boolean local()
		{
			return true;
		}

		@Override
		public int inheritanceCount()
		{
			return 0;
		}

		@Override
		public RegCollation collation()
		{
			return m_type.collation();
		}
	}

	private static Identity identityFromCatalog(byte b)
	{
		switch ( b )
		{
		case (byte)'\0': return Identity.INAPPLICABLE;
		case (byte) 'a': return Identity.GENERATED_ALWAYS;
		case (byte) 'd': return Identity.GENERATED_BY_DEFAULT;
		}
		throw unchecked(new SQLException(
			"unrecognized Identity '" + (char)b + "' in catalog", "XX000"));
	}

	private static Generated generatedFromCatalog(byte b)
	{
		switch ( b )
		{
		case (byte)'\0': return Generated.INAPPLICABLE;
		case (byte) 's': return Generated.STORED;
		}
		throw unchecked(new SQLException(
			"unrecognized Generated '" + (char)b + "' in catalog", "XX000"));
	}
}
