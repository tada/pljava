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

import java.lang.annotation.Native;

import java.nio.ByteBuffer;

import java.util.List;

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

import static
	org.postgresql.pljava.pg.CatalogObjectImpl.Factory.staticFormObjectId;

import static org.postgresql.pljava.pg.ModelConstants.*;

import java.lang.reflect.Field;

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

	@Override // XXX testing
	public Adapter adapterPlease(String cname, String field)
	throws ReflectiveOperationException
	{
		@SuppressWarnings("unchecked")
		Class<? extends Adapter> cls =
			(Class<? extends Adapter>)Class.forName(cname);
		Field f = cls.getField(field);
		return (Adapter)f.get(null);
	}

	@Override
	public RegClass relation()
	{
		throw notyet();
	}

	@Override
	public TupleDescriptor descriptor()
	{
		throw notyet();
	}

	@Override
	public <T> T get(Attribute att, As<T,?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public long get(Attribute att, AsLong<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public double get(Attribute att, AsDouble<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public int get(Attribute att, AsInt<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public float get(Attribute att, AsFloat<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public short get(Attribute att, AsShort<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public char get(Attribute att, AsChar<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public byte get(Attribute att, AsByte<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public boolean get(Attribute att, AsBoolean<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public <T> T get(int idx, As<T,?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public long get(int idx, AsLong<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public double get(int idx, AsDouble<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public int get(int idx, AsInt<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public float get(int idx, AsFloat<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public short get(int idx, AsShort<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public char get(int idx, AsChar<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public byte get(int idx, AsByte<?> adapter) throws SQLException
	{
		throw notyet();
	}

	@Override
	public boolean get(int idx, AsBoolean<?> adapter) throws SQLException
	{
		throw notyet();
	}
}
