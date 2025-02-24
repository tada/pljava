/*
 * Copyright (c) 2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.nopolicy;

import java.io.InputStream;
import java.io.Reader;

import static java.util.Arrays.copyOfRange;
import java.util.Collection;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.stream.Collectors.toSet;

/**
 * An unmodifiable subclass of {@link Properties}.
 *<p>
 * The overidden methods violate the superclass API specs to the extent that the
 * specs allow modification, or the returning of modifiable sets or collections.
 *<p>
 * When any overridden method would, per the spec, modify the map, the method
 * will throw {@link UnsupportedOperationException} instead.
 */
public final class FrozenProperties extends Properties
{
	/**
	 * Constructs a {@code FrozenProperties} instance from an existing
	 * {@link Properties} instance.
	 *<p>
	 * The instance will have a defaults list (also frozen) if <var>p</var> has
	 * defaults that have not been superseded by later settings. Defaults are
	 * flattened into a single default properties instance, even if <var>p</var>
	 * had a defaults instance chaining to another or a chain of others.
	 * @param p the instance whose entries are to be copied
	 */
	public FrozenProperties(Properties p)
	{
		super(defaults(p));
		super.putAll(p); // putAll copies only non-default entries
	}

	/**
	 * Constructor used internally to return a frozen instance with only
	 * <var>p</var>'s defaults (entries with keys in <var>subset</var>).
	 */
	private FrozenProperties(Properties p, Set<String> subset)
	{
		// super(subset.size()); // has no @Since but first appears in Java 10
		for ( String s : subset )
			super.put(s, p.get(s));
	}

	/**
	 * Returns a {@code FrozenProperties} instance representing defaults of
	 * <var>p</var> not superseded by later settings.
	 * @return FrozenProperties with the defaults, or null if none
	 */
	private static FrozenProperties defaults(Properties p)
	{
		Set<String> defaultedNames =
			p.stringPropertyNames().stream().filter(n -> ! p.containsKey(n))
			.collect(toSet());
		if ( defaultedNames.isEmpty() )
			return null;
		return new FrozenProperties(p, defaultedNames);
	}

	@Override
	public Object setProperty(String key, String value)
	{
		throw readonly();
	}

	@Override
	public void load(Reader reader)
	{
		throw readonly();
	}

	@Override
	public void load(InputStream inStream)
	{
		throw readonly();
	}

	@Override
	public void loadFromXML(InputStream in)
	{
		throw readonly();
	}

	@Override
	public void clear()
	{
		throw readonly();
	}

	@Override
	public Object computeIfAbsent(
		Object key, Function<Object,?> mappingFunction)
	{
		Object v = get(key);
		if ( null != v )
			return v;
		v = mappingFunction.apply(key);
		if ( null != v )
			throw readonly();
		return null;
	}

	@Override
	public Object computeIfPresent(
		Object key, BiFunction<Object,Object,?> remappingFunction)
	{
		Object v = get(key);
		if ( null == v )
			return null;
		v = remappingFunction.apply(key, v); // if it throws, let it. Else:
		throw readonly();
	}

	@Override
	public Set<Map.Entry<Object,Object>> entrySet()
	{
		return unmodifiableSet(super.entrySet());
	}

	@Override
	public Set<Object> keySet()
	{
		return unmodifiableSet(super.keySet());
	}

	@Override
	public Object merge(Object key, Object value,
		BiFunction<Object,Object,?> remappingFunction)
	{
		throw readonly();
	}

	@Override
	public Object put(Object key, Object value)
	{
		throw readonly();
	}

	@Override
	public void putAll(Map<?,?> t)
	{
		if ( 0 < t.size() )
			throw readonly();
	}

	@Override
	public Object remove(Object key)
	{
		Object v = get(key);
		if ( null != v )
			throw readonly();
		return null;
	}

	@Override
	public Collection<Object> values()
	{
		return unmodifiableCollection(super.values());
	}

	private static UnsupportedOperationException readonly()
	{
		UnsupportedOperationException e =
			new UnsupportedOperationException("FrozenProperties modification");
		StackTraceElement[] t = e.getStackTrace();
		e.setStackTrace(copyOfRange(t, 1, t.length));
		return e;
	}
}
