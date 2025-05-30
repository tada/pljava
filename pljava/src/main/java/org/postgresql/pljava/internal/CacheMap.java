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
package org.postgresql.pljava.internal;

import java.lang.ref.Reference;
import static java.lang.ref.Reference.reachabilityFence;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import java.nio.ByteBuffer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.stream.Collectors.joining;

/**
 * Utility class for constructing caches that map from keys that consist of
 * one or more primitive values, whose entries can be retained strongly, softly,
 * or weakly.
 */
public class CacheMap<T>
{
	private final Map<ByteBuffer,KeyedEntry<T>> m_map;
	private final ThreadLocal<KVHolder<T>> m_holder;
	private final ThreadLocal<KVHolder<T>> m_holderWithBuffer;
	private final ReferenceQueue<T> m_queue = new ReferenceQueue<>();

	private CacheMap(
		Map<ByteBuffer,KeyedEntry<T>> map,
		Supplier<? extends ByteBuffer> keyBufferSupplier)
	{
		m_map = map;
		m_holder = ThreadLocal.withInitial(() -> new KVHolder<T>());
		m_holderWithBuffer = ThreadLocal.withInitial(() ->
			{
				KVHolder<T> h = m_holder.get();
				h.key = keyBufferSupplier.get();
				return h;
			});
	}

	/**
	 * Construct a {@code CacheMap} based on a concurrent map.
	 */
	public static <T> CacheMap<T> newConcurrent(
		Supplier<? extends ByteBuffer> keyBufferSupplier)
	{
		return new CacheMap<>(
			new ConcurrentHashMap<ByteBuffer,KeyedEntry<T>>(),
			keyBufferSupplier);
	}

	/**
	 * Construct a {@code CacheMap} based on a non-thread-safe map, for cases
	 * where concurrent access from multiple threads can be ruled out.
	 */
	public static <T> CacheMap<T> newThreadConfined(
		Supplier<? extends ByteBuffer> keyBufferSupplier)
	{
		return new CacheMap<>(
			new HashMap<ByteBuffer,KeyedEntry<T>>(),
			keyBufferSupplier);
	}

	private void poll()
	{
		for ( KeyedEntry e; null != (e = (KeyedEntry)m_queue.poll()); )
			m_map.remove(e.key(), e);
		/*
		 * Reference objects (of which e is one) do not override equals() from
		 * Object, which is good, because Map's remove(k,v) actually uses
		 * v.equals(...) and could therefore remove a different object than
		 * intended, if the object had other than == semantics for equals().
		 */
	}

	public <E extends Throwable, E1 extends E, E2 extends E> T softlyCache(
		Checked.Consumer<ByteBuffer,E1> keyer,
		Checked.Function<ByteBuffer,T,E2> cacher)
		throws E
	{
		BiFunction<ByteBuffer,T,KeyedEntry<T>> wrapper =
			(k,v) -> new SoftEntry<>(k, v, m_queue);
		return cache(keyer, cacher, wrapper);
	}

	public <E extends Throwable, E1 extends E, E2 extends E> T weaklyCache(
		Checked.Consumer<ByteBuffer,E1> keyer,
		Checked.Function<ByteBuffer,T,E2> cacher)
		throws E
	{
		BiFunction<ByteBuffer,T,KeyedEntry<T>> wrapper =
			(k,v) -> new WeakEntry<>(k, v, m_queue);
		return cache(keyer, cacher, wrapper);
	}

	public <E extends Throwable, E1 extends E, E2 extends E> T stronglyCache(
		Checked.Consumer<ByteBuffer,E1> keyer,
		Checked.Function<ByteBuffer,T,E2> cacher)
		throws E
	{
		BiFunction<ByteBuffer,T,KeyedEntry<T>> wrapper =
			(k,v) -> new StrongEntry<>(k, v, m_map);
		return cache(keyer, cacher, wrapper);
	}

	@Override
	public String toString()
	{
		return m_map.values().stream()
			.map(Entry::get)
			.filter(Objects::nonNull)
			.map(Object::toString)
			.collect(joining(", ", "{", "}"));
	}

	private <E extends Throwable, E1 extends E, E2 extends E> T cache(
		Checked.Consumer<ByteBuffer,E1> keyer,
		Checked.Function<ByteBuffer,T,E2> cacher,
		BiFunction<ByteBuffer,T,KeyedEntry<T>> wrapper)
		throws E
	{
		poll();
		KVHolder<T> h = m_holderWithBuffer.get();
		ByteBuffer b = h.key;
		b.clear();
		keyer.accept(b);
		b.flip();
		KeyedEntry<T> w;
		for ( ;; )
		{
			w = cacher.inReturning(Checked.Function.use(
				(c) -> m_map.computeIfAbsent(b,
					(k) ->
					{
						m_holderWithBuffer.remove();
						T v = c.apply(k);
						h.value = v; // keep it live while returning through ref
						return null == v ? null : wrapper.apply(k,v);
					}
				)
			));

			if ( null == w )
				return null;
			T v = w.get();
			reachabilityFence(h.value);
			h.value = null; // no longer needed now that v is a strong reference
			if ( null != v )
				return v;
			m_map.remove(w.key(), w);
		}
	}

	/**
	 * Simple lookup, with no way to cache a new entry; returns null if no such
	 * entry is present.
	 *<p>
	 * Returns an {@link Entry Entry} if found, which provides a method to
	 * remove the entry if appropriate.
	 */
	public <E extends Throwable> Entry<T> find(
		Checked.Consumer<ByteBuffer,E> keyer)
		throws E
	{
		poll();
		KVHolder<T> h = m_holderWithBuffer.get();
		ByteBuffer b = h.key;
		b.clear();
		keyer.accept(b);
		b.flip();
		return m_map.get(b);
	}

	public void forEachValue(Consumer<T> action)
	{
		if ( m_map instanceof ConcurrentHashMap )
		{
			ConcurrentHashMap<ByteBuffer,KeyedEntry<T>> m =
				(ConcurrentHashMap<ByteBuffer,KeyedEntry<T>>)m_map;
			m.forEachValue(Long.MAX_VALUE, Entry::get, action);
			return;
		}
		m_map.values().stream().map(Entry::get).filter(Objects::nonNull)
			.forEach(action);
	}

	/**
	 * An entry in a {@link CacheMap CacheMap}.
	 */
	public interface Entry<T>
	{
		T get();
		void remove();
	}

	/**
	 * An {@link Entry Entry} that keeps a reference to its key.
	 */
	interface KeyedEntry<T> extends Entry<T>
	{
		ByteBuffer key();
	}

	/**
	 * A {@link KeyedEntry KeyedEntry} that holds
	 * a {@link SoftReference SoftReference} to its value.
	 */
	static class SoftEntry<T> extends SoftReference<T> implements KeyedEntry<T>
	{
		final ByteBuffer m_key;

		SoftEntry(ByteBuffer k, T v, ReferenceQueue<T> q)
		{
			super(v, q);
			m_key = k;
		}

		@Override
		public ByteBuffer key()
		{
			return m_key;
		}

		@Override
		public void remove()
		{
			clear();
			enqueue();
		}
	}

	/**
	 * A {@link KeyedEntry KeyedEntry} that holds
	 * a {@link WeakReference WeakReference} to its value.
	 */
	static class WeakEntry<T> extends WeakReference<T> implements KeyedEntry<T>
	{
		final ByteBuffer m_key;

		WeakEntry(ByteBuffer k, T v, ReferenceQueue<T> q)
		{
			super(v, q);
			m_key = k;
		}

		@Override
		public ByteBuffer key()
		{
			return m_key;
		}

		@Override
		public void remove()
		{
			clear();
			enqueue();
		}
	}

	/**
	 * A {@link KeyedEntry KeyedEntry} that holds
	 * a strong reference to its value.
	 */
	static class StrongEntry<T> implements KeyedEntry<T>
	{
		final ByteBuffer m_key;
		T m_value;
		final Map<ByteBuffer, KeyedEntry<T>> m_map;

		StrongEntry(ByteBuffer k, T v, Map<ByteBuffer, KeyedEntry<T>> map)
		{
			m_key = k;
			m_value = v;
			m_map = map;
		}

		@Override
		public ByteBuffer key()
		{
			return m_key;
		}

		@Override
		public T get()
		{
			return m_value;
		}

		@Override
		public void remove()
		{
			m_value = null;
			m_map.remove(m_key, this);
		}
	}

	/*
	 * Hold a ByteBuffer for key use, and any new value briefly between
	 * construction and return (to avoid any chance of its being found
	 * weakly reachable before its return).
	 */
	/**
	 * An effectively private class used during {@link CacheMap CacheMap}
	 * operations.
	 *<p>
	 * Until PL/Java's support horizon for Java moves to Java >= 11 where
	 * classes have nestmates, there can be overhead in making nested classes
	 * private, so some in the internal module have been left at package access
	 * for now.
	 */
	static class KVHolder<T>
	{
		ByteBuffer key;
		T value;
	}
}
