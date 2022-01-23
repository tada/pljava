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
 * Model of a database defined within the PostgreSQL cluster.
 */
public interface Database
extends
	Addressed<Database>, Named<Simple>, Owned,
	AccessControlled<Grant.OnDatabase>
{
	RegClass.Known<Database> CLASSID =
		formClassId(DatabaseRelationId, Database.class);

	Database CURRENT = currentDatabase(CLASSID);

	CharsetEncoding encoding();

	/**
	 * A string identifying the collation rules for use in this database (when
	 * not overridden for a specific column or expression).
	 *<p>
	 * At least through PostgreSQL 14, this is always the identifier of an
	 * operating system ("libc") collation, even in builds with ICU available.
	 */
	String collate();

	/**
	 * A string identifying the collation rules for use in this database (when
	 * not overridden for a specific column or expression).
	 *<p>
	 * At least through PostgreSQL 14, this is always the identifier of an
	 * operating system ("libc") collation, even in builds with ICU available.
	 */
	String ctype();

	boolean template();
	boolean allowConnection();
	int connectionLimit();
	// oid lastsysoid
	// xid frozenxid
	// xid minmxid
	// oid tablespace
}
