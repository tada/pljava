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

import java.lang.annotation.Native;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import java.util.List;

import java.util.function.IntUnaryOperator;

import java.sql.SQLException;

import static java.util.Objects.checkIndex;
import static java.util.Objects.requireNonNull;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.Adapter.AsLong;
import org.postgresql.pljava.Adapter.AsDouble;
import org.postgresql.pljava.Adapter.AsInt;
import org.postgresql.pljava.Adapter.AsFloat;
import org.postgresql.pljava.Adapter.AsShort;
import org.postgresql.pljava.Adapter.AsChar;
import org.postgresql.pljava.Adapter.AsByte;
import org.postgresql.pljava.Adapter.AsBoolean;

import org.postgresql.pljava.Lifespan;

import org.postgresql.pljava.adt.spi.Datum;
import org.postgresql.pljava.adt.spi.Datum.Accessor;

import static org.postgresql.pljava.internal.Backend.doInPG;
import org.postgresql.pljava.internal.DualState;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.RegClass;
import org.postgresql.pljava.model.TupleDescriptor;
import org.postgresql.pljava.model.TupleTableSlot;

import static org.postgresql.pljava.pg.CatalogObjectImpl.notyet;

import static org.postgresql.pljava.pg.DatumUtils.mapFixedLength;
import static org.postgresql.pljava.pg.DatumUtils.mapCString;
import static org.postgresql.pljava.pg.DatumUtils.asAlwaysCopiedDatum;
import static org.postgresql.pljava.pg.DatumUtils.asReadOnlyNativeOrder;
import static org.postgresql.pljava.pg.DatumUtils.inspectVarlena;
import static org.postgresql.pljava.pg.DatumUtils.Accessor.forDeformed;
import static org.postgresql.pljava.pg.DatumUtils.Accessor.forHeap;

import static org.postgresql.pljava.pg.ModelConstants.HEAPTUPLESIZE;

import static
	org.postgresql.pljava.pg.CatalogObjectImpl.Factory.staticFormObjectId;

import static org.postgresql.pljava.pg.ModelConstants.*;

/*
 * bool always 1 byte (see c.h).
 *
 * From PG 12:
 *  type, flags, nvalid, tupleDescriptor, *values, *isnull, mcxt, tid, tableOid
 *  flags: EMPTY SHOULDFREE SLOW FIXED
 *
 * Pre-PG 12 (= for fields present in both):
 *  type
 *  individual bool flags
 *    isempty, shouldFree, shouldFreeMin, slow, fixedTupleDescriptor
 *  HeapTuple tuple
 *  =tupleDescriptor
 *  =mcxt
 *  buffer
 *  =nvalid
 *  =*values
 *  =*isnull
 *  mintuple, minhdr, off
 *
 * tableOid is tuple->t_tableOid, tid is tuple->t_self.
 * Can a tuple from a different descendant table then get loaded in the slot?
 * Answer: yes. So tableOid can change per tuple. (See ExecStoreHeapTuple.)
 * Fetching the tableOid is easy starting with PG 12 (it's right in the TTS
 * struct). For PG < 12, a native method will be needed to inspect 'tuple' (or
 * just return a ByteBuffer windowing it, to be inspected here). That native
 * method will not need to be serialized onto the PG thread, as it only looks at
 * an existing struct in memory.
 * FWIW, *HeapTuple is a HeapTupleData, and a HeapTupleData has a t_len.
 * heap_copytuple allocates HEAPTUPLESIZE + tuple->t_len. The HEAPTUPLESIZE
 * covers the HeapTupleData that precedes the HeapTupleHeader; from the start
 * of that it's t_len. They could be allocated separately but typically aren't.
 * (A HeapTuple in the form of a Datum is without the HeapTupleData part; see
 * ExecStoreHeapTupleDatum, which just puts a transient HeapTupleData struct
 * on the stack to point to the thing during the operation, deforms it, and
 * stores it in virtual form.)
 *
 * (Also FWIW, to make a MinimalTuple from a HeapTuple, subract
 * MINIMAL_TUPLE_OFFSET from the latter's t_len; the result is the amount to
 * allocate and the amount to copy and what goes in the result's t_len.)
 *
 * For now: support only FIXED/fixedTupleDescriptor slots. For those, the native
 * code can create ByteBuffers and pass them all at once to the constructor for:
 * the TTS struct itself, the values array, the isnull array, and the TupleDesc
 * (this constructor can pass that straight to the TupleDesc constructor). If it
 * later makes sense to support non-fixed slots, that will mean checking for
 * changes, and possibly creating a new TupleDesc and new values/isnull buffers
 * on the fly.
 *
 * A PostgreSQL TupleTableSlot can be configured with TTSOpsVirtual or
 * TTSOpsHeapTuple (or others, not contemplated here). The Heap and Deformed
 * subclasses here don't exactly mirror that distinction. What they are really
 * distinguishing is which flavor of DatumUtils.Accessor will be used.
 *
 * That is, the Deformed subclass here relies on getsomeattrs and the
 * tts_values/tts_isnull arrays of the slot (which are in fact available for any
 * flavor of slot). The Heap subclass here overloads m_values and m_isnull to
 * directly map the tuple data, rather than relying on tts_values and
 * tts_isnull, so it can only work for slot flavors where such regions exist in
 * the expected formats. In other words, a Deformed can be constructed over any
 * flavor of PostgreSQL slot (and is the only choice if the slot is
 * TTSOpsVirtual); a Heap is an alternative choice only available if the
 * underlying slot is known to have the expected null bitmap and data layout,
 * and may save the overhead of populating tts_isnull and tts_values arrays from
 * the underlying tuple. It would still be possible in principle to exploit
 * those arrays in the Heap case if they have been populated, to avoid
 * repeatedly walking the tuple, but the Heap implementation here, as of this
 * writing, doesn't. Perhaps some refactoring / renaming is needed, so Heap has
 * its own instance fields for the directly accessed tuple regions, and the
 * m_values / m_isnull in the superclass always map the tts_values / tts_isnull
 * arrays?
 */

/**
 * Implementation of {@link TupleTableSlot TupleTableSlot}.
 */
public abstract class TupleTableSlotImpl<L extends Datum.Layout>
implements TupleTableSlot
{
	@Native private static final int OFFSET_HeapTupleData_t_len      = 0;
	@Native private static final int OFFSET_HeapTupleData_t_tableOid = 12;

	@Native private static final int SIZEOF_HeapTupleData_t_len      = 4;
	@Native private static final int SIZEOF_HeapTupleData_t_tableOid = 4;

	@Native private static final int OFFSET_HeapTupleHeaderData_t_infomask2= 18;
	@Native private static final int OFFSET_HeapTupleHeaderData_t_infomask = 20;
	@Native private static final int OFFSET_HeapTupleHeaderData_t_hoff = 22;
	@Native private static final int OFFSET_HeapTupleHeaderData_t_bits = 23;

	@Native private static final int SIZEOF_HeapTupleHeaderData_t_infomask2 = 2;
	@Native private static final int SIZEOF_HeapTupleHeaderData_t_infomask  = 2;
	@Native private static final int SIZEOF_HeapTupleHeaderData_t_hoff = 1;

	@Native private static final int HEAP_HASNULL = 1; // lives in infomask
	@Native private static final int HEAP_HASEXTERNAL = 4; // lives in infomask
	@Native private static final int HEAP_NATTS_MASK = 0x07FF;  // infomask2

	@Native private static final int OFFSET_NullableDatum_value = 0;

	protected final ByteBuffer m_tts;
	/* These can be final only because non-FIXED slots aren't supported yet. */
	protected final TupleDescriptor  m_tupdesc;
	protected final ByteBuffer m_values;
	protected final ByteBuffer m_isnull;
	protected final Accessor<ByteBuffer,L>[] m_accessors;
	protected final Adapter<?,?>[] m_adapters;

	/*
	 * Experimenting with yet another pattern for use of DualState. We will
	 * keep one here and be agnostic about its exact subtype. Methods that
	 * install a tuple in the slot will be expected to provide a DualState
	 * instance with this slot as its referent and encapsulating whatever object
	 * and behavior it needs for cleaning up. Pin/unpin should be done at
	 * outermost API-exposed methods, not by internal ones.
	 */
	DualState<TupleTableSlotImpl> m_state;

	TupleTableSlotImpl(
		ByteBuffer tts, TupleDescriptor tupleDesc,
		ByteBuffer values, ByteBuffer isnull)
	{
		m_tts = null == tts ? null : asReadOnlyNativeOrder(tts);
		m_tupdesc = tupleDesc;
		/*
		 * From the Deformed constructor, this is the array of Datum elements.
		 * From the Heap constructor, it may be null.
		 */
		m_values = null == values ? null : asReadOnlyNativeOrder(values);
		/*
		 * From the Deformed constructor, this is an array of one-byte booleans.
		 * From the Heap constructor, it may be null.
		 */
		m_isnull = null == isnull ? null : asReadOnlyNativeOrder(isnull);
		m_adapters = new Adapter<?,?> [ m_tupdesc.size() ];

		@SuppressWarnings("unchecked")
		Object dummy =
			m_accessors = new Accessor [ m_adapters.length ];

		/*
		 * A subclass constructor other than Deformed could pass null for tts,
		 * provided it overrides the inherited relation(), which relies on it.
		 */
		if ( null == m_tts )
			return;

		/*
		 * Verify (for now) that this is a FIXED TupleTableSlot.
		 * JIT will specialize to the test that applies in this PG version
		 */
		if ( NOCONSTANT != OFFSET_TTS_FLAGS )
		{
			if ( 0 != (TTS_FLAG_FIXED & m_tts.getChar(OFFSET_TTS_FLAGS)) )
				return;
		}
		else if ( NOCONSTANT != OFFSET_TTS_FIXED )
		{
			if ( 0 != m_tts.get(OFFSET_TTS_FIXED) )
				return;
		}
		else
			throw new UnsupportedOperationException(
				"Cannot construct non-fixed TupleTableSlot (PG < 11)");
		throw new UnsupportedOperationException(
				"Cannot construct non-fixed TupleTableSlot");
	}

	static Deformed newDeformed(
		ByteBuffer tts, TupleDescriptor tupleDesc,
		ByteBuffer values, ByteBuffer isnull)
	{
		return new Deformed(tts, tupleDesc, values, isnull);
	}

	static NullableDatum newNullableDatum(
		TupleDescriptor tupleDesc, ByteBuffer values)
	{
		return new NullableDatum(tupleDesc, values);
	}

	/**
	 * Allocate a 'light' (no native TupleTableSlot struct)
	 * {@code TupleTableSlotImpl.Heap} object, given a tuple descriptor and
	 * a byte buffer that maps a single-chunk-allocated {@code HeapTuple} (one
	 * where the {@code HeapTupleHeader} directly follows the
	 * {@code HeapTupleData}) that's to be passed to {@code heap_freetuple} when
	 * no longer needed.
	 *<p>
	 * If an optional {@code Lifespan} is supplied, the slot will be linked
	 * to it and invalidated when it expires. Otherwise, the tuple will be
	 * assumed allocated in an immortal memory context and freed upon the
	 * {@code javaStateUnreachable} or {@code javaStateReleased} events.
	 */
	static Heap heapTupleGetLightSlot(
		TupleDescriptor td, ByteBuffer ht, Lifespan lifespan)
	{
		ht = asReadOnlyNativeOrder(ht);

		assert 4 == SIZEOF_HeapTupleData_t_len
			: "sizeof HeapTupleData.t_len changed";
		int len = ht.getInt(OFFSET_HeapTupleData_t_len);

		assert ht.capacity() == len + HEAPTUPLESIZE
			: "unexpected length for single-chunk HeapTuple";

		int relOid = ht.getInt(OFFSET_HeapTupleData_t_tableOid);

		boolean disallowExternal = true;

		/*
		 * Following offsets are relative to the HeapTupleHeaderData struct.
		 * Could slice off a new ByteBuffer from HEAPTUPLESIZE here and use
		 * the offsets directly, but we'll just add HEAPTUPLESIZE to the offsets
		 * and save constructing that intermediate object. We will slice off
		 * values and nulls ByteBuffers further below.
		 */

		assert 2 == SIZEOF_HeapTupleHeaderData_t_infomask
			: "sizeof HeapTupleHeaderData.t_infomask changed";
		short infomask = ht.getShort(
			HEAPTUPLESIZE + OFFSET_HeapTupleHeaderData_t_infomask);

		assert 2 == SIZEOF_HeapTupleHeaderData_t_infomask2
			: "sizeof HeapTupleHeaderData.t_infomask2 changed";
		short infomask2 = ht.getShort(
			HEAPTUPLESIZE + OFFSET_HeapTupleHeaderData_t_infomask2);

		assert 1 == SIZEOF_HeapTupleHeaderData_t_hoff
			: "sizeof HeapTupleHeaderData.t_hoff changed";
		int hoff =
			Byte.toUnsignedInt(ht.get(
				HEAPTUPLESIZE + OFFSET_HeapTupleHeaderData_t_hoff));

		if ( disallowExternal && 0 != ( infomask & HEAP_HASEXTERNAL ) )
			throw notyet("heapTupleGetLightSlot with external values in tuple");

		int voff = hoff + HEAPTUPLESIZE;

		ByteBuffer values = mapFixedLength(ht, voff, ht.capacity() - voff);
		ByteBuffer nulls = null;

		if ( 0 != ( infomask & HEAP_HASNULL ) )
		{
			int nlen = ( td.size() + 7 ) / 8;
			if ( nlen + OFFSET_HeapTupleHeaderData_t_bits > hoff )
			{
				int attsReallyPresent = infomask2 & HEAP_NATTS_MASK;
				nlen = ( attsReallyPresent + 7 ) / 8;
				assert nlen + OFFSET_HeapTupleHeaderData_t_bits <= hoff
					: "heap null bitmap length";
			}
			nulls = mapFixedLength(ht,
				HEAPTUPLESIZE + OFFSET_HeapTupleHeaderData_t_bits, nlen);
		}

		Heap slot = new Heap(
			staticFormObjectId(RegClass.CLASSID, relOid), td, values, nulls);

		slot.m_state = new HTChunkState(slot, lifespan, ht);

		return slot;
	}

	/**
	 * Return the index into {@code m_accessors} for this attribute,
	 * ensuring the elements at that index of {@code m_accessors} and
	 * {@code m_adapters} are set, or throw an exception if
	 * this {@code Attribute} doesn't belong to this slot's
	 * {@code TupleDescriptor}, or if the supplied {@code Adapter} can't
	 * fetch it.
	 *<p>
	 * Most tests are skipped if the index is in range and {@code m_adapters}
	 * at that index already contains the supplied {@code Adapter}.
	 */
	protected int toIndex(Attribute att, Adapter<?,?> adp)
	{
		int idx = att.subId() - 1;

		if ( 0 > idx || idx >= m_adapters.length
			|| m_adapters [ idx ] != requireNonNull(adp) )
		{
			if ( ! m_tupdesc.contains(att) )
			{
				throw new IllegalArgumentException(
					"attribute " + att + " does not go with slot " + this);
			}

			memoize(idx, att, adp);
		}

		return idx;
	}

	/**
	 * Return the {@code Attribute} at this index into the associated
	 * {@code TupleDescriptor},
	 * ensuring the elements at that index of {@code m_accessors} and
	 * {@code m_adapters} are set, or throw an exception if
	 * this {@code Attribute} doesn't belong to this slot's
	 * {@code TupleDescriptor}, or if the supplied {@code Adapter} can't
	 * fetch it.
	 *<p>
	 * Most tests are skipped if the index is in range and {@code m_adapters}
	 * at that index already contains the supplied {@code Adapter}.
	 */
	protected Attribute fromIndex(int idx, Adapter<?,?> adp)
	{
		Attribute att = m_tupdesc.get(idx);
		if ( m_adapters [ idx ] != requireNonNull(adp) )
			memoize(idx, att, adp);
		return att;
	}

	/**
	 * Called after verifying that <var>att</var> belongs to this slot's
	 * {@code TupleDescriptor}, that <var>idx</var> is its corresponding
	 * (zero-based) index, and that {@code m_adapters[idx]} does not already
	 * contain <var>adp</var>.
	 */
	protected void memoize(int idx, Attribute att, Adapter<?,?> adp)
	{
		if ( ! adp.canFetch(att) )
		{
			throw new IllegalArgumentException(String.format(
				"cannot fetch attribute %s of type %s using %s",
				att, att.type(), adp));
		}

		m_adapters [ idx ] = adp;

		if ( null == m_accessors [ idx ] )
		{
			boolean byValue = att.byValue();
			short length = att.length();

			m_accessors [ idx ] = selectAccessor(byValue, length);
		}
	}

	/**
	 * Selects appropriate {@code Accessor} for this {@code Layout} given
	 * <var>byValue</var> and <var>length</var>.
	 */
	protected abstract Accessor<ByteBuffer,L> selectAccessor(
		boolean byValue, short length);

	/**
	 * Returns the previously-selected {@code Accessor} for the item at the
	 * given index.
	 *<p>
	 * The indirection's cost may be regrettable, but it simplifies the
	 * implementation of {@code Indexed}.
	 */
	protected Accessor<ByteBuffer,L> accessor(int idx)
	{
		return m_accessors[idx];
	}

	/**
	 * Only to be called after <em>idx</em> is known valid
	 * from calling {@code toIndex}.
	 */
	protected abstract boolean isNull(int idx);

	/**
	 * Only to be called after <em>idx</em> is known valid
	 * from calling {@code toIndex}.
	 */
	protected abstract int toOffset(int idx);

	static class Deformed extends TupleTableSlotImpl<Accessor.Deformed>
	{
		Deformed(
			ByteBuffer tts, TupleDescriptor tupleDesc,
			ByteBuffer values, ByteBuffer isnull)
		{
			super(tts, tupleDesc, values, requireNonNull(isnull));
		}

		@Override
		protected int toIndex(Attribute att, Adapter<?,?> adp)
		{
			int idx = super.toIndex(att, adp);

			getsomeattrs(idx);
			return idx;
		}

		@Override
		protected Attribute fromIndex(int idx, Adapter<?,?> adp)
		{
			Attribute att = super.fromIndex(idx, adp);

			getsomeattrs(idx);
			return att;
		}

		@Override
		protected Accessor<ByteBuffer,Accessor.Deformed> selectAccessor(
			boolean byValue, short length)
		{
			return forDeformed(byValue, length);
		}

		@Override
		protected boolean isNull(int idx)
		{
			return 0 != m_isnull.get(idx);
		}

		@Override
		protected int toOffset(int idx)
		{
			return idx * SIZEOF_DATUM;
		}

		/**
		 * Like PostgreSQL's {@code slot_getsomeattrs}, but {@code idx} here is
		 * zero-based (one will be added when it is passed to PostgreSQL).
		 */
		private void getsomeattrs(int idx)
		{
			int nValid;
			if ( 2 == SIZEOF_TTS_NVALID )
				nValid = m_tts.getShort(OFFSET_TTS_NVALID);
			else
			{
				assert 4 == SIZEOF_TTS_NVALID : "unexpected SIZEOF_TTS_NVALID";
				nValid = m_tts.getInt(OFFSET_TTS_NVALID);
			}
			if ( nValid <= idx )
				doInPG(() -> _getsomeattrs(m_tts, 1 + idx));
		}
	}

	static class Heap extends TupleTableSlotImpl<Accessor.Heap>
	{
		protected final ByteBuffer m_hValues;
		protected final ByteBuffer m_hIsNull;
		protected final RegClass m_relation;

		Heap(
			RegClass relation, TupleDescriptor tupleDesc,
			ByteBuffer hValues, ByteBuffer hIsNull)
		{
			super(null, tupleDesc, null, null);
			m_relation = requireNonNull(relation);
			m_hValues = requireNonNull(hValues);
			m_hIsNull = hIsNull;
		}

		@Override
		protected Accessor<ByteBuffer,Accessor.Heap> selectAccessor(
			boolean byValue, short length)
		{
			return forHeap(byValue, length);
		}

		@Override
		protected boolean isNull(int idx)
		{
			if ( null == m_hIsNull )
				return false;

			// XXX we could have actual natts < m_tupdesc.size()
			return 0 == ( m_hIsNull.get(idx >>> 3) & (1 << (idx & 7)) );
		}

		@Override
		protected int toOffset(int idx)
		{
			int offset = 0;
			List<Attribute> atts = m_tupdesc;
			Attribute att;

			/*
			 * This logic is largely duplicated in Heap.Indexed.toOffsetNonFixed
			 * and will probably need to be changed there too if anything is
			 * changed here.
			 */
			for ( int i = 0 ; i < idx ; ++ i )
			{
				if ( isNull(i) )
					continue;

				att = atts.get(i);

				int align = alignmentModulus(att.alignment());
				int len = att.length();

				/*
				 * Skip the fuss of aligning if align isn't greater than 1.
				 * More interestingly, whether to align in the varlena case
				 * (length of -1) depends on whether the byte at the current
				 * offset is zero. Each outcome includes two subcases, for one
				 * of which it doesn't matter whether we align or not because
				 * the offset is already aligned, and for the other of which it
				 * does matter, so that determines the choice. If the byte seen
				 * there is zero, it might be a pad byte and require aligning,
				 * so align. See att_align_pointer in PG's access/tupmacs.h.
				 */
				if ( align > 1 && ( -1 != len || 0 == m_hValues.get(offset) ) )
					offset +=
						- m_hValues.alignmentOffset(offset, align) & (align-1);

				if ( 0 <= len )       // a nonnegative length is used directly
					offset += len;
				else if ( -1 == len ) // find and skip the length of the varlena
					offset += inspectVarlena(m_hValues, offset);
				else if ( -2 == len ) // NUL-terminated value, skip past the NUL
				{
					while ( 0 != m_hValues.get(offset) )
						++ offset;
					++ offset;
				}
				else
					throw new AssertionError(
						"cannot skip attribute with weird length " + len);
			}

			att = atts.get(idx);

			int align = alignmentModulus(att.alignment());
			int len = att.length();
			/*
			 * Same alignment logic as above.
			 */
			if ( align > 1 && ( -1 != len  ||  0 == m_hValues.get(offset) ) )
				offset += -m_hValues.alignmentOffset(offset, align) & (align-1);

			return offset;
		}

		@Override
		ByteBuffer values()
		{
			return m_hValues;
		}

		@Override
		public RegClass relation()
		{
			return m_relation;
		}

		/**
		 * Something that resembles a {@code Heap} tuple, but consists of
		 * a number of elements all of the same type, distinguished by index.
		 *<p>
		 * Constructed with a one-element {@code TupleDescriptor} whose single
		 * {@code Attribute} describes the type of all elements.
		 *<p>
		 *
		 */
		static class Indexed extends Heap implements TupleTableSlot.Indexed
		{
			private final int m_elements;
			private final IntUnaryOperator m_toOffset;

			Indexed(
				TupleDescriptor td, int elements,
				ByteBuffer nulls, ByteBuffer values)
			{
				super(td.get(0).relation(), td, values, nulls);
				assert elements >= 0 : "negative element count";
				assert null == nulls || nulls.capacity() == (elements+7)/8
					: "nulls length element count mismatch";
				m_elements = elements;

				Attribute att = td.get(0);
				int length = att.length();
				int align = alignmentModulus(att.alignment());
				assert 0 == values.alignmentOffset(0, align)
					: "misaligned ByteBuffer passed";
				int mask = align - 1; // make it a mask
				if ( length < 0 ) // the non-fixed case
					/*
					 * XXX without offset memoization of some kind, this will be
					 * a quadratic way of accessing elements, but that can be
					 * improved later.
					 */
					m_toOffset = i -> toOffsetNonFixed(i, length, mask);
				else
				{
					int stride = length + ( -(length & mask) & mask );
					if ( null == nulls )
						m_toOffset = i -> i * stride;
					else
						m_toOffset = i -> (i - nullsPreceding(i)) * stride;
				}
			}

			@Override
			public int elements()
			{
				return m_elements;
			}

			@Override
			protected Attribute fromIndex(int idx, Adapter<?,?> adp)
			{
				checkIndex(idx, m_elements);
				Attribute att = m_tupdesc.get(0);
				if ( m_adapters [ 0 ] != requireNonNull(adp) )
					memoize(0, att, adp);
				return att;
			}

			@Override
			protected int toOffset(int idx)
			{
				return m_toOffset.applyAsInt(idx);
			}

			@Override
			protected Accessor<ByteBuffer,Accessor.Heap> accessor(int idx)
			{
				return m_accessors[0];
			}

			private int nullsPreceding(int idx)
			{
				int targetByte = idx >>> 3;
				int targetBit  = 1 << ( idx & 7 );
				byte b = m_hIsNull.get(targetByte);
				/*
				 * The nulls bitmask has 1 bits where values are *not* null.
				 * Java has a bitCount method that counts 1 bits. So the loop
				 * below will have an invert step before counting bits. That
				 * means we want to modify *this* byte to have 1 at the target
				 * position *and above*, so all those bits will invert to zero
				 * before we count them. The next step does that.
				 */
				b |= - targetBit;
				int count = Integer.bitCount(Byte.toUnsignedInt(b) ^ 0xff);
				for ( int i = 0; i < targetByte; ++ i )
				{
					b = m_hIsNull.get(i);
					count += Integer.bitCount(Byte.toUnsignedInt(b) ^ 0xff);
				}
				return count;
			}

			/**
			 * Largely duplicates the superclass {@code toOffset} but
			 * specialized to only a single attribute type that is repeated.
			 *<p>
			 * Only covers the non-fixed-length cases (length of -1 or -2).
			 * Assumes the byte buffer is already aligned such that offset 0
			 * satisfies the alignment constraint.
			 *<p>
			 * <b>Important:</b> <var>align</var> here is a mask; the caller
			 * has subtracted 1 from it, compared to the <var>align</var> value
			 * seen in the superclass implementation.
			 */
			private int toOffsetNonFixed(int idx, int len, int align)
			{
				int offset = 0;

				if ( null != m_hIsNull )
					idx -= nullsPreceding(idx);

				/*
				 * The following code is very similar to that in the superclass,
				 * other than having already converted align to a mask (changing
				 * the test below to align>0 where the superclass has align>1),
				 * and having already reduced idx by the preceding nulls. If any
				 * change is needed here, it is probably needed there too.
				 */
				for ( int i = 0 ; i < idx ; ++ i )
				{
					if ( align > 0
						&& ( -1 != len || 0 == m_hValues.get(offset) ) )
						offset += - (offset & align) & align;

					if ( -1 == len ) // find and skip the length of the varlena
						offset += inspectVarlena(m_hValues, offset);
					else if ( -2 == len ) // NUL-terminated, skip past the NUL
					{
						while ( 0 != m_hValues.get(offset) )
							++ offset;
						++ offset;
					}
					else
						throw new AssertionError(
							"cannot skip attribute with weird length " + len);
				}

				/*
				 * Same alignment logic as above.
				 */
				if ( align > 0 && ( -1 != len  ||  0 == m_hValues.get(offset) ) )
					offset += - (offset & align) & align;

				return offset;
			}
		}
	}

	static class NullableDatum extends TupleTableSlotImpl<Accessor.Deformed>
	{
		NullableDatum(TupleDescriptor tupleDesc, ByteBuffer values)
		{
			super(null, tupleDesc, values, null);
		}

		@Override
		protected Accessor<ByteBuffer,Accessor.Deformed> selectAccessor(
			boolean byValue, short length)
		{
			return forDeformed(byValue, length);
		}

		@Override
		protected boolean isNull(int idx)
		{
			return 0 != m_values.get(
				idx * SIZEOF_NullableDatum + OFFSET_NullableDatum_isnull);
		}

		@Override
		protected int toOffset(int idx)
		{
			return idx * SIZEOF_NullableDatum + OFFSET_NullableDatum_value;
		}

		@Override
		public RegClass relation()
		{
			return RegClass.CLASSID.invalid();
		}
	}

	@Override
	public RegClass relation()
	{
		int tableOid;

		if ( NOCONSTANT == OFFSET_TTS_TABLEOID )
			throw notyet("table Oid from TupleTableSlot in PostgreSQL < 12");

		tableOid = m_tts.getInt(OFFSET_TTS_TABLEOID);
		return staticFormObjectId(RegClass.CLASSID, tableOid);
	}

	@Override
	public TupleDescriptor descriptor()
	{
		return m_tupdesc;
	}

	ByteBuffer values()
	{
		return m_values;
	}

	void store_heaptuple(long ht, boolean shouldFree)
	{
		doInPG(() -> _store_heaptuple(m_tts, ht, shouldFree));
	}

	private static native void _getsomeattrs(ByteBuffer tts, int idx);

	private static native void _store_heaptuple(
		ByteBuffer tts, long ht, boolean shouldFree);

	@Override
	public <T> T get(Attribute att, As<T,?> adapter)
	{
		int idx = toIndex(att, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(m_accessors[idx], values(), off, att);
	}

	@Override
	public long get(Attribute att, AsLong<?> adapter)
	{
		int idx = toIndex(att, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(m_accessors[idx], values(), off, att);
	}

	@Override
	public double get(Attribute att, AsDouble<?> adapter)
	{
		int idx = toIndex(att, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(m_accessors[idx], values(), off, att);
	}

	@Override
	public int get(Attribute att, AsInt<?> adapter)
	{
		int idx = toIndex(att, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(m_accessors[idx], values(), off, att);
	}

	@Override
	public float get(Attribute att, AsFloat<?> adapter)
	{
		int idx = toIndex(att, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(m_accessors[idx], values(), off, att);
	}

	@Override
	public short get(Attribute att, AsShort<?> adapter)
	{
		int idx = toIndex(att, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(m_accessors[idx], values(), off, att);
	}

	@Override
	public char get(Attribute att, AsChar<?> adapter)
	{
		int idx = toIndex(att, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(m_accessors[idx], values(), off, att);
	}

	@Override
	public byte get(Attribute att, AsByte<?> adapter)
	{
		int idx = toIndex(att, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(m_accessors[idx], values(), off, att);
	}

	@Override
	public boolean get(Attribute att, AsBoolean<?> adapter)
	{
		int idx = toIndex(att, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(m_accessors[idx], values(), off, att);
	}

	@Override
	public <T> T get(int idx, As<T,?> adapter)
	{
		Attribute att = fromIndex(idx, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(accessor(idx), values(), off, att);
	}

	@Override
	public long get(int idx, AsLong<?> adapter)
	{
		Attribute att = fromIndex(idx, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(accessor(idx), values(), off, att);
	}

	@Override
	public double get(int idx, AsDouble<?> adapter)
	{
		Attribute att = fromIndex(idx, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(accessor(idx), values(), off, att);
	}

	@Override
	public int get(int idx, AsInt<?> adapter)
	{
		Attribute att = fromIndex(idx, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(accessor(idx), values(), off, att);
	}

	@Override
	public float get(int idx, AsFloat<?> adapter)
	{
		Attribute att = fromIndex(idx, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(accessor(idx), values(), off, att);
	}

	@Override
	public short get(int idx, AsShort<?> adapter)
	{
		Attribute att = fromIndex(idx, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(accessor(idx), values(), off, att);
	}

	@Override
	public char get(int idx, AsChar<?> adapter)
	{
		Attribute att = fromIndex(idx, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(accessor(idx), values(), off, att);
	}

	@Override
	public byte get(int idx, AsByte<?> adapter)
	{
		Attribute att = fromIndex(idx, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(accessor(idx), values(), off, att);
	}

	@Override
	public boolean get(int idx, AsBoolean<?> adapter)
	{
		Attribute att = fromIndex(idx, adapter);

		if ( isNull(idx) )
			return adapter.fetchNull(att);

		int off = toOffset(idx);
		return adapter.fetch(accessor(idx), values(), off, att);
	}

	private static class HTChunkState
	extends DualState.BBHeapFreeTuple<TupleTableSlotImpl>
	{
		private HTChunkState(
			TupleTableSlotImpl referent, Lifespan span, ByteBuffer ht)
		{
			super(referent, span, ht);
		}
	}
}
