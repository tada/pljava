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

import java.nio.ByteBuffer;

import java.sql.SQLException;

import java.util.Iterator;
import java.util.List;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.internal.Checked;
import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import org.postgresql.pljava.internal.SwitchPointCache.SwitchPoint;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.model.*;
import static org.postgresql.pljava.model.MemoryContext.JavaMemoryContext;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.MemoryContextImpl.allocatingIn;
import static org.postgresql.pljava.pg.ModelConstants.Anum_pg_extension_oid;
import static org.postgresql.pljava.pg.ModelConstants.ExtensionOidIndexId;
import static org.postgresql.pljava.pg.TupleTableSlotImpl.heapTupleGetLightSlot;

import static org.postgresql.pljava.pg.adt.ArrayAdapter
	.FLAT_STRING_LIST_INSTANCE;
import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGNAMESPACE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.BOOLEAN_INSTANCE;
import org.postgresql.pljava.pg.adt.TextAdapter;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

class ExtensionImpl extends Addressed<Extension>
implements Nonshared<Extension>, Named<Simple>, Owned, Extension
{
	private static final UnaryOperator<MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<Extension> classId()
	{
		return CLASSID;
	}

	private static TupleTableSlot cacheTuple(ExtensionImpl o)
	throws SQLException
	{
		ByteBuffer heapTuple;
		TupleDescImpl td = (TupleDescImpl)o.cacheDescriptor();

		/*
		 * See this method in CatalogObjectImpl.Addressed for more on the choice
		 * of memory context and lifespan.
		 */
		try ( Checked.AutoCloseable<RuntimeException> ac =
			allocatingIn(JavaMemoryContext()) )
		{
			heapTuple = _sysTableGetByOid(
				o.classId().oid(), o.oid(), Anum_pg_extension_oid,
				ExtensionOidIndexId, td.address());
			if ( null == heapTuple )
				return null;
		}
		return heapTupleGetLightSlot(td, heapTuple, null);
	}

	/* Implementation of Named, Owned */

	private static Simple name(ExtensionImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return
			t.get(Att.EXTNAME, SIMPLE_INSTANCE);
	}

	private static RegRole owner(ExtensionImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.EXTOWNER, REGROLE_INSTANCE);
	}

	/* Implementation of Extension */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	ExtensionImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_TARGETNAMESPACE;
	static final int SLOT_RELOCATABLE;
	static final int SLOT_VERSION;
	static final int SLOT_CONFIG;
	static final int SLOT_CONDITION;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(ExtensionImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(ExtensionImpl.class.getDeclaredMethods())

			/*
			 * First declare some slots whose consuming API methods are found
			 * on inherited interfaces. This requires some adjustment of method
			 * types so that run-time adaptation isn't needed.
			 */
			.withReceiverType(CatalogObjectImpl.Addressed.class)
			.withDependent("cacheTuple", SLOT_TUPLE)

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)

			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withReturnType(null) // cancel adjustment from above
			.withDependent(    "owner", SLOT_OWNER)

			/*
			 * Next come slots where the compute and API methods are here.
			 */
			.withReceiverType(null)

			.withDependent(  "namespace", SLOT_TARGETNAMESPACE = i++)
			.withDependent("relocatable", SLOT_RELOCATABLE     = i++)
			.withDependent(    "version", SLOT_VERSION         = i++)
			.withDependent(     "config", SLOT_CONFIG          = i++)
			.withDependent(  "condition", SLOT_CONDITION       = i++)

			.build();
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute EXTNAME;
		static final Attribute EXTOWNER;
		static final Attribute EXTNAMESPACE;
		static final Attribute EXTRELOCATABLE;
		static final Attribute EXTVERSION;
		static final Attribute EXTCONFIG;
		static final Attribute EXTCONDITION;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"extname",
				"extowner",
				"extnamespace",
				"extrelocatable",
				"extversion",
				"extconfig",
				"extcondition"
			).iterator();

			EXTNAME        = itr.next();
			EXTOWNER       = itr.next();
			EXTNAMESPACE   = itr.next();
			EXTRELOCATABLE = itr.next();
			EXTVERSION     = itr.next();
			EXTCONFIG      = itr.next();
			EXTCONDITION   = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	private static RegNamespace namespace(ExtensionImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.EXTNAMESPACE, REGNAMESPACE_INSTANCE);
	}

	private static boolean relocatable(ExtensionImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.EXTRELOCATABLE, BOOLEAN_INSTANCE);
	}

	private static String version(ExtensionImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.EXTVERSION, TextAdapter.INSTANCE);
	}

	private static List<RegClass> config(ExtensionImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(Att.EXTCONFIG,
				ArrayAdapters.REGCLASS_LIST_INSTANCE);
	}

	private static List<String> condition(ExtensionImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(Att.EXTCONDITION,
				FLAT_STRING_LIST_INSTANCE);
	}

	/* API methods */

	@Override
	public RegNamespace namespace()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TARGETNAMESPACE];
			return (RegNamespace)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean relocatable()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_RELOCATABLE];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public String version()
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
	public List<RegClass> config()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CONFIG];
			return (List<RegClass>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public List<String> condition()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CONDITION];
			return (List<String>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}
}
