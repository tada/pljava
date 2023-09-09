/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.annotation.Function.Effects;
import org.postgresql.pljava.annotation.Function.OnNullInput;
import org.postgresql.pljava.annotation.Function.Parallel;
import org.postgresql.pljava.annotation.Function.Security;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.PROCOID; // syscache

import static org.postgresql.pljava.pg.adt.ArrayAdapter
	.FLAT_STRING_LIST_INSTANCE;
import org.postgresql.pljava.pg.adt.GrantAdapter;
import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.PLANG_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGNAMESPACE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGPROCEDURE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGTYPE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.BOOLEAN_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.FLOAT4_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.INT1_INSTANCE;
import org.postgresql.pljava.pg.adt.TextAdapter;
import static org.postgresql.pljava.pg.adt.XMLAdapter.SYNTHETIC_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

class RegProcedureImpl<M extends RegProcedure.Memo<M>>
extends Addressed<RegProcedure<?>>
implements
	Nonshared<RegProcedure<?>>, Namespaced<Simple>, Owned,
	AccessControlled<CatalogObject.EXECUTE>, RegProcedure<M>
{
	private static UnaryOperator<MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<RegProcedure<?>> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return PROCOID;
	}

	/* Implementation of Named, Namespaced, Owned, AccessControlled */

	private static Simple name(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.PRONAME, SIMPLE_INSTANCE);
	}

	private static RegNamespace namespace(RegProcedureImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.PRONAMESPACE, REGNAMESPACE_INSTANCE);
	}

	private static RegRole owner(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.PROOWNER, REGROLE_INSTANCE);
	}

	private static List<CatalogObject.Grant> grants(RegProcedureImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.PROACL, GrantAdapter.LIST_INSTANCE);
	}

	/* Implementation of RegProcedure */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	RegProcedureImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_LANGUAGE;
	static final int SLOT_COST;
	static final int SLOT_ROWS;
	static final int SLOT_VARIADICTYPE;
	static final int SLOT_SUPPORT;
	static final int SLOT_KIND;
	static final int SLOT_SECURITY;
	static final int SLOT_LEAKPROOF;
	static final int SLOT_ONNULLINPUT;
	static final int SLOT_RETURNSSET;
	static final int SLOT_EFFECTS;
	static final int SLOT_PARALLEL;
	static final int SLOT_RETURNTYPE;
	static final int SLOT_ARGTYPES;
	static final int SLOT_ALLARGTYPES;
	static final int SLOT_ARGMODES;
	static final int SLOT_ARGNAMES;
	static final int SLOT_TRANSFORMTYPES;
	static final int SLOT_SRC;
	static final int SLOT_BIN;
	static final int SLOT_CONFIG;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(RegProcedureImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(RegProcedureImpl.class.getDeclaredMethods())

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.Namespaced.class)
			.withDependent( "namespace", SLOT_NAMESPACE)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent(     "owner", SLOT_OWNER)
			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withDependent(    "grants", SLOT_ACL)

			.withReceiverType(null)
			.withDependent(      "language", SLOT_LANGUAGE       = i++)
			.withDependent(          "cost", SLOT_COST           = i++)
			.withDependent(          "rows", SLOT_ROWS           = i++)
			.withDependent(  "variadicType", SLOT_VARIADICTYPE   = i++)
			.withDependent(       "support", SLOT_SUPPORT        = i++)
			.withDependent(          "kind", SLOT_KIND           = i++)
			.withDependent(      "security", SLOT_SECURITY       = i++)
			.withDependent(     "leakproof", SLOT_LEAKPROOF      = i++)
			.withDependent(   "onNullInput", SLOT_ONNULLINPUT    = i++)
			.withDependent(    "returnsSet", SLOT_RETURNSSET     = i++)
			.withDependent(       "effects", SLOT_EFFECTS        = i++)
			.withDependent(      "parallel", SLOT_PARALLEL       = i++)
			.withDependent(    "returnType", SLOT_RETURNTYPE     = i++)
			.withDependent(      "argTypes", SLOT_ARGTYPES       = i++)
			.withDependent(   "allArgTypes", SLOT_ALLARGTYPES    = i++)
			.withDependent(      "argModes", SLOT_ARGMODES       = i++)
			.withDependent(      "argNames", SLOT_ARGNAMES       = i++)
			.withDependent("transformTypes", SLOT_TRANSFORMTYPES = i++)
			.withDependent(           "src", SLOT_SRC            = i++)
			.withDependent(           "bin", SLOT_BIN            = i++)
			.withDependent(        "config", SLOT_CONFIG         = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer)::apply;
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute PRONAME;
		static final Attribute PRONAMESPACE;
		static final Attribute PROOWNER;
		static final Attribute PROACL;
		static final Attribute PROLANG;
		static final Attribute PROCOST;
		static final Attribute PROROWS;
		static final Attribute PROVARIADIC;
		static final Attribute PROKIND;
		static final Attribute PROSECDEF;
		static final Attribute PROLEAKPROOF;
		static final Attribute PROISSTRICT;
		static final Attribute PRORETSET;
		static final Attribute PROVOLATILE;
		static final Attribute PROPARALLEL;
		static final Attribute PRORETTYPE;
		static final Attribute PROARGTYPES;
		static final Attribute PROALLARGTYPES;
		static final Attribute PROARGMODES;
		static final Attribute PROARGNAMES;
		static final Attribute PROTRFTYPES;
		static final Attribute PROSRC;
		static final Attribute PROBIN;
		static final Attribute PROCONFIG;
		static final Attribute PROARGDEFAULTS;
		static final Attribute PROTRANSFORM;
		static final Attribute PROSUPPORT;
		static final Attribute PROSQLBODY;

		static
		{
			Iterator<Attribute> itr = attNames(
				"proname",
				"pronamespace",
				"proowner",
				"proacl",
				"prolang",
				"procost",
				"prorows",
				"provariadic",
				"prokind",
				"prosecdef",
				"proleakproof",
				"proisstrict",
				"proretset",
				"provolatile",
				"proparallel",
				"prorettype",
				"proargtypes",
				"proallargtypes",
				"proargmodes",
				"proargnames",
				"protrftypes",
				"prosrc",
				"probin",
				"proconfig",
				"proargdefaults"
			).alsoIf(PG_VERSION_NUM <  120000,
				"protransform" // early internal version of prosupport
			).alsoIf(PG_VERSION_NUM >= 120000,
				"prosupport"
			).alsoIf(PG_VERSION_NUM >= 140000,
				"prosqlbody"
			).project(CLASSID.tupleDescriptor());

			PRONAME        = itr.next();
			PRONAMESPACE   = itr.next();
			PROOWNER       = itr.next();
			PROACL         = itr.next();
			PROLANG        = itr.next();
			PROCOST        = itr.next();
			PROROWS        = itr.next();
			PROVARIADIC    = itr.next();
			PROKIND        = itr.next();
			PROSECDEF      = itr.next();
			PROLEAKPROOF   = itr.next();
			PROISSTRICT    = itr.next();
			PRORETSET      = itr.next();
			PROVOLATILE    = itr.next();
			PROPARALLEL    = itr.next();
			PRORETTYPE     = itr.next();
			PROARGTYPES    = itr.next();
			PROALLARGTYPES = itr.next();
			PROARGMODES    = itr.next();
			PROARGNAMES    = itr.next();
			PROTRFTYPES    = itr.next();
			PROSRC         = itr.next();
			PROBIN         = itr.next();
			PROCONFIG      = itr.next();
			PROARGDEFAULTS = itr.next();
			PROTRANSFORM   = itr.next();
			PROSUPPORT     = itr.next();
			PROSQLBODY     = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	private static ProceduralLanguage language(RegProcedureImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.PROLANG, PLANG_INSTANCE);
	}

	private static float cost(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.PROCOST, FLOAT4_INSTANCE);
	}

	private static float rows(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.PROROWS, FLOAT4_INSTANCE);
	}

	private static RegType variadicType(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.PROVARIADIC, REGTYPE_INSTANCE);
	}

	private static RegProcedure<PlannerSupport> support(RegProcedureImpl o)
	throws SQLException
	{
		RegProcedure<?> p;
		Attribute a = Att.PROSUPPORT;

		if ( null == a ) // missing in this PG version
			a = Att.PROTRANSFORM; // use earlier internal-only equivalent

		TupleTableSlot t = o.cacheTuple();
		p = t.get(a, REGPROCEDURE_INSTANCE);

		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<PlannerSupport> narrowed = (RegProcedure<PlannerSupport>)p;

		return narrowed;
	}

	private static Kind kind(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		byte b = s.get(Att.PROKIND, INT1_INSTANCE);
		switch ( b )
		{
		case (byte)'f':
			return Kind.FUNCTION;
		case (byte)'p':
			return Kind.PROCEDURE;
		case (byte)'a':
			return Kind.AGGREGATE;
		case (byte)'w':
			return Kind.WINDOW;
		default:
			throw new UnsupportedOperationException(String.format(
				"Unrecognized procedure/function kind value %#x", b));
		}
	}

	private static Security security(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		if ( s.get(Att.PROSECDEF, BOOLEAN_INSTANCE) )
			return Security.DEFINER;
		return Security.INVOKER;
	}

	private static boolean leakproof(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.PROLEAKPROOF, BOOLEAN_INSTANCE);
	}

	private static OnNullInput onNullInput(RegProcedureImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		if ( s.get(Att.PROISSTRICT, BOOLEAN_INSTANCE) )
			return OnNullInput.RETURNS_NULL;
		return OnNullInput.CALLED;
	}

	private static boolean returnsSet(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.PRORETSET, BOOLEAN_INSTANCE);
	}

	private static Effects effects(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		byte b = s.get(Att.PROVOLATILE, INT1_INSTANCE);
		switch ( b )
		{
		case (byte)'i':
			return Effects.IMMUTABLE;
		case (byte)'s':
			return Effects.STABLE;
		case (byte)'v':
			return Effects.VOLATILE;
		default:
			throw new UnsupportedOperationException(String.format(
				"Unrecognized procedure/function volatility value %#x", b));
		}
	}

	private static Parallel parallel(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		byte b = s.get(Att.PROPARALLEL, INT1_INSTANCE);
		switch ( b )
		{
		case (byte)'s':
			return Parallel.SAFE;
		case (byte)'r':
			return Parallel.RESTRICTED;
		case (byte)'u':
			return Parallel.UNSAFE;
		default:
			throw new UnsupportedOperationException(String.format(
				"Unrecognized procedure/function parallel safety value %#x",b));
		}
	}

	private static RegType returnType(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.PRORETTYPE, REGTYPE_INSTANCE);
	}

	private static List<RegType> argTypes(RegProcedureImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(Att.PROARGTYPES,
				ArrayAdapters.REGTYPE_LIST_INSTANCE);
	}

	private static List<RegType> allArgTypes(RegProcedureImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(Att.PROALLARGTYPES,
				ArrayAdapters.REGTYPE_LIST_INSTANCE);
	}

	private static List<ArgMode> argModes(RegProcedureImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(Att.PROARGMODES,
				ArrayAdapters.ARGMODE_LIST_INSTANCE);
	}

	private static List<Simple> argNames(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(Att.PROARGNAMES,
				ArrayAdapters.TEXT_NAME_LIST_INSTANCE);
	}

	private static List<RegType> transformTypes(RegProcedureImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(Att.PROTRFTYPES,
				ArrayAdapters.REGTYPE_LIST_INSTANCE);
	}

	private static String src(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.PROSRC, TextAdapter.INSTANCE);
	}

	private static String bin(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.PROBIN, TextAdapter.INSTANCE);
	}

	private static List<String> config(RegProcedureImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return
			s.get(Att.PROCONFIG, FLAT_STRING_LIST_INSTANCE);
	}

	/* API methods */

	@Override
	public ProceduralLanguage language()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_LANGUAGE];
			return (ProceduralLanguage)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public float cost()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_COST];
			return (float)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public float rows()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ROWS];
			return (float)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegType variadicType()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_VARIADICTYPE];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<PlannerSupport> support()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_SUPPORT];
			return (RegProcedure<PlannerSupport>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Kind kind()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_KIND];
			return (Kind)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Security security()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_SECURITY];
			return (Security)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean leakproof()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_LEAKPROOF];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public OnNullInput onNullInput()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ONNULLINPUT];
			return (OnNullInput)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean returnsSet()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_RETURNSSET];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Effects effects()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_EFFECTS];
			return (Effects)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public Parallel parallel()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_PARALLEL];
			return (Parallel)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegType returnType()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_RETURNTYPE];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public List<RegType> argTypes()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ARGTYPES];
			return (List<RegType>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public List<RegType> allArgTypes()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ALLARGTYPES];
			return (List<RegType>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public List<ArgMode> argModes()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ARGMODES];
			return (List<ArgMode>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public List<Simple> argNames()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ARGNAMES];
			return (List<Simple>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public SQLXML argDefaults()
	{
		/*
		 * Because of the JDBC rules that an SQLXML instance lasts no longer
		 * than one transaction and can only be read once, it is not a good
		 * candidate for caching. We will just fetch a new one from the cached
		 * tuple as needed.
		 */
		TupleTableSlot s = cacheTuple();
		return s.get(Att.PROARGDEFAULTS, SYNTHETIC_INSTANCE);
	}

	@Override
	public List<RegType> transformTypes()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TRANSFORMTYPES];
			return (List<RegType>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public String src()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_SRC];
			return (String)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public String bin()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_BIN];
			return (String)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public SQLXML sqlBody()
	{
		/*
		 * Because of the JDBC rules that an SQLXML instance lasts no longer
		 * than one transaction and can only be read once, it is not a good
		 * candidate for caching. We will just fetch a new one from the cached
		 * tuple as needed.
		 */
		if ( null == Att.PROSQLBODY ) // missing in this PG version
			return null;

		TupleTableSlot s = cacheTuple();
		return s.get(Att.PROSQLBODY, SYNTHETIC_INSTANCE);
	}

	@Override
	public List<String> config()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CONFIG];
			return (List<String>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public M memo()
	{
		throw notyet();
	}
}
