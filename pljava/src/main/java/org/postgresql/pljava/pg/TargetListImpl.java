/*
 * Copyright (c) 2023 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.TupleDescriptor;
import org.postgresql.pljava.model.TupleTableSlot;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import java.lang.ref.WeakReference;

import java.sql.SQLException;

import java.util.AbstractList;
import java.util.Arrays;
import static java.util.Arrays.copyOfRange;
import java.util.BitSet;
import java.util.Collection;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import static java.util.Objects.checkFromToIndex;
import static java.util.Objects.requireNonNull;
import java.util.Spliterator;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import java.util.Spliterators;
import static java.util.Spliterators.spliteratorUnknownSize;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implementation of {@link TargetList TargetList}.
 */
class TargetListImpl extends AbstractList<Attribute> implements TargetList
{
	private static final Projected EMPTY = new Projected(null, new short[0]);

	private final TupleDescriptor m_tdesc;
	private final short[] m_map;

	private TargetListImpl(TupleDescriptor tdesc, short[] map)
	{
		m_tdesc = tdesc;
		m_map = map; // not cloned here; caller should ensure no aliasing
	}

	@Override // List
	public Attribute get(int index)
	{
		return m_tdesc.get(m_map[index]);
	}

	@Override // List
	public int size()
	{
		return m_map.length;
	}

	@Override // TargetList
	public TargetList subList(int fromIndex, int toIndex)
	{
		if ( 0 == fromIndex  &&  m_map.length == toIndex )
			return this;
		checkFromToIndex(fromIndex, toIndex, m_map.length);
		if ( fromIndex == toIndex )
			return EMPTY;
		return new TargetListImpl(
			m_tdesc, copyOfRange(m_map, fromIndex, toIndex));
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

	static class Projected extends TargetListImpl implements Projection
	{
		Projected(TupleDescriptor tdesc, short[] map)
		{
			super(tdesc, map);
		}

		static Projection project(TupleDescriptor src, int... indices)
		{
			if ( requireNonNull(indices, "project() indices null").length == 0 )
				return EMPTY;

			int n = src.size();

			IntSummaryStatistics s =
				Arrays.stream(indices).distinct().summaryStatistics();

			if ( s.getCount() < indices.length || indices.length > n
				|| 0 > s.getMin() || s.getMax() > n - 1 )
				throw new IllegalArgumentException(String.format(
					"project() indices must be distinct, >= 0, and < %d: %s",
					n, Arrays.toString(indices)
				));

			if ( ( indices.length == n )
				&& Arrays.stream(indices).allMatch(i -> i == indices[i]) )
				return src;

			short[] map = new short [ indices.length ];
			for ( int i = 0 ; i < indices.length ; ++ i )
				map[i] = (short)indices[i];

			return new Projected(src, map);
		}

		static Projection sqlProject(TupleDescriptor src, int... indices)
		{
			if ( requireNonNull(indices, "sqlProject() indices null").length
				== 0 )
				return EMPTY;

			int n = src.size();

			IntSummaryStatistics s =
				Arrays.stream(indices).distinct().summaryStatistics();

			if ( s.getCount() < indices.length || indices.length > n
				|| 1 > s.getMin() || s.getMax() > n )
				throw new IllegalArgumentException(String.format(
					"sqlProject() indices must be distinct, > 0, and <= %d: %s",
					n, Arrays.toString(indices)
				));

			if ( ( indices.length == src.size() )
				&& Arrays.stream(indices).allMatch(i -> i == indices[i-1]) )
				return src;

			short[] map = new short [ indices.length ];
			for ( int i = 0 ; i < indices.length ; ++ i )
				map[i] = (short)(indices[i] - 1);

			return new Projected(src, map);
		}

		static Projection project(TupleDescriptor src, Simple... names)
		{
			if ( requireNonNull(names, "project() names null").length == 0 )
				return EMPTY;

			int n = src.size();

			/*
			 * An exception could be thrown here if names.length > n, but that
			 * condition ensures the later exception for names left unmatched
			 * will have to be thrown, and as long as that's going to happen
			 * anyway, the extra work to see just what names didn't match
			 * produces a more helpful message.
			 */

			BitSet pb = new BitSet(names.length);
			pb.set(0, names.length);

			short[] map = new short [ names.length ];

			for ( int i = 0 ; i < n ; ++ i )
			{
				Attribute attr = src.get(i);
				Simple name = attr.name();

				for ( int j = pb.nextSetBit(0); 0 <= j; j = pb.nextSetBit(++j) )
				{
					if ( ! name.equals(names[j]) )
						continue;
					map[j] = (short)i;
					pb.clear(j);
					if ( pb.isEmpty() )
						return new Projected(src, map);
					break;
				}
			}

			throw new IllegalArgumentException(
				"project() left unmatched by name: " + Arrays.toString(
					pb.stream().mapToObj(i->names[i]).toArray(Simple[]::new)));
		}

		static Projection project(TupleDescriptor src, Attribute... attrs)
		{
			if ( requireNonNull(attrs, "project() attrs null").length == 0 )
				return EMPTY;

			int n = src.size();

			if ( attrs.length > n )
				throw new IllegalArgumentException(String.format(
					"project() more than %d attributes supplied", n));

			BitSet pb = new BitSet(attrs.length);
			pb.set(0, attrs.length);

			BitSet sb = new BitSet(src.size()); // to detect repetition

			short[] map = new short [ attrs.length ];

			for ( int i = 0 ; i < attrs.length ; ++ i )
			{
				Attribute attr = attrs[i];
				int idx = attr.subId() - 1;
				if ( sb.get(idx) )           // repetition?
					continue;
				if ( ! foundIn(attr, src) )
					continue;
				sb.set(idx);
				map[i] = (short)idx;
				pb.clear(i);
			}

			if ( pb.isEmpty() )
				return new Projected(src, map);

			throw new IllegalArgumentException(
				"project() extraneous attributes: " + Arrays.toString(
					pb.stream()
						.mapToObj(i->attrs[i]).toArray(Attribute[]::new)));
		}

		static Projection subList(
			TupleDescriptor src, int fromIndex, int toIndex)
		{
			int n = src.size();

			if ( 0 == fromIndex  &&  n == toIndex )
				return src;
			checkFromToIndex(fromIndex, toIndex, n);
			if ( fromIndex == toIndex )
				return EMPTY;
			short[] map = new short [ toIndex - fromIndex ];
			for ( int i = 0; i < map.length ; ++ i )
				map[i] = (short)(i + fromIndex);
			return new Projected(src, map);
		}

		@Override // Projection
		public Projection subList(int fromIndex, int toIndex)
		{
			TargetListImpl sup = (TargetListImpl)this; // m_tdesc/m-map private

			if ( 0 == fromIndex  &&  sup.m_map.length == toIndex )
				return this;
			checkFromToIndex(fromIndex, toIndex, sup.m_map.length);
			if ( fromIndex == toIndex )
				return EMPTY;
			return new Projected(
				sup.m_tdesc, copyOfRange(sup.m_map, fromIndex, toIndex));
		}

		@Override // Projection
		public Projection project(int... indices)
		{
			if ( requireNonNull(indices, "project() indices null").length == 0 )
				return EMPTY;

			TargetListImpl sup = (TargetListImpl)this; // m_tdesc/m-map private

			int n = sup.m_map.length;

			IntSummaryStatistics s =
				Arrays.stream(indices).distinct().summaryStatistics();

			if ( s.getCount() < indices.length || indices.length > n
				|| 0 > s.getMin() || s.getMax() > n - 1 )
				throw new IllegalArgumentException(String.format(
					"project() indices must be distinct, >= 0, and < %d: %s",
					n, Arrays.toString(indices)
				));

			if ( ( indices.length == n )
				&& Arrays.stream(indices).allMatch(i -> i == indices[i]) )
				return this;

			short[] map = new short [ indices.length ];
			for ( int i = 0 ; i < indices.length ; ++ i )
				map[i] = sup.m_map[indices[i]];

			return new Projected(sup.m_tdesc, map);
		}

		@Override // Projection
		public Projection sqlProject(int... indices)
		{
			if ( requireNonNull(indices, "sqlProject() indices null").length
				== 0 )
				return EMPTY;

			TargetListImpl sup = (TargetListImpl)this; // m_tdesc/m-map private

			int n = sup.m_map.length;

			IntSummaryStatistics s =
				Arrays.stream(indices).distinct().summaryStatistics();

			if ( s.getCount() < indices.length || indices.length > n
				|| 1 > s.getMin() || s.getMax() > n )
				throw new IllegalArgumentException(String.format(
					"sqlProject() indices must be distinct, > 0, and <= %d: %s",
					n, Arrays.toString(indices)
				));

			if ( ( indices.length == n )
				&& Arrays.stream(indices).allMatch(i -> i == indices[i-1]) )
				return this;

			short[] map = new short [ indices.length ];
			for ( int i = 0 ; i < indices.length ; ++ i )
				map[i] = sup.m_map[indices[i] - 1];

			return new Projected(sup.m_tdesc, map);
		}

		@Override // Projection
		public Projection project(Simple... names)
		{
			if ( requireNonNull(names, "project() names null").length == 0 )
				return EMPTY;

			TargetListImpl sup = (TargetListImpl)this; // m_tdesc/m-map private

			int n = sup.m_map.length;

			/*
			 * An exception could be thrown here if names.length > n, but that
			 * condition ensures the later exception for names left unmatched
			 * will have to be thrown, and as long as that's going to happen
			 * anyway, the extra work to see just what names didn't match
			 * produces a more helpful message.
			 */

			BitSet pb = new BitSet(names.length);
			pb.set(0, names.length);

			short[] map = new short [ names.length ];

			for ( int i = 0 ; i < n ; ++ i )
			{
				short mapped = sup.m_map[i];
				Simple name = sup.m_tdesc.get(mapped).name();

				for ( int j = pb.nextSetBit(0); 0 <= j; j = pb.nextSetBit(++j) )
				{
					if ( ! name.equals(names[j]) )
						continue;
					map[j] = mapped;
					pb.clear(j);
					if ( pb.isEmpty() )
						return new Projected(sup.m_tdesc, map);
					break;
				}
			}

			throw new IllegalArgumentException(
				"project() left unmatched by name: " + Arrays.toString(
					pb.stream().mapToObj(i->names[i]).toArray(Simple[]::new)));
		}

		@Override // Projection
		public Projection project(Attribute... attrs)
		{
			if ( requireNonNull(attrs, "project() attrs null").length == 0 )
				return EMPTY;

			TargetListImpl sup = (TargetListImpl)this; // m_tdesc/m-map private

			int n = sup.m_map.length;

			if ( attrs.length > n )
				throw new IllegalArgumentException(String.format(
					"project() more than %d attributes supplied", n));

			BitSet pb = new BitSet(attrs.length);
			pb.set(0, attrs.length);

			BitSet sb = new BitSet(sup.m_tdesc.size());
			for ( short i : sup.m_map )
				sb.set(i);

			short[] map = new short [ attrs.length ];

			for ( int i = 0 ; i < attrs.length ; ++ i )
			{
				Attribute attr = attrs[i];
				int idx = attr.subId() - 1;
				if ( ! sb.get(idx) )
					continue;
				if ( ! foundIn(attr, sup.m_tdesc) )
					continue;
				map[i] = (short)idx;
				pb.clear(i);
				sb.clear(idx);
			}

			if ( pb.isEmpty() )
				return new Projected(sup.m_tdesc, map);

			throw new IllegalArgumentException(
				"project() extraneous attributes: " + Arrays.toString(
					pb.stream()
						.mapToObj(i->attrs[i]).toArray(Attribute[]::new)));
		}
	}

	private static boolean foundIn(Attribute a, TupleDescriptor td)
	{
		return ((AttributeImpl)a).foundIn(td);
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

			if ( m_slots instanceof Collection )
				spl = Spliterators
					.spliterator(itr, ((Collection)m_slots).size(), chr);
			else
				spl = spliteratorUnknownSize(itr, chr);

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
