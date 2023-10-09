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

import java.io.Closeable;
import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static java.nio.ByteOrder.nativeOrder;
import java.nio.BufferUnderflowException;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import java.sql.SQLException;

import java.util.BitSet;
import java.util.List;

import org.postgresql.pljava.adt.spi.Datum;

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.MemoryContext;
import org.postgresql.pljava.model.RegType;
import org.postgresql.pljava.model.ResourceOwner;
import static org.postgresql.pljava.model.MemoryContext.TopTransactionContext;
import static
	org.postgresql.pljava.model.ResourceOwner.TopTransactionResourceOwner;
import org.postgresql.pljava.model.TupleDescriptor;
import org.postgresql.pljava.model.TupleTableSlot;

import static org.postgresql.pljava.internal.Backend.doInPG;
import org.postgresql.pljava.internal.DualState;
import org.postgresql.pljava.internal.LifespanImpl.Addressed;

import static org.postgresql.pljava.pg.CatalogObjectImpl.notyet;
import static org.postgresql.pljava.pg.ModelConstants.*;

/**
 * Implementations of {@link Datum.Accessor} and a collection of related
 * static methods.
 */
public /*XXX*/ class DatumUtils
{
	static final boolean BIG_ENDIAN = ByteOrder.BIG_ENDIAN == nativeOrder();

	public static TupleTableSlot.Indexed indexedTupleSlot(
		RegType type, int elements, ByteBuffer nulls, ByteBuffer values)
	{
		TupleDescriptor td = new TupleDescImpl.OfType(type);
		return new TupleTableSlotImpl.Heap.Indexed(td, elements, nulls, values);
	}

	public static long addressOf(ByteBuffer bb)
	{
		if ( bb.isDirect() )
			return _addressOf(bb);
		throw new IllegalArgumentException(
			"addressOf(non-direct " + bb + ")");
	}

	public static long fetchPointer(ByteBuffer bb, int offset)
	{
		return Accessor.ByReference.Deformed.s_pointerAccessor
			.getLongZeroExtended(bb, offset);
	}

	public static void storePointer(ByteBuffer bb, int offset, long value)
	{
		/*
		 * Stopgap implementation; use s_pointer_accessor as above once
		 * accessors have store methods.
		 */
		if ( 4 == SIZEOF_DATUM )
			bb.putInt(offset, (int)value);
		else
			bb.putLong(offset, value);
	}

	public static ByteBuffer asReadOnlyNativeOrder(ByteBuffer bb)
	{
		if ( null == bb )
			return bb;
		if ( ! bb.isReadOnly() )
			bb = bb.asReadOnlyBuffer();
		return bb.order(nativeOrder());
	}

	static ByteBuffer mapFixedLength(long nativeAddress, int length)
	{
		if ( 0 == nativeAddress )
			return null;
		ByteBuffer bb = _map(nativeAddress, length);
		return asReadOnlyNativeOrder(bb);
	}

	public static ByteBuffer mapFixedLength(
		ByteBuffer bb, int offset, int length)
	{
		// Java 13: bb.slice(offset, length).order(bb.order())
		ByteBuffer bnew = bb.duplicate();
		bnew.position(offset).limit(offset + length);
		return bnew.slice().order(bb.order());
	}

	static ByteBuffer mapCString(long nativeAddress)
	{
		if ( 0 == nativeAddress )
			return null;
		ByteBuffer bb = _mapCString(nativeAddress);
		if ( null == bb )
		{
			/*
			 * This may seem an odd exception to throw in this case (the
			 * native code found no NUL terminator within the maximum size
			 * allowed for a ByteBuffer), but it is the same exception that
			 * would be thrown by the mapCString(ByteBuffer,int) flavor if
			 * it found no NUL within the bounds of its source buffer.
			 */
			throw new BufferUnderflowException();
		}
		return asReadOnlyNativeOrder(bb);
	}

	public static ByteBuffer mapCString(ByteBuffer bb, int offset)
	{
		ByteBuffer bnew = bb.duplicate();
		int i = offset;
		while ( 0 != bnew.get(i) )
			++i;
		bnew.position(offset).limit(i);
		return bnew.slice().order(bb.order());
	}

	static Datum.Input mapVarlena(long nativeAddress,
		ResourceOwner resowner, MemoryContext memcontext)
	{
		long ro = ((Addressed)resowner).address();
		long mc = ((Addressed)memcontext).address();
		return doInPG(() -> _mapVarlena(null, nativeAddress, ro, mc));
	}

	static Datum.Input mapVarlena(ByteBuffer bb, long offset,
		ResourceOwner resowner, MemoryContext memcontext)
	{
		long ro = ((Addressed)resowner).address();
		long mc = ((Addressed)memcontext).address();
		return doInPG(() -> _mapVarlena(bb, offset, ro, mc));
	}

	/**
	 * For now, just return the inline size (the size to be skipped if stepping
	 * over this varlena in a heap tuple).
	 *<p>
	 * This is a reimplementation of some of the top of {@code postgres.h}, so
	 * that this common operation can be done without a JNI call to the C code.
	 */
	public static int inspectVarlena(ByteBuffer bb, int offset)
	{
		byte b1 = bb.get(offset);
		byte shortbit;
		int tagsize;

		if ( BIG_ENDIAN )
		{
			shortbit = (byte)(b1 & 0x80);
			if ( 0 == shortbit ) // it has a four-byte header and we're aligned
			{
				// here is where to discern if it's inline compressed if we care
				return bb.getInt(offset) & 0x3FFFFFFF;
			}
			if ( shortbit != b1 ) // it is inline and short
				return b1 & 0x7F;
		}
		else // little endian
		{
			shortbit = (byte)(b1 & 0x01);
			if ( 0 == shortbit ) // it has a four-byte header and we're aligned
			{
				// here is where to discern if it's inline compressed if we care
				return bb.getInt(offset) >>> 2;
			}
			if ( shortbit != b1 ) // it is inline and short
				return b1 >>> 1 & 0x7F;
		}

		/*
		 * If we got here, it is a TOAST pointer of some kind. Its identifying
		 * tag is the next byte, and its total size is VARHDRSZ_EXTERNAL plus
		 * something that depends on the tag.
		 */
		switch ( bb.get(offset + 1) )
		{
		case VARTAG_INDIRECT:
			tagsize = SIZEOF_varatt_indirect;
			break;
		case VARTAG_EXPANDED_RO:
		case VARTAG_EXPANDED_RW:
			tagsize = SIZEOF_varatt_expanded;
			break;
		case VARTAG_ONDISK:
			tagsize = SIZEOF_varatt_external;
			break;
		default:
			throw new AssertionError("unrecognized TOAST vartag");
		}

		return VARHDRSZ_EXTERNAL + tagsize;
	}

	static Datum.Input asAlwaysCopiedDatum(
		ByteBuffer bb, int offset, int length)
	{
		byte[] bytes = new byte [ length ];
		// Java 13: bb.get(offset, bytes);
		((ByteBuffer)bb.duplicate().position(offset)).get(bytes);
		ByteBuffer copy = ByteBuffer.wrap(bytes);
		return new DatumImpl.Input.JavaCopy(asReadOnlyNativeOrder(copy));
	}

	/**
	 * Turns a {@link BitSet} into a direct-allocated {@link ByteBuffer} whose
	 * address can be passed in C code to PostgreSQL's {@code bms_copy} and used
	 * as a {@code bitmapset}.
	 *<p>
	 * While the {@code ByteBuffer} is direct-allocated, it is allocated by
	 * Java, not by {@code palloc}, and the PostgreSQL code must not be allowed
	 * to try to grow, shrink, or {@code pfree} it. Hence the {@code bms_copy}.
	 *<p>
	 * If the result of operations in C will be wanted in Java, the fuss of
	 * allocating a different direct {@code ByteBuffer} for the result can be
	 * avoided by <em>carefully</em> letting the C code update this
	 * {@code bitmapset} in place, so that no resizing or freeing can occur.
	 * That can be done by OR-ing one extra bit into the Java {@code BitSet} in
	 * advance, at an index higher than the bits of interest, and having the C
	 * code manipulate only the lower-indexed bits (such as by using a
	 * {@code bms_prev_member} loop unrolled with one first call unused).
	 *<p>
	 * While the {@code ByteBuffer} returned is read-only (as far as Java is
	 * concerned), if it is updated in place by C code, it can be passed
	 * afterward to {@link #fromBitmapset fromBitmapset} to recover the result.
	 */
	static ByteBuffer toBitmapset(BitSet b)
	{
		if ( BITS_PER_BITMAPWORD == Long.SIZE )
		{
			long[] ls = b.toLongArray();
			int size = SIZEOF_NodeTag + SIZEOF_INT + ls.length * Long.BYTES;
			ByteBuffer bb = ByteBuffer.allocateDirect(size);
			bb.order(nativeOrder());
			assert SIZEOF_NodeTag == Integer.BYTES : "sizeof NodeTag";
			bb.putInt(T_Bitmapset);
			assert SIZEOF_INT == Integer.BYTES : "sizeof int";
			bb.putInt(ls.length);
			LongBuffer dst = bb.asLongBuffer();
			dst.put(ls);
			return asReadOnlyNativeOrder(bb.rewind());
		}
		else if ( BITS_PER_BITMAPWORD == Integer.SIZE )
		{
			byte[] bs = b.toByteArray();
			int widthLessOne = Integer.BYTES - 1;
			int size = (bs.length + widthLessOne) & ~widthLessOne;
			int words = size / Integer.BYTES;
			size += SIZEOF_NodeTag + SIZEOF_INT;
			ByteBuffer bb = ByteBuffer.allocateDirect(size);
			bb.order(nativeOrder());
			assert SIZEOF_NodeTag == Integer.BYTES : "sizeof NodeTag";
			bb.putInt(T_Bitmapset);
			assert SIZEOF_INT == Integer.BYTES : "sizeof int";
			bb.putInt(words);
			IntBuffer dst = bb.asIntBuffer();

			IntBuffer src = ByteBuffer.wrap(bs)
				.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
			dst.put(src);
			return asReadOnlyNativeOrder(bb.rewind());
		}
		else
			throw new AssertionError(
				"no support for BITS_PER_BITMAPWORD " + BITS_PER_BITMAPWORD);
	}

	static BitSet fromBitmapset(ByteBuffer bb)
	{
		bb.rewind().order(nativeOrder());
		assert SIZEOF_NodeTag == Integer.BYTES : "sizeof NodeTag";
		if ( T_Bitmapset != bb.getInt() )
			throw new AssertionError("not a bitmapset: " + bb);
		assert SIZEOF_INT == Integer.BYTES : "sizeof int";
		int words = bb.getInt();

		if ( BITS_PER_BITMAPWORD == Long.SIZE )
		{
			LongBuffer lb = bb.asLongBuffer();
			if ( words > lb.remaining() )
				throw new AssertionError("corrupted bitmapset: " + bb);
			if ( words < lb.remaining() )
				lb.limit(words);
			return BitSet.valueOf(lb);
		}
		else if ( BITS_PER_BITMAPWORD == Integer.SIZE )
		{
			IntBuffer src = bb.asIntBuffer();
			if ( words > src.remaining() )
				throw new AssertionError("corrupted bitmapset: " + bb);
			if ( words < src.remaining() )
				src.limit(words);
			ByteBuffer le =
				ByteBuffer.allocate(bb.position() + words * Integer.SIZE);
			le.order(ByteOrder.LITTLE_ENDIAN);
			IntBuffer dst = le.asIntBuffer();
			dst.put(src);
			return BitSet.valueOf(le);
		}
		else
			throw new AssertionError(
				"no support for BITS_PER_BITMAPWORD " + BITS_PER_BITMAPWORD);
	}

	private static native long _addressOf(ByteBuffer bb);

	private static native ByteBuffer _map(long nativeAddress, int length);

	private static native ByteBuffer _mapCString(long nativeAddress);

	/*
	 * Uses offset as address directly if bb is null.
	 */
	private static native Datum.Input _mapVarlena(
		ByteBuffer bb, long offset, long resowner, long memcontext);

	abstract static class Accessor<B,L extends Datum.Layout>
	implements Datum.Accessor<B,L>
	/*
	 * Accessors handle fixed-length types no wider than a Datum; for such
	 * types, they support access as all suitable Java primitive types as well
	 * as Datum. For wider or variable-length types, only Datum access applies.
	 * For by-value, only power-of-2 sizes and corresponding alignments allowed:
	 * https://git.postgresql.org/gitweb/?p=postgresql.git;a=commit;h=82a1f09
	 *
	 * The primitive-typed methods all have SignExtended and ZeroExtended
	 * flavors (except for short and char where the flavor is explicit, and byte
	 * which has no narrower type to extend). The get methods return the
	 * specified type, which means the choice of flavor will have no detectable
	 * effect on the return value when the value being read is exactly that
	 * width (as always in Java, a long, int, or byte will be treated as
	 * signed); the flavor will make a difference if the method is used to read
	 * a value that is actually narrower (say, getLongZeroExtended or
	 * getLongSignExtended on an int-sized field).
	 */
	{
		static Datum.Accessor<ByteBuffer,Deformed> forDeformed(
			boolean byValue, short length)
		{
			if ( byValue )
				return ByValue.Deformed.ACCESSORS [ length ];
			if ( 0 <= length )
			{
				/*
				 * specific by-reference accessors are always available for
				 * lengths up to Long.BYTES, even in 4-byte-datum builds. The
				 * by-reference value doesn't have to fit in a Datum, and it
				 * may be useful to access it as a Java primitive.
				 */
				if ( Long.BYTES >= length )
					return ByReference.Deformed.ACCESSORS [ length ];
				return ByReference.Deformed.ACCESSORS [
					ByReference.FIXED_ACCESSOR_INDEX
				];
			}
			if ( -1 == length )
				return ByReference.Deformed.ACCESSORS [
					ByReference.VARLENA_ACCESSOR_INDEX
				];
			if ( -2 == length )
				return ByReference.Deformed.ACCESSORS [
					ByReference.CSTRING_ACCESSOR_INDEX
				];
			throw new IllegalArgumentException(
				"invalid attribute length: "+length);
		}

		static Datum.Accessor<ByteBuffer,Heap> forHeap(
			boolean byValue, short length)
		{
			if ( byValue )
				return ByValue.Heap.ACCESSORS [ length ];
			if ( 0 <= length )
			{
				/*
				 * specific by-reference accessors are always available for
				 * lengths up to Long.BYTES, even in 4-byte-datum builds. The
				 * by-reference value doesn't have to fit in a Datum, and it
				 * may be useful to access it as a Java primitive.
				 */
				if ( Long.BYTES >= length )
					return ByReference.Heap.ACCESSORS [ length ];
				return ByReference.Heap.ACCESSORS [
					ByReference.FIXED_ACCESSOR_INDEX
				];
			}
			if ( -1 == length )
				return ByReference.Heap.ACCESSORS [
					ByReference.VARLENA_ACCESSOR_INDEX
				];
			if ( -2 == length )
				return ByReference.Heap.ACCESSORS [
					ByReference.CSTRING_ACCESSOR_INDEX
				];
			throw new IllegalArgumentException(
				"invalid attribute length: "+length);
		}

		@Override
		public long getLongSignExtended(B buf, int off)
		{
			return getIntSignExtended(buf, off);
		}

		@Override
		public long getLongZeroExtended(B buf, int off)
		{
			return Integer.toUnsignedLong(getIntZeroExtended(buf, off));
		}

		@Override
		public double getDouble(B buf, int off)
		{
			return getFloat(buf, off);
		}

		@Override
		public int getIntSignExtended(B buf, int off)
		{
			return getShort(buf, off);
		}

		@Override
		public int getIntZeroExtended(B buf, int off)
		{
			return getChar(buf, off);
		}

		@Override
		public float getFloat(B buf, int off)
		{
			throw new AccessorWidthException();
		}

		@Override
		public short getShort(B buf, int off)
		{
			return getByte(buf, off);
		}

		@Override
		public char getChar(B buf, int off)
		{
			return (char)Byte.toUnsignedInt(getByte(buf, off));
		}

		@Override
		public byte getByte(B buf, int off)
		{
			throw new AccessorWidthException();
		}

		@Override
		public boolean getBoolean(B buf, int off)
		{
			return 0 != getLongZeroExtended(buf, off);
		}

		@Override
		public Datum.Input getDatum(B buf, int off, Attribute a)
		{
			throw new AccessorWidthException();
		}

		static class ByValue<L extends Datum.Layout>
		extends Accessor<ByteBuffer,L>
		{
			/*
			 * Convention: when invoking a deformed accessor method, the offset
			 * shall be a multiple of SIZEOF_DATUM.
			 */
			static class Deformed extends ByValue<Datum.Accessor.Deformed>
			{
				@SuppressWarnings("unchecked")
				static final ByValue<Datum.Accessor.Deformed>[] ACCESSORS =
					new ByValue[ 1 + SIZEOF_DATUM ];
				static
				{
					ByValue<Datum.Accessor.Deformed> none = new ByValue<>();
					(
						(8 == SIZEOF_DATUM)
						? List.<ByValue<Datum.Accessor.Deformed>>of(
							none,
							new DV81(), new DV82(), none, new DV84(),
							none,       none,       none, new DV88()
						)
						: List.<ByValue<Datum.Accessor.Deformed>>of(
							none,
							new DV41(), new DV42(), none, new DV44()
						)
					).toArray(ACCESSORS);
				}
			}

			/*
			 * Convention: when invoking a heap accessor method, the offset
			 * shall already have been adjusted for alignment (according to
			 * PostgreSQL's alignment rules, that is, so the right value will be
			 * accessed). Java's ByteBuffer API will still check and possibly
			 * split accesses according to the hardware's rules; there's no way
			 * to talk it out of that, so there's little to gain by being more
			 * clever here.
			 */
			static class Heap extends ByValue<Datum.Accessor.Heap>
			{
				@SuppressWarnings("unchecked")
				static final ByValue<Datum.Accessor.Heap>[] ACCESSORS =
					new ByValue[ 1 + SIZEOF_DATUM ];
				static
				{
					ByValue<Datum.Accessor.Heap> none = new ByValue<>();
					(
						(8 == SIZEOF_DATUM)
						? List.<ByValue<Datum.Accessor.Heap>>of(
							none,
							new HV1(), new HV2(), none, new HV4(),
							none,      none,      none, new HV8()
						)
						: List.<ByValue<Datum.Accessor.Heap>>of(
							none,
							new HV1(), new HV2(), none, new HV4()
						)
					).toArray(ACCESSORS);
				}
			}
		}

		static class DV88 extends ByValue.Deformed
		{
			@Override
			public long getLongSignExtended(ByteBuffer bb, int off)
			{
				return bb.getLong(off);
			}
			@Override
			public long getLongZeroExtended(ByteBuffer bb, int off)
			{
				return bb.getLong(off);
			}
			@Override
			public double getDouble(ByteBuffer bb, int off)
			{
				return bb.getDouble(off);
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				return asAlwaysCopiedDatum(bb, off, 8);
			}
		}

		static class DV84 extends ByValue.Deformed
		{
			@Override
			public int getIntSignExtended(ByteBuffer bb, int off)
			{
				long r = bb.getLong(off);
				return (int)r;
			}
			@Override
			public int getIntZeroExtended(ByteBuffer bb, int off)
			{
				long r = bb.getLong(off);
				return (int)r;
			}
			@Override
			public float getFloat(ByteBuffer bb, int off)
			{
				return bb.getFloat(off);
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				if ( BIG_ENDIAN )
					off += SIZEOF_DATUM - 4;
				return asAlwaysCopiedDatum(bb, off, 4);
			}
		}

		static class DV82 extends ByValue.Deformed
		{
			@Override
			public short getShort(ByteBuffer bb, int off)
			{
				long r = bb.getLong(off);
				return (short)r;
			}
			@Override
			public char getChar(ByteBuffer bb, int off)
			{
				long r = bb.getLong(off);
				return (char)r;
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				if ( BIG_ENDIAN )
					off += SIZEOF_DATUM - 2;
				return asAlwaysCopiedDatum(bb, off, 2);
			}
		}

		static class DV81 extends ByValue.Deformed
		{
			@Override
			public byte getByte(ByteBuffer bb, int off)
			{
				long r = bb.getLong(off);
				return (byte)r;
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				if ( BIG_ENDIAN )
					off += SIZEOF_DATUM - 1;
				return asAlwaysCopiedDatum(bb, off, 1);
			}
		}

		static class DV44 extends ByValue.Deformed
		{
			@Override
			public int getIntSignExtended(ByteBuffer bb, int off)
			{
				return bb.getInt(off);
			}
			@Override
			public int getIntZeroExtended(ByteBuffer bb, int off)
			{
				return bb.getInt(off);
			}
			@Override
			public float getFloat(ByteBuffer bb, int off)
			{
				return bb.getFloat(off);
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				return asAlwaysCopiedDatum(bb, off, 4);
			}
		}

		static class DV42 extends ByValue.Deformed
		{
			@Override
			public short getShort(ByteBuffer bb, int off)
			{
				int r = bb.getInt(off);
				return (short)r;
			}
			@Override
			public char getChar(ByteBuffer bb, int off)
			{
				int r = bb.getInt(off);
				return (char)r;
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				if ( BIG_ENDIAN )
					off += SIZEOF_DATUM - 2;
				return asAlwaysCopiedDatum(bb, off, 2);
			}
		}

		static class DV41 extends ByValue.Deformed
		{
			@Override
			public byte getByte(ByteBuffer bb, int off)
			{
				int r = bb.getInt(off);
				return (byte)r;
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				if ( BIG_ENDIAN )
					off += SIZEOF_DATUM - 1;
				return asAlwaysCopiedDatum(bb, off, 1);
			}
		}

		static class HV8 extends ByValue.Heap
		{
			@Override
			public long getLongSignExtended(ByteBuffer bb, int off)
			{
				return bb.getLong(off);
			}
			@Override
			public long getLongZeroExtended(ByteBuffer bb, int off)
			{
				return bb.getLong(off);
			}
			@Override
			public double getDouble(ByteBuffer bb, int off)
			{
				return bb.getDouble(off);
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				return asAlwaysCopiedDatum(bb, off, 8);
			}
		}

		static class HV4 extends ByValue.Heap
		{
			@Override
			public int getIntSignExtended(ByteBuffer bb, int off)
			{
				return bb.getInt(off);
			}
			@Override
			public int getIntZeroExtended(ByteBuffer bb, int off)
			{
				return bb.getInt(off);
			}
			@Override
			public float getFloat(ByteBuffer bb, int off)
			{
				return bb.getFloat(off);
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				return asAlwaysCopiedDatum(bb, off, 4);
			}
		}

		static class HV2 extends ByValue.Heap
		{
			@Override
			public short getShort(ByteBuffer bb, int off)
			{
				return bb.getShort(off);
			}
			@Override
			public char getChar(ByteBuffer bb, int off)
			{
				return bb.getChar(off);
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				return asAlwaysCopiedDatum(bb, off, 2);
			}
		}

		static class HV1 extends ByValue.Heap
		{
			@Override
			public byte getByte(ByteBuffer bb, int off)
			{
				return bb.get(off);
			}
			@Override
			public Datum.Input getDatum(ByteBuffer bb, int off, Attribute a)
			{
				return asAlwaysCopiedDatum(bb, off, 1);
			}
		}

		/*
		 * In the ByReference case, the accessors for Deformed and Heap differ
		 * only in what the map*Reference() methods do, so the accessors are
		 * all made inner classes of Impl, and instantiated in the
		 * constructors of its subclasses Deformed and Heap, so they have access
		 * to the right copy/map methods by enclosure rather than inheritance.
		 * The constructor of each (Heap and Deformed) is invoked just once,
		 * statically, to populate the ACCESSORS arrays.
		 *
		 * There are always length-specific accessors for each length through 8,
		 * even in 4-byte-datum builds, plus accessors for fixed lengths greater
		 * than 8, cstrings, and varlenas.
		 */
		abstract static class ByReference<L extends Datum.Layout>
		extends Accessor<ByteBuffer,L>
		{
			static final int   FIXED_ACCESSOR_INDEX = 9;
			static final int CSTRING_ACCESSOR_INDEX = 10;
			static final int VARLENA_ACCESSOR_INDEX = 11;
			static final int ACCESSORS_ARRAY_LENGTH = 12;

			static final class Deformed extends Impl<Datum.Accessor.Deformed>
			{
				@SuppressWarnings("unchecked")
				static final ByReference<Datum.Accessor.Deformed>[] ACCESSORS =
					new ByReference[ACCESSORS_ARRAY_LENGTH];

				static final ByValue<Datum.Accessor.Deformed> s_pointerAccessor;

				static
				{
					new Deformed();
					s_pointerAccessor =
						ByValue.Deformed.ACCESSORS[SIZEOF_DATUM];
				}

				private Deformed()
				{
					List.<ByReference<Datum.Accessor.Deformed>>of(
						this,
						new R1<>(), new R2<>(), new R3<>(), new R4<>(),
						new R5<>(), new R6<>(), new R7<>(), new R8<>(),
						new Fixed<>(), new CString<>(), new Varlena<>()
					).toArray(ACCESSORS);
				}

				@Override
				protected ByteBuffer mapFixedLengthReference(
					ByteBuffer bb, int off, int len)
				{
					long p = s_pointerAccessor.getLongZeroExtended(bb, off);
					return mapFixedLength(p, len);
				}

				@Override
				protected ByteBuffer mapCStringReference(ByteBuffer bb, int off)
				{
					long p = s_pointerAccessor.getLongZeroExtended(bb, off);
					return mapCString(p);
				}

				@Override
				protected Datum.Input mapVarlenaReference(ByteBuffer b, int off,
					ResourceOwner ro, MemoryContext mc)
				{
					long p = s_pointerAccessor.getLongZeroExtended(b, off);
					return mapVarlena(p, ro, mc);
				}
			}

			/*
			 * Convention: when invoking a heap accessor method, the offset
			 * shall already have been adjusted for alignment (according to
			 * PostgreSQL's alignment rules, that is, so the right value will be
			 * accessed). Java's ByteBuffer API will still check and possibly
			 * split accesses according to the hardware's rules; there's no way
			 * to talk it out of that, so there's little to gain by being more
			 * clever here.
			 *
			 * The ByReference case includes accessors for non-power-of-two
			 * sizes. To keep things simple here, they just put the widest
			 * accesses first, which should be as good as it gets in the most
			 * expected case where the initial offset is aligned, and Java will
			 * make other cases work too.
			 */
			static class Heap extends Impl<Datum.Accessor.Heap>
			{
				@SuppressWarnings("unchecked")
				static final ByReference<Datum.Accessor.Heap>[] ACCESSORS =
					new ByReference[ACCESSORS_ARRAY_LENGTH];

				static
				{
					new Heap();
				}

				private Heap()
				{
					List.<ByReference<Datum.Accessor.Heap>>of(
						this,
						new R1<>(), new R2<>(), new R3<>(), new R4<>(),
						new R5<>(), new R6<>(), new R7<>(), new R8<>(),
						new Fixed<>(), new CString<>(), new Varlena<>()
					).toArray(ACCESSORS);
				}

				@Override
				protected ByteBuffer mapFixedLengthReference(
					ByteBuffer bb, int off, int len)
				{
					return mapFixedLength(bb, off, len);
				}

				@Override
				protected ByteBuffer mapCStringReference(ByteBuffer bb, int off)
				{
					return mapCString(bb, off);
				}

				@Override
				protected Datum.Input mapVarlenaReference(ByteBuffer b, int off,
					ResourceOwner ro, MemoryContext mc)
				{
					return mapVarlena(b, off, ro, mc);
				}
			}

			abstract static class Impl<L extends Datum.Layout>
			extends ByReference<L>
			{
				abstract ByteBuffer mapFixedLengthReference(
					ByteBuffer bb, int off, int len);

				abstract ByteBuffer mapCStringReference(ByteBuffer bb, int off);

				/*
				 * If the varlena is a TOAST pointer and can be parked until
				 * needed by pinning a snapshot, ro is the ResourceOwner it will
				 * be pinned to. If the content gets fetched, uncompressed, or
				 * copied, it will be into a new memory context with mc as its
				 * parent.
				 */
				abstract Datum.Input mapVarlenaReference(ByteBuffer bb, int off,
					ResourceOwner ro, MemoryContext mc);

				Datum.Input copyFixedLengthReference(
					ByteBuffer b, int off, int len)
				{
					return
						asAlwaysCopiedDatum(
							mapFixedLengthReference(b, off, len), 0, len);
				}

				class R8<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public long getLongSignExtended(ByteBuffer bb, int off)
					{
						return mapFixedLengthReference(bb, off, 8).getLong();
					}
					@Override
					public long getLongZeroExtended(ByteBuffer bb, int off)
					{
						return mapFixedLengthReference(bb, off, 8).getLong();
					}
					@Override
					public double getDouble(ByteBuffer bb, int off)
					{
						return mapFixedLengthReference(bb, off, 8).getDouble();
					}
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						return copyFixedLengthReference(bb, off, 8);
					}
				}

				class R7<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public long getLongSignExtended(ByteBuffer bb, int off)
					{
						long r = getLongZeroExtended(bb, off);
						return r | (0L - ((r & 0x80_0000_0000_0000L) << 1));
					}
					@Override
					public long getLongZeroExtended(ByteBuffer bb, int off)
					{
						ByteBuffer mb = mapFixedLengthReference(bb, off, 7);
						long r;
						if ( BIG_ENDIAN )
						{
							r = Integer.toUnsignedLong(mb.getInt()) << 24;
							r |= (long)mb.getChar() << 8;
							r |= Byte.toUnsignedLong(mb.get());
							return r;
						}
						r = Integer.toUnsignedLong(mb.getInt());
						r |= (long)mb.getChar() << 32;
						r |= Byte.toUnsignedLong(mb.get()) << 48;
						return r;
					}
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						return copyFixedLengthReference(bb, off, 7);
					}
				}

				class R6<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public long getLongSignExtended(ByteBuffer bb, int off)
					{
						long r = getLongZeroExtended(bb, off);
						return r | (0L - ((r & 0x8000_0000_0000L) << 1));
					}
					@Override
					public long getLongZeroExtended(ByteBuffer bb, int off)
					{
						ByteBuffer mb = mapFixedLengthReference(bb, off, 6);
						long r;
						if ( BIG_ENDIAN )
						{
							r = Integer.toUnsignedLong(mb.getInt()) << 16;
							r |= (long)mb.getChar();
							return r;
						}
						r = Integer.toUnsignedLong(mb.getInt());
						r |= (long)mb.getChar() << 32;
						return r;
					}
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						return copyFixedLengthReference(bb, off, 6);
					}
				}

				class R5<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public long getLongSignExtended(ByteBuffer bb, int off)
					{
						long r = getLongZeroExtended(bb, off);
						return r | (0L - ((r & 0x80_0000_0000L) << 1));
					}
					@Override
					public long getLongZeroExtended(ByteBuffer bb, int off)
					{
						ByteBuffer mb = mapFixedLengthReference(bb, off, 5);
						long r;
						if ( BIG_ENDIAN )
						{
							r = Integer.toUnsignedLong(mb.getInt()) << 8;
							r |= Byte.toUnsignedLong(mb.get());
							return r;
						}
						r = Integer.toUnsignedLong(mb.getInt());
						r |= Byte.toUnsignedLong(mb.get()) << 32;
						return r;
					}
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						return copyFixedLengthReference(bb, off, 5);
					}
				}

				class R4<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public int getIntSignExtended(ByteBuffer bb, int off)
					{
						return mapFixedLengthReference(bb, off, 4).getInt();
					}
					@Override
					public int getIntZeroExtended(ByteBuffer bb, int off)
					{
						return mapFixedLengthReference(bb, off, 4).getInt();
					}
					@Override
					public float getFloat(ByteBuffer bb, int off)
					{
						return mapFixedLengthReference(bb, off, 4).getFloat();
					}
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						return copyFixedLengthReference(bb, off, 4);
					}
				}

				class R3<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public int getIntSignExtended(ByteBuffer bb, int off)
					{
						int r = getIntZeroExtended(bb, off);
						return r | (0 - ((r & 0x80_0000) << 1));
					}
					@Override
					public int getIntZeroExtended(ByteBuffer bb, int off)
					{
						ByteBuffer mb = mapFixedLengthReference(bb, off, 3);
						int r;
						if ( BIG_ENDIAN )
						{
							r = (int)mb.getChar() << 8;
							r |= Byte.toUnsignedInt(mb.get());
							return r;
						}
						r = (int)mb.getChar();
						r |= Byte.toUnsignedInt(mb.get()) << 16;
						return r;
					}
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						return copyFixedLengthReference(bb, off, 3);
					}
				}

				class R2<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public short getShort(ByteBuffer bb, int off)
					{
						return mapFixedLengthReference(bb, off, 2).getShort();
					}
					@Override
					public char getChar(ByteBuffer bb, int off)
					{
						return mapFixedLengthReference(bb, off, 2).getChar();
					}
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						return copyFixedLengthReference(bb, off, 2);
					}
				}

				class R1<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public byte getByte(ByteBuffer bb, int off)
					{
						return mapFixedLengthReference(bb, off, 1).get();
					}
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						return copyFixedLengthReference(bb, off, 1);
					}
				}

				class Fixed<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						int len = a.length();
						if ( len <= NAMEDATALEN )
							return copyFixedLengthReference(bb, off, len);
						// XXX even copy bigger ones, for now
						return copyFixedLengthReference(bb, off, len);
					}
				}

				class CString<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						ByteBuffer bnew = mapCStringReference(bb, off);
						// XXX for now, return a Java copy regardless of size
						return asAlwaysCopiedDatum(bnew, 0, bnew.remaining());
					}
				}

				class Varlena<L extends Datum.Layout> extends ByReference<L>
				{
					@Override
					public Datum.Input getDatum(
						ByteBuffer bb, int off, Attribute a)
					{
						// XXX no control over resowner and context for now
						return mapVarlenaReference(bb, off,
							TopTransactionResourceOwner(),
							TopTransactionContext());
					}
				}
			}
		}
	}

	private static class AccessorWidthException extends RuntimeException
	{
		AccessorWidthException()
		{
			super(null, null, false, false);
		}
	}
}
