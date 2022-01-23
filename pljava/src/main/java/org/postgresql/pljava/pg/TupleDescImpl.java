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

import org.postgresql.pljava.model.*;
import static org.postgresql.pljava.model.RegType.RECORD;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import static org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.*;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

import java.util.AbstractList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link TupleDescriptor TupleDescriptor}.
 *<p>
 * A {@link Cataloged Cataloged} descriptor corresponds to a known composite
 * type declared in the PostgreSQL catalogs; its {@link #rowType rowType} method
 * returns that type. A {@link Blessed Blessed} descriptor has been constructed
 * on the fly and then interned in the type cache, such that the type
 * {@code RECORD} and its type modifier value will identify it uniquely for
 * the life of the backend; {@code rowType} will return the corresponding
 * {@link RegTypeImpl.Blessed} instance. An {@link Ephemeral Ephemeral}
 * descriptor has been constructed ad hoc and not interned; {@code rowType} will
 * return {@link RegType#RECORD RECORD} itself, which isn't a useful identifier
 * (many such ephemeral descriptors, all different, could exist at once).
 * An ephemeral descriptor is only useful as long as a reference to it is held.
 *<p>
 * A {@code Cataloged} descriptor can be obtained from the PG {@code relcache}
 * or the {@code typcache}, should respond to cache invalidation for
 * the corresponding relation, and is reference-counted, so the count should be
 * incremented when cached here, and decremented/released if this instance
 * goes unreachable from Java.
 *<p>
 * A {@code Blessed} descriptor can be obtained from the PG {@code typcache}
 * by {@code lookup_rowtype_tupdesc}. No invalidation logic is needed, as it
 * will persist, and its identifying typmod will remain unique, for the life of
 * the backend. It may or may not be reference-counted.
 *<p>
 * An {@code Ephemeral} tuple descriptor may need to be copied out of
 * a short-lived memory context where it is found, either into a longer-lived
 * context (and invalidated when that context is), or onto the Java heap and
 * used until GC'd.
 */
abstract class TupleDescImpl extends AbstractList<Attribute>
implements TupleDescriptor
{
	private final Attribute[] m_attrs = null;
	private final Map<Simple,Attribute> m_byName = Map.of();

	@Override
	public List<Attribute> attributes()
	{
		return this;
	}

	@Override
	public Attribute get(Simple name) throws SQLException
	{
		/*
		 * computeIfAbsent would be notationally simple here, but its guarantees
		 * aren't needed (this isn't a place where uniqueness needs to be
		 * enforced) and it's tricky to rule out that some name() call in the
		 * update could recursively end up here. So the longer check, compute,
		 * putIfAbsent is good enough.
		 */
		Attribute found = m_byName.get(name);
		if ( null != found )
			return found;

		for ( int i = m_byName.size() ; i < m_attrs.length ; ++ i )
		{
			Attribute a = m_attrs[i];
			Simple n = a.name();
			Attribute other = m_byName.putIfAbsent(n, a);
			assert null == other || found == other
				: "TupleDescriptor name cache";
			if ( ! name.equals(n) )
				continue;
			found = a;
			break;
		}

		if ( null == found )
			throw new SQLSyntaxErrorException(
				"no such column: " + name, "42703");

		return found;
	}

	@Override
	public Attribute sqlGet(int index)
	{
		return m_attrs[index - 1];
	}

	/*
	 * AbstractList implementation
	 */
	@Override
	public int size()
	{
		return m_attrs.length;
	}

	@Override
	public Attribute get(int index)
	{
		return m_attrs[index];
	}

	static class Cataloged extends TupleDescImpl implements Interned
	{
		@Override
		public RegType rowType()
		{
			throw notyet();
		}
	}

	static class Blessed extends TupleDescImpl implements Interned
	{
		@Override
		public RegType rowType()
		{
			throw notyet();
		}
	}

	static class Ephemeral extends TupleDescImpl
	implements TupleDescriptor.Ephemeral
	{
		@Override
		public RegType rowType()
		{
			return RECORD;
		}

		@Override
		public Interned intern()
		{
			throw notyet();
		}
	}

	static class OfType extends TupleDescImpl
	implements TupleDescriptor.Ephemeral
	{
		OfType(RegType type)
		{
		}

		@Override
		public RegType rowType()
		{
			return RECORD;
		}

		@Override
		public Interned intern()
		{
			throw notyet();
		}
	}
}
