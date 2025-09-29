/*
 * Copyright (c) 2022-2025 Tada AB and other contributors, as listed below.
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
 * Model of a registered PostgreSQL collation, consisting of a provider and
 * version, {@code collate} and {@code ctype} strings meaningful to that
 * provider, and a {@code CharsetEncoding} (or {@code ANY} if the collation
 * is usable with any encoding).
 */
public interface RegCollation
extends Addressed<RegCollation>, Namespaced<Simple>, Owned
{
	RegClass.Known<RegCollation> CLASSID =
		formClassId(CollationRelationId, RegCollation.class);

	RegCollation DEFAULT = formObjectId(CLASSID, DEFAULT_COLLATION_OID);
	RegCollation       C = formObjectId(CLASSID,       C_COLLATION_OID);

	/*
	 * Static lc_messages/lc_monetary/lc_numeric/lc_time getters? They are not
	 * components of RegCollation, but simply GUCs. They don't have PGDLLIMPORT,
	 * so on Windows they'd have to be retrieved through the GUC machinery
	 * by name. At least they're strings anyway.
	 */

	enum Provider { DEFAULT, LIBC, ICU }

	CharsetEncoding encoding();
	String          collate();
	String          ctype();

	/**
	 * @since PG 10
	 */
	Provider provider();

	/**
	 * @since PG 10
	 */
	String version();

	/**
	 * @since PG 12
	 */
	boolean deterministic();
}
