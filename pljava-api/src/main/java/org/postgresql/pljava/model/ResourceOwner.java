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

import org.postgresql.pljava.model.CatalogObject.Factory;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

/**
 * The representation of a PostgreSQL {@code ResourceOwner}, usable as
 * a PL/Java {@link Lifespan Lifespan}.
 *<p>
 * The {@code ResourceOwner} API in PostgreSQL is described <a href=
"https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/resowner/README;hb=HEAD"
>here</a>.
 *<p>
 * PostgreSQL invokes callbacks in phases when a {@code ResourceOwner}
 * is released, and all of its built-in consumers get notified before
 * loadable modules (like PL/Java) for each phase in turn. The release
 * behavior of this PL/Java instance is tied to the
 * {@code RESOURCE_RELEASE_LOCKS} phase of the underlying PostgreSQL object,
 * and therefore occurs after all of the built-in PostgreSQL lock-related
 * releases, but before any of the built-in stuff released in the
 * {@code RESOURCE_RELEASE_AFTER_LOCKS} phase.
 */
public interface ResourceOwner extends Lifespan
{
	static ResourceOwner CurrentResourceOwner()
	{
		return INSTANCE.resourceOwner(RSO_Current);
	}

	static ResourceOwner CurTransactionResourceOwner()
	{
		return INSTANCE.resourceOwner(RSO_CurTransaction);
	}

	static ResourceOwner TopTransactionResourceOwner()
	{
		return INSTANCE.resourceOwner(RSO_TopTransaction);
	}
}
