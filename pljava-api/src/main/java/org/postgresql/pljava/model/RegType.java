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

import java.sql.SQLType;

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Model of a PostgreSQL data type, as defined in the system catalogs.
 *<p>
 * This class also has static final fields for a selection of commonly used
 * {@code RegType}s, such as those that correspond to types mentioned in JDBC,
 * and others that are just ubiquitous when working in PostgreSQL in general,
 * or are used in this model package.
 *<p>
 * An instance of {@code RegType} also implements the JDBC
 * {@link SQLType SQLType} interface, with the intention that it could be used
 * with a suitably-aware JDBC implementation to identify any type available
 * in PostgreSQL.
 *<p>
 * A type can have a 'modifier' (think {@code NUMERIC(4)} versus plain
 * {@code NUMERIC}). In PostgreSQL's C code, a type oid and modifier have to
 * be passed around in tandem. Here, you apply
 * {@link #modifier(int) modifier(int)} to the unmodified {@code RegType} and
 * obtain a distinct {@code RegType} instance incorporating the modifier.
 */
public interface RegType
extends
	Addressed<RegType>, Namespaced<Simple>, Owned, AccessControlled<USAGE>,
	SQLType
{
	RegClass.Known<RegType> CLASSID =
		formClassId(TypeRelationId, RegType.class);
}
