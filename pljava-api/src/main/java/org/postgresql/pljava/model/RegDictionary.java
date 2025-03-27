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
 * A PostgreSQL text search dictionary.
 *<p>
 * This interface is included in the model per the (arguably arbitrary) goal of
 * covering all the catalog classes for which a {@code Reg...} type is provided
 * in PostgreSQL. However, completing its implementation (to include a
 * {@code template()} method) would require also defining an interface to
 * represent a text search template.
 */
public interface RegDictionary
extends Addressed<RegDictionary>, Namespaced<Simple>, Owned
{
	RegClass.Known<RegDictionary> CLASSID =
		formClassId(TSDictionaryRelationId, RegDictionary.class);

	/*
	 * dictinitoption is a text column, but it clearly (see CREATE TEXT SEARCH
	 * DICTIONARY and examples in the catalog) has an option = value , ...
	 * structure. An appropriate return type for a method could be a map,
	 * and the implementation would have to match the quoting/escaping/parsing
	 * rules used by PG.
	 */
}
