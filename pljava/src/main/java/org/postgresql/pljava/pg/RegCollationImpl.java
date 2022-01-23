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
import static java.lang.invoke.MethodHandles.lookup;
import java.lang.invoke.SwitchPoint;

import java.sql.SQLException;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.COLLOID; // syscache

import org.postgresql.pljava.pg.adt.EncodingAdapter;
import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.NameAdapter.AS_STRING_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGNAMESPACE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.BOOLEAN_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.INT1_INSTANCE;
import org.postgresql.pljava.pg.adt.TextAdapter;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

class RegCollationImpl extends Addressed<RegCollation>
implements Nonshared<RegCollation>, Namespaced<Simple>, Owned, RegCollation
{
	private static UnaryOperator<MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<RegCollation> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return COLLOID;
	}

	/* Implementation of Named, Namespaced, Owned */

	private static Simple name(RegCollationImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(t.descriptor().get("collname"), SIMPLE_INSTANCE);
	}

	private static RegNamespace namespace(RegCollationImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return
			t.get(t.descriptor().get("collnamespace"), REGNAMESPACE_INSTANCE);
	}

	private static RegRole owner(RegCollationImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(t.descriptor().get("collowner"), REGROLE_INSTANCE);
	}

	/* Implementation of RegCollation */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	RegCollationImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_ENCODING;
	static final int SLOT_COLLATE;
	static final int SLOT_CTYPE;
	static final int SLOT_PROVIDER;
	static final int SLOT_VERSION;
	static final int SLOT_DETERMINISTIC;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(RegCollationImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(RegCollationImpl.class.getDeclaredMethods())

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.Namespaced.class)
			.withDependent( "namespace", SLOT_NAMESPACE)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent(     "owner", SLOT_OWNER)

			.withReceiverType(null)
			.withDependent(     "encoding", SLOT_ENCODING      = i++)
			.withDependent(      "collate", SLOT_COLLATE       = i++)
			.withDependent(        "ctype", SLOT_CTYPE         = i++)
			.withDependent(     "provider", SLOT_PROVIDER      = i++)
			.withDependent(      "version", SLOT_VERSION       = i++)
			.withDependent("deterministic", SLOT_DETERMINISTIC = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer)::apply;
		NSLOTS = i;
	}

	/* computation methods */

	private static CharsetEncoding encoding(RegCollationImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(s.descriptor().get("collencoding"), EncodingAdapter.INSTANCE);
	}

	private static String collate(RegCollationImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("collcollate"), AS_STRING_INSTANCE);
	}

	private static String ctype(RegCollationImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("collctype"), AS_STRING_INSTANCE);
	}

	private static Provider provider(RegCollationImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		byte p = s.get(s.descriptor().get("collprovider"), INT1_INSTANCE);
		switch ( p )
		{
		case (byte)'d':
			return Provider.DEFAULT;
		case (byte)'c':
			return Provider.LIBC;
		case (byte)'i':
			return Provider.ICU;
		default:
			throw new UnsupportedOperationException(String.format(
				"Unrecognized collation provider value %#x", p));
		}
	}

	private static String version(RegCollationImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("collversion"), TextAdapter.INSTANCE);
	}

	private static boolean deterministic(RegCollationImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(s.descriptor().get("collisdeterministic"), BOOLEAN_INSTANCE);
	}

	/* API methods */

	@Override
	public CharsetEncoding encoding()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ENCODING];
			return (CharsetEncoding)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public String collate()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_COLLATE];
			return (String)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public String ctype()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CTYPE];
			return (String)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Provider provider() // since PG 10
	{
		try
		{
			MethodHandle h = m_slots[SLOT_PROVIDER];
			return (Provider)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public String version() // since PG 10
	{
		try
		{
			MethodHandle h = m_slots[SLOT_VERSION];
			return (String)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean deterministic() // since PG 12
	{
		try
		{
			MethodHandle h = m_slots[SLOT_DETERMINISTIC];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}
}
