/*
 * Copyright (c) 2023-2025 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.Adapter.AdapterException;
import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.Adapter.AsBoolean;
import org.postgresql.pljava.Adapter.AsByte;
import org.postgresql.pljava.Adapter.AsChar;
import org.postgresql.pljava.Adapter.AsDouble;
import org.postgresql.pljava.Adapter.AsFloat;
import org.postgresql.pljava.Adapter.AsInt;
import org.postgresql.pljava.Adapter.AsLong;
import org.postgresql.pljava.Adapter.AsShort;
import org.postgresql.pljava.TargetList;
import org.postgresql.pljava.TargetList.Cursor;
import org.postgresql.pljava.TargetList.Projection;

import
	org.postgresql.pljava.internal.AbstractNoSplitList.IteratorNonSpliterator;

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.TupleDescriptor;
import org.postgresql.pljava.model.TupleTableSlot;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import java.lang.ref.WeakReference;

import java.sql.SQLException;

import java.util.AbstractSequentialList;
import java.util.Arrays;
import static java.util.Arrays.copyOfRange;
import java.util.BitSet;
import java.util.Collection;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import static java.util.Objects.checkFromToIndex;
import static java.util.Objects.checkIndex;
import static java.util.Objects.requireNonNull;
import java.util.Spliterator;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import java.util.Spliterators;
import static java.util.Spliterators.spliteratorUnknownSize;

import java.util.function.IntUnaryOperator;

import static java.util.stream.Collectors.joining;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implementation of {@link TargetList TargetList}.
 */
 /*
 * This abstract base class in fact implements neither TargetList nor
 * Projection.
 *
 * It always holds a TupleDescriptor and a BitSet. In the concrete subclasses
 * that represent a subset of the attributes with no repetition or permutation,
 * the BitSet is all there is. Subclasses that need to represent repetition
 * (TargetList only) or permutation (TargetList or Projection) also include
 * a mapping array.
 *
 * Bits in the BitSet always (even in multiply-derived projections) correspond
 * to indices in the original TupleDescriptor, streamlining contains() tests.
 */
abstract class TargetListImpl extends AbstractSequentialList<Attribute>
{
	protected static final Projection EMPTY = new P(null, new BitSet());

	protected final TupleDescriptor m_tdesc;
	protected final BitSet m_bitset;

	protected TargetListImpl(TupleDescriptor tdesc, BitSet bitset)
	{
		m_tdesc = tdesc;
		m_bitset = bitset; // not cloned here; caller should ensure no aliasing
	}

	@Override // Collection
	public boolean contains(Object o)
	{
		if ( ! (o instanceof AttributeImpl) )
			return false;
		AttributeImpl ai = (AttributeImpl)o;
		return m_bitset.get(ai.subId() - 1)  &&  m_tdesc.contains(ai);
	}

	@Override // List
	public int indexOf(Object o) // override in M where reordering is possible
	{
		if ( ! contains(o) )
			return -1;
		int index = ((AttributeImpl)o).subId() - 1;
		return (int)m_bitset.stream().takeWhile(i -> i < index).count();
	}

	@Override // List
	public int lastIndexOf(Object o)
	{
		return indexOf(o); // override in MT where o could appear more than once
	}

	@Override
	public ListIterator<Attribute> listIterator()
	{
		return listIterator(0);
	}

	@Override
	public ListIterator<Attribute> listIterator(int index)
	{
		checkIndex(index, size() + 1); // ListIterator can point beyond end
		int attno = m_bitset.stream().skip(index).findFirst().orElse(-1);
		return new BLI(m_tdesc, m_bitset, attno, index);
	}

	@Override // Collection
	public int size()
	{
		return m_bitset.cardinality();
	}

	public <R,X extends Throwable> R applyOver(
		Iterable<TupleTableSlot> tuples, Cursor.Function<R,X> f)
		throws X, SQLException
	{
		return TargetListImpl.applyOver((TargetList)this, tuples, f);
	}

	public <R,X extends Throwable> R applyOver(
		TupleTableSlot tuple, Cursor.Function<R,X> f)
		throws X, SQLException
	{
		return TargetListImpl.applyOver((TargetList)this, tuple, f);
	}

	public Projection project(Simple... names)
	{
		return project(m_tdesc, (TargetList)this, m_bitset, names);
	}

	public Projection project(Attribute... attrs)
	{
		return project(m_tdesc, (TargetList)this, m_bitset.length(), attrs);
	}

	public Projection project(int... indices)
	{
		if ( null == indices )
			throw new NullPointerException("project() indices null");
		return project(Flavor.ZEROBASED, indices.length, i -> indices[i]);
	}

	public Projection sqlProject(int... indices)
	{
		if ( null == indices )
			throw new NullPointerException("sqlProject() indices null");
		return project(Flavor.SQL, indices.length, i -> indices[i]);
	}

	public Projection project(short... indices)
	{
		if ( null == indices )
			throw new NullPointerException("project() indices null");
		return project(Flavor.ZEROBASED, indices.length, i -> indices[i]);
	}

	public Projection sqlProject(short... indices)
	{
		if ( null == indices )
			throw new NullPointerException("sqlProject() indices null");
		return project(Flavor.SQL, indices.length, i -> indices[i]);
	}

	public Projection project(BitSet indices)
	{
		return project(Flavor.ZEROBASED, indices);
	}

	public Projection sqlProject(BitSet indices)
	{
		return project(Flavor.SQL, indices);
	}

	abstract Projection project(Flavor flavor, int n, IntUnaryOperator indices);

	abstract Projection project(Flavor flavor, BitSet indices);

	enum Flavor
	{
		ZEROBASED(
			0, "project",
			"project() indices must be distinct, >= 0, and < %d: %s")
		{
			BitSet cloned(BitSet b)
			{
				return (BitSet)b.clone();
			}
		},
		SQL(
			1, "sqlProject",
			"sqlProject() indices must be distinct, > 0, and <= %d: %s")
		{
			BitSet cloned(BitSet b)
			{
				return b.get(1, b.length());
			}
		};

		int offset;
		String method;
		String checkFormat;

		Flavor(int offset, String method, String checkFormat)
		{
			this.offset = offset;
			this.method = method;
			this.checkFormat = checkFormat;
		}

		abstract BitSet cloned(BitSet b);

		/*
		 * On success, returns the max of the supplied indices, minus offset.
		 * Returns -1 for the empty set.
		 */
		int check(int size, int inLen, IntUnaryOperator indices)
		{
			if ( 0 == inLen )
				return -1;

			IntSummaryStatistics s =
				IntStream.range(0, inLen).map(indices).distinct()
					.summaryStatistics();
			int max = s.getMax();

			if ( s.getCount() < inLen  ||  inLen > size
				||  s.getMin() < offset  ||  size + offset <= max )
				throw new IllegalArgumentException(
					String.format(checkFormat, size,
					IntStream.range(0, inLen).map(indices)
					.mapToObj(Integer::toString)
					.collect(joining(","))));

			return max - offset;
		}

		int check(int size, BitSet indices)
		{
			if ( null == indices )
				throw new NullPointerException(method + "() indices null");

			if ( indices.isEmpty() )
				return -1;

			int max = indices.length() - 1;
			int min = indices.nextSetBit(0);

			if ( min < offset  ||  size + offset <= max )
				throw new IllegalArgumentException(
					String.format(checkFormat, size, indices));

			return max - offset;
		}
	}

	static final class P extends TargetListImpl implements Projection
	{
		protected P(TupleDescriptor tdesc, BitSet bitset)
		{
			/*
			 * Nothing here prevents construction of an instance with bits for
			 * all of tdesc's columns. The idea is never to return such a thing
			 * (just return tdesc itself for that case), but one may be
			 * constructed as a temporary within the static methods here that
			 * TupleDescriptor uses.
			 */
			super(tdesc, bitset);
		}

		@Override // List
		public Projection subList(int fromIndex, int toIndex)
		{
			int n = size();
			if ( 0 == fromIndex  &&  n == toIndex )
				return this;
			checkFromToIndex(fromIndex, toIndex, n);
			if ( fromIndex == toIndex )
				return EMPTY;

			BitSet newBits = new BitSet(m_bitset.length());
			
			m_bitset.stream().skip(fromIndex).limit(toIndex - fromIndex)
				.forEach(newBits::set);
			
			return new P(m_tdesc, newBits);
		}

		@Override
		Projection project(Flavor flavor, int inLen, IntUnaryOperator indices)
		{
			final int offset = flavor.offset;

			int n = size();
			int max = flavor.check(n, inLen, indices);
			if ( -1 == max )
				return EMPTY;

			boolean increasing = increasing(inLen, indices);

			if ( increasing ) // no permutation involved, make a P instance
			{
				if ( inLen == n ) // n distinct increasing values 0..n-1
					return this;  // can only be this exactly

				BitSet newBits = new BitSet(m_bitset.length());

				for (
					int i = 0,                    // index in supplied indices
						j = 0,                    // index 1st col in this proj
						v = m_bitset.nextSetBit(0)// tupledesc index of 1st col
				;
					v >= 0					// nextSetBit returns -1 when done
				;
					++ j,						  // next col in this projection
					v = m_bitset.nextSetBit(v + 1)
				)
				{
					if ( j < indices.applyAsInt(i)-offset )//j not a wanted col
						continue;
					newBits.set(v);			// set tupledesc index in new set
					if ( ++ i == inLen ) // next wanted index
						break;
				}

				return new P(m_tdesc, newBits);
			}

			/*
			 * The indices are not strictly increasing; make MP instance with
			 * a map array to represent permutation.
			 *
			 * First expand this current projection's tupledesc indices
			 * from BitSet into array form.
			 */
			short[] td_indices = new short [ n ];

			for (
				int i = 0,
					v = m_bitset.nextSetBit(0)
			;
				v >= 0
			;
				++ i,
				v = m_bitset.nextSetBit(++v)
			)
			{
				td_indices[i] = (short)v;
			}

			/*
			 * Now construct a new BitSet and map array for an MP instance
			 */
			BitSet newBits = new BitSet(td_indices[max]);
			short[] map = new short [ inLen ];
			for ( int i = 0; i < map.length; ++ i )
			{
				newBits.set(map[i] = td_indices[indices.applyAsInt(i)-offset]);
 			}

			return new MP(m_tdesc, newBits, map);
		}

		@Override
		Projection project(Flavor flavor, BitSet indices)
		{
			final int offset = flavor.offset;

			int n = size();
			int max = flavor.check(n, indices);
			if ( -1 == max )
				return EMPTY;

			if ( indices.cardinality() == n )
				return this;

			BitSet newBits = new BitSet(m_bitset.length());

			for (
				int i = 0,
					v = m_bitset.nextSetBit(0),
					w = indices.nextSetBit(0)
			;
				v >= 0
			;
				++ i,
				v = m_bitset.nextSetBit(v + 1)
			)
			{
				if ( i < w - offset )
					continue;
				newBits.set(v);
				w = indices.nextSetBit(w);
				if ( w < 0 )
					break;
			}

			return new P(m_tdesc, newBits);
		}
	}

	abstract static class M extends TargetListImpl
	{
		protected final short[] m_map;

		M(TupleDescriptor tdesc, BitSet bits, short[] map)
		{
			super(tdesc, bits);
			m_map = map;
		}

		@Override // Collection
		public int size()
		{
			return m_map.length;
		}

		@Override // List
		public Attribute get(int index)
		{
			checkIndex(index, m_map.length);
			return m_tdesc.get(m_map[index]);
		}

		@Override // List
		public int indexOf(Object o)
		{
			if ( ! contains(o) )
				return -1;
			int index = ((AttributeImpl)o).subId() - 1;
			for ( int i = 0; i < m_map.length; ++ i )
				if ( index == m_map[i] )
					return i;

			throw new AssertionError("contains vs. indexOf");
		}

		@Override
		public ListIterator<Attribute> listIterator(int index)
		{
			checkIndex(index, size() + 1);
			return new MLI(m_tdesc, m_map, index);
		}

		@Override // List
		public TargetList subList(int fromIndex, int toIndex)
		{
			if ( 0 == fromIndex  &&  m_map.length == toIndex )
				return (TargetList)this;
			checkFromToIndex(fromIndex, toIndex, m_map.length);
			if ( fromIndex == toIndex )
				return EMPTY;

			BitSet newBits = new BitSet(m_bitset.length());
			short[] map = copyOfRange(m_map, fromIndex, toIndex);

			boolean increasing = true;
			boolean duplicates = false;

			for ( short mapped : map )
			{
				if ( newBits.get(mapped) )
					duplicates = true;
				if ( mapped < newBits.length() - 1 )
					increasing = false;
				newBits.set(mapped);
			}

			if ( duplicates )
				return new MT(m_tdesc, newBits, map);

			if ( increasing )
				return new P(m_tdesc, newBits);

			return new MP(m_tdesc, newBits, map);
		}

		@Override
		Projection project(Flavor flavor, int inLen, IntUnaryOperator indices)
		{
			final int offset = flavor.offset;

			int n = size();
			int max = flavor.check(n, inLen, indices);
			if ( -1 == max )
				return EMPTY;

			if ( ( inLen == n )  &&  increasing(inLen, indices)
				&&  this instanceof Projection )
				return (Projection)this;

			BitSet newBits = new BitSet(m_map[max]);
			short[] map = new short [ inLen ];

			boolean increasing = true;
			boolean duplicates = false;

			for ( int i = 0 ; i < inLen ; ++ i )
			{
				short mapped = m_map[indices.applyAsInt(i) - offset];
				if ( newBits.get(mapped) )
					duplicates = true;
				if ( mapped < newBits.length() - 1 )
					increasing = false;
				newBits.set(mapped);
				map[i] = mapped;
			}

			if ( duplicates )
				throw new IllegalArgumentException(
					flavor.method + "() result would have repeated attributes" +
					" and not be a Projection");

			if ( increasing )
				return new P(m_tdesc, newBits);

			return new MP(m_tdesc, newBits, map);
		}

		@Override
		Projection project(Flavor flavor, BitSet indices)
		{
			final int offset = flavor.offset;

			int n = size();
			int max = flavor.check(n, indices);
			if ( -1 == max )
				return EMPTY;

			BitSet newBits = new BitSet(m_bitset.length());
			short[] map = new short [ indices.cardinality() ];

			boolean increasing = true;
			boolean duplicates = false;

			for (
				int i = 0,
					v = indices.nextSetBit(0)
			;
				v >= 0
			;
				++i,
				v = m_bitset.nextSetBit(v + 1)
			)
			{
				short mapped = m_map[v - offset];
				if ( mapped < newBits.length() - 1 )
					increasing = false;
				if ( newBits.get(mapped) )
					duplicates = true;
				newBits.set(mapped);
				map[i] = mapped;
			}

			if ( duplicates )
				throw new IllegalArgumentException(
					flavor.method + "() result would have repeated attributes" +
					" and not be a Projection");

			if ( increasing )
				return new P(m_tdesc, newBits);

			return new MP(m_tdesc, newBits, map);
		}
	}

	static final class MP extends M implements Projection
	{
		MP(TupleDescriptor tdesc, BitSet bits, short[] map)
		{
			super(tdesc, bits, map);
		}

		@Override // List
		public Projection subList(int fromIndex, int toIndex)
		{
			return (Projection)super.subList(fromIndex, toIndex);
		}
	}

	static final class MT extends M implements TargetList
	{
		MT(TupleDescriptor tdesc, BitSet bits, short[] map)
		{
			super(tdesc, bits, map);
		}

		@Override // List
		public int lastIndexOf(Object o)
		{
			if ( ! contains(o) )
				return -1;
			int index = ((AttributeImpl)o).subId() - 1;
			for ( int i = m_map.length; i --> 0; )
				if ( index == m_map[i] )
					return i;

			throw new AssertionError("contains vs. lastIndexOf");
		}
	}

	static boolean increasing(int nValues, IntUnaryOperator values)
	{
		if ( nValues < 2 )
			return true;
		for ( int i = 1; i < nValues; ++ i )
			if ( values.applyAsInt(i) <= values.applyAsInt(i-1) )
				return false;
		return true;
	}

	static Projection subList(TupleDescriptor src, int fromIndex, int toIndex)
	{
		int n = src.size();

		if ( 0 == fromIndex  &&  n == toIndex )
			return src;
		checkFromToIndex(fromIndex, toIndex, n);
		if ( fromIndex == toIndex )
			return EMPTY;
		BitSet newBits = new BitSet(toIndex);
		newBits.set(fromIndex, toIndex);
		return new P(src, newBits);
	}

	static Projection project(TupleDescriptor src, int... indices)
	{
		if ( null == indices )
			throw new NullPointerException("project() indices null");
		return project(Flavor.ZEROBASED, src, indices.length, i -> indices[i]);
	}

	static Projection sqlProject(TupleDescriptor src, int... indices)
	{
		if ( null == indices )
			throw new NullPointerException("sqlProject() indices null");
		return project(Flavor.SQL, src, indices.length, i -> indices[i]);
	}

	static Projection project(TupleDescriptor src, short... indices)
	{
		if ( null == indices )
			throw new NullPointerException("project() indices null");
		return project(Flavor.ZEROBASED, src, indices.length, i -> indices[i]);
	}

	static Projection sqlProject(TupleDescriptor src, short... indices)
	{
		if ( null == indices )
			throw new NullPointerException("sqlProject() indices null");
		return project(Flavor.SQL, src, indices.length, i -> indices[i]);
	}

	static Projection project(TupleDescriptor src, Simple... names)
	{
		int n = src.size();
		BitSet b = new BitSet(n);
		b.set(0, n);
		return project(src, src, b, names);
	}

	static Projection project(TupleDescriptor src, Attribute... attrs)
	{
		return project(src, src, src.size(), attrs);
	}

	private static Projection project(
		Flavor flavor, TupleDescriptor src, int inLen, IntUnaryOperator indices)
	{
		final int offset = flavor.offset;

		int n = src.size();
		int max = flavor.check(n, inLen, indices);
		if ( -1 == max )
			return EMPTY;

		if ( ( inLen == n )  &&  increasing(inLen, indices) )
			return src;

		BitSet newBits = new BitSet(max);
		short[] map = new short [ inLen ];

		boolean increasing = true;

		for ( int i = 0 ; i < inLen ; ++ i )
		{
			int idx = indices.applyAsInt(i) - offset;
			if ( idx < newBits.length() - 1 )
				increasing = false;
			newBits.set(idx);
			map[i] = (short)idx;
		}

		if ( increasing )
			return new P(src, newBits);

		return new MP(src, newBits, map);
	}

	private static Projection project(
			TupleDescriptor base, TargetList proxy, BitSet proxyHas,
			Simple... names)
	{
		if ( requireNonNull(names, "project() names null").length == 0 )
			return EMPTY;

		/*
		 * An exception could be thrown here if names.length > n, but that
		 * condition ensures the later exception for names left unmatched
		 * will have to be thrown, and as long as that's going to happen
		 * anyway, the extra work to see just what names didn't match
		 * produces a more helpful message.
		 */

		BitSet namesYetToMatch = new BitSet(names.length);
		namesYetToMatch.set(0, names.length);

		BitSet newBits = new BitSet(proxyHas.length());
		short[] map = new short [ names.length ];

		boolean increasing = true;
		int jMax = -1;

outer:	for (
			int i = proxyHas.nextSetBit(0);
			0 <= i;
			i = proxyHas.nextSetBit(i+1)
		)
		{
			Simple name = base.get(i).name();

			for (
				int j = namesYetToMatch.nextSetBit(0);
				0 <= j;
				j = namesYetToMatch.nextSetBit(j+1)
			)
			{
				if ( name.equals(names[j]) )
				{
					if ( j < jMax )
						increasing = false;
					else
						jMax = j;
					newBits.set(i);
					map[j] = (short)i;
					namesYetToMatch.clear(j);
					if ( namesYetToMatch.isEmpty() )
						break outer;
					break;
				}
			}
		}

		if ( ! namesYetToMatch.isEmpty() )
			throw new IllegalArgumentException(String.format(
				"project() left unmatched by name: %s",
				Arrays.toString(
					namesYetToMatch.stream().mapToObj(i->names[i])
					.toArray(Simple[]::new)
				)));

		return project(base, proxy, newBits, map, increasing);
	}

	private static Projection project(
		TupleDescriptor base, TargetList proxy,
		int highestProxyAttrPlus1, Attribute... attrs)
	{
		if ( requireNonNull(attrs, "project() attrs null").length == 0 )
			return EMPTY;

		BitSet attrsYetToMatch = new BitSet(attrs.length);
		attrsYetToMatch.set(0, attrs.length);

		BitSet newBits = new BitSet(highestProxyAttrPlus1);
		short[] map = new short [ attrs.length ];

		boolean increasing = true;

		for ( int i = 0 ; i < attrs.length ; ++ i )
		{
			Attribute attr = attrs[i];
			if ( ! proxy.contains(attr) )
				continue;
			int idx = attr.subId() - 1;
			if ( newBits.get(idx) ) // it's a duplicate
				continue;
			if ( idx < newBits.length() - 1 )
				increasing = false;
			newBits.set(idx);
			map[i] = (short)idx;
			attrsYetToMatch.clear(i);
		}

		if ( ! attrsYetToMatch.isEmpty() )
			throw new IllegalArgumentException(String.format(
				"project() extraneous attributes: %s",
				Arrays.toString(
					attrsYetToMatch.stream().mapToObj(i->attrs[i])
					.toArray(Attribute[]::new))));

		return project(base, proxy, newBits, map, increasing);
	}

	static Projection project(TupleDescriptor src, BitSet indices)
	{
		return project(Flavor.ZEROBASED, src, indices);
	}

	static Projection sqlProject(TupleDescriptor src, BitSet indices)
	{
		return project(Flavor.SQL, src, indices);
	}

	private static Projection project(
		Flavor flavor, TupleDescriptor src, BitSet indices)
	{
		int n = src.size();
		int max = flavor.check(n, indices);
		if ( -1 == max )
			return EMPTY;

		if ( indices.cardinality() == n )
			return src;

		return new P(src, flavor.cloned(indices));
	}

	/*
	 * A factored-out epilogue. If we have generated newBits/map representing
	 * n distinct attributes and n was proxy.size(), then proxy was a Projection
	 * to start with and may be what to return.
	 */
	private static Projection project(
		TupleDescriptor base, TargetList proxy,
		BitSet newBits, short[] map, boolean increasing)
	{
		if ( map.length == proxy.size() )
		{
			if ( increasing )
			{
				if ( proxy instanceof P  ||  proxy instanceof TupleDescriptor )
					return (Projection)proxy;
			}
			else if ( proxy instanceof MP )
				if ( Arrays.equals(map, ((MP)proxy).m_map) )
					return (Projection)proxy;
		}

		return increasing ? new P(base, newBits) : new MP(base, newBits, map);
	}

	static <R,X extends Throwable> R applyOver(
		TargetList tl, Iterable<TupleTableSlot> tuples, Cursor.Function<R,X> f)
		throws X, SQLException
	{
		try
		{
			return f.apply(new CursorImpl(tl, tuples));
		}
		catch ( AdapterException e )
		{
			throw e.unwrap(SQLException.class);
		}
	}

	static <R,X extends Throwable> R applyOver(
		TargetList tl, TupleTableSlot tuple, Cursor.Function<R,X> f)
		throws X, SQLException
	{
		try
		{
			return f.apply(new CursorImpl(tl, tuple));
		}
		catch ( AdapterException e )
		{
			throw e.unwrap(SQLException.class);
		}
	}

	abstract static class ALI implements ListIterator<Attribute>
	{
		protected final TupleDescriptor m_tdesc;
		/*
		 * Invariant on m_idx: except transiently during an operation, it
		 * doesn't point to the item last returned. It points where the
		 * *next* item will come from if fetching in the same direction.
		 * It is incremented/decremented after every item fetch.
		 *  After fetching everything possible backward, it has an otherwise
		 * invalid value, -1. After fetching everything possible forward, it
		 * has an otherwise invalid value, the underlying source's length.
		 * These are, in fact, the values previousIndex() or nextIndex(),
		 * respectively, will return in those cases.
		 *  Any forward operation that follows a previous() begins by
		 * incrementing this index (for real, if next(), or notionally, for
		 * hasNext or nextIndex); likewise, any backward operation that
		 * follows a next() begins by (really or notionally) decrementing
		 * it.
		 *  The constructor should be called passing idx and forward so chosen
		 * that a call of nextIndex() will produce the caller's desired result.
		 * That can be accomplished either by passing the intended index itself
		 * and forward=true, or the intended index minus one and forward=false.
		 * See the BLI constructor for where both approaches can be useful for
		 * edge cases.
		 */
		protected int m_idx;
		protected boolean m_forward;

		ALI(TupleDescriptor td, int idx, boolean forward)
		{
			m_tdesc = td;
			m_idx = idx;
			m_forward = forward;
		}

		@Override
		public boolean hasPrevious()
		{
			return m_idx >= (m_forward ? 1 : 0);
		}

		@Override
		public int nextIndex()
		{
			return m_forward ? m_idx : m_idx + 1;
		}

		@Override
		public int previousIndex()
		{
			return m_forward ? m_idx - 1 : m_idx;
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException("ListIterator.remove");
		}

		@Override
		public void set(Attribute e)
		{
			throw new UnsupportedOperationException("ListIterator.set");
		}

		@Override
		public void add(Attribute e)
		{
			throw new UnsupportedOperationException("ListIterator.add");
		}
	}

	static class BLI extends ALI
	{
		private final BitSet m_bitset;
		/*
		 * The bit index last returned by the bitset's nextSetBit or
		 * previousSetBit method and used to make a return value from
		 * next() or previous(). This is not the m_idx'th set bit, because
		 * m_idx is left as the index to be used next in the same direction.
		 * A *change* of direction will bump m_idx back into correspondence with
		 * this value, and the value can be reused (and then m_idx will be
		 * bumped again and left pointing past it in the new direction).
		 *  BitSet's nextSetBit and previousSetBit methods can return -1 when
		 * no such bit exists in either direction, but none of the iterator
		 * options should store such a value here. They should simply leave
		 * the last-used value here, and adjust m_idx and m_forward so that it
		 * will be reused if the direction changes.
		 *  On construction, the caller may pass -1 if listIterator(index) has
		 * been called with index the otherwise-invalid value equal to
		 * bits.length. For that case, we pass idx and forward=true to
		 * the superclass constructor, and initialize m_attno here to
		 * bits.length() - 1, so that value can be used for the first backward
		 * fetch. In all other cases, the super constructor gets idx - 1 and
		 * forward=false, so the value stored here will be used for the first
		 * forward fetch. The only way a -1 value can be stored here is in
		 * the constructor, if the bitset is empty.
		 */
		private int m_attno;

		BLI(TupleDescriptor td, BitSet bits, int attno, int idx)
		{
			super(td, -1 == attno ? idx : idx - 1, -1 == attno);
			m_bitset = bits;
			m_attno = -1 != attno ? attno : bits.length() - 1;
		}

		@Override
		public boolean hasNext()
		{
			if ( -1 == m_attno )
				return false;
			if ( m_forward )
				return -1 != m_bitset.nextSetBit(m_attno + 1);
			/*
			 * Existing direction is backward, so next() would be a direction
			 * change, and the valid value in m_attno is what it would use.
			 */
			return true;
		}

		@Override
		public Attribute next()
		{
			int attno = m_attno;
			if ( ! m_forward )
			{
				m_forward = true;
				++ m_idx;
			}
			else if ( -1 != attno )
			{
				attno = m_bitset.nextSetBit(attno + 1);
				if ( -1 != attno )
					m_attno = attno;
			}

			if ( -1 == attno )
				throw new NoSuchElementException();

			++ m_idx;
			return m_tdesc.get(attno);
		}

		@Override
		public Attribute previous()
		{
			int attno = m_attno;
			if ( m_forward )
			{
				m_forward = false;
				-- m_idx;
			}
			else if ( -1 != attno )
			{
				attno = m_bitset.previousSetBit(attno - 1);
				if ( -1 != attno )
					m_attno = attno;
			}

			if ( -1 == attno )
				throw new NoSuchElementException();

			-- m_idx;
			return m_tdesc.get(attno);
		}
	}

	static class MLI extends ALI
	{
		private final short[] m_map;

		MLI(TupleDescriptor td, short[] map, int idx)
		{
			super(td, idx, true);
			m_map = map;
		}

		@Override
		public boolean hasNext()
		{
			return m_map.length > (m_forward ? m_idx : m_idx + 1);
		}

		@Override
		public Attribute next()
		{
			if ( ! m_forward )
			{
				m_forward = true;
				++ m_idx;
			}

			if ( m_idx > m_map.length - 1 )
				throw new NoSuchElementException();

			return m_tdesc.get(m_map[m_idx ++]);
		}

		@Override
		public Attribute previous()
		{
			if ( m_forward )
			{
				m_forward = false;
				-- m_idx;
			}

			if ( m_idx < 0 )
				throw new NoSuchElementException();

			return m_tdesc.get(m_map[m_idx --]);
		}
	}

	static class CursorImpl implements TargetList.Cursor, AutoCloseable
	{
		private final TargetList m_tlist;
		private final int m_targets;
		private Iterable<TupleTableSlot> m_slots;
		private TupleTableSlot m_currentSlot;
		private int m_currentTarget;
		private int m_nestLevel;
		private WeakReference<Itr> m_activeIterator;

		CursorImpl(TargetList tlist, Iterable<TupleTableSlot> slots)
		{
			m_tlist = tlist;
			m_targets = tlist.size();
			m_slots = requireNonNull(slots, "applyOver() tuples null");
		}

		CursorImpl(TargetList tlist, TupleTableSlot slot)
		{
			m_tlist = tlist;
			m_targets = tlist.size();
			m_currentSlot = requireNonNull(slot, "applyOver() tuple null");
		}

		@Override // Iterable<Cursor>
		public Iterator<Cursor> iterator()
		{
			if ( 0 < m_nestLevel )
				throw new IllegalStateException(
					"Cursor.iterator() called within a curried CursorFunction");

			/*
			 * Only one Iterator should be active at a time. There is nothing in
			 * Iterator's API to indicate when one is no longer active (its user
			 * might just stop iterating it), so just keep track of whether an
			 * earlier-created one is still around and, if so, sabotage it.
			 */
			WeakReference<Itr> iRef = m_activeIterator;
			if ( null != iRef )
			{
				Itr i = iRef.get();
				if ( null != i )
				{
					i.slot_iter = new Iterator<TupleTableSlot>()
					{
						@Override
						public boolean hasNext()
						{
							throw new IllegalStateException(
								"another iterator for this Cursor has been " +
								"started");
						}
						@Override
						public TupleTableSlot next()
						{
							hasNext();
							return null;
						}
					};
				}
			}

			if ( null == m_slots )
			{
				m_slots = List.of(m_currentSlot);
				m_currentSlot = null;
			}

			Itr i = new Itr();
			m_activeIterator = new WeakReference<>(i);
			return i;
		}

		@Override // Cursor
		public Stream<Cursor> stream()
		{
			Iterator<Cursor> itr = iterator();
			Spliterator<Cursor> spl;
			int chr = IMMUTABLE | NONNULL | ORDERED;
			long est = Long.MAX_VALUE;

			if ( m_slots instanceof Collection )
			{
				est = ((Collection)m_slots).size();
				chr |= SIZED;
			}

			spl = new IteratorNonSpliterator<>(itr, est, chr);

			return StreamSupport.stream(spl, false);
		}

		class Itr implements Iterator<Cursor>
		{
			private Iterator<TupleTableSlot> slot_iter = m_slots.iterator();

			@Override
			public boolean hasNext()
			{
				return slot_iter.hasNext();
			}

			@Override
			public Cursor next()
			{
				m_currentSlot = slot_iter.next();
				m_currentTarget = 0;
				return CursorImpl.this;
			}
		}

		@Override // Iterator<Attribute>
		public boolean hasNext()
		{
			return m_currentTarget < m_targets;
		}

		@Override // Iterator<Attribute>
		public Attribute next()
		{
			if ( m_currentTarget < m_targets )
				return m_tlist.get(m_currentTarget++);

			throw new NoSuchElementException(
				"fewer Attributes in TargetList than parameters to assign");
		}

		private CursorImpl nest()
		{
			++ m_nestLevel;
			return this;
		}

		@Override // AutoCloseable
		public void close()
		{
			if ( 0 == -- m_nestLevel )
				m_currentTarget = 0;
		}

		@Override
		public <R,X extends Throwable> R apply(
			L0<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				return f.apply();
			}
		}

		@Override
		public <R,X extends Throwable,A> R apply(
			As<A,?> a0,
			L1<R,X,A> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				A v0 = m_currentSlot.get(next(), a0);
				return f.apply(v0);
			}
		}

		@Override
		public <R,X extends Throwable,A,B> R apply(
			As<A,?> a0, As<B,?> a1,
			L2<R,X,A,B> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				A v0 = m_currentSlot.get(next(), a0);
				B v1 = m_currentSlot.get(next(), a1);
				return f.apply(v0, v1);
			}
		}

		@Override
		public <R,X extends Throwable,A,B,C> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2,
			L3<R,X,A,B,C> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				A v0 = m_currentSlot.get(next(), a0);
				B v1 = m_currentSlot.get(next(), a1);
				C v2 = m_currentSlot.get(next(), a2);
				return f.apply(v0, v1, v2);
			}
		}

		@Override
		public <R,X extends Throwable,A,B,C,D> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			L4<R,X,A,B,C,D> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				A v0 = m_currentSlot.get(next(), a0);
				B v1 = m_currentSlot.get(next(), a1);
				C v2 = m_currentSlot.get(next(), a2);
				D v3 = m_currentSlot.get(next(), a3);
				return f.apply(v0, v1, v2, v3);
			}
		}

		@Override
		public <R,X extends Throwable,A,B,C,D,E> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			As<E,?> a4,
			L5<R,X,A,B,C,D,E> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				A v0 = m_currentSlot.get(next(), a0);
				B v1 = m_currentSlot.get(next(), a1);
				C v2 = m_currentSlot.get(next(), a2);
				D v3 = m_currentSlot.get(next(), a3);
				E v4 = m_currentSlot.get(next(), a4);
				return f.apply(v0, v1, v2, v3, v4);
			}
		}

		@Override
		public <R,X extends Throwable,A,B,C,D,E,F> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			As<E,?> a4, As<F,?> a5,
			L6<R,X,A,B,C,D,E,F> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				A v0 = m_currentSlot.get(next(), a0);
				B v1 = m_currentSlot.get(next(), a1);
				C v2 = m_currentSlot.get(next(), a2);
				D v3 = m_currentSlot.get(next(), a3);
				E v4 = m_currentSlot.get(next(), a4);
				F v5 = m_currentSlot.get(next(), a5);
				return f.apply(v0, v1, v2, v3, v4, v5);
			}
		}

		@Override
		public <R,X extends Throwable,A,B,C,D,E,F,G> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			As<E,?> a4, As<F,?> a5, As<G,?> a6,
			L7<R,X,A,B,C,D,E,F,G> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				A v0 = m_currentSlot.get(next(), a0);
				B v1 = m_currentSlot.get(next(), a1);
				C v2 = m_currentSlot.get(next(), a2);
				D v3 = m_currentSlot.get(next(), a3);
				E v4 = m_currentSlot.get(next(), a4);
				F v5 = m_currentSlot.get(next(), a5);
				G v6 = m_currentSlot.get(next(), a6);
				return f.apply(v0, v1, v2, v3, v4, v5, v6);
			}
		}

		@Override
		public <R,X extends Throwable,A,B,C,D,E,F,G,H> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			As<E,?> a4, As<F,?> a5, As<G,?> a6, As<H,?> a7,
			L8<R,X,A,B,C,D,E,F,G,H> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				A v0 = m_currentSlot.get(next(), a0);
				B v1 = m_currentSlot.get(next(), a1);
				C v2 = m_currentSlot.get(next(), a2);
				D v3 = m_currentSlot.get(next(), a3);
				E v4 = m_currentSlot.get(next(), a4);
				F v5 = m_currentSlot.get(next(), a5);
				G v6 = m_currentSlot.get(next(), a6);
				H v7 = m_currentSlot.get(next(), a7);
				return f.apply(v0, v1, v2, v3, v4, v5, v6, v7);
			}
		}

		@Override
		public <R,X extends Throwable,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			As<E,?> a4, As<F,?> a5, As<G,?> a6, As<H,?> a7,
			As<I,?> a8, As<J,?> a9, As<K,?> aa, As<L,?> ab,
			As<M,?> ac, As<N,?> ad, As<O,?> ae, As<P,?> af,
			L16<R,X,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				A v0 = m_currentSlot.get(next(), a0);
				B v1 = m_currentSlot.get(next(), a1);
				C v2 = m_currentSlot.get(next(), a2);
				D v3 = m_currentSlot.get(next(), a3);
				E v4 = m_currentSlot.get(next(), a4);
				F v5 = m_currentSlot.get(next(), a5);
				G v6 = m_currentSlot.get(next(), a6);
				H v7 = m_currentSlot.get(next(), a7);
				I v8 = m_currentSlot.get(next(), a8);
				J v9 = m_currentSlot.get(next(), a9);
				K va = m_currentSlot.get(next(), aa);
				L vb = m_currentSlot.get(next(), ab);
				M vc = m_currentSlot.get(next(), ac);
				N vd = m_currentSlot.get(next(), ad);
				O ve = m_currentSlot.get(next(), ae);
				P vf = m_currentSlot.get(next(), af);
				return f.apply(
					v0, v1, v2, v3, v4, v5, v6, v7,
					v8, v9, va, vb, vc, vd, ve, vf);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsLong<?> a0,
			J1<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				long v0 = m_currentSlot.get(next(), a0);
				return f.apply(v0);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsLong<?> a0, AsLong<?> a1,
			J2<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				long v0 = m_currentSlot.get(next(), a0);
				long v1 = m_currentSlot.get(next(), a1);
				return f.apply(v0, v1);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsLong<?> a0, AsLong<?> a1, AsLong<?> a2,
			J3<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				long v0 = m_currentSlot.get(next(), a0);
				long v1 = m_currentSlot.get(next(), a1);
				long v2 = m_currentSlot.get(next(), a2);
				return f.apply(v0, v1, v2);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsLong<?> a0, AsLong<?> a1, AsLong<?> a2, AsLong<?> a3,
			J4<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				long v0 = m_currentSlot.get(next(), a0);
				long v1 = m_currentSlot.get(next(), a1);
				long v2 = m_currentSlot.get(next(), a2);
				long v3 = m_currentSlot.get(next(), a3);
				return f.apply(v0, v1, v2, v3);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsDouble<?> a0,
			D1<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				double v0 = m_currentSlot.get(next(), a0);
				return f.apply(v0);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsDouble<?> a0, AsDouble<?> a1,
			D2<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				double v0 = m_currentSlot.get(next(), a0);
				double v1 = m_currentSlot.get(next(), a1);
				return f.apply(v0, v1);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsDouble<?> a0, AsDouble<?> a1, AsDouble<?> a2,
			D3<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				double v0 = m_currentSlot.get(next(), a0);
				double v1 = m_currentSlot.get(next(), a1);
				double v2 = m_currentSlot.get(next(), a2);
				return f.apply(v0, v1, v2);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsDouble<?> a0, AsDouble<?> a1, AsDouble<?> a2, AsDouble<?> a3,
			D4<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				double v0 = m_currentSlot.get(next(), a0);
				double v1 = m_currentSlot.get(next(), a1);
				double v2 = m_currentSlot.get(next(), a2);
				double v3 = m_currentSlot.get(next(), a3);
				return f.apply(v0, v1, v2, v3);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsInt<?> a0,
			I1<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				int v0 = m_currentSlot.get(next(), a0);
				return f.apply(v0);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsInt<?> a0, AsInt<?> a1,
			I2<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				int v0 = m_currentSlot.get(next(), a0);
				int v1 = m_currentSlot.get(next(), a1);
				return f.apply(v0, v1);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsInt<?> a0, AsInt<?> a1, AsInt<?> a2,
			I3<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				int v0 = m_currentSlot.get(next(), a0);
				int v1 = m_currentSlot.get(next(), a1);
				int v2 = m_currentSlot.get(next(), a2);
				return f.apply(v0, v1, v2);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsInt<?> a0, AsInt<?> a1, AsInt<?> a2, AsInt<?> a3,
			I4<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				int v0 = m_currentSlot.get(next(), a0);
				int v1 = m_currentSlot.get(next(), a1);
				int v2 = m_currentSlot.get(next(), a2);
				int v3 = m_currentSlot.get(next(), a3);
				return f.apply(v0, v1, v2, v3);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsFloat<?> a0,
			F1<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				float v0 = m_currentSlot.get(next(), a0);
				return f.apply(v0);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsFloat<?> a0, AsFloat<?> a1,
			F2<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				float v0 = m_currentSlot.get(next(), a0);
				float v1 = m_currentSlot.get(next(), a1);
				return f.apply(v0, v1);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsFloat<?> a0, AsFloat<?> a1, AsFloat<?> a2,
			F3<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				float v0 = m_currentSlot.get(next(), a0);
				float v1 = m_currentSlot.get(next(), a1);
				float v2 = m_currentSlot.get(next(), a2);
				return f.apply(v0, v1, v2);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsFloat<?> a0, AsFloat<?> a1, AsFloat<?> a2, AsFloat<?> a3,
			F4<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				float v0 = m_currentSlot.get(next(), a0);
				float v1 = m_currentSlot.get(next(), a1);
				float v2 = m_currentSlot.get(next(), a2);
				float v3 = m_currentSlot.get(next(), a3);
				return f.apply(v0, v1, v2, v3);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsShort<?> a0,
			S1<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				short v0 = m_currentSlot.get(next(), a0);
				return f.apply(v0);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsShort<?> a0, AsShort<?> a1,
			S2<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				short v0 = m_currentSlot.get(next(), a0);
				short v1 = m_currentSlot.get(next(), a1);
				return f.apply(v0, v1);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsShort<?> a0, AsShort<?> a1, AsShort<?> a2,
			S3<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				short v0 = m_currentSlot.get(next(), a0);
				short v1 = m_currentSlot.get(next(), a1);
				short v2 = m_currentSlot.get(next(), a2);
				return f.apply(v0, v1, v2);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsShort<?> a0, AsShort<?> a1, AsShort<?> a2, AsShort<?> a3,
			S4<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				short v0 = m_currentSlot.get(next(), a0);
				short v1 = m_currentSlot.get(next(), a1);
				short v2 = m_currentSlot.get(next(), a2);
				short v3 = m_currentSlot.get(next(), a3);
				return f.apply(v0, v1, v2, v3);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsChar<?> a0,
			C1<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				char v0 = m_currentSlot.get(next(), a0);
				return f.apply(v0);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsChar<?> a0, AsChar<?> a1,
			C2<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				char v0 = m_currentSlot.get(next(), a0);
				char v1 = m_currentSlot.get(next(), a1);
				return f.apply(v0, v1);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsChar<?> a0, AsChar<?> a1, AsChar<?> a2,
			C3<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				char v0 = m_currentSlot.get(next(), a0);
				char v1 = m_currentSlot.get(next(), a1);
				char v2 = m_currentSlot.get(next(), a2);
				return f.apply(v0, v1, v2);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsChar<?> a0, AsChar<?> a1, AsChar<?> a2, AsChar<?> a3,
			C4<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				char v0 = m_currentSlot.get(next(), a0);
				char v1 = m_currentSlot.get(next(), a1);
				char v2 = m_currentSlot.get(next(), a2);
				char v3 = m_currentSlot.get(next(), a3);
				return f.apply(v0, v1, v2, v3);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsByte<?> a0,
			B1<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				byte v0 = m_currentSlot.get(next(), a0);
				return f.apply(v0);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsByte<?> a0, AsByte<?> a1,
			B2<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				byte v0 = m_currentSlot.get(next(), a0);
				byte v1 = m_currentSlot.get(next(), a1);
				return f.apply(v0, v1);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsByte<?> a0, AsByte<?> a1, AsByte<?> a2,
			B3<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				byte v0 = m_currentSlot.get(next(), a0);
				byte v1 = m_currentSlot.get(next(), a1);
				byte v2 = m_currentSlot.get(next(), a2);
				return f.apply(v0, v1, v2);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsByte<?> a0, AsByte<?> a1, AsByte<?> a2, AsByte<?> a3,
			B4<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				byte v0 = m_currentSlot.get(next(), a0);
				byte v1 = m_currentSlot.get(next(), a1);
				byte v2 = m_currentSlot.get(next(), a2);
				byte v3 = m_currentSlot.get(next(), a3);
				return f.apply(v0, v1, v2, v3);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsBoolean<?> a0,
			Z1<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				boolean v0 = m_currentSlot.get(next(), a0);
				return f.apply(v0);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsBoolean<?> a0, AsBoolean<?> a1,
			Z2<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				boolean v0 = m_currentSlot.get(next(), a0);
				boolean v1 = m_currentSlot.get(next(), a1);
				return f.apply(v0, v1);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsBoolean<?> a0, AsBoolean<?> a1, AsBoolean<?> a2,
			Z3<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				boolean v0 = m_currentSlot.get(next(), a0);
				boolean v1 = m_currentSlot.get(next(), a1);
				boolean v2 = m_currentSlot.get(next(), a2);
				return f.apply(v0, v1, v2);
			}
		}

		@Override
		public <R,X extends Throwable> R apply(
			AsBoolean<?> a0, AsBoolean<?> a1, AsBoolean<?> a2, AsBoolean<?> a3,
			Z4<R,X> f)
			throws X
		{
			try ( CursorImpl unnest = nest() )
			{
				boolean v0 = m_currentSlot.get(next(), a0);
				boolean v1 = m_currentSlot.get(next(), a1);
				boolean v2 = m_currentSlot.get(next(), a2);
				boolean v3 = m_currentSlot.get(next(), a3);
				return f.apply(v0, v1, v2, v3);
			}
		}
	}
}
