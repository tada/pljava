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

import java.util.BitSet;
import static java.util.Collections.unmodifiableSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import java.util.function.UnaryOperator;

import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.PLPrincipal;

import org.postgresql.pljava.annotation.Function.Trust;

import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;
import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.LANGOID; // syscache
import org.postgresql.pljava.pg.RegProcedureImpl.AbstractMemo;

import org.postgresql.pljava.pg.adt.GrantAdapter;
import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGPROCEDURE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.BOOLEAN_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

class ProceduralLanguageImpl extends Addressed<ProceduralLanguage>
implements
	Nonshared<ProceduralLanguage>, Named<Simple>, Owned,
	AccessControlled<CatalogObject.USAGE>, ProceduralLanguage
{
	private static UnaryOperator<MethodHandle[]> s_initializer;

	private final SwitchPoint[] m_sp;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<ProceduralLanguage> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return LANGOID;
	}

	/* Implementation of Named, Owned, AccessControlled */

	private static Simple name(ProceduralLanguageImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return
			t.get(Att.LANNAME, SIMPLE_INSTANCE);
	}

	private static RegRole owner(ProceduralLanguageImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.LANOWNER, REGROLE_INSTANCE);
	}

	private static List<CatalogObject.Grant> grants(ProceduralLanguageImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.LANACL, GrantAdapter.LIST_INSTANCE);
	}

	/* Implementation of ProceduralLanguage */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	ProceduralLanguageImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
		m_sp = new SwitchPoint[] { new SwitchPoint() };
	}

	@Override
	void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
	{
		SwitchPoint oldSP = m_sp[0];
		if ( null == oldSP )
			return; // reentrant call

		try
		{
			/*
			 * Assigning null here ensures quick return from a reentrant call,
			 * and also serves as an assertion that validator() and language()
			 * have current cached values, as assumed below; with null here,
			 * they'll fail if they don't.
			 */
			m_sp[0] = null;
			sps.add(oldSP);

			Set<?> deps = m_dependents;
			m_dependents = null;

			if ( deps instanceof RoutineSet )
			{
				/*
				 * If I have a RoutineSet for my dependencies, I am a
				 * user-defined procedural language. My validator is written in
				 * a system-defined "pljavahandler" language (one that has a
				 * LanguageSet for its dependencies), and I no longer belong in
				 * its LanguageSet.
				 *
				 * To find that language, I can cheaply follow validator() and
				 * language(), as those must still hold cached values until the
				 * SwitchPoint gets invalidated after this method returns.
				 */
				RegProcedure<Validator> vp = validator();
				ProceduralLanguage vl = vp.language();
				Set<?> ls = ((ProceduralLanguageImpl)vl).m_dependents;
				if ( null != ls )
				{
					assert ls instanceof LanguageSet : "not a LanguageSet";
					ls.remove(this);
				}

				/*
				 * My validator needn't necessarily be invalidated
				 * *as a routine*; it simply isn't a *validator* routine
				 * anymore, so its Validator memo is what should be invalidated
				 * here. (That will involve a reentrant call of this method,
				 * which will quickly return.)
				 */
				Validator v = ((RegProcedureImpl<Validator>)vp).m_memo;
				if ( null != v )
					((ValidatorMemo)v).invalidate(sps, postOps);

				((RoutineSet)deps).forEach(r ->
					((RegProcedureImpl<?>)r).invalidate(sps, postOps));
			}
			else if ( deps instanceof LanguageSet )
			{
				s_plJavaHandlers.remove(this);
				((LanguageSet)deps).forEach(l ->
					((ProceduralLanguageImpl)l).invalidate(sps, postOps));
			}
		}
		finally
		{
			m_sp[0] = new SwitchPoint();
		}
	}

	static final int SLOT_PRINCIPAL;
	static final int SLOT_HANDLER;
	static final int SLOT_INLINEHANDLER;
	static final int SLOT_VALIDATOR;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(ProceduralLanguageImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> o.m_sp[0])
			.withSlots(o -> o.m_slots)

			.withCandidates(
				CatalogObjectImpl.Addressed.class.getDeclaredMethods())
			.withReceiverType(CatalogObjectImpl.Addressed.class)
			.withDependent("cacheTuple", SLOT_TUPLE)

			.withCandidates(ProceduralLanguageImpl.class.getDeclaredMethods())
			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent(     "owner", SLOT_OWNER)
			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withDependent(    "grants", SLOT_ACL)

			.withReceiverType(null)
			.withDependent(    "principal", SLOT_PRINCIPAL     = i++)
			.withDependent(      "handler", SLOT_HANDLER       = i++)
			.withDependent("inlineHandler", SLOT_INLINEHANDLER = i++)
			.withDependent(    "validator", SLOT_VALIDATOR     = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer)::apply;
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute LANNAME;
		static final Attribute LANOWNER;
		static final Attribute LANACL;
		static final Attribute LANPLTRUSTED;
		static final Attribute LANPLCALLFOID;
		static final Attribute LANINLINE;
		static final Attribute LANVALIDATOR;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"lanname",
				"lanowner",
				"lanacl",
				"lanpltrusted",
				"lanplcallfoid",
				"laninline",
				"lanvalidator"
			).iterator();

			LANNAME       = itr.next();
			LANOWNER      = itr.next();
			LANACL        = itr.next();
			LANPLTRUSTED  = itr.next();
			LANPLCALLFOID = itr.next();
			LANINLINE     = itr.next();
			LANVALIDATOR  = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	static final Set<?> NOT_PLJAVA_BASED  = unmodifiableSet(new HashSet<>());

	private static class LanguageSet extends HashSet<ProceduralLanguage>
	{
	}

	private static class RoutineSet extends HashSet<RegProcedure<PLJavaBased>>
	{
	}

	/* mutable non-API data used only on the PG thread */

	/**
	 * Remembers instances that represent the {@code pljavahandler} 'language'
	 * as they are discovered, keeping them live.
	 *<p>
	 * While no reason for multiple aliases is foreseen, a {@code Set} is used
	 * instead of a single field, so they do not have to be forbidden.
	 */
	private static final Set<ProceduralLanguageImpl>
		s_plJavaHandlers = new HashSet<>();

	/**
	 * For an instance determined to be a PL/Java-based language, a
	 * {@code RoutineSet} keeping live any known dependent routines; for an
	 * instance representing the "PL/Java handler language", a
	 * {@code LanguageSet} keeping live any known dependent languages;
	 * {@code NOT_PLJAVA_BASED} if known to be neither; null if not yet
	 * classified.
	 */
	private Set<?> m_dependents;

	void removeDependentRoutine(RegProcedure<?> r)
	{
		assert threadMayEnterPG() : "removeDependentRoutine thread";

		if ( ! (m_dependents instanceof RoutineSet) )
			return;

		((RoutineSet)m_dependents).remove(r);
	}

	PLJavaMemo addDependentRoutine(RegProcedure<?> r)
	{
		assert threadMayEnterPG() : "addDependentRoutine thread";

		assert m_dependents instanceof RoutineSet : "not PL/Java-based";
		if ( ! (m_dependents instanceof RoutineSet) )
			return null;

		@SuppressWarnings("unchecked")
		RegProcedureImpl<PLJavaBased> rpi = (RegProcedureImpl<PLJavaBased>)r;

		if ( ((RoutineSet)m_dependents).add(rpi) )
			new PLJavaMemo(rpi).apply();

		return (PLJavaMemo)r.memo(); // unnarrowed r to check cast as assertion
	}

	/**
	 * Indicates whether this instance represents a PL/Java-based language.
	 *<p>
	 * A <em>PL/Java-based language</em> is one that:
	 *<ul>
	 * <li>Has one or both of:
	 * <ul>
	 *  <li>a {@code handler} whose {@code language} is {@link #C}, whose
	 *  {@code src} is {@code pljavaDispatchRoutine}, and whose {@code bin} is
	 *  a shared-object file name, or</li>
	 *  <li>an {@code inlineHandler} whose {@code language} is {@link #C}, whose
	 *  {@code src} is {@code pljavaDispatchInline}, and whose {@code bin} is
	 *  a shared-object file name</li>
	 * </ul>, and
	 * <li>has a {@code validator} whose {@code language} is an instance
	 *  representing the {@code pljavahandler} language.</li>
	 *</ul>
	 * and for which the shared-object file names, if more than one, are equal.
	 *<p>
	 * An instance <em>represents the {@code pljavahandler} language</em> if it:
	 *<ul>
	 * <li>has no {@code inlineHandler}, and
	 * <li>has both a {@code handler} and a {@code validator} with
	 * {@code language} of {@link #C}, the same {@code src} of
	 * {@code pljavaDispatchValidator}, and whose {@code bin} is
	 *  the same shared-object file name.
	 *</ul>
	 * Such an instance is not also considered a PL/Java-based language.
	 *<p>
	 * A PL/Java-based language has a set of dependent routines
	 * ({@code RegProcedure} instances), keeping live whatever such routines
	 * have been discovered. A language representing {@code pljavahandler} has
	 * a set of dependent languages, keeping live whichever of those have been
	 * discovered. The validator routines for those languages do not need to be
	 * treated additionally as dependent routines; it suffices that they are
	 * cached as the validators of their respective languages.
	 *<p>
	 * On invalidation, a PL/Java-based language invalidates its dependent
	 * routines, and removes itself from the dependent-languages set of its
	 * validator's language. (Finding its validator and the validator's language
	 * are inexpensive as both references must have been traversed when the
	 * instance was determined to be PL/Java-based, and their cached values are
	 * lost only after {@code invalidate} returns.) A language that represents
	 * {@code pljavahandler} removes itself from the static set of those, and
	 * invalidates its dependent languages.
	 */
	boolean isPLJavaBased()
	{
		assert threadMayEnterPG() : "isPLJavaBased thread";

		if ( m_dependents instanceof RoutineSet )
			return true;

		if ( null != m_dependents )
			return false;

		do // while ( false ): break to mark notPLJavaBased and return false
		{
			if ( INTERNAL == this  ||  C == this  ||  SQL == this )
				break;

			RegProcedure<Handler> hp = handler();
			RegProcedure<InlineHandler> ip = inlineHandler();
			RegProcedure<Validator> vp = validator();

			if ( ! vp.exists() )
				break;

			ProceduralLanguageImpl vl = (ProceduralLanguageImpl)vp.language();

			if ( INTERNAL == vl  ||  SQL == vl )
				break;

			if ( C == vl )
			{
				if ( isPLJavaHandler(null) )
					return false; // will have made m_dependents a LanguageSet
				break;
			}

			List<String> bins =
				Stream.of(
					binFromCWithSrc(hp, "pljavaDispatchRoutine"),
					binFromCWithSrc(ip, "pljavaDispatchInline"))
				.filter(Objects::nonNull)
				.distinct()
				.collect(toList());

			if ( 1 != bins.size() )
				break;

			if ( ! vl.isPLJavaHandler(bins.get(0)) )
				break;

			((LanguageSet)vl.m_dependents).add(this);

			m_dependents = new RoutineSet(); // found to be null above

			return true;
		}
		while ( false );

		m_dependents = NOT_PLJAVA_BASED;
		return false;
	}

	/**
	 * Indicates whether this instance represents the (or possibly a)
	 * "PL/Java handler" language, and can serve as the language for the
	 * validator function of a PL/Java-based language.
	 * @param dependentRoutine a {@code RegProcedure} that has named this
	 * instance as its {@code language} and is the occasion for this query.
	 * It will be remembered in {@code m_dependentRoutines} in the affirmative
	 * case.
	 *<p>
	 * A <em>"PL/Java handler" language</em> is one that:
	 *<ul>
	 * <li>has no {@code inlineHandler}, and
	 * <li>has both a {@code handler} and a {@code validator} with
	 * {@code language} of {@link #C}, the same {@code src} of
	 * {@code pljavaDispatchValidator}, and whose {@code bin} is
	 *  the same shared-object file name.
	 *</ul>
	 * Such an instance is not also considered a PL/Java-based language.
	 *<p>
	 * A language representing {@code pljavahandler} is kept live in a static
	 * set, and has a set of dependent languages, keeping live whichever of
	 * those have been discovered. The validator routines for those languages do
	 * not need to be treated additionally as dependent routines; it suffices
	 * that they are cached as the validators of their respective languages.
	 *<p>
	 * On invalidation, a language that represents {@code pljavahandler}
	 * removes itself from the static set of those, and invalidates its
	 * dependent languages.
	 * @param expectedBinOrNull if the caller knows the name of the loaded
	 * PL/Java shared object, it can pass that to ensure this method only
	 * matches entries using that name. Or, when called from
	 * {@code isPLJavaBased}, this parameter will be the same shared-object name
	 * used in the purported PL/Java-based language's entries, to make sure this
	 * method will only match the same one, whether or not it is independently
	 * known to be right. If null, the shared-object name is not checked.
	 */
	boolean isPLJavaHandler(String expectedBinOrNull)
	{
		assert threadMayEnterPG() : "isPLJavaHandler thread";

		if ( m_dependents instanceof LanguageSet )
			return true;

		if ( null != m_dependents )
			return false;

		do // while ( false ): break to mark notPLJavaBased and return false
		{
			if ( INTERNAL == this  ||  C == this  ||  SQL == this )
				break;

			RegProcedure<Handler> hp = handler();
			RegProcedure<InlineHandler> ip = inlineHandler();
			RegProcedure<Validator> vp = validator();

			if ( ! vp.exists()  ||  ! hp.exists()  ||  ip.isValid() )
				break;

			String hbin = binFromCWithSrc(hp, "pljavaDispatchValidator");
			String vbin = binFromCWithSrc(vp, "pljavaDispatchValidator");

			if ( ! Objects.equals(hbin, vbin)  ||  null == hbin )
				break;

			if ( null != expectedBinOrNull && ! expectedBinOrNull.equals(hbin) )
				break;

			s_plJavaHandlers.add(this);

			m_dependents = new LanguageSet();

			RegProcedureImpl<Validator> vpi = (RegProcedureImpl<Validator>)vp;
			new ValidatorMemo(vpi, this).apply();

			return true;
		}
		while ( false );

		return false;
	}

	/**
	 * A quick check that a {@code RegProcedure} <var>p</var> is in language
	 * {@code C} with {@code src} matching a given known entry point symbol.
	 * @return <var>p</var>'s shared-object name if so, otherwise null
	 */
	private String binFromCWithSrc(RegProcedure<?> p, String src)
	{
		if ( p.exists()  &&  C == p.language()  &&  src.equals(p.src()) )
			return p.bin();
		return null;
	}

	/* computation methods */

	private static PLPrincipal principal(ProceduralLanguageImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		if ( s.get(Att.LANPLTRUSTED, BOOLEAN_INSTANCE) )
			return new PLPrincipal.Sandboxed(o.name());
		return new PLPrincipal.Unsandboxed(o.name());
	}

	private static RegProcedure<Handler> handler(ProceduralLanguageImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<Handler> p = (RegProcedure<Handler>)
			s.get(Att.LANPLCALLFOID, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<InlineHandler> inlineHandler(
		ProceduralLanguageImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<InlineHandler> p = (RegProcedure<InlineHandler>)
			s.get(Att.LANINLINE, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<Validator> validator(ProceduralLanguageImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<Validator> p = (RegProcedure<Validator>)
			s.get(Att.LANVALIDATOR, REGPROCEDURE_INSTANCE);
		return p;
	}

	/* API methods */

	@Override
	public PLPrincipal principal()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_PRINCIPAL];
			return (PLPrincipal)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<Handler> handler()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_HANDLER];
			return (RegProcedure<Handler>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<InlineHandler> inlineHandler()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_INLINEHANDLER];
			return (RegProcedure<InlineHandler>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<Validator> validator()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_VALIDATOR];
			return (RegProcedure<Validator>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	static class ValidatorMemo extends AbstractMemo<Validator>
	implements Validator
	{
		/**
		 * The language that the carrier routine serves as validator for.
		 *<p>
		 * Contrast with {@code m_carrier.language()}, the language the carrier
		 * routine is written in.
		 */
		final ProceduralLanguageImpl m_associatedLanguage;

		private ValidatorMemo(
			RegProcedureImpl<? super Validator> carrier,
			ProceduralLanguageImpl associatedLanguage)
		{
			super(carrier);
			m_associatedLanguage = associatedLanguage;
		}

		@Override
		void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
		{
			super.invalidate(sps, postOps);
			m_associatedLanguage.invalidate(sps, postOps);
		}
	}

	static class PLJavaMemo extends AbstractMemo<PLJavaBased>
	implements PLJavaBased
	{
		private PLJavaMemo(RegProcedureImpl<? super PLJavaBased> carrier)
		{
			super(carrier);
		}

		@Override
		void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
		{
			super.invalidate(sps, postOps);
			ProceduralLanguageImpl pl =
				(ProceduralLanguageImpl)m_carrier.language();
			pl.removeDependentRoutine(m_carrier);
		}

		@Override
		public TupleDescriptor inputsTemplate()
		{
			return m_carrier.inputsTemplate();
		}

		@Override
		public BitSet unresolvedInputs()
		{
			return m_carrier.unresolvedInputs();
		}

		@Override
		public TupleDescriptor outputsTemplate()
		{
			return m_carrier.outputsTemplate();
		}

		@Override
		public BitSet unresolvedOutputs()
		{
			return m_carrier.unresolvedOutputs();
		}
	}
}
