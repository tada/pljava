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
package org.postgresql.pljava.pg;

import java.lang.invoke.MethodHandle;
import static java.lang.invoke.MethodHandles.lookup;

import java.sql.SQLException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.util.function.Function;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import org.postgresql.pljava.internal.SwitchPointCache.SwitchPoint;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.TABLESPACEOID; // syscache

import org.postgresql.pljava.pg.adt.GrantAdapter;
import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

import static org.postgresql.pljava.internal.UncheckedException.unchecked;

/**
 * Implementation of the {@link Tablespace Tablespace} interface.
 */
class TablespaceImpl extends Addressed<Tablespace>
implements
	Shared<Tablespace>, Named<Simple>, Owned,
	AccessControlled<CatalogObject.CREATE>, Tablespace
{
	private static final Function<MethodHandle[],MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<Tablespace> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return TABLESPACEOID;
	}

	/* Implementation of Named, Owned, AccessControlled */

	private static Simple name(TablespaceImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.SPCNAME, SIMPLE_INSTANCE);
	}

	private static RegRole owner(TablespaceImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.SPCOWNER, REGROLE_INSTANCE);
	}

	private static List<CatalogObject.Grant> grants(TablespaceImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.SPCACL, GrantAdapter.LIST_INSTANCE);
	}

	/* Implementation of Tablespace */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	TablespaceImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_OPTIONS;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(TablespaceImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(TablespaceImpl.class.getDeclaredMethods())

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent(     "owner", SLOT_OWNER)
			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withDependent(    "grants", SLOT_ACL)

			.withReceiverType(null)
			.withDependent("options", SLOT_OPTIONS = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer);
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute SPCNAME;
		static final Attribute SPCOWNER;
		static final Attribute SPCACL;
		static final Attribute SPCOPTIONS;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"spcname",
				"spcowner",
				"spcacl",
				"spcoptions"
			).iterator();

			SPCNAME    = itr.next();
			SPCOWNER   = itr.next();
			SPCACL     = itr.next();
			SPCOPTIONS = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	private static Map<Simple,String> options(TablespaceImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.SPCOPTIONS, ArrayAdapters.RELOPTIONS_INSTANCE);
	}

	/* API methods */

	@Override
	public Map<Simple,String> options()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_OPTIONS];
			return (Map<Simple,String>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}
}
