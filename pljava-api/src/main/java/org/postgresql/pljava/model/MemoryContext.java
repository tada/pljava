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
package org.postgresql.pljava.model;

import org.postgresql.pljava.Lifespan;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

/**
 * A PostgreSQL {@code MemoryContext}, which is usable as a PL/Java
 * {@link Lifespan Lifespan} to scope the lifetimes of PL/Java objects
 * (as when they depend on native memory allocated in the underlying context).
 *<p>
 * The {@code MemoryContext} API in PostgreSQL is described <a href=
"https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/mmgr/README;hb=HEAD"
>here</a>.
 *<p>
 * Static getters for the globally known contexts are spelled and capitalized
 * as they are in PostgreSQL.
 */
public interface MemoryContext extends Lifespan
{
	/**
	 * The top level of the context tree, of which every other context is
	 * a descendant.
	 *<p>
	 * Used as described <a href=
"https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/mmgr/README;hb=REL_14_0#l179"
>here</a>.
	 */
	MemoryContext TopMemoryContext =
		INSTANCE.memoryContext(MCX_TopMemory);

	/**
	 * The "current" memory context, which supplies all allocations made by
	 * PostgreSQL {@code palloc} and related functions that do not explicitly
	 * specify a context.
	 *<p>
	 * Used as described <a href=
"https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/mmgr/README;hb=REL_14_0#l72"
>here</a>.
	 */
	static MemoryContext CurrentMemoryContext()
	{
		return INSTANCE.memoryContext(MCX_CurrentMemory);
	}

	/**
	 * Getter method equivalent to the final
	 * {@link #TopMemoryContext TopMemoryContext} field, for consistency with
	 * the other static getters.
	 */
	static MemoryContext TopMemoryContext()
	{
		return TopMemoryContext;
	}

	/**
	 * Holds everything that lives until end of the top-level transaction.
	 *<p>
	 * Can be appropriate when a specification, for example JDBC, provides that
	 * an object should remain valid for the life of the transaction.
	 *<p>
	 * Uses are described <a href=
"https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/mmgr/README;hb=REL_14_0#l217"
>here</a>.
	 */
	static MemoryContext TopTransactionContext()
	{
		return INSTANCE.memoryContext(MCX_TopTransaction);
	}

	/**
	 * The same as {@link #TopTransactionContext() TopTransactionContext} when
	 * in a top-level transaction, but different in subtransactions (such as
	 * those associated with PL/Java savepoints).
	 *<p>
	 * Used as described <a href=
"https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/mmgr/README;hb=REL_14_0#l226"
>here</a>.
	 */
	static MemoryContext CurTransactionContext()
	{
		return INSTANCE.memoryContext(MCX_CurTransaction);
	}

	/**
	 * Context of the currently active execution portal.
	 *<p>
	 * Used as described <a href=
"https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/mmgr/README;hb=REL_14_0#l242"
>here</a>.
	 */
	static MemoryContext PortalContext()
	{
		return INSTANCE.memoryContext(MCX_Portal);
	}

	/**
	 * A permanent context switched into for error recovery processing.
	 *<p>
	 * Used as described <a href=
"https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/mmgr/README;hb=REL_14_0#l247"
>here</a>.
	 */
	static MemoryContext ErrorContext()
	{
		return INSTANCE.memoryContext(MCX_Error);
	}

	/**
	 * A long-lived, never-reset context created by PL/Java as a child of
	 * {@code TopMemoryContext}.
	 *<p>
	 * Perhaps useful for PL/Java-related allocations that will be long-lived,
	 * or managed only from the Java side, as a way of accounting for them
	 * separately, as opposed to just putting them in {@code TopMemoryContext}.
	 * It hasn't been used consistently even in the historical PL/Java
	 * code base, and should perhaps be a candidate for deprecation (or for
	 * a thorough code review to establish firmer guidelines for its use).
	 */
	static MemoryContext JavaMemoryContext()
	{
		return INSTANCE.memoryContext(MCX_JavaMemory);
	}

	/**
	 * The "upper executor" memory context (that is, the context on entry, prior
	 * to any use of SPI) associated with the current (innermost) PL/Java
	 * function invocation.
	 *<p>
	 * This is "precisely the right context for a value returned" from a
	 * function that uses SPI, as described
	 * <a href="https://www.postgresql.org/docs/14/spi-memory.html">here</a>.
	 */
	static MemoryContext UpperMemoryContext()
	{
		return INSTANCE.upperMemoryContext();
	}
}
