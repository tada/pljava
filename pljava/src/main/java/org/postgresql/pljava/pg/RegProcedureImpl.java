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
import java.sql.SQLXML;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import java.util.concurrent.CopyOnWriteArraySet;

import java.util.function.Function;
import java.util.function.Supplier;

import java.util.stream.IntStream;

import org.postgresql.pljava.annotation.Function.Effects;
import org.postgresql.pljava.annotation.Function.OnNullInput;
import org.postgresql.pljava.annotation.Function.Parallel;
import org.postgresql.pljava.annotation.Function.Security;

import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;
import org.postgresql.pljava.internal.Checked;
import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import org.postgresql.pljava.internal.SwitchPointCache.SwitchPoint;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.model.*;
import org.postgresql.pljava.model.RegProcedure.Memo;
import org.postgresql.pljava.model.ProceduralLanguage.PLJavaBased;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.CatalogObjectImpl.Factory.PROCOID_CB;
import static org.postgresql.pljava.pg.ModelConstants.PROCOID; // syscache
import org.postgresql.pljava.pg.ProceduralLanguageImpl.PLJavaMemo;
import static org.postgresql.pljava.pg.TupleDescImpl.synthesizeDescriptor;

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

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

/**
 * Implementation of the {@link RegProcedure RegProcedure} interface.
 */
class RegProcedureImpl<M extends Memo.Why<M>> extends Addressed<RegProcedure<?>>
implements
	Nonshared<RegProcedure<?>>, Namespaced<Simple>, Owned,
	AccessControlled<CatalogObject.EXECUTE>, RegProcedure<M>
{
	private static final Function<MethodHandle[],MethodHandle[]> s_initializer;

	/**
	 * Count of instances subject to invalidation.
	 *<p>
	 * Only accessed in invalidate and SP.onFirstUse, both on the PG thread.
	 */
	private static int s_instances;

	private static class SP extends SwitchPoint
	{
		@Override
		protected void onFirstUse()
		{
			if ( 1 == ++ s_instances )
				sysCacheInvalArmed(PROCOID_CB, true);
		}
	}

	private final SwitchPoint[] m_sp;

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
		m_sp = new SwitchPoint[] { new SP() };
	}

	@Override
	void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
	{
		SwitchPoint sp = m_sp[0];
		if ( sp.unused() )
			return;
		sps.add(sp);
		m_sp[0] = new SP();
		if ( 0 == -- s_instances )
			sysCacheInvalArmed(PROCOID_CB, false);

		M why = m_why;
		PLJavaMemo how = m_how;
		boolean dependsOnTransforms = m_dependsOnTransforms;
		m_why = null;
		m_how = null;
		m_dependsOnTransforms = false;
		if ( why instanceof AbstractMemo )
			((AbstractMemo)why).invalidate(sps, postOps);
		if ( null != how )
			how.invalidate(sps, postOps);
		if ( dependsOnTransforms )
			TransformImpl.removeDependentRoutine(this, transforms());
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

	/*
	 * Slots for some additional computed values that are not exposed in API
	 * but will be useful here in the internals.
	 */
	static final int SLOT_INPUTSTEMPLATE;
	static final int SLOT_UNRESOLVEDINPUTS;
	static final int SLOT_OUTPUTSTEMPLATE;
	static final int SLOT_UNRESOLVEDOUTPUTS;
	static final int SLOT_TRANSFORMS;

	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(RegProcedureImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> o.m_sp[0])
			.withSlots(o -> o.m_slots)

			.withCandidates(
				CatalogObjectImpl.Addressed.class.getDeclaredMethods())
			.withReceiverType(CatalogObjectImpl.Addressed.class)
			.withDependent("cacheTuple", SLOT_TUPLE)

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

			.withDependent(   "inputsTemplate", SLOT_INPUTSTEMPLATE    = i++)
			.withDependent( "unresolvedInputs", SLOT_UNRESOLVEDINPUTS  = i++)
			.withDependent(  "outputsTemplate", SLOT_OUTPUTSTEMPLATE   = i++)
			.withDependent("unresolvedOutputs", SLOT_UNRESOLVEDOUTPUTS = i++)
			.withDependent(       "transforms", SLOT_TRANSFORMS        = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer);
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
		static final Attribute PROSUPPORT;
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
				"prosupport",
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
			PROSUPPORT     = itr.next();
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
			PROSQLBODY     = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* mutable non-API fields that will only be used on the PG thread */

	/*
	 * This is the idea behind the API memo() method.
	 *
	 * A Why memo can be retrieved with the memo() method. The method does not
	 * synchronize. It is documented to return a valid result only in certain
	 * circumstances, which an individual Why subinterface should detail. A Why
	 * memo's type is constrained by the type parameter M.
	 *
	 * As the only foreseeable How memo for now is PLJavaMemo, that field is
	 * of fixed type for now. PLJavaBased documents that it is valid when
	 * the dispatcher passes it to a language-handler method along with
	 * a RegProcedure<?>. The dispatcher, while allowing the handler to run
	 * in another thread, is always on "the PG thread" when manipulating m_how.
	 */
	M m_why;
	PLJavaMemo m_how;

	/*
	 * This flag is only set in ProceduralLanguageImpl.transformsFor when it is
	 * about to return a nonempty list of transforms and has registered this
	 * routine as depending on them. It is checked and cleared in invalidate
	 * above. If it is set, invalidate can safely use transforms() to retrieve
	 * the list, which is in the cache slot until actual invalidation of the
	 * SwitchPoint.
	 */
	boolean m_dependsOnTransforms = false;

	/*
	 * Computation methods for ProceduralLanguage.PLJavaBased API methods
	 * that happen to be implemented here for now.
	 */

	static final EnumSet<ArgMode> s_parameterModes =
		EnumSet.of(ArgMode.IN, ArgMode.INOUT, ArgMode.VARIADIC);

	static final EnumSet<ArgMode> s_resultModes =
		EnumSet.of(ArgMode.INOUT, ArgMode.OUT, ArgMode.TABLE);

	static final BitSet s_noBits = new BitSet(0);

	private static TupleDescriptor inputsTemplate(RegProcedureImpl<?> o)
	throws SQLException
	{
		List<Simple> names = o.argNames();
		List<RegType> types = o.allArgTypes();

		if ( null == types )
		{
			types = o.argTypes();
			return synthesizeDescriptor(types, names, null);
		}

		List<ArgMode> modes = o.argModes();
		BitSet select = new BitSet(modes.size());
		IntStream.range(0, modes.size())
			.filter(i -> s_parameterModes.contains(modes.get(i)))
			.forEach(select::set);

		return synthesizeDescriptor(types, names, select);
	}

	private static BitSet unresolvedInputs(RegProcedureImpl<?> o)
	throws SQLException
	{
		TupleDescriptor td = o.inputsTemplate();
		BitSet unr = new BitSet(0);
		IntStream.range(0, td.size())
			.filter(i -> td.get(i).type().needsResolution())
			.forEach(unr::set);
		return unr;
	}

	private static TupleDescriptor outputsTemplate(RegProcedureImpl<?> o)
	throws SQLException
	{
		RegTypeImpl returnType = (RegTypeImpl)o.returnType();

		if ( RegType.VOID == returnType )
			return null;

		if ( RegType.RECORD != returnType )
			return returnType.notionalDescriptor();

		/*
		 * For plain unmodified RECORD, there's more work to do. If there are
		 * declared outputs, gin up a descriptor from those. If there aren't,
		 * this can only be a function that relies on every call site supplying
		 * a column definition list; return null.
		 */
		List<ArgMode> modes = o.argModes();
		if ( null == modes )
			return null; // Nothing helpful here. Must rely on call site.

		BitSet select = new BitSet(modes.size());
		IntStream.range(0, modes.size())
			.filter(i -> s_resultModes.contains(modes.get(i)))
			.forEach(select::set);

		if ( select.isEmpty() )
			return null; // No INOUT/OUT/TABLE cols; still need call site.

		/*
		 * Build a descriptor from the INOUT/OUT/TABLE types and names.
		 */

		List<RegType> types = o.allArgTypes();
		List<Simple> names = o.argNames();

		return synthesizeDescriptor(types, names, select);
	}

	private static BitSet unresolvedOutputs(RegProcedureImpl<?> o)
	throws SQLException
	{
		TupleDescriptor td = o.outputsTemplate();
		if ( null == td )
			return RegType.VOID == o.returnType() ? s_noBits : null;
		BitSet unr = new BitSet(0);
		IntStream.range(0, td.size())
			.filter(i -> td.get(i).type().needsResolution())
			.forEach(unr::set);
		return unr;
	}

	private static Checked.Supplier<List<Transform>,SQLException> transforms(
		RegProcedureImpl<?> o)
	throws SQLException
	{
		List<RegType> types = o.transformTypes();
		if ( null == types  ||  types.isEmpty() )
			return () -> null;

		ProceduralLanguageImpl pl = (ProceduralLanguageImpl)o.language();
		return pl.transformsFor(types, o);
	}

	/* computation methods for API */

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
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<PlannerSupport> p = (RegProcedure<PlannerSupport>)
			s.get(Att.PROSUPPORT, REGPROCEDURE_INSTANCE);
		return p;
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

	/*
	 * API-like methods not actually exposed as RegProcedure API.
	 * There are exposed on the RegProcedure.Memo.How subinterface
	 * ProceduralLanguage.PLJavaBased. These implementations could
	 * conceivably be moved to the implementation of that, so that
	 * not all RegProcedure instances would haul around five extra slots.
	 */
	public TupleDescriptor inputsTemplate()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_INPUTSTEMPLATE];
			return (TupleDescriptor)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	public BitSet unresolvedInputs()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_UNRESOLVEDINPUTS];
			BitSet unr = (BitSet)h.invokeExact(this, h);
			return (BitSet)unr.clone();
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	public TupleDescriptor outputsTemplate()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_OUTPUTSTEMPLATE];
			return (TupleDescriptor)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	public BitSet unresolvedOutputs()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_UNRESOLVEDOUTPUTS];
			BitSet unr = (BitSet)h.invokeExact(this, h);
			return null == unr ? null : (BitSet)unr.clone();
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	public List<Transform> transforms()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TRANSFORMS];
			Checked.Supplier<List<Transform>,SQLException> s =
				(Checked.Supplier<List<Transform>,SQLException>)
				h.invokeExact(this, h);
			return s.get();
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
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
		/*
		 * See the m_why declaration comments on this lack of synchronization.
		 */
		return m_why;
	}

	/**
	 * Abstract superclass of both {@link Why Why} and {@link How How} memo
	 * implementations.
	 */
	public static abstract class AbstractMemo
	{
		protected AbstractMemo()
		{
			assert threadMayEnterPG() : "AbstractMemo thread";
		}

		abstract void invalidate(List<SwitchPoint> sps, List<Runnable> postOps);

		/**
		 * Abstract base class for a {@link Why Why} memo implementation.
		 */
		public static abstract class Why<M extends Memo.Why<M>>
		extends AbstractMemo implements Memo.Why<M>
		{
			/**
			 * The {@code RegProcedure} instance carrying this memo.
			 */
			protected final RegProcedureImpl<M> m_carrier;

			protected Why(RegProcedure<? super M> carrier)
			{
				@SuppressWarnings("unchecked")
				RegProcedureImpl<M> narrowed = (RegProcedureImpl<M>)carrier;
				if ( null != narrowed.m_why )
					throw new AssertionError("carrier already has why memo");
				m_carrier = narrowed;
			}

			public RegProcedureImpl<M> apply()
			{
				assert threadMayEnterPG() : "AbstractMemo.Why thread";
				assert null == m_carrier.m_why : "carrier memo became nonnull";

				@SuppressWarnings("unchecked")
				M self = (M)this;

				m_carrier.m_why = self;
				return m_carrier;
			}

			void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
			{
				m_carrier.m_why = null;
			}
		}

		/**
		 * Abstract base class for a {@link How How} memo implementation.
		 */
		public static abstract class How<M extends Memo.How<M>>
		extends AbstractMemo implements Memo.How<M>
		{
			/**
			 * The {@code RegProcedure} instance carrying this memo.
			 */
			protected final RegProcedureImpl<?> m_carrier;

			protected How(RegProcedure<?> carrier)
			{
				RegProcedureImpl<?> narrowed = (RegProcedureImpl<?>)carrier;
				if ( null != narrowed.m_how )
					throw new AssertionError("carrier already has how memo");
				m_carrier = narrowed;
			}

			public RegProcedureImpl<?> apply()
			{
				assert threadMayEnterPG() : "AbstractMemo.how thread";
				assert null == m_carrier.m_how : "carrier memo became nonnull";

				// generalize later if there is ever any other possibility
				PLJavaMemo self = (PLJavaMemo)this;

				m_carrier.m_how = self;
				return m_carrier;
			}

			void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
			{
				m_carrier.m_how = null;
			}
		}
	}

	/**
	 * Abstract superclass of a {@code Why} memo used on routines that play
	 * specific support roles for other catalog objects (such as a
	 * {@code Handler} or {@code Validator} for a {@code ProceduralLanguage}
	 * or a {@code FromSQL} or {@code ToSQL} for a {@code Transform>}, where
	 * dependent objects should be invalidated if the support routine is.
	 *<p>
	 * Because a support routine can be depended on by more than one object
	 * (multiple languages, say, can share the same handler or validator
	 * routines), the memo carries a {@code Set} of dependent objects, not
	 * just a single reference. The {@code Set} implementation is chosen on
	 * an expectation of rare mutations and relatively small sets.
	 *<p>
	 * A concrete subclass should supply an appropriately-typed static
	 * {@code addDependent} method that delegates to the protected
	 * {@link #add add} method here. The static {@code removeDependent}
	 * method of this class can be invoked directly (typically qualified
	 * by the concrete subclass name, for consistency with the
	 * {@code addDependent} method).
	 */
	static abstract class SupportMemo<
		M extends Memo.Why<M>,
		A extends CatalogObjectImpl.Addressed<?>
	>
	extends AbstractMemo.Why<M>
	{
		private final Set<A> m_dependents;

		protected SupportMemo(
			RegProcedure<? super M> carrier, A dep)
		{
			super(carrier);
			m_dependents = new CopyOnWriteArraySet<>(Set.of(dep));
		}

		/**
		 * Has the effect of {@code super.invalidate} (simply nulling
		 * the carrier {@code RegProcedure}'s reference to this memo),
		 * and additionally calls
		 * {@link CatalogObjectImpl.Addressed#invalidate invalidate}
		 * on each recorded dependent <var>A</var> object.
		 */
		@Override
		void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
		{
			super.invalidate(sps, postOps);
			m_dependents.forEach(a -> a.invalidate(sps, postOps));
		}

		/**
		 * Removes <var>dep</var> as a recorded dependency on
		 * <var>proc</var>, with no effect if <var>proc</var> isn't carrying
		 * a memo that extends this class or if its dependency set does not
		 * contain <var>dep</var>.
		 */
		static <
			M extends Memo.Why<M>,
			A extends CatalogObjectImpl.Addressed<?>
		>
		void removeDependent(RegProcedure<M> proc, A dep)
		{
			M memo = proc.memo();
			if ( memo instanceof SupportMemo<?,?> )
				((SupportMemo<?,?>)memo).m_dependents.remove(dep);
		}

		/**
		 * Adds <var>dep</var> as a recorded dependency on <var>proc</var>,
		 * using an existing memo corresponding to type <var>T</var>
		 * if present, or getting a new one from <var>supplier</var> and
		 * applying it.
		 *<p>
		 * The <var>supplier</var> will typically be a lambda that passes
		 * <var>proc</var> and <var>dep</var> to the constructor of
		 * the concrete subclass of this class.
		 *<p>
		 * No action will be taken if <var>proc</var> is the invalid
		 * instance. It is not expected that <var>proc</var> will already
		 * be carrying a memo of some other type; an exception will result
		 * if it is.
		 */
		protected static <
			O extends Memo.Why<O>,
			M extends Memo.Why<M>,
			T extends SupportMemo<M,A>,
			A extends CatalogObjectImpl.Addressed<?>
		>
		void add(
			RegProcedure<O> proc, A dep,
			Class<T> witness, Supplier<T> supplier)
		{
			if ( ! proc.isValid() )
				return;
			O memo = proc.memo();
			if ( witness.isInstance(memo) )
			{
				@SuppressWarnings("unchecked")
				SupportMemo<?,A> sm = (SupportMemo<?,A>)memo;
				sm.m_dependents.add(dep);
			}
			else
				supplier.get().apply();
		}
	}
}
