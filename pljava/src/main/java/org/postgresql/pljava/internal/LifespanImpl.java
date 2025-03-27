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
package org.postgresql.pljava.internal;

import java.lang.ref.Reference; // for javadoc

import org.postgresql.pljava.Lifespan;

import org.postgresql.pljava.internal.DualState;

/**
 * Implements PL/Java's generalized notion of lifespans.
 *<p>
 * Subclasses are likely to maintain cache mappings from addresses of PostgreSQL
 * native objects to instances. Such mappings must hold strong references to the
 * instances, because any {@code LifespanImpl} instance can serve as the
 * head of a list of {@code DualState} objects, which are
 * {@link Reference Reference} instances, and the Java runtime will cease
 * tracking those if they themselves are not kept strongly reachable. This
 * requirement is acceptable, because all instances represent bounded lifespans
 * that end with explicit invalidation and decaching; that's what they're for,
 * after all.
 */
public class LifespanImpl extends DualState.ListHead implements Lifespan
{
	public interface Addressed
	{
		long address();
	}

	/**
	 * Overrides the version provided by {@code DualState} to simply call
	 * the niladic {@code toString}, as a resource owner isn't directly
	 * associated with another object the way a {@code DualState} instance
	 * generally is.
	 */
	@Override
	public String toString(Object o)
	{
		assert null == o || this == o;
		return toString();
	}

	@Override
	public String toString()
	{
		Class<?> c = getClass();
		String cn = c.getCanonicalName();
		int pnl = c.getPackageName().length();
		return cn.substring(1 + pnl);
	}
}
