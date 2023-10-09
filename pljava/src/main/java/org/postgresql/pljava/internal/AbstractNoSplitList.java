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
package org.postgresql.pljava.internal;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import java.util.Spliterators.AbstractSpliterator;

import java.util.function.Consumer;

import java.util.stream.Stream;

/**
 * An {@link AbstractList} whose {@link #parallelStream parallelStream} method
 * returns a sequential stream (a behavior the spec does allow), and whose
 * {@link #spliterator spliterator} method returns a {@link Spliterator} that
 * never splits.
 *<p>
 * In interfacing with the single-threaded PostgreSQL backend, there are many
 * uses for a class with the behavior of {@link List} but that does not invite
 * unintended parallelism through the stream API.
 */
public abstract class AbstractNoSplitList<E> extends AbstractList<E>
{
	/**
	 * "It is allowable" (and, in this case, inevitable) for this method to
	 * return a sequential stream.
	 */
	@Override
	public Stream<E> parallelStream()
	{
		return stream();
	}

	/**
	 * Returns a {@code Spliterator} that never splits.
	 */
	@Override
	public Spliterator<E> spliterator()
	{
		return new IteratorNonSpliterator<>(iterator(), size(), ORDERED|SIZED);
	}

	public static class IteratorNonSpliterator<E> extends AbstractSpliterator<E>
	{
		private Iterator<E> it;

		public IteratorNonSpliterator(
			Iterator<E> it, long est, int characteristics)
		{
			super(est, characteristics);
			this.it = it;
		}

		@Override
		public boolean tryAdvance(Consumer<? super E> action)
		{
			if ( ! it.hasNext() )
				return false;
			action.accept(it.next());
			return true;
		}

		@Override
		public Spliterator<E> trySplit()
		{
			return null;
		}
	}
}
