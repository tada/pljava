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

import org.postgresql.pljava.Adapter.Contract;

/**
 * Container for abstract-type functional interfaces, not quite exactly
 * corresponding to PostgreSQL's {@code INTERNAL} category; there are some
 * fairly "internal" types that ended up in the {@code USER} category too,
 * for whatever reason.
 */
public interface Internal
{
	/**
	 * The {@code tid} type's PostgreSQL semantics: a block ID and
	 * a row index within that block.
	 */
	@FunctionalInterface
	public interface Tid<T> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 * @param blockId (treat as unsigned) identifies the block in a table
		 * containing the target row
		 * @param offsetNumber (treat as unsigned) the index of the target row
		 * within the identified block
		 */
		T construct(int blockId, short offsetNumber);
	}
}
