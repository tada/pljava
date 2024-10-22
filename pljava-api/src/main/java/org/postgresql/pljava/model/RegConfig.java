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

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * A PostgreSQL text search configuration.
 *<p>
 * This interface is included in the model per the (arguably arbitrary) goal of
 * covering all the catalog classes for which a {@code Reg...} type is provided
 * in PostgreSQL. However, completing its implementation (to include a
 * {@code parser()} method) would require also defining an interface to
 * represent a text search parser.
 */
public interface RegConfig
extends Addressed<RegConfig>, Namespaced<Simple>, Owned
{
	RegClass.Known<RegConfig> CLASSID =
		formClassId(TSConfigRelationId, RegConfig.class);
}
