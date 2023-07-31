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

	<T>  T  get(Attribute att, As<T,?>  	adapter) throws SQLException;
	long	get(Attribute att, AsLong<?>	adapter) throws SQLException;
	double  get(Attribute att, AsDouble<?>  adapter) throws SQLException;
	int 	get(Attribute att, AsInt<?> 	adapter) throws SQLException;
	float	get(Attribute att, AsFloat<?>	adapter) throws SQLException;
	short	get(Attribute att, AsShort<?>	adapter) throws SQLException;
	char	get(Attribute att, AsChar<?>	adapter) throws SQLException;
	byte	get(Attribute att, AsByte<?>	adapter) throws SQLException;
	boolean get(Attribute att, AsBoolean<?> adapter) throws SQLException;

	<T>  T  get(int idx, As<T,?>	  adapter) throws SQLException;
	long	get(int idx, AsLong<?>    adapter) throws SQLException;
	double  get(int idx, AsDouble<?>  adapter) throws SQLException;
	int 	get(int idx, AsInt<?>	  adapter) throws SQLException;
	float	get(int idx, AsFloat<?>   adapter) throws SQLException;
	short	get(int idx, AsShort<?>   adapter) throws SQLException;
	char	get(int idx, AsChar<?>    adapter) throws SQLException;
	byte	get(int idx, AsByte<?>    adapter) throws SQLException;
	boolean get(int idx, AsBoolean<?> adapter) throws SQLException;

	default <T>  T  sqlGet(int idx, As<T,?> 	 adapter) throws SQLException
	{
		return get(idx - 1, adapter);
	}

	default long	sqlGet(int idx, AsLong<?>	 adapter) throws SQLException
	{
		return get(idx - 1, adapter);
	}

	default double  sqlGet(int idx, AsDouble<?>  adapter) throws SQLException
	{
		return get(idx - 1, adapter);
	}

	default int 	sqlGet(int idx, AsInt<?>	 adapter) throws SQLException
	{
		return get(idx - 1, adapter);
	}

	default float	sqlGet(int idx, AsFloat<?>   adapter) throws SQLException
	{
		return get(idx - 1, adapter);
	}

	default short	sqlGet(int idx, AsShort<?>   adapter) throws SQLException
	{
		return get(idx - 1, adapter);
	}

	default char	sqlGet(int idx, AsChar<?>	 adapter) throws SQLException
	{
		return get(idx - 1, adapter);
	}

	default byte	sqlGet(int idx, AsByte<?>	 adapter) throws SQLException
	{
		return get(idx - 1, adapter);
	}

	default boolean sqlGet(int idx, AsBoolean<?> adapter) throws SQLException
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
