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

import java.lang.invoke.MethodHandle;

import java.sql.SQLType;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.TYPEOID; // syscache

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
	@Override
	int cacheId()
	{
		return TYPEOID;
	}

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	RegTypeImpl(MethodHandle[] slots)
	{
		super(slots);
	}

	/**
	 * Temporary scaffolding.
	 */
	RegTypeImpl()
	{
	}

	@Override
	public TupleDescriptor.Interned tupleDescriptor()
	{
		throw notyet();
	}

	@Override
	public RegClass relation()
	{
		throw notyet();
	}

	@Override
	public RegType element()
	{
		throw notyet();
	}

	/**
	 * Return the expected zero value for {@code subId}.
	 *<p>
	 * For keying the {@code CacheMap}, we sneak type modifiers in there
	 * (PG types do not otherwise use {@code subId}), but that's an
	 * implementation detail that could be done a different way if upstream
	 * ever decided to have subIds for types, and having it show in the address
	 * triple of a modified type could be surprising to an old PostgreSQL hand.
	 */
	@Override
	public int subId()
	{
		return 0;
	}

	/**
	 * Return the type modifier.
	 *<p>
	 * In this implementation, where we snuck it in as the third component
	 * of the cache key, sneak it back out.
	 */
	@Override
	public int modifier()
	{
		int m = super.subId();
		if ( -1 == m )
			return 0;
		return m;
	}

	/**
	 * Represents a type that has been mentioned without an accompanying type
	 * modifier (or with the 'unspecified' value -1 for its type modifier).
	 */
	static class NoModifier extends RegTypeImpl
	{
		@Override
		public int modifier()
		{
			return -1;
		}

		@Override
		public RegType modifier(int typmod)
		{
			if ( -1 == typmod )
				return this;
			return (RegType)
				CatalogObjectImpl.Factory.formMaybeModifiedType(oid(), typmod);
		}

		public RegType withoutModifier()
		{
			return this;
		}
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
		private final NoModifier m_base;

		Modified(NoModifier base)
		{
			super(base.m_slots);
			m_base = base; // must keep it live, not only share its slots
		}

		@Override
		public RegType modifier(int typmod)
		{
			if ( modifier() == typmod )
				return this;
			return m_base.modifier(typmod);
		}

		@Override
		public RegType withoutModifier()
		{
			return m_base;
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
		TupleDescriptor.Interned[] m_tupDescHolder;

		Blessed()
		{
			super(((RegTypeImpl)RECORD).m_slots);
			// RECORD is static final, no other effort needed to keep it live
		}

		@Override
		public RegType modifier(int typmod)
		{
			throw new UnsupportedOperationException(
				"may not alter the type modifier of an interned row type");
		}

		@Override
		public RegType withoutModifier()
		{
			return RECORD;
		}
	}
}
