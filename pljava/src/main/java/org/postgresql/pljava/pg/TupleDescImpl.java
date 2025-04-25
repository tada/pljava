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

import org.postgresql.pljava.model.*;
import static org.postgresql.pljava.model.CharsetEncoding.SERVER_ENCODING;
import static org.postgresql.pljava.model.RegType.RECORD;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import static org.postgresql.pljava.internal.Backend.doInPG;
import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;
import org.postgresql.pljava.internal.DualState;
import org.postgresql.pljava.internal.SwitchPointCache.SwitchPoint;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.pg.TargetListImpl;
import static org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.*;
import static org.postgresql.pljava.pg.DatumUtils.addressOf;
import static org.postgresql.pljava.pg.DatumUtils.asReadOnlyNativeOrder;

import java.lang.invoke.MethodHandle;
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

import static java.lang.Math.ceil;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import static java.nio.ByteOrder.nativeOrder;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

import java.util.AbstractList;
import static java.util.Arrays.fill;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.ToIntBiFunction;

/**
 * Implementation of {@link TupleDescriptor TupleDescriptor}.
 *<p>
 * A {@link Cataloged Cataloged} descriptor corresponds to a known composite
 * type declared in the PostgreSQL catalogs; its {@link #rowType rowType} method
 * returns that type. A {@link Blessed Blessed} descriptor has been constructed
 * on the fly and then interned in the type cache, such that the type
 * {@code RECORD} and its type modifier value will identify it uniquely for
 * the life of the backend; {@code rowType} will return the corresponding
 * {@link RegTypeImpl.Blessed} instance. An {@link Ephemeral Ephemeral}
 * descriptor has been constructed ad hoc and not interned; {@code rowType} will
 * return {@link RegType#RECORD RECORD} itself, which isn't a useful identifier
 * (many such ephemeral descriptors, all different, could exist at once).
 * An ephemeral descriptor is only useful as long as a reference to it is held.
 *<p>
 * A {@code Cataloged} descriptor can be obtained from the PG {@code relcache}
 * or the {@code typcache}, should respond to cache invalidation for
 * the corresponding relation, and is reference-counted, so the count should be
 * incremented when cached here, and decremented/released if this instance
 * goes unreachable from Java.
 *<p>
 * A {@code Blessed} descriptor can be obtained from the PG {@code typcache}
 * by {@code lookup_rowtype_tupdesc}. No invalidation logic is needed, as it
 * will persist, and its identifying typmod will remain unique, for the life of
 * the backend. It may or may not be reference-counted.
 *<p>
 * An {@code Ephemeral} tuple descriptor may need to be copied out of
 * a short-lived memory context where it is found, either into a longer-lived
 * context (and invalidated when that context is), or onto the Java heap and
 * used until GC'd.
 */
abstract class TupleDescImpl extends AbstractList<Attribute>
implements TupleDescriptor
{
	private final MethodHandle m_tdH;
	private final Attribute[] m_attrs;
	private final State m_state;

	/*
	 * Implementation of Projection
	 */

	@Override // Projection
	public Projection subList(int fromIndex, int toIndex)
	{
		return TargetListImpl.subList(this, fromIndex, toIndex);
	}

	@Override // Projection
	public Projection project(Simple... names)
	{
		return TargetListImpl.project(this, names);
	}

	@Override // Projection
	public Projection project(int... indices)
	{
		return TargetListImpl.project(this, indices);
	}

	@Override // Projection
	public Projection sqlProject(int... indices)
	{
		return TargetListImpl.sqlProject(this, indices);
	}

	@Override // Projection
	public Projection project(short... indices)
	{
		return TargetListImpl.project(this, indices);
	}

	@Override // Projection
	public Projection sqlProject(short... indices)
	{
		return TargetListImpl.sqlProject(this, indices);
	}

	@Override // Projection
	public Projection project(Attribute... attrs)
	{
		return TargetListImpl.project(this, attrs);
	}

	@Override // Projection
	public Projection project(BitSet indices)
	{
		return TargetListImpl.project(this, indices);
	}

	@Override // Projection
	public Projection sqlProject(BitSet indices)
	{
		return TargetListImpl.sqlProject(this, indices);
	}

	@Override // TargetList
	public <R,X extends Throwable> R applyOver(
		Iterable<TupleTableSlot> tuples, Cursor.Function<R,X> f)
		throws X, SQLException
	{
		return TargetListImpl.applyOver(this, tuples, f);
	}

	@Override // TargetList
	public <R,X extends Throwable> R applyOver(
		TupleTableSlot tuple, Cursor.Function<R,X> f)
		throws X, SQLException
	{
		return TargetListImpl.applyOver(this, tuple, f);
	}

	/**
	 * A "getAndAdd" (with just plain memory effects, as it will only be used on
	 * the PG thread) tailored to the width of the tdrefcount field (which is,
	 * oddly, declared as C int rather than a specific-width type).
	 */
	private static final ToIntBiFunction<ByteBuffer,Integer> s_getAndAddPlain;

	private static final MethodHandle s_everNull;
	private static final MethodHandle s_throwInvalidated;

	private static ByteBuffer throwInvalidated()
	{
		throw new IllegalStateException(
			"use of stale TupleDescriptor outdated by a DDL change");
	}

	static
	{
		assert Integer.BYTES == SIZEOF_Oid : "sizeof Oid";
		assert Integer.BYTES == SIZEOF_pg_attribute_atttypmod : "sizeof typmod";

		if ( 4 == SIZEOF_TUPLEDESC_TDREFCOUNT )
		{
			s_getAndAddPlain = (b,i) ->
			{
				int count = b.getInt(OFFSET_TUPLEDESC_TDREFCOUNT);
				b.putInt(OFFSET_TUPLEDESC_TDREFCOUNT, count + i);
				return count;
			};
		}
		else
			throw new ExceptionInInitializerError(
				"Implementation needed for platform with " +
				"sizeof TupleDesc->tdrefcount = " +SIZEOF_TUPLEDESC_TDREFCOUNT);

		s_everNull = constant(ByteBuffer.class, null);

		try
		{
			s_throwInvalidated = lookup().findStatic(TupleDescImpl.class,
				"throwInvalidated", methodType(ByteBuffer.class));
		}
		catch ( ReflectiveOperationException e )
		{
			throw new ExceptionInInitializerError(e);
		}
	}

	private ByteBuffer bufferIfValid()
	{
		try
		{
			return (ByteBuffer)m_tdH.invokeExact();
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	/**
	 * Called after the {@code SwitchPoint} has been invalidated.
	 *<p>
	 * Only happens for a {@link Cataloged} descriptor, on the PG thread, as a
	 * consequence of invalidation of the {@link RegClass} that defines it.
	 */
	void invalidate()
	{
		assert threadMayEnterPG() : "TupleDescImpl slice thread";

		m_state.release();
		fill(m_attrs, null);
	}

	/**
	 * Address of the native tuple descriptor (not supported on
	 * an {@code Ephemeral} instance).
	 */
	long address() throws SQLException
	{
		try
		{
			m_state.pin();
			return m_state.address();
		}
		finally
		{
			m_state.unpin();
		}
	}

	/**
	 * Slice off the portion of the buffer representing one attribute.
	 *<p>
	 * Only called by {@code AttributeImpl}.
	 */
	ByteBuffer slice(int index)
	{
		assert threadMayEnterPG() : "TupleDescImpl slice thread";

		ByteBuffer td = bufferIfValid();

		int len = SIZEOF_FORM_PG_ATTRIBUTE;
		int off = OFFSET_TUPLEDESC_ATTRS + len * index;
		len = ATTRIBUTE_FIXED_PART_SIZE; // TupleDesc hasn't got the whole thing
		// Java 13: td.slice(off, len).order(td.order())
		ByteBuffer bnew = td.duplicate();
		bnew.position(off).limit(off + len);
		return bnew.slice().order(td.order());
	}

	/**
	 * Construct a descriptor given a {@code ByteBuffer} windowing a native one.
	 * @param td ByteBuffer over a native TupleDesc
	 * @param sp SwitchPoint that the instance will rely on to detect
	 * invalidation, or null if invalidation will not be possible.
	 * @param useState whether a native TupleDesc is associated, and therefore a
	 * State object must be used to release it on unreachability of this object.
	 * @param ctor constructor to be used for each Attribute instance. (The
	 * Attribute constructors also determine, indirectly, what SwitchPoint, if
	 * any, the Attribute instances will rely on to detect invalidation.)
	 */
	private TupleDescImpl(
		ByteBuffer td, SwitchPoint sp, boolean useState,
		BiFunction<TupleDescImpl,Integer,Attribute> ctor)
	{
		assert threadMayEnterPG() : "TupleDescImpl construction thread";

		m_state = useState ? new State(this, td) : null;

		MethodHandle c = constant(ByteBuffer.class, asReadOnlyNativeOrder(td));
		m_tdH = (null == sp) ? c : sp.guardWithTest(c, s_throwInvalidated);

		Attribute[] attrs =
			new Attribute [ (td.capacity() - OFFSET_TUPLEDESC_ATTRS)
							/ SIZEOF_FORM_PG_ATTRIBUTE ];

		for ( int i = 0 ; i < attrs.length ; ++ i )
			attrs[i] = ctor.apply(this, 1 + i);

		m_attrs = attrs;
	}

	/**
	 * Constructor used only by OfType to produce a synthetic tuple descriptor
	 * with one element of a specified RegType.
	 */
	private TupleDescImpl(RegType type)
	{
		m_state = null;
		m_tdH = s_everNull;
		m_attrs = new Attribute[] { new AttributeImpl.OfType(this, type) };
	}

	/**
	 * Return a {@code TupleDescImpl} given a byte buffer that maps a PostgreSQL
	 * {@code TupleDesc} structure.
	 *<p>
	 * This method is called from native code, and assumes the caller has not
	 * (or not knowingly) obtained the descriptor directly from the type cache,
	 * so if it is not reference-counted (its count is -1) it will be assumed
	 * unsafe to directly cache. In that case, if it represents a cataloged
	 * or interned ("blessed") descriptor, we will get one directly from the
	 * cache and return that, or if it is ephemeral, we will return one based
	 * on a defensive copy.
	 *<p>
	 * If the descriptor is reference-counted, and we use it (that is, we do not
	 * find an existing version in our cache), we increment the reference count
	 * here. That does <em>not</em> have the effect of requesting leak warnings
	 * at the exit of PostgreSQL's current resource owner, because we have every
	 * intention of hanging on to it longer, until GC or an invalidation
	 * callback tells us not to.
	 *<p>
	 * While we can just read the type oid, typmod, and reference count through
	 * the byte buffer, as long as the only caller is C code, it saves some fuss
	 * just to have it pass those values. If the C caller has the relation oid
	 * handy also, it can pass that as well and save a lookup here.
	 */
	private static TupleDescriptor fromByteBuffer(
		ByteBuffer td, int typoid, int typmod, int reloid, int refcount)
	{
		TupleDescriptor.Interned result;

		td.order(nativeOrder());

		/*
		 * Case 1: if the type is not RECORD, it's a cataloged composite type.
		 * Build an instance of Cataloged (unless the implicated RegClass has
		 * already got one).
		 */
		if ( RECORD.oid() != typoid )
		{
			RegTypeImpl t =
				(RegTypeImpl)Factory.formMaybeModifiedType(typoid, typmod);

			RegClassImpl c =
				(RegClassImpl)( InvalidOid == reloid ? t.relation()
					: Factory.staticFormObjectId(RegClass.CLASSID, reloid) );

			assert c.isValid() : "Cataloged row type without matching RegClass";

			if ( -1 == refcount ) // don't waste time on an ephemeral copy.
				return c.tupleDescriptor(); // just go get the real one.

			TupleDescriptor.Interned[] holder = c.m_tupDescHolder;
			if ( null != holder )
			{
				result = holder[0];
				assert null != result : "disagree whether RegClass has desc";
				return result;
			}

			holder = new TupleDescriptor.Interned[1];
			/*
			 * The constructor assumes the reference count has already been
			 * incremented to account for the reference constructed here.
			 */
			s_getAndAddPlain.applyAsInt(td, 1);
			holder[0] = result = new Cataloged(td, c);
			c.m_tupDescHolder = holder;
			return result;
		}

		/*
		 * Case 2: if RECORD with a modifier, it's an interned tuple type.
		 * Build an instance of Blessed (unless the implicated RegType has
		 * already got one).
		 */
		if ( -1 != typmod )
		{
			RegTypeImpl.Blessed t =
				(RegTypeImpl.Blessed)RECORD.modifier(typmod);

			if ( -1 == refcount ) // don't waste time on an ephemeral copy.
				return t.tupleDescriptor(); // just go get the real one.

			TupleDescriptor.Interned[] holder = t.m_tupDescHolder;
			if ( null != holder )
			{
				result = holder[0];
				assert null != result : "disagree whether RegType has desc";
				return result;
			}

			holder = new TupleDescriptor.Interned[1];
			/*
			 * The constructor assumes the reference count has already been
			 * incremented to account for the reference constructed here.
			 */
			s_getAndAddPlain.applyAsInt(td, 1);
			holder[0] = result = new Blessed(td, t);
			t.m_tupDescHolder = holder;
			return result;
		}

		/*
		 * Case 3: it's RECORD with no modifier, an ephemeral tuple type.
		 * Build an instance of Ephemeral unconditionally, defensively copying
		 * the descriptor if it isn't reference-counted (which we assert it
		 * isn't).
		 */
		assert -1 == refcount : "can any ephemeral TupleDesc be refcounted?";
		return new Ephemeral(td);
	}

	/**
	 * Copy a byte buffer (which may refer to native-managed memory) to one
	 * with JVM-managed backing memory.
	 *<p>
	 * Acquiescing to JDK-8318966, it still has to be a direct buffer to avoid
	 * exceptions when checking alignment. But it will use off-heap memory
	 * managed by the JVM (reclaimed when the buffer is unreachable), and so
	 * will not depend on the lifespan of the source buffer.
	 */
	private static ByteBuffer asManagedNativeOrder(ByteBuffer bb)
	{
		ByteBuffer copy = ByteBuffer.allocateDirect(bb.capacity()).put(bb);
		bb = copy;
		return bb.order(nativeOrder());
	}

	@Override
	public Attribute sqlGet(int index)
	{
		bufferIfValid(); // just for the check
		return m_attrs[index - 1];
	}

	/*
	 * AbstractList implementation
	 */
	@Override
	public int size()
	{
		return m_attrs.length;
	}

	@Override
	public Attribute get(int index)
	{
		bufferIfValid(); // just for the check
		return m_attrs[index];
	}

	@Override // Collection
	public boolean contains(Object o)
	{
		if ( ! (o instanceof AttributeImpl) )
			return false;

		AttributeImpl ai = (AttributeImpl)o;
		int idx = ai.subId() - 1;
		return ( idx < m_attrs.length ) && ( ai == m_attrs[idx] );
	}

	@Override // List
	public int indexOf(Object o)
	{
		if ( ! contains(o) )
			return -1;

		return ((Attribute)o).subId() - 1;
	}

	@Override // List
	public int lastIndexOf(Object o)
	{
		return indexOf(o);
	}

	/**
	 * An abstract base shared by the {@code Blessed} and {@code Ephemeral}
	 * concrete classes, which are populated with
	 * {@code AttributeImpl.Transient} instances.
	 *<p>
	 * Supplies their implementation of {@code contains}. {@code OfType} is also
	 * populated with {@code AttributeImpl.Transient} instances, but it has an
	 * even more trivial {@code contains} method.
	 */
	abstract static class NonCataloged extends TupleDescImpl
	{
		NonCataloged(
			ByteBuffer td, SwitchPoint sp, boolean useState,
			BiFunction<TupleDescImpl,Integer,Attribute> ctor)
		{
			super(td, sp, useState, ctor);
		}

		@Override // Collection
		public boolean contains(Object o)
		{
			if ( ! (o instanceof AttributeImpl.Transient) )
				return false;

			AttributeImpl ai = (AttributeImpl)o;
			return this == ai.containingTupleDescriptor();
		}
	}

	/**
	 * A tuple descriptor for a row type that appears in the catalog.
	 */
	static class Cataloged extends TupleDescImpl implements Interned
	{
		private final RegClass m_relation;// using its SwitchPoint, keep it live

		Cataloged(ByteBuffer td, RegClassImpl c)
		{
			/*
			 * Invalidation of a Cataloged tuple descriptor happens with the
			 * SwitchPoint attached to the RegClass. Every Cataloged descriptor
			 * from the cache had better be reference-counted, so unconditional
			 * true is passed for useState.
			 */
			super(
				td, c.cacheSwitchPoint(), true,
				(o, i) -> CatalogObjectImpl.Factory.formAttribute(
					c.oid(), i, () -> new AttributeImpl.Cataloged(c))
			);

			m_relation = c; // we need it alive for its SwitchPoint
		}

		@Override
		public RegType rowType()
		{
			return m_relation.type();
		}
	}

	/**
	 * A tuple descriptor that is not in the catalog, but has been interned and
	 * can be identified by {@code RECORD} and a distinct type modifier for the
	 * life of the backend.
	 */
	static class Blessed extends NonCataloged implements Interned
	{
		private final RegType m_rowType; // using its SwitchPoint, keep it live

		Blessed(ByteBuffer td, RegTypeImpl t)
		{
			/*
			 * A Blessed tuple descriptor has no associated RegClass, and is
			 * expected to live for the life of the backend without invalidation
			 * events, so we pass null for the SwitchPoint, and a constructor
			 * that will build AttributeImpl.Transient instances.
			 *
			 * If the caller, fromByteBuffer, saw a non-reference-counted
			 * descriptor, it grabbed one straight from the type cache instead.
			 * But sometimes, the one in PostgreSQL's type cache is
			 * non-reference counted, and that's ok, because that one will be
			 * good for the life of the process. So we do need to check, in this
			 * constructor, whether to pass true or false for useState.
			 * (Checking with getAndAddPlain(0) is a bit goofy, but it was
			 * already set up, matched to the field width, does the job.)
			 */
			super(
				td, null, -1 != s_getAndAddPlain.applyAsInt(td, 0),
				(o, i) -> new AttributeImpl.Transient(o, i)
			);

			m_rowType = t;
		}

		@Override
		public RegType rowType()
		{
			return m_rowType;
		}
	}

	/**
	 * A tuple descriptor that is not in the catalog, has not been interned, and
	 * is useful only so long as a reference is held.
	 */
	static class Ephemeral extends NonCataloged
	implements TupleDescriptor.Ephemeral
	{
		private Ephemeral(ByteBuffer td)
		{
			super(
				asManagedNativeOrder(td), null, false,
				(o, i) -> new AttributeImpl.Transient(o, i)
			);
		}

		@Override
		public RegType rowType()
		{
			return RECORD;
		}

		@Override
		public Interned intern()
		{
			TupleDescImpl sup = (TupleDescImpl)this; // bufferIfValid is private

			return doInPG(() ->
			{
				ByteBuffer td = sup.bufferIfValid();

				ByteBuffer direct = ByteBuffer.allocateDirect(
					td.capacity()).put(td.rewind());

				int assigned = _assign_record_type_typmod(direct);

				/*
				 * That will have saved in the typcache an authoritative
				 * new copy of the descriptor. It will also have written
				 * the assigned modifier into the 'direct' copy of this
				 * descriptor, but this is still an Ephemeral instance,
				 * the wrong Java type. We need to return a new instance
				 * over the authoritative typcache copy.
				 */
				return RECORD.modifier(assigned).tupleDescriptor();
			});
		}
	}

	/**
	 * A specialized, synthetic tuple descriptor representing a single column
	 * of the given {@code RegType}.
	 */
	static class OfType extends TupleDescImpl
	implements TupleDescriptor.Ephemeral
	{
		OfType(RegType type)
		{
			super(type);
		}

		@Override
		public RegType rowType()
		{
			return RECORD;
		}

		@Override
		public Interned intern()
		{
			throw notyet();
		}

		@Override // Collection
		public boolean contains(Object o)
		{
			return get(0) == o;
		}
	}

	/**
	 * Based on {@code SingleFreeTupleDesc}, but really does
	 * {@code ReleaseTupleDesc}.
	 *<p>
	 * Decrements the reference count and, if it was 1 before decrementing,
	 * proceeds to the superclass method to free the descriptor.
	 */
	private static class State
	extends DualState.SingleFreeTupleDesc<TupleDescImpl>
	{
		private final IntSupplier m_getAndDecrPlain;

		private State(TupleDescImpl referent, ByteBuffer td)
		{
			super(referent, null, addressOf(td));
			/*
			 * The only reference to this non-readonly ByteBuffer retained here
			 * is what's bound into this getAndDecr for the reference count.
			 */
			m_getAndDecrPlain = () -> s_getAndAddPlain.applyAsInt(td, -1);
		}

		@Override
		protected void javaStateUnreachable(boolean nativeStateLive)
		{
			if ( nativeStateLive && 1 == m_getAndDecrPlain.getAsInt() )
				super.javaStateUnreachable(nativeStateLive);
		}

		private void release()
		{
			releaseFromJava();
		}

		private long address()
		{
			return guardedLong();
		}
	}

	static Ephemeral synthesizeDescriptor(
		List<RegType> types, List<Simple> names, BitSet selected)
	{
		int n = types.size();
		IntFunction<String> toName;
		if ( null == names )
			toName = i -> "";
		else
		{
			assert names.size() == n;
			toName = i -> names.get(i).nonFolded();
		}

		if ( null != selected )
			assert selected.length() <= n;
		else
		{
			selected = new BitSet(n);
			selected.set(0, n);
		}

		CharsetEncoder enc = SERVER_ENCODING.newEncoder();
		float maxbpc = enc.maxBytesPerChar();
		int alignmentModulus = ALIGNOF_INT;
		int maxToAlign = alignmentModulus - 1;
		int alignmask = maxToAlign;
		int sizeTypeTypmodBool = 2 * Integer.BYTES + 1;

		int size =
			selected.stream()
			.map(i -> toName.apply(i).length())
			.map(len ->
				sizeTypeTypmodBool + (int)ceil(len*maxbpc) + 1 + maxToAlign)
			.reduce(0, Math::addExact);

		ByteBuffer direct =
			ByteBuffer.allocateDirect(size)
			.alignedSlice(ALIGNOF_INT).order(nativeOrder());

		selected.stream().forEachOrdered(i ->
		{
			int pos = direct.position();
			int misalign = direct.alignmentOffset(pos, alignmentModulus);
			pos += - misalign & alignmask;
			direct.position(pos);

			RegType t = types.get(i);
			direct.putInt(t.oid()).putInt(t.modifier());

			/*
			 * The C code will want a value for attndims, about which the docs
			 * for pg_attribute say: Presently, the number of dimensions of an
			 * array is not enforced, so any nonzero value effectively means
			 * "it's an array".
			 */
			direct.put(t.element().isValid() ? (byte)1 : (byte)0);

			pos = direct.position();
			CharBuffer cb = CharBuffer.wrap(toName.apply(i));
			CoderResult rslt = enc.encode(cb, direct, true);
			if ( rslt.isUnderflow() )
				rslt = enc.flush(direct);
			if ( ! rslt.isUnderflow() )
				throw new AssertionError("name to server encoding: " + rslt);
			enc.reset();
			direct.put((byte)'\0');
			while ( '\0' != direct.get(pos) )
				++ pos;
			if ( ++ pos != direct.position() )
				throw new AssertionError("server encoding of name has NUL");
		});

		int c = selected.cardinality();

		return new Ephemeral(doInPG(() -> _synthesizeDescriptor(c, direct)));
	}

	/**
	 * Call the PostgreSQL {@code typcache} function of the same name, but
	 * return the assigned typmod rather than {@code void}.
	 */
	private static native int _assign_record_type_typmod(ByteBuffer bb);

	private static native ByteBuffer _synthesizeDescriptor(int n,ByteBuffer bb);
}
