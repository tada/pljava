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
package org.postgresql.pljava.adt;

import java.sql.SQLException;

import java.util.List;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.Adapter.Contract;

import org.postgresql.pljava.model.TupleTableSlot.Indexed;

/**
 * Container for functional interfaces presenting a PostgreSQL array.
 */
public interface Array
{
	/**
	 * A contract whereby an array is returned flattened into a Java list,
	 * with no attention to its specified dimensionality or index bounds.
	 */
	@FunctionalInterface
	interface AsFlatList<E> extends Contract.Array<List<E>,E,Adapter.As<E,?>>
	{
		/**
		 * Shorthand for a cast of a suitable method reference to this
		 * functional interface type.
		 */
		static <E> AsFlatList<E> of(AsFlatList<E> instance)
		{
			return instance;
		}

		/**
		 * An implementation that produces a Java list eagerly copied from the
		 * PostgreSQL array, which is then no longer needed; null elements in
		 * the array are included in the list.
		 */
		static <E> List<E> nullsIncludedCopy(
			int nDims, int[] dimsAndBounds, Adapter.As<E,?> adapter,
			Indexed slot)
			throws SQLException
		{
			int n = slot.elements();
			E[] result = adapter.arrayOf(n);
			for ( int i = 0; i < n; ++ i )
				result[i] = slot.get(i, adapter);
			return List.of(result);
		}
	}
}
