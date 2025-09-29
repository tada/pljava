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

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;
import java.util.Spliterator;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import java.util.Spliterators.AbstractSpliterator;

import java.util.function.Consumer;
import java.util.function.IntToLongFunction;

import org.postgresql.pljava.internal.AbstractNoSplitList;
import
	org.postgresql.pljava.internal.AbstractNoSplitList.IteratorNonSpliterator;
import org.postgresql.pljava.internal.DualState;
import org.postgresql.pljava.internal.DualState.Pinned;
import org.postgresql.pljava.internal.Invocation;

import org.postgresql.pljava.model.MemoryContext; // for javadoc
import org.postgresql.pljava.model.TupleTableSlot;

import static org.postgresql.pljava.pg.DatumUtils.asReadOnlyNativeOrder;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_DATUM;

/*
 * Plan: a group (maybe a class or interface with nested classes) of
 * implementations that look like lists of TupleTableSlot over different kinds
 * of result:
 * - SPITupleTable (these: a tupdesc, and vals array of HeapTuple pointers)
 * - CatCList (n_members and a members array of CatCTup pointers, where each
 *   CatCTup has a HeapTupleData and HeapTupleHeader nearly but not quite
 *   adjacent), must find tupdesc
 * - Tuplestore ? (is this visible, or concealed behind SPI's cursors?)
 * - Tuplesort ? (")
 * - SFRM results? (Ah, SFRM_Materialize makes a Tuplestore.)
 * - will we ever see a "tuple table" ("which is a List of independent
 *   TupleTableSlots")?
 */

/**
 * Superinterface of one or more classes that can present a sequence of tuples,
 * working from the forms in which PostgreSQL can present them.
 */
public interface TupleList extends List<TupleTableSlot>, AutoCloseable
{
	@Override
	default void close()
	{
	}

	TupleList EMPTY = new Empty();

	/**
	 * Returns a {@code Spliterator} that never splits.
	 *<p>
	 * Because a {@code TupleList} is typically built on a single
	 * {@code TupleTableSlot} holding each tuple in turn, there can be no
	 * thought of parallel stream execution.
	 *<p>
	 * Also, because a {@code TupleList} iterator may return the same
	 * {@code TupleTableSlot} repeatedly, stateful {@code Stream} operations
	 * such as {@code distinct} or {@code sorted} will make no sense applied
	 * to those objects.
	 */
	@Override
	default public Spliterator<TupleTableSlot> spliterator()
	{
		return new IteratorNonSpliterator<>(iterator(), size(),
			IMMUTABLE | NONNULL | ORDERED | SIZED);
	}

	/**
	 * A permanently-empty {@link TupleList TupleList}.
	 */
	final static class Empty
	extends AbstractNoSplitList<TupleTableSlot> implements TupleList
	{
		private Empty()
		{
		}

		@Override
		public int size()
		{
			return 0;
		}

		@Override
		public TupleTableSlot get(int i)
		{
			throw new IndexOutOfBoundsException(
				"Index " + i + " out of bounds for length 0");
		}
	}

	/**
	 * A {@code TupleList} constructed atop a PostgreSQL {@code SPITupleTable}.
	 *<p>
	 * The native table is allocated in a {@link MemoryContext} that will be
	 * deleted when {@code SPI_finish} is called on exit of the current
	 * {@code Invocation}. This class merely maps the native tuple table in
	 * place, and so will prevent later access.
	 */
	class SPI extends AbstractNoSplitList<TupleTableSlot>
	implements TupleList, RandomAccess
	{
		private final State state;
		private final TupleTableSlotImpl ttSlot;
		private final int nTuples;
		private final IntToLongFunction indexToPointer;

		private static class State
		extends DualState.SingleSPIfreetuptable<SPI>
		{
			private State(SPI r, long tt)
			{
				/*
				 * Each SPITupleTable is constructed in a context of its own
				 * that is a child of the SPI Proc context, and is used by
				 * SPI_freetuptable to efficiently free it. By rights, that
				 * context should be the Lifespan here, but that member of
				 * SPITupleTable is declared a private member "not intended for
				 * external callers" in the documentation.
				 *
				 * If that admonition is to be obeyed, a next-best choice is the
				 * current Invocation. As long as SPI connection continues to be
				 * managed automatically and disconnected when the invocation
				 * exits (and it makes its lifespanRelease call before
				 * disconnecting SPI, which it does), it should be safe enough.
				 */
				super(r, Invocation.current(), tt);
			}

			private void close()
			{
				unlessReleased(() ->
				{
					releaseFromJava();
				});
			}
		}

		/**
		 * Constructs an instance over an {@code SPITupleTable}.
		 * @param slot a TupleTableSlotImpl to use. The constructed object's
		 * iterator will return this slot repeatedly, with each tuple in turn
		 * stored into it.
		 * @param spiStructP address of the SPITupleTable structure itself,
		 * saved here to be freed if this object is closed or garbage-collected.
		 * @param htarray ByteBuffer over the consecutive HeapTuple pointers at
		 * spiStructP->vals.
		 */
		SPI(TupleTableSlotImpl slot, long spiStructP, ByteBuffer htarray)
		{
			ttSlot = slot;
			htarray = asReadOnlyNativeOrder(htarray);
			state = new State(this, spiStructP);

			if ( 8 == SIZEOF_DATUM )
			{
				LongBuffer tuples = htarray.asLongBuffer();
				nTuples = tuples.capacity();
				indexToPointer = tuples::get;
				return;
			}
			else if ( 4 == SIZEOF_DATUM )
			{
				IntBuffer tuples = htarray.asIntBuffer();
				nTuples = tuples.capacity();
				indexToPointer = tuples::get;
				return;
			}
			else
				throw new AssertionError("unsupported SIZEOF_DATUM");
		}

		@Override
		public TupleTableSlot get(int index)
		{
			try ( Pinned p = state.pinnedNoChecked() )
			{
				ttSlot.store_heaptuple(
					indexToPointer.applyAsLong(index), false);
				return ttSlot;
			}
		}

		@Override
		public int size()
		{
			return nTuples;
		}

		@Override
		public void close()
		{
			state.close();
		}
	}
}
