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
package org.postgresql.pljava.pg;

import java.sql.SQLType;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/*
 * Can get lots of information, including TupleDesc, domain constraints, etc.,
 * from the typcache. A typcache entry is immortal but bits of it can change.
 * So it may be safe to keep a reference to the entry forever, but detect when
 * bits have changed. See in particular tupDesc_identifier.
 *
 * Many of the attributes of pg_type are available in the typcache. But
 * lookup_type_cache() does not have a _noerror version. If there is any doubt
 * about the existence of a type to be looked up, one must either do a syscache
 * lookup first anyway, or have a plan to catch an undefined_object error.
 * Same if you happen to look up a type still in the "only a shell" stage.
 * At that rate, may as well rely on the syscache for all the pg_type info.
 */

abstract class RegTypeImpl extends Addressed<RegType>
implements
	Nonshared<RegType>,	Namespaced<Simple>, Owned,
	AccessControlled<CatalogObject.USAGE>, RegType
{

	/**
	 * Represents a type that has been mentioned without an accompanying type
	 * modifier (or with the 'unspecified' value -1 for its type modifier).
	 */
	static class NoModifier extends RegTypeImpl
	{
	}

	/**
	 * Represents a type that is not {@code RECORD} and has a type modifier that
	 * is not the unspecified value.
	 *<p>
	 * When the {@code RECORD} type appears in PostgreSQL with a type modifier,
	 * that is a special case; see {@link Blessed Blessed}.
	 */
	static class Modified extends RegTypeImpl
	{
		Modified(NoModifier base)
		{
		}
	}

	/**
	 * Represents the "row type" of a {@link TupleDescriptor TupleDescriptor}
	 * that has been programmatically constructed and interned ("blessed").
	 *<p>
	 * Such a type is represented in PostgreSQL as the type {@code RECORD}
	 * with a type modifier assigned uniquely for the life of the backend.
	 */
	static class Blessed extends RegTypeImpl
	{
	}
}
