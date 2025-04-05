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
import java.lang.invoke.SwitchPoint;

import java.sql.SQLException;
import java.sql.SQLXML;

import java.util.Iterator;
import java.util.List;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.TargetList.Projection;

import org.postgresql.pljava.adt.Array.AsFlatList;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.CONSTROID; // syscache

import org.postgresql.pljava.pg.adt.ArrayAdapter;
import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.CONSTRAINT_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGCLASS_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGNAMESPACE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGOPERATOR_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGTYPE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.BOOLEAN_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.INT1_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.INT2_INSTANCE;
import org.postgresql.pljava.pg.adt.TextAdapter;
import static org.postgresql.pljava.pg.adt.XMLAdapter.SYNTHETIC_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

class ConstraintImpl extends Addressed<Constraint>
implements Nonshared<Constraint>, Namespaced<Simple>, Constraint
{
	private static UnaryOperator<MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<Constraint> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return CONSTROID;
	}

	/* Implementation of Named, Namespaced */

	private static Simple name(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.CONNAME, SIMPLE_INSTANCE);
	}

	private static RegNamespace namespace(ConstraintImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return
			t.get(Att.CONNAMESPACE, REGNAMESPACE_INSTANCE);
	}

	/* Implementation of Constraint */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	ConstraintImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_TYPE;
	static final int SLOT_DEFERRABLE;
	static final int SLOT_DEFERRED;
	static final int SLOT_VALIDATED;
	static final int SLOT_RELID;
	static final int SLOT_TYPID;
	static final int SLOT_INDID;
	static final int SLOT_PARENTID;
	static final int SLOT_FRELID;
	static final int SLOT_FUPDTYPE;
	static final int SLOT_FDELTYPE;
	static final int SLOT_FMATCHTYPE;
	static final int SLOT_ISLOCAL;
	static final int SLOT_INHCOUNT;
	static final int SLOT_NOINHERIT;
	static final int SLOT_PFEQOP;
	static final int SLOT_PPEQOP;
	static final int SLOT_FFEQOP;
	static final int SLOT_EXCLOP;
	static final int NSLOTS;

	private static final Adapter.Array<List<RegOperator>> OPRLIST_INSTANCE;

	private static final Adapter.Array<short[]> I2ARRAY_INSTANCE;

	static
	{
		OPRLIST_INSTANCE = new ArrayAdapter<>(
			REGOPERATOR_INSTANCE, AsFlatList.of(AsFlatList::nullsIncludedCopy));

		I2ARRAY_INSTANCE = INT2_INSTANCE.a1().build();

		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(ConstraintImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(ConstraintImpl.class.getDeclaredMethods())

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.Namespaced.class)
			.withDependent( "namespace", SLOT_NAMESPACE)

			.withReceiverType(null)
			.withDependent(           "type", SLOT_TYPE        = i++)
			.withDependent(     "deferrable", SLOT_DEFERRABLE  = i++)
			.withDependent(       "deferred", SLOT_DEFERRED    = i++)
			.withDependent(      "validated", SLOT_VALIDATED   = i++)
			.withDependent(        "onTable", SLOT_RELID       = i++)
			.withDependent(       "onDomain", SLOT_TYPID       = i++)
			.withDependent(          "index", SLOT_INDID       = i++)
			.withDependent(         "parent", SLOT_PARENTID    = i++)
			.withDependent("referencedTable", SLOT_FRELID      = i++)
			.withDependent(   "updateAction", SLOT_FUPDTYPE    = i++)
			.withDependent(   "deleteAction", SLOT_FDELTYPE    = i++)
			.withDependent(      "matchType", SLOT_FMATCHTYPE  = i++)
			.withDependent(        "isLocal", SLOT_ISLOCAL     = i++)
			.withDependent(   "inheritCount", SLOT_INHCOUNT    = i++)
			.withDependent(      "noInherit", SLOT_NOINHERIT   = i++)
			.withDependent(         "pfEqOp", SLOT_PFEQOP      = i++)
			.withDependent(         "ppEqOp", SLOT_PPEQOP      = i++)
			.withDependent(         "ffEqOp", SLOT_FFEQOP      = i++)
			.withDependent(         "exclOp", SLOT_EXCLOP      = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer)::apply;
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute CONNAME;
		static final Attribute CONNAMESPACE;
		static final Attribute CONTYPE;
		static final Attribute CONDEFERRABLE;
		static final Attribute CONDEFERRED;
		static final Attribute CONVALIDATED;
		static final Attribute CONRELID;
		static final Attribute CONTYPID;
		static final Attribute CONINDID;
		static final Attribute CONPARENTID;
		static final Attribute CONFRELID;
		static final Attribute CONFUPDTYPE;
		static final Attribute CONFDELTYPE;
		static final Attribute CONFMATCHTYPE;
		static final Attribute CONISLOCAL;
		static final Attribute CONINHCOUNT;
		static final Attribute CONNOINHERIT;
		static final Attribute CONKEY;
		static final Attribute CONFKEY;
		static final Attribute CONPFEQOP;
		static final Attribute CONPPEQOP;
		static final Attribute CONFFEQOP;
		static final Attribute CONFDELSETCOLS;
		static final Attribute CONEXCLOP;
		static final Attribute CONBIN;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"conname",
				"connamespace",
				"contype",
				"condeferrable",
				"condeferred",
				"convalidated",
				"conrelid",
				"contypid",
				"conindid",
				"conparentid",
				"confrelid",
				"confupdtype",
				"confdeltype",
				"confmatchtype",
				"conislocal",
				"coninhcount",
				"connoinherit",
				"conkey",
				"confkey",
				"conpfeqop",
				"conppeqop",
				"conffeqop",
				"confdelsetcols",
				"conexclop",
				"conbin"
			).iterator();

			CONNAME        = itr.next();
			CONNAMESPACE   = itr.next();
			CONTYPE        = itr.next();
			CONDEFERRABLE  = itr.next();
			CONDEFERRED    = itr.next();
			CONVALIDATED   = itr.next();
			CONRELID       = itr.next();
			CONTYPID       = itr.next();
			CONINDID       = itr.next();
			CONPARENTID    = itr.next();
			CONFRELID      = itr.next();
			CONFUPDTYPE    = itr.next();
			CONFDELTYPE    = itr.next();
			CONFMATCHTYPE  = itr.next();
			CONISLOCAL     = itr.next();
			CONINHCOUNT    = itr.next();
			CONNOINHERIT   = itr.next();
			CONKEY         = itr.next();
			CONFKEY        = itr.next();
			CONPFEQOP      = itr.next();
			CONPPEQOP      = itr.next();
			CONFFEQOP      = itr.next();
			CONFDELSETCOLS = itr.next();
			CONEXCLOP      = itr.next();
			CONBIN         = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	private static Type type(ConstraintImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		byte b = s.get(Att.CONTYPE, INT1_INSTANCE);
		switch ( b )
		{
		case (byte)'c': return Type.CHECK;
		case (byte)'f': return Type.FOREIGN_KEY;
		case (byte)'n': return Type.NOT_NULL;
		case (byte)'p': return Type.PRIMARY_KEY;
		case (byte)'u': return Type.UNIQUE;
		case (byte)'t': return Type.CONSTRAINT_TRIGGER;
		case (byte)'x': return Type.EXCLUSION;
		default:
			throw new UnsupportedOperationException(String.format(
				"Unrecognized constraint type value %#x", b));
		}
	}

	private static boolean deferrable(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONDEFERRABLE, BOOLEAN_INSTANCE);
	}

	private static boolean deferred(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONDEFERRED, BOOLEAN_INSTANCE);
	}

	private static boolean validated(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONVALIDATED, BOOLEAN_INSTANCE);
	}

	private static RegClass onTable(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONRELID, REGCLASS_INSTANCE);
	}

	private static RegType onDomain(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONTYPID, REGTYPE_INSTANCE);
	}

	private static RegClass index(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONINDID, REGCLASS_INSTANCE);
	}

	private static Constraint parent(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONPARENTID, CONSTRAINT_INSTANCE);
	}

	private static RegClass referencedTable(ConstraintImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONFRELID, REGCLASS_INSTANCE);
	}

	private static ReferentialAction updateAction(ConstraintImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		byte b = s.get(Att.CONFUPDTYPE, INT1_INSTANCE);
		return refActionFromCatalog(b, "upd");
	}

	private static ReferentialAction deleteAction(ConstraintImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		byte b = s.get(Att.CONFDELTYPE, INT1_INSTANCE);
		return refActionFromCatalog(b, "del");
	}

	private static MatchType matchType(ConstraintImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		byte b = s.get(Att.CONFMATCHTYPE, INT1_INSTANCE);
		switch ( b )
		{
		case (byte)'f': return MatchType.FULL;
		case (byte)'p': return MatchType.PARTIAL;
		case (byte)'s': return MatchType.SIMPLE;
		case (byte)' ': return null; // not a foreign key constraint
		default:
			throw new UnsupportedOperationException(String.format(
				"Unrecognized constraint match type value %#x", b));
		}
	}

	private static boolean isLocal(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONISLOCAL, BOOLEAN_INSTANCE);
	}

	private static short inheritCount(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONINHCOUNT, INT2_INSTANCE);
	}

	private static boolean noInherit(ConstraintImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONNOINHERIT, BOOLEAN_INSTANCE);
	}

	private static List<RegOperator> pfEqOp(ConstraintImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONPFEQOP, OPRLIST_INSTANCE);
	}

	private static List<RegOperator> ppEqOp(ConstraintImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONPPEQOP, OPRLIST_INSTANCE);
	}

	private static List<RegOperator> ffEqOp(ConstraintImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONFFEQOP, OPRLIST_INSTANCE);
	}

	private static List<RegOperator> exclOp(ConstraintImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.CONEXCLOP, OPRLIST_INSTANCE);
	}

	/* API methods */

	@Override
	public Type type()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TYPE];
			return (Type)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean deferrable()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_DEFERRABLE];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean deferred()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_DEFERRED];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean validated()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_VALIDATED];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegClass onTable()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_RELID];
			return (RegClass)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegType onDomain()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TYPID];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegClass index()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_INDID];
			return (RegClass)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Constraint parent()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_PARENTID];
			return (Constraint)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegClass referencedTable()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_FRELID];
			return (RegClass)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public ReferentialAction updateAction()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_FUPDTYPE];
			return (ReferentialAction)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public ReferentialAction deleteAction()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_FDELTYPE];
			return (ReferentialAction)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public MatchType matchType()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_FMATCHTYPE];
			return (MatchType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean isLocal()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ISLOCAL];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public short inheritCount()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_INHCOUNT];
			return (short)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean noInherit()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_NOINHERIT];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Projection key()
	{
		/*
		 * The reason for not caching this Projection is it depends on
		 * another CatalogObject (the RegClass, for its TupleDescriptor)
		 * that may be invalidated separately. Getting SwitchPointCache
		 * to guard validity with more than one SwitchPoint could be an
		 * interesting project, but not for today.
		 */
		TupleTableSlot s = cacheTuple();
		short[] indices = s.get(Att.CONKEY, I2ARRAY_INSTANCE);
		if ( null == indices )
			return null;
		return onTable().tupleDescriptor().sqlProject(indices);
	}

	@Override
	public Projection fkey()
	{
		/*
		 * See key() above for notes.
		 */
		TupleTableSlot s = cacheTuple();
		short[] indices = s.get(Att.CONFKEY, I2ARRAY_INSTANCE);
		if ( null == indices )
			return null;
		return referencedTable().tupleDescriptor().sqlProject(indices);
	}

	@Override
	public List<RegOperator> pfEqOp()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_PFEQOP];
			return (List<RegOperator>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public List<RegOperator> ppEqOp()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_PPEQOP];
			return (List<RegOperator>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public List<RegOperator> ffEqOp()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_FFEQOP];
			return (List<RegOperator>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Projection fdelSetColumns()
	{
		/*
		 * See key() above for notes.
		 */
		TupleTableSlot s = cacheTuple();
		short[] indices = s.get(Att.CONFDELSETCOLS, I2ARRAY_INSTANCE);
		if ( null == indices )
			return null;
		return onTable().tupleDescriptor().sqlProject(indices);
	}

	@Override
	public List<RegOperator> exclOp()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_EXCLOP];
			return (List<RegOperator>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public SQLXML bin()
	{
		/*
		 * Because of the JDBC rules that an SQLXML instance lasts no longer
		 * than one transaction and can only be read once, it is not a good
		 * candidate for caching. We will just fetch a new one from the cached
		 * tuple as needed.
		 */
		TupleTableSlot s = cacheTuple();
		return s.get(Att.CONBIN, SYNTHETIC_INSTANCE);
	}

	private static ReferentialAction refActionFromCatalog(byte b, String event)
	{
		switch ( b )
		{
		case (byte)'a': return ReferentialAction.NONE;
		case (byte)'r': return ReferentialAction.RESTRICT;
		case (byte)'c': return ReferentialAction.CASCADE;
		case (byte)'n': return ReferentialAction.SET_NULL;
		case (byte)'d': return ReferentialAction.SET_DEFAULT;
		case (byte)' ': return null; // not a foreign key constraint
		default:
			throw new UnsupportedOperationException(String.format(
				"Unrecognized referential integrity %s action value %#x",
				event, b));
		}
	}
}
