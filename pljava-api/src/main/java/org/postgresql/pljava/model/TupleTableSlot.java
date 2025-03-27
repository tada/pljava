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
package org.postgresql.pljava.model;

import java.sql.SQLException;

import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.Adapter.AsLong;
import org.postgresql.pljava.Adapter.AsDouble;
import org.postgresql.pljava.Adapter.AsInt;
import org.postgresql.pljava.Adapter.AsFloat;
import org.postgresql.pljava.Adapter.AsShort;
import org.postgresql.pljava.Adapter.AsChar;
import org.postgresql.pljava.Adapter.AsByte;
import org.postgresql.pljava.Adapter.AsBoolean;

/**
 * A PostgreSQL abstraction that can present a variety of underlying tuple
 * representations in a common way.
 *<p>
 * PL/Java may take the liberty of extending this class to present even some
 * other tuple-like things that are not native tuple forms to PostgreSQL.
 *<p>
 * A readable instance that relies on PostgreSQL's "deforming" can be
 * constructed over any supported flavor of underlying tuple. Retrieving
 * its values can involve JNI calls to the support functions in PostgreSQL.
 * Its writable counterpart is also what must be used for constructing a tuple
 * on the fly; after its values/nulls have been set (pure Java), it can be
 * flattened (at the cost of a JNI call) to return a pass-by-reference
 * {@code Datum} usable as a composite function argument or return value.
 *<p>
 * A specialized instance, with support only for reading, can be constructed
 * over a PostgreSQL tuple in its widely-used 'heap' form. PL/Java knows that
 * form well enough to walk it and retrieve values mostly without JNI calls.
 *<p>
 * A {@code TupleTableSlot} is not safe for concurrent use by multiple threads,
 * in the absence of appropriate synchronization.
 */
public interface TupleTableSlot
{
	TupleDescriptor descriptor();
	RegClass relation();

	/*
	 * Idea: move these methods out of public API, as they aren't very
	 * efficient. Make them invocable internally via TargetList. As an interim
	 * measure, remove their "throws SQLException" clauses; the implementation
	 * hasn't been throwing those anyway, but wrapping them in a runtime
	 * version. (Which needs to get unwrapped eventually, somewhere suitable.)
	 */
	<T>  T  get(Attribute att, As<T,?>  	adapter);
	long	get(Attribute att, AsLong<?>	adapter);
	double  get(Attribute att, AsDouble<?>  adapter);
	int 	get(Attribute att, AsInt<?> 	adapter);
	float	get(Attribute att, AsFloat<?>	adapter);
	short	get(Attribute att, AsShort<?>	adapter);
	char	get(Attribute att, AsChar<?>	adapter);
	byte	get(Attribute att, AsByte<?>	adapter);
	boolean get(Attribute att, AsBoolean<?> adapter);

	<T>  T  get(int idx, As<T,?>	  adapter);
	long	get(int idx, AsLong<?>    adapter);
	double  get(int idx, AsDouble<?>  adapter);
	int 	get(int idx, AsInt<?>	  adapter);
	float	get(int idx, AsFloat<?>   adapter);
	short	get(int idx, AsShort<?>   adapter);
	char	get(int idx, AsChar<?>    adapter);
	byte	get(int idx, AsByte<?>    adapter);
	boolean get(int idx, AsBoolean<?> adapter);

	default <T>  T  sqlGet(int idx, As<T,?> 	 adapter)
	{
		return get(idx - 1, adapter);
	}

	default long	sqlGet(int idx, AsLong<?>	 adapter)
	{
		return get(idx - 1, adapter);
	}

	default double  sqlGet(int idx, AsDouble<?>  adapter)
	{
		return get(idx - 1, adapter);
	}

	default int 	sqlGet(int idx, AsInt<?>	 adapter)
	{
		return get(idx - 1, adapter);
	}

	default float	sqlGet(int idx, AsFloat<?>   adapter)
	{
		return get(idx - 1, adapter);
	}

	default short	sqlGet(int idx, AsShort<?>   adapter)
	{
		return get(idx - 1, adapter);
	}

	default char	sqlGet(int idx, AsChar<?>	 adapter)
	{
		return get(idx - 1, adapter);
	}

	default byte	sqlGet(int idx, AsByte<?>	 adapter)
	{
		return get(idx - 1, adapter);
	}

	default boolean sqlGet(int idx, AsBoolean<?> adapter)
	{
		return get(idx - 1, adapter);
	}

	/**
	 * A form of {@code TupleTableSlot} consisting of a number of indexable
	 * elements all of the same type, described by the single {@code Attribute}
	 * of a one-element {@code TupleDescriptor}.
	 *<p>
	 * This is one form in which a PostgreSQL array can be accessed.
	 *<p>
	 * The {@code get} methods that take an {@code Attribute} are not especially
	 * useful with this type of slot, and will simply return its first element.
	 */
	interface Indexed extends TupleTableSlot
	{
		/**
		 * Count of the slot's elements (one greater than the maximum index
		 * that may be passed to {@code get}).
		 */
		int elements();
	}
}
