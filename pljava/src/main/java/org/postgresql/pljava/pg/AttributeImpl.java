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

import java.sql.SQLException;

import static java.util.Objects.requireNonNull;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.internal.Checked;
import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import static org.postgresql.pljava.internal.SwitchPointCache.setConstant;

import org.postgresql.pljava.model.*;
import static org.postgresql.pljava.model.MemoryContext.JavaMemoryContext;

import static org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.MemoryContextImpl.allocatingIn;
import static org.postgresql.pljava.pg.ModelConstants.*;
import static org.postgresql.pljava.pg.TupleDescImpl.Ephemeral;
import static org.postgresql.pljava.pg.TupleTableSlotImpl.heapTupleGetLightSlot;

import org.postgresql.pljava.pg.adt.NameAdapter;

import org.postgresql.pljava.annotation.BaseUDT.Alignment;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

import static org.postgresql.pljava.internal.UncheckedException.unchecked;

abstract class AttributeImpl extends Addressed<RegClass>
implements
	Nonshared<RegClass>, Named<Simple>,
	AccessControlled<CatalogObject.Grant.OnAttribute>, Attribute
{
	abstract SwitchPoint cacheSwitchPoint();

	private static UnaryOperator<MethodHandle[]> s_initializer;

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

	private static Simple name(AttributeImpl o) throws SQLException
	{
		TupleTableSlot t = o.partialTuple();
		return
			t.get(t.descriptor().sqlGet(Anum_pg_attribute_attname),
				NameAdapter.SIMPLE_INSTANCE);
	}

	AttributeImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_RAWBUFFER;
	static final int SLOT_PARTIALTUPLE;

	static final int SLOT_TYPE;
	static final int SLOT_LENGTH;
	static final int SLOT_BYVALUE;
	static final int SLOT_ALIGNMENT;

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

			/*
			 * Next come slots where the compute and API methods are here.
			 */
			.withReceiverType(null)
			.withDependent(   "rawBuffer", SLOT_RAWBUFFER    = i++)
			.withDependent("partialTuple", SLOT_PARTIALTUPLE = i++)

			.withDependent(            "type", SLOT_TYPE             = i++)
			.withDependent(          "length", SLOT_LENGTH           = i++)
			.withDependent(         "byValue", SLOT_BYVALUE          = i++)
			.withDependent(       "alignment", SLOT_ALIGNMENT        = i++)

			.build();
		NSLOTS = i;
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
	public TupleDescriptor containingTupleDescriptor()
	{
		return relation().tupleDescriptor();
	}

	boolean foundIn(TupleDescriptor td)
	{
		return this == td.attributes().get(subId() - 1);
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
			return m_relation.m_cacheSwitchPoint;
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

		@Override
		public boolean equals(Object other)
		{
			if ( this == other )
				return true;
			if ( ! super.equals(other) )
				return false;
			return ! ( m_containingTupleDescriptor instanceof Ephemeral );
		}

		@Override
		public int hashCode()
		{
			if ( m_containingTupleDescriptor instanceof Ephemeral )
				return System.identityHashCode(this);
			return super.hashCode();
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

		@Override
		boolean foundIn(TupleDescriptor td)
		{
			return m_containingTupleDescriptor == td;
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
		private static final Simple s_anonymous = Simple.fromJava("?column?");

		private final RegType m_type;

		OfType(TupleDescriptor td, RegType type)
		{
			super(td, 1);
			m_type = requireNonNull(type);
		}

		@Override
		public Simple name()
		{
			return s_anonymous;
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
		public boolean byValue()
		{
			return m_type.byValue();
		}

		@Override
		public Alignment alignment()
		{
			return m_type.alignment();
		}
	}
}
