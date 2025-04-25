/*
 * Copyright (c) 2023-2025 Tada AB and other contributors, as listed below.
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

import java.lang.annotation.Native;

import java.lang.reflect.Constructor;

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;
import java.nio.IntBuffer;

import java.nio.charset.CharacterCodingException;

import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.sql.SQLSyntaxErrorException;

import java.util.BitSet;
import java.util.List;
import static java.util.Objects.requireNonNull;

import java.util.regex.Pattern;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import org.postgresql.pljava.PLJavaBasedLanguage;
import org.postgresql.pljava.PLJavaBasedLanguage.InlineBlocks;
import org.postgresql.pljava.PLJavaBasedLanguage.Routine;
import org.postgresql.pljava.PLJavaBasedLanguage.Routines;
import org.postgresql.pljava.PLJavaBasedLanguage.Template;
import org.postgresql.pljava.PLJavaBasedLanguage.TriggerFunction;
import org.postgresql.pljava.PLJavaBasedLanguage.Triggers;
import org.postgresql.pljava.PLJavaBasedLanguage.TriggerTemplate;
import org.postgresql.pljava.PLJavaBasedLanguage.UsingTransforms;

import org.postgresql.pljava.TargetList.Projection;

import static org.postgresql.pljava.internal.Backend.doInPG;
import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;
import static org.postgresql.pljava.internal.Backend.validateBodies;
import org.postgresql.pljava.internal.Checked;
import org.postgresql.pljava.internal.DualState;
import org.postgresql.pljava.internal.Invocation;
import org.postgresql.pljava.internal.UncheckedException;

import org.postgresql.pljava.model.Attribute;
import static org.postgresql.pljava.model.CharsetEncoding.SERVER_ENCODING;
import org.postgresql.pljava.model.MemoryContext;
import org.postgresql.pljava.model.ProceduralLanguage;
import static org.postgresql.pljava.model.ProceduralLanguage.C;
import org.postgresql.pljava.model.ProceduralLanguage.PLJavaBased;
import org.postgresql.pljava.model.ProceduralLanguage.Validator;
import org.postgresql.pljava.model.RegCollation;
import org.postgresql.pljava.model.RegProcedure;
import org.postgresql.pljava.model.RegProcedure.Call;
import org.postgresql.pljava.model.RegProcedure.Call.Context;
import org.postgresql.pljava.model.RegProcedure.Call.ResultInfo;
import static org.postgresql.pljava.model.RegProcedure.Kind.PROCEDURE;
import org.postgresql.pljava.model.RegType;
import static org.postgresql.pljava.model.RegType.TRIGGER;
import org.postgresql.pljava.model.TupleDescriptor;
import org.postgresql.pljava.model.TupleTableSlot;

import static org.postgresql.pljava.pg.CatalogObjectImpl.notyet;
import static org.postgresql.pljava.pg.CatalogObjectImpl.of;
import static org.postgresql.pljava.pg.CatalogObjectImpl.Factory.ANYOID;
import static org.postgresql.pljava.pg.DatumUtils.asReadOnlyNativeOrder;
import static org.postgresql.pljava.pg.DatumUtils.fetchPointer;
import static org.postgresql.pljava.pg.DatumUtils.fromBitmapset;
import static org.postgresql.pljava.pg.DatumUtils.mapFixedLength;
import static org.postgresql.pljava.pg.DatumUtils.toBitmapset;
import static org.postgresql.pljava.pg.ModelConstants.ALIGNOF_INT;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_fcinfo_args;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_fcinfo_fncollation;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_fcinfo_isnull;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_fcinfo_nargs;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_fcinfo_fncollation;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_fcinfo_isnull;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_fcinfo_nargs;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_NodeTag;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_Oid;
import static org.postgresql.pljava.pg.ModelConstants.T_Invalid;
import static org.postgresql.pljava.pg.ModelConstants.T_AggState;
import static org.postgresql.pljava.pg.ModelConstants.T_CallContext;
import static org.postgresql.pljava.pg.ModelConstants.T_ErrorSaveContext;
import static org.postgresql.pljava.pg.ModelConstants.T_EventTriggerData;
import static org.postgresql.pljava.pg.ModelConstants.T_ReturnSetInfo;
import static org.postgresql.pljava.pg.ModelConstants.T_TriggerData;
import static org.postgresql.pljava.pg.ModelConstants.T_WindowAggState;
import static org.postgresql.pljava.pg.ModelConstants.T_WindowObjectData;

import org.postgresql.pljava.pg.ProceduralLanguageImpl.PLJavaMemo;

import org.postgresql.pljava.pg.TupleDescImpl.OfType;
import static org.postgresql.pljava.pg.TupleDescImpl.synthesizeDescriptor;
import static org.postgresql.pljava.pg.TupleTableSlotImpl.newNullableDatum;

import static org.postgresql.pljava.pg.adt.OidAdapter.REGPROCEDURE_INSTANCE;

import static org.postgresql.pljava.sqlgen.Lexicals.Identifier;

import static org.postgresql.pljava.sqlj.Loader.getSchemaLoader;

/* Imports for TriggerData implementation */

import org.postgresql.pljava.annotation.Trigger.Called;
import org.postgresql.pljava.annotation.Trigger.Event;
import org.postgresql.pljava.annotation.Trigger.Scope;
import org.postgresql.pljava.model.RegClass;
import org.postgresql.pljava.model.Trigger;

import static org.postgresql.pljava.pg.ModelConstants.OFFSET_Relation_rd_id;

import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRGD_tg_event;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRGD_tg_relation;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRGD_tg_trigtuple;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRGD_tg_newtuple;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRGD_tg_trigger;
import static
	org.postgresql.pljava.pg.ModelConstants.OFFSET_TRGD_tg_updatedcols;

import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_Trigger;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgoid;
import static
	org.postgresql.pljava.pg.TupleTableSlotImpl.heapTupleGetLightSlotNoFree;

/**
 * The implementation of {@link Lookup}, serving as the dispatcher for routines,
 * validators, and inline code blocks in PL/Java-based languages.
 */
class LookupImpl implements RegProcedure.Lookup
{
	static
	{
		assert Integer.BYTES == SIZEOF_Oid : "sizeof oid";
	}

	private static final Routine s_placeholderRoutine = fcinfo ->
	{
		throw new IllegalStateException(String.format(
			"reentrant resolution of Routine for %s",
			fcinfo.lookup().target()));
	};

	private static final Template s_placeholderTemplate = flinfo ->
	{
		throw new IllegalStateException(String.format(
			"reentrant resolution of Template for %s",
			flinfo.target()));
	};

	private static final Routines s_placeholderInstance = new Routines()
	{
		@Override
		public void essentialChecks(
			RegProcedure<PLJavaBased> subject, boolean checkBody)
		{
			throw squawk(subject.language());
		}

		@Override
		public Template prepare(RegProcedure<PLJavaBased> target)
		{
			throw squawk(target.language());
		}

		private IllegalStateException squawk(ProceduralLanguage pl)
		{
			return new IllegalStateException(String.format(
				"reentrant construction of implementing class for %s", pl));
		}
	};

	private final RegProcedureImpl<?> m_target;
	private final State m_state;
	private final boolean m_forValidator;

	/*
	 * Mutable fields to be mutated only on the PG thread.
	 *
	 * m_outputsDescriptor is a 'notional' descriptor that will be supplied to
	 * the language handler, so the target routine always delivers any result
	 * by storing something in a TupleTableSlot, and doesn't need to get into
	 * the PostgreSQL weeds of "a scalar gets returned, but OUT parameters make
	 * a composite result, unless it's just one OUT parameter and that's treated
	 * just like a scalar, but a polymorphic type later resolved to a one-column
	 * composite isn't" and so on.
	 *
	 * But here, once the target routine returns, we need to get into the weeds
	 * to know the right thing to do with the result, so we need m_returnType
	 * too.
	 */
	private boolean m_hasExpr; // PROBABLY could be final
	private TupleDescriptor m_inputsDescriptor;
	private TupleDescriptor m_outputsDescriptor;
	private RegType m_returnType;
	/*
	 * The most recent Call instance that referred to this Lookup. Strictly
	 * considered, one should be treated as referring to valid memory only while
	 * the invocation using it is on the PG thread's stack (in contrast to a
	 * Lookup instance, which can be longer-lived). A Call instance can be saved
	 * here, though (just not read or written through!) after such an invocation
	 * returns. If another invocation is found to have the same C structs in the
	 * same places, the ByteBuffers it encapsulates are still usable, and it can
	 * be treated as alive again for the duration of that call.
	 *
	 * Implicit in the way the PostgreSQL call convention works is that whenever
	 * control arrives here (at dispatchNew or dispatch), there is an fcinfo
	 * that is live. By the time preDispatch has returned, the Call instance in
	 * this field (be it newly constructed or just revivified) corresponds to
	 * that live fcinfo, and may, through the remaining dispatch action here, be
	 * used as such.
	 */
	private CallImpl m_savedCall;

	private Routine m_routine;

	private LookupImpl(
		MemoryContext cxt, long extra, RegProcedure<?> target,
		boolean forValidator)
	{
		assert threadMayEnterPG() : "LookupImpl.<init> thread";

		m_target = (RegProcedureImpl)target;
		m_state = new State(this, cxt, extra);
		m_forValidator = forValidator;
	}

	/**
	 * Invoked from native code when no existing instance was on hand.
	 *<p>
	 * Creates fresh {@code Lookup} and {@code Call} instances, caches the
	 * {@code Lookup} reference in our 'extra' struct attached to the C
	 * {@code FmgrInfo}, calls {@link #preDispatch preDispatch} to ensure the
	 * fresh instances reflect the passed arguments, and finally proceeds
	 * through resolution and dispatch of the target routine.
	 * @param mcxt {@code fn_mcxt} from the underlying C {@code FmgrInfo}
	 * struct, used to bound the {@code Lifespan} of this instance
	 * @param extra address of the cache struct that has been installed in
	 * the C struct's {@code fn_extra} field, and will be used to cache a JNI
	 * global reference to this instance. Our {@link State State} will take care
	 * of reclaiming the JNI reference when the memory context is to be deleted.
	 * @param targetOid oid of the routine to be invoked
	 * @param forValidator true if control arrived through the validator entry
	 * point. For that case, targetOid (and therefore this {@code Lookup}
	 * instance) belongs to a validator function, and the oid of the user
	 * routine to be validated (call it the 'subject') is passed to it
	 * as argument 0.
	 * @param hasExpr whether information on the calling expression is available
	 * in the C struct. If false, no resolution of polymorphic parameter types
	 * will be possible.
	 * @param fcinfo {@code ByteBuffer} windowing the C function call info
	 * struct
	 * @param context {@code ByteBuffer} windowing one of the {@code Node} types
	 * that might appear in C at {@code fcinfo->context}, if present and of a
	 * node type we recognize, otherwise null.
	 * @param resultinfo {@code ByteBuffer} windowing a {@code Node} type that
	 * might appear in C at {@code fcinfo->resultinfo}, if present and of a
	 * node type we recognize, otherwise null.
	 */
	private static void dispatchNew(
		long mcxt, long extra, int targetOid,
		boolean forValidator, boolean hasExpr,
		ByteBuffer fcinfo, ByteBuffer context, ByteBuffer resultinfo)
	throws SQLException
	{
		/*
		 * This method is only called from native code and is already on the PG
		 * thread, so a simple assert threadMayEnterPG() would be tempting here.
		 * However, in java_thread_pg_entry=allow mode, threadMayEnterPG can be
		 * false here if the JNI upcall was not one of the ...Locked variants.
		 * That flavor isn't used for this upcall, because the intent is that
		 * we'll ultimately dispatch to arbitrary user code and not want the
		 * monitor held for that. Hence this otherwise-redundant doInPG.
		 */
		Checked.Runnable<SQLException> r = doInPG(() ->
		{
			MemoryContext cxt = MemoryContextImpl.fromAddress(mcxt);
			RegProcedure<?> target = of(RegProcedure.CLASSID, targetOid);

			LookupImpl flinfo =
				new LookupImpl(cxt, extra, target, forValidator);

			_cacheReference(flinfo, extra);

			CallImpl cImpl = flinfo.preDispatch(
				targetOid, true, hasExpr, fcinfo, context, resultinfo);

			Routine routine = flinfo.selectRoutine();

			return () -> cImpl.dispatch(routine);
		});

		r.run();
	}

	/**
	 * Invoked from native code when this existing instance has been found
	 * in {@code fn_extra}.
	 *<p>
	 * Calls {@link #preDispatch preDispatch} to ensure the {@code Lookup}
	 * instance (this instance, reused) and the {@code Call} instance
	 * (conditionally reused) reflect the current passed arguments, and finally
	 * proceeds through resolution / dispatch of the target routine.
	 * @param targetOid asserted the same as what's stored in this instance;
	 * many assumptions here go out the window if that could ever change
	 * @param newExpr whether the calling-expression information (or its
	 * presence/absence) appears to have changed since the last dispatch through
	 * this instance; I suspect that's not possible, but there has been no
	 * response to the query on -hackers.
	 * @param hasExpr if <var>newExpr</var> is true, indicates whether
	 * expression information is now present at all.
	 * @param fcinfo {@code ByteBuffer} windowing the C function call info
	 * struct for this call; null if the address and size are unchanged since
	 * the last call, meaning the existing {@code ByteBuffer} can be reused.
	 * @param context {@code ByteBuffer} windowing the C {@code context} node
	 * for this call; null to reuse an existing {@code ByteBuffer};
	 * a zero-length {@code ByteBuffer} to indicate that there no longer is
	 * a node there.
	 * @param resultinfo {@code ByteBuffer} windowing the C {@code resultinfo}
	 * node for this call; null to reuse an existing {@code ByteBuffer};
	 * a zero-length {@code ByteBuffer} to indicate that there no longer is
	 * a node there.
	 */
	private void dispatch(
		int targetOid, boolean newExpr, boolean hasExpr,
		ByteBuffer fcinfo, ByteBuffer context, ByteBuffer resultinfo)
	throws SQLException
	{
		/*
		 * For the same reason as in dispatchNew, this is a doInPG and not just
		 * an assert, even though the only caller is native code and always
		 * on the PG thread.
		 */
		Checked.Runnable<SQLException> r = doInPG(() ->
		{
			CallImpl cImpl = preDispatch(
				targetOid, newExpr, hasExpr, fcinfo, context, resultinfo);

			Routine routine = selectRoutine();

			return () -> cImpl.dispatch(routine);
		});

		r.run();
	}

	/**
	 * Invoked from native code to handle an inline code block.
	 *<p>
	 * This method is nearly independent of most of this class, which is
	 * otherwise geared toward the validation, caching, and execution of
	 * functions and procedures. Other than resolving the implementing class
	 * of the inline block's declared language, very little of that is
	 * necessary here.
	 */
	private static void dispatchInline(
		int langOid, boolean atomic, ByteBuffer source_text)
	throws SQLException
	{
		/*
		 * For the same reason as in dispatchNew, this is a doInPG and not just
		 * an assert, even though the only caller is native code and always
		 * on the PG thread.
		 */
		Checked.Runnable<SQLException> r = doInPG(() ->
		{
			int placeholders = 0;
			ProceduralLanguageImpl pl_outer = null;
			try
			{
				ProceduralLanguageImpl pl = pl_outer =
					(ProceduralLanguageImpl)of(
						ProceduralLanguage.CLASSID, langOid);

				if ( ! pl.isPLJavaBased() )
					throw new SQLSyntaxErrorException(String.format(
						"%s not recognized as a PL/Java-based language", pl),
						"42883");

				String source_string;
				try
				{
					source_string =
						SERVER_ENCODING.decode(source_text).toString();
				}
				catch ( CharacterCodingException e ) // shouldn't really happen
				{
					throw new SQLException("in text of DO block: " + e, e);
				}

				/*
				 * True return from isPLJavaBased => safe to call
				 * implementingClass.
				 */
				PLJavaBasedLanguage pbl = pl.implementingClass();

				if ( pbl instanceof InlineBlocks )
					return () ->
						((InlineBlocks)pbl).execute(source_string, atomic);

				Checked.Function
					<ProceduralLanguage,? extends InlineBlocks,SQLException>
					ctor = validatorToCtor(pl.validator(), InlineBlocks.class);

				/*
				 * This is explained where it happens in selectRoutine below.
				 */
				pl.memoizeImplementingClass(null, s_placeholderInstance);
				++ placeholders;

				return () ->
				{
					InlineBlocks impl = ctor.apply(pl);

					doInPG(() ->
						pl.memoizeImplementingClass(s_placeholderInstance, impl));

					impl.execute(source_string, atomic);
				};
			}
			catch ( Throwable t )
			{
				if ( 1 == placeholders  &&  null != pl_outer )
					pl_outer.memoizeImplementingClass(
						s_placeholderInstance, null);
				throw t;
			}
		});

		r.run();
	}

	/**
	 * Logic shared between {@link #dispatchNew dispatchNew} (after freshly
	 * constructing this instance and a {@code Call} instance) and
	 * {@link #dispatch dispatch} (having found and reused this instance) to
	 * update this instance and/or construct a new {@code Call} instance, as
	 * needed, to reflect the latest values passed from the native code.
	 *<p>
	 * At return, {@code m_savedCall} is known to be a {@code Call} instance
	 * reflecting the current values, and can be used for dispatch.
	 * @return the up-to-date {@code Call} instance, usable for dispatch (also
	 * saved in m_savedCall)
	 */
	private CallImpl preDispatch(
		int targetOid, boolean newExpr, boolean hasExpr,
		ByteBuffer fcinfo, ByteBuffer context, ByteBuffer resultinfo)
	throws SQLException
	{
		assert threadMayEnterPG() : "LookupImpl.dispatchNew thread";
		assert targetOid == m_target.oid() : "flinfo target oid changed";

		if ( newExpr )
		{
			m_hasExpr = hasExpr;
			m_inputsDescriptor = m_outputsDescriptor = null;
			m_returnType = null;
			m_routine = null;
		}

		boolean newCallNeeded = false;
		if ( null == m_savedCall )
			newCallNeeded = true;
		else
		{
			if ( null == fcinfo )
				fcinfo = m_savedCall.m_fcinfo;
			else
				newCallNeeded = true;

			if ( null == context )
				context = m_savedCall.m_context;
			else
			{
				newCallNeeded = true;
				if ( 0 == context.capacity() )
					context = null;
			}

			if ( null == resultinfo )
				resultinfo = m_savedCall.m_resultinfo;
			else
			{
				newCallNeeded = true;
				if ( 0 == resultinfo.capacity() )
					resultinfo = null;
			}
		}

		if ( newCallNeeded )
			m_savedCall = new CallImpl(fcinfo, context, resultinfo);

		return m_savedCall;
	}

	/**
	 * Returns the {@link Routine Routine} to be dispatched to for handling
	 * this call.
	 *<p>
	 * When a suitable {@code Routine} has already been cached, specialized to
	 * this call site (if required), it is returned directly. Otherwise,
	 * depending on what else has been cached to this point, when this
	 * {@code Lookup} instance does not represent a validator call, a temporary
	 * {@code Routine} is returned that will have one of these behaviors:
	 *<ol>
	 * <li>Starting with a {@link Template Template} object already cached on
	 * the target {@code RegProcedure}, calls its {@code specialize} method to
	 * obtain a {@code Routine}, caches that on this {@code Lookup} instance,
	 * and then proceeds to execute it.</li>
	 * <li>Starting with the {@link Routines Routines} instance already cached
	 * on the target's {@code ProceduralLanguage}, calls its
	 * {@code essentialChecks} and {@code prepare} methods to obtain
	 * a {@code Template}, caches that on the {@code RegProcedure} instance, and
	 * then proceeds with the actions of (1).</li>
	 * <li>Using the class name in {@code target.language().validator().src()},
	 * constructs an instance (loading and initializing the class if need be),
	 * caches that on the {@code ProceduralLanguage} instance, and then proceeds
	 * with the actions of (2).</li>
	 *</ol>
	 *<p>
	 * When this {@code Lookup} instance does represent a validator call,
	 * caches and returns a {@code Routine} that handles that.
	 */
	private Routine selectRoutine() throws SQLException
	{
		assert threadMayEnterPG() : "selectRoutine thread";

		int placeholders = 0;
		ProceduralLanguageImpl pl = null;
		PLJavaBased memo = null;
		try
		{
			/*
			 * If the Routine to execute is already cached, we're done
			 * immediately.
			 */
			if ( null != m_routine )
				return m_routine;

			/*
			 * If this is a validator call, do that. Otherwise, we want a
			 * Template that can be passed to specializingRoutine (to return a
			 * Routine that, when executed, will invoke Template.specialize to
			 * obtain the real Routine, memoize that on this Lookup instance,
			 * then execute it).
			 */
			pl = (ProceduralLanguageImpl)m_target.language();

			if ( m_forValidator )
			{
				if ( pl.isPLJavaHandler(null) )
					return m_routine = LookupImpl::routineValidator;

				if ( C == pl )
					return m_routine = LookupImpl::validatorValidator;

				throw new SQLSyntaxErrorException(String.format(
					"%s of %s not recognized as PL/Java handler language",
					pl, m_target), "42883");
			}

			/*
			 * Not a validator. So, confirm the PL is something PL/Java-based,
			 * to justify casting it to RegProcedureImpl<PLJavaBased> and
			 * expecting to find a PLJavaBased memo there. That's where we will
			 * find the Template if one has already been cached.
			 */
			if ( ! pl.isPLJavaBased() )
				throw new SQLSyntaxErrorException(String.format(
					"%s of %s not recognized as a PL/Java-based language",
					pl, m_target), "42883");

			@SuppressWarnings("unchecked")
			RegProcedureImpl<PLJavaBased> target =
				(RegProcedureImpl<PLJavaBased>)m_target;

			memo = target.m_memo;

			/*
			 * We are getting ready to return a Routine that, when executed,
			 * will generate the real Routine that should be cached here. Before
			 * doing so, cache s_placeholderRoutine for now, to make noise in
			 * case we arrive here reentrantly; that's not expected, but we are
			 * about to execute user language-handler code whose behavior isn't
			 * known. The user specialize() code won't be running in doInPG(),
			 * but the code to cache the result will be, and will make sure this
			 * placeholder is still here.
			 */
			m_routine = s_placeholderRoutine;
			++ placeholders;

			if ( null != memo )
			{
				Template template = ((PLJavaMemo)memo).m_routineTemplate;
				assert null != template : "PLJavaBased memo with null template";
				return specializingRoutine(template);
			}

			/*
			 * No memo with a Template was already cached. So, we want to
			 * find the implementing class instance for this PL, to pass to
			 * preparingRoutine(), to return a Routine that, when executed, will
			 * invoke that instance's prepare method to obtain a Template, cache
			 * that in the RegProcedure's memo, and then proceed as for
			 * specializingRoutine.
			 */
			PLJavaBasedLanguage pbl = pl.implementingClass();

			/*
			 * We are getting ready to return a Routine that, when executed,
			 * will generate the real Template that should be cached here.
			 * Before doing so, cache s_placeholderTemplate for now, all
			 * analogously to the use of s_placeholderRoutine above.
			 */
			memo = pl.addDependentRoutine(target);
			((PLJavaMemo)memo).m_routineTemplate = s_placeholderTemplate;
			++ placeholders;

			if ( pbl instanceof Routines )
				return preparingRoutine(
					target, (PLJavaMemo)memo, (Routines)pbl);

			assert null == pbl
				: "PL with wrong type of implementing class cached";

			/*
			 * No implementing class instance was already cached for this PL.
			 * So, we want to resolve the class providing the implementation,
			 * to pass to instantiatingRoutine, to return a Routine that, when
			 * executed, will (if necessary, initialize, and) instantiate that
			 * class, memoize the instance on this PL, and then proceed as for
			 * preparingRoutine.
			 */
			Checked.Function<ProceduralLanguage,? extends Routines,SQLException>
				ctor = validatorToCtor(pl.validator(), Routines.class);

			/*
			 * We are getting ready to return a Routine that, when executed,
			 * will generate the implementing class instance that should be
			 * cached on this PL. Before doing so, cache s_placeholderInstance
			 * for now, all analogously to the use of s_placeholderRoutine
			 * above.
			 */
			pl.memoizeImplementingClass(null, s_placeholderInstance);
			++ placeholders;

			return instantiatingRoutine(target, (PLJavaMemo)memo, ctor, pl);
		}
		catch ( Throwable t )
		{
			switch ( placeholders )
			{
			case 3:
				pl.memoizeImplementingClass(s_placeholderInstance, null);
				/* FALLTHROUGH */
			case 2:
				memo.m_routineTemplate = null;
				((PLJavaMemo)memo).discardIncomplete();
				/* FALLTHROUGH */
			case 1:
				m_routine = null;
			}
			throw t;
		}
	}

	/**
	 * Returns a {@code Routine} that first generates, from a {@code Template},
	 * a {@code Routine} specialized to the current call site, then caches that
	 * on the call site and executes it.
	 */
	private static Routine specializingRoutine(Template template)
	{
		return fcinfo ->
		{
			LookupImpl flinfo = (LookupImpl)fcinfo.lookup();
			Routine outer_r = null;

			try
			{
				outer_r = requireNonNull(template.specialize(flinfo));
			}
			finally
			{
				Routine r = outer_r;
				doInPG(() ->
				{
					assert s_placeholderRoutine == flinfo.m_routine
						: "routine updated by reentrant call?";
					flinfo.m_routine = r;
				});
			}

			outer_r.call(fcinfo);
		};
	}

	/**
	 * Returns a {@code Routine} that first generates, from a target routine and
	 * its language's implementing class, a {@code Template}, caches that on the
	 * target {@code RegProcedure}, and then proceeds as for
	 * {@code specializingRoutine}.
	 */
	private static Routine preparingRoutine(
		RegProcedure<PLJavaBased> target, PLJavaMemo memo, Routines impl)
	throws SQLException
	{
		return fcinfo ->
		{
			Template outer_template = null;

			try
			{
				validate(impl, target, true/*checkBody*/, false/*additional*/);
				outer_template = prepare(impl, target);
			}
			finally
			{
				Template template = outer_template;
				doInPG(() ->
				{
					assert s_placeholderTemplate == memo.m_routineTemplate
						: "template updated by reentrant call?";
					memo.m_routineTemplate = template;
					if ( null == template )
						memo.discardIncomplete();
				});
			}

			specializingRoutine(outer_template).call(fcinfo);
		};
	}

	/**
	 * Returns a {@code Routine} that first instantiates the implementing class
	 * for a PL/Java-based language, caches that on the
	 * {@code ProceduralLanguage} instance, and then proceeds as for
	 * {@code preparingRoutine}.
	 */
	private static Routine instantiatingRoutine(
		RegProcedure<PLJavaBased> target, PLJavaMemo memo,
		Checked.Function<ProceduralLanguage,? extends Routines,SQLException>
			ctor,
		ProceduralLanguageImpl pl)
	{
		return fcinfo ->
		{
			Routines impl = ctor.apply(pl);

			doInPG(() ->
				pl.memoizeImplementingClass(s_placeholderInstance, impl));

			preparingRoutine(target, memo, impl).call(fcinfo);
		};
	}

	/**
	 * Given a {@link RegProcedure RegProcedure} understood to represent the
	 * validator of a PL/Java-based language, returns the proper constructor
	 * for instances of the language's implementing class.
	 */
	private static <T extends PLJavaBasedLanguage>
		Checked.Function<ProceduralLanguage,? extends T,SQLException>
		validatorToCtor(RegProcedure<Validator> vp, Class<T> wanted)
	throws SQLException
	{
		String spec = vp.src();
		ClassLoader loader = getSchemaLoader(vp.namespace().name());
		Class<?> c;
		try
		{
			c = Class.forName(spec, false, loader);

			if ( ! wanted.isAssignableFrom(c) )
				throw new SQLSyntaxErrorException(String.format(
					"%s 'AS' class, %s, does not implement %s",
					vp, c.getCanonicalName(), wanted.getCanonicalName()),
					"42883");

			/*
			 * If the caller has passed only the PLJavaBasedLanguage interface,
			 * the caller is the language-handler validator, and this is a good
			 * place to insist that the class implement at least one of the
			 * specific subinterfaces.
			 */
			if ( PLJavaBasedLanguage.class == wanted
				&& ! InlineBlocks.class.isAssignableFrom(c)
				&& ! Routines.class.isAssignableFrom(c) )
				throw new SQLSyntaxErrorException(String.format(
					"%s 'AS' class, %s, does not implement at least one of " +
					"%s, %s",
					vp, c.getCanonicalName(),
					InlineBlocks.class.getCanonicalName(),
					Routines.class.getCanonicalName()),
					"42883");

			Constructor<? extends T> ctor =
				c.asSubclass(wanted).getConstructor(ProceduralLanguage.class);

			return pl ->
			{
				try
				{
					return ctor.newInstance(pl);
				}
				catch ( ReflectiveOperationException e )
				{
					throw new SQLNonTransientException(
						"instantiating class " + spec + ": " + e, "46103", e);
				}
			};
		}
		catch ( ReflectiveOperationException e )
		{
			throw new SQLNonTransientException(
				"resolving class " + spec + ": " + e, "46103", e);
		}
	}

	/**
	 * The dispatching validator for a proposed routine in a PL/Java-based
	 * language.
	 *<p>
	 * Obtains the subject routine from {@code fcinfo.arguments().get(0)},
	 * identifies that routine's declared language, obtains that language's
	 * implementing class (instantiating it if necessary, with the aid of
	 * {@link #validatorCtor validatorToCtor} on that language's declared
	 * validator and memoizing that instance on the {@code ProceduralLanguage}),
	 * provisionally records the proposed new routine as a dependent routine on
	 * the implementing language, and invokes {@link #validate validate} to
	 * apply the validation logic, which includes applying the language
	 * implementation's {@code essentialChecks} and {@code additionalChecks}
	 * on the subject routine.
	 */
	private static void routineValidator(Call fcinfo) throws SQLException
	{
		RegProcedure<?> subject =
			fcinfo.arguments().get(0, REGPROCEDURE_INSTANCE);

		boolean checkBody = validateBodies.getAsBoolean();

		Checked.Runnable<SQLException> r = doInPG(() ->
		{
			int placeholders = 0;
			ProceduralLanguageImpl pl_outer = null;
			boolean reportable = true;
			try
			{
				ProceduralLanguageImpl pl = pl_outer =
					(ProceduralLanguageImpl)subject.language();

				if ( ! pl.isPLJavaBased() )
					throw new SQLException(String.format(
						"the language %s of %s does not appear to be " +
						"PL/Java-based", pl, subject));

				/*
				 * True return from isPLJavaBased => safe to call
				 * implementingClass.
				 */

				PLJavaBasedLanguage pbl = pl.implementingClass();

				if ( pbl instanceof Routines )
				{
					/*
					 * This is done here so the references all exist as expected
					 * while executing the validator, but it won't stick.
					 * Not only will there be a selective-invalidation message
					 * undoing it if the validator routines reject the routine
					 * and cause rollback, the successful creation of the
					 * routine will also generate such a message, undoing the
					 * link. We'll just recreate it on first actual use:
					 */
					pl.addDependentRoutine(subject);
					return () -> validate((Routines)pbl, subject, checkBody);
				}
				else if ( null != pbl )
					throw new SQLSyntaxErrorException(String.format(
						"%s of %s does not support functions / procedures",
						pl, subject));

				if ( ! checkBody )
					reportable = false;

				Checked.Function
					<ProceduralLanguage,? extends Routines,SQLException>
					ctor = validatorToCtor(pl.validator(), Routines.class);

				pl.memoizeImplementingClass(null, s_placeholderInstance);
				++ placeholders;

				return () ->
				{
					Routines impl = null;
					try
					{
						impl = ctor.apply(pl);
					}
					catch ( Throwable t )
					{
						if ( checkBody )
							throw t;
					}
					finally
					{
						Routines f_impl = impl;
						doInPG(() ->
						{
							pl.memoizeImplementingClass(
								s_placeholderInstance, f_impl);

							/*
							 * See note above where this is also done:
							 */
							if ( null != f_impl )
								pl.addDependentRoutine(subject);
						});
					}

					if ( null != impl )
						validate(impl, subject, checkBody);
				};
			}
			catch ( Throwable t )
			{
				if ( 1 == placeholders  &&  null != pl_outer )
					pl_outer.memoizeImplementingClass(
						s_placeholderInstance, null);
				if ( reportable )
					throw t;
				return () -> {};
			}
		});

		r.run();
	}

	/**
	 * Validates a proposed routine, given the language implementing instance,
	 * which has already been looked up and confirmed to implement
	 * {@code Routines}.
	 */
	private static void validate(
		Routines impl, RegProcedure<?> subject, boolean checkBody)
	throws SQLException
	{
		@SuppressWarnings("unchecked")
		RegProcedure<PLJavaBased> narrowed = (RegProcedure<PLJavaBased>)subject;

		validate(impl, narrowed, checkBody, true);

		/*
		 * On the way to invoking this method, nearly all of the linkages
		 * between the subject RegProcedure and its language and validator have
		 * been created, except of course for a null in the RegProcedure memo
		 * where a Template ought to be.
		 *
		 * Often, the linkages are torn down by a shared-invalidation event
		 * should validate() fail, or even if it succeeds and the affected
		 * pg_proc row is committed. But it can happen, if creation / validation
		 * and use occur in the same transaction, that the teardown by a SINVAL
		 * event can't be relied on, and the attempt to use the now-validated
		 * routine could stumble on a null template reference in the memo.
		 *
		 * There are two ways to prevent that. An obvious choice is to simulate
		 * what a SINVAL event would do and discard the incomplete memo. We'll
		 * do that here if checkBody was false, as some necessary validation
		 * could have been skipped.
		 *
		 * On the other hand, if checkBody was true, we have here a fully
		 * validated RegProcedure and the Routines instance needed to prepare
		 * it, and may as well replace the null with a preparingTemplate which,
		 * if it is still there (no SINVAL) on the first attempt at use, will
		 * directly call impl.prepare and replace itself in the memo.
		 */
		doInPG(() ->
		{
			PLJavaMemo m = (PLJavaMemo)narrowed.memo();
			if ( null == m  ||  null != m.m_routineTemplate )
				return;
			if ( checkBody )
				m.m_routineTemplate = preparingTemplate(narrowed, impl);
			else
				m.discardIncomplete();
		});
	}

	/**
	 * Calls the implementation's essential (and additional, if requested)
	 * checks, following general checks common to all languages.
	 *<p>
	 * If <var>subject</var>'s return type is {@link RegType#TRIGGER TRIGGER},
	 * this method will also check that <var>impl</var> implements
	 * {@link Triggers Triggers} and that <var>subject</var> declares no
	 * parameters and is not declared {@code SETOF}, throwing appropriate
	 * exceptions if those conditions do not hold, and will then call
	 * <var>impl</var>'s {@code essentialTriggerChecks} (and, if requested,
	 * {@code additionalTriggerChecks}) methods.
	 *<p>
	 * Otherwise, calls <var>impl</var>'s {@code essentialChecks} (and, if
	 * requested, {@code additionalChecks}) methods.
	 */
	private static void validate(
		Routines impl, RegProcedure<PLJavaBased> subject,
		boolean checkBody, boolean additionalChecks)
	throws SQLException
	{
		checkForTransforms(subject, impl, additionalChecks);

		if ( TRIGGER == subject.returnType() )
		{
			if ( ! (impl instanceof Triggers) )
				throw new SQLSyntaxErrorException(String.format(
					"%s of %s does not support triggers",
					subject.language(), subject), "42P13");

			if ( 0 < subject.argTypes().size()
				|| null != subject.allArgTypes() )
				throw new SQLSyntaxErrorException(String.format(
					"%s declares arguments, but a trigger function may not",
					subject), "42P13");

			if ( subject.returnsSet() )
				throw new SQLSyntaxErrorException(String.format(
					"%s returns SETOF, but a trigger function may not",
					subject), "42P13");

			Triggers timpl = (Triggers)impl;

			timpl.essentialTriggerChecks(subject, checkBody);

			if ( ! additionalChecks )
				return;

			timpl.additionalTriggerChecks(subject, checkBody);
			return;
		}

		impl.essentialChecks(subject, checkBody);

		if ( ! additionalChecks )
			return;

		impl.additionalChecks(subject, checkBody);
	}

	/**
	 * Calls the appropriate method on <var>impl</var> to prepare and return
	 * a {@code Template}.
	 *<p>
	 * If <var>target</var>'s return type is not
	 * {@link RegType#TRIGGER TRIGGER}, simply calls {@code impl.prepare}.
	 *<p>
	 * Otherwise, validation has already established that <var>impl</var>
	 * implements {@link Triggers Triggers} and <var>target</var> has proper
	 * form. This method calls {@code prepareTrigger} and wraps the returned
	 * {@code TriggerTemplate} in a {@code Template} whose {@code specialize}
	 * method will pass the appropriate {@link Trigger Trigger} instance to
	 * the {@code TriggerTemplate}'s {@code specialize} method, and wrap the
	 * returned {@code TriggerFunction} in a {@code Routine} that will pass
	 * a caller's {@link Context.TriggerData TriggerData} to the
	 * {@code TriggerFunction}'s {@code apply} method.
	 */
	private static Template prepare(
		Routines impl, RegProcedure<PLJavaBased> target)
	throws SQLException
	{
		if ( TRIGGER != target.returnType() )
			return requireNonNull(impl.prepare(target));

		/*
		 * It's a trigger (and validated before we got here).
		 */

		TriggerTemplate ttpl =
			requireNonNull(((Triggers)impl).prepareTrigger(target));

		return flinfo ->
		{
			TriggerFunction tf;

			// block, so these variable names don't collide with lambda below
			{
				Context c = ((LookupImpl)flinfo).m_savedCall.context();

				if ( ! ( c instanceof Context.TriggerData ) )
					throw new SQLNonTransientException(String.format(
						"%s was not called by trigger manager", target),
						"39P01");

				CallImpl.TriggerDataImpl tdi = (CallImpl.TriggerDataImpl)c;
				TriggerImpl trgi = (TriggerImpl)tdi.trigger();

				tf = trgi.withTriggerData(tdi, () -> ttpl.specialize(trgi));
			}

			TriggerFunction final_tf = requireNonNull(tf);

			return fcinfo ->
			{
				CallImpl.TriggerDataImpl tdi =
					(CallImpl.TriggerDataImpl)fcinfo.context();
				TriggerImpl trgi = (TriggerImpl)tdi.trigger();
				TupleTableSlot tts =
					trgi.withTriggerData(tdi, () -> final_tf.apply(tdi));
				// XXX when result-returning implemented, do the right stuff
			};
		};
	}

	/**
	 * Returns a placeholder {@code Template} whose {@code specialize} method
	 * will first generate and memoize the real {@code Template} and then call
	 * its {@code specialize} method and return the result.
	 */
	private static Template preparingTemplate(
		RegProcedure<PLJavaBased> subject, Routines impl)
	{
		return flinfo ->
		{
			assert flinfo.target() == subject: "preparingTemplate wrong target";

			/*
			 * A call of prepare is normally preceded by a call of
			 * essentialChecks in case the routine was not fully validated.
			 * This Template, however, is only present after successful full
			 * validation, so a repeated call of essentialChecks isn't needed.
			 */
			Template t = prepare(impl, subject);

			doInPG(() ->
			{
				PLJavaMemo m = (PLJavaMemo)subject.memo();
				if ( null == m )
					return;
				m.m_routineTemplate = t;
			});

			return t.specialize(flinfo);
		};
	}

	/**
	 * Pattern to recognize a Java type name, possibly qualified,
	 * without array brackets.
	 */
	private static final Pattern javaTypeName = Pattern.compile(String.format(
		"(?:%1$s\\.)*+%1$s",
		String.format("\\p{%1$sStart}\\p{%1sPart}*+", "javaJavaIdentifier"))
	);

	/**
	 * The validator of a PL/Java-based language implementation's validator.
	 *<p>
	 * The subject {@code RegProcedure} (argument 0) needs to be a function
	 * with one parameter, typed oid, and we'll say void return, for
	 * consistency. Its language (of implementation; the language it validates
	 * may not be declared yet) must return true for {@code isPLJavaHandler}.
	 *<p>
	 * Its {@code src} needs to be a class name, and the class needs to
	 * implement {@code PLJavaBasedLanguage.Routines}.
	 */
	private static void validatorValidator(Call fcinfo) throws SQLException
	{
		RegProcedure<?> subject =
			fcinfo.arguments().get(0, REGPROCEDURE_INSTANCE);

		boolean checkBody = validateBodies.getAsBoolean();

		switch ( subject.kind() )
		{
		case FUNCTION: break;
		default:
			throw new SQLException(String.format(
				"%s must have kind FUNCTION; has %s", subject, subject.kind()));
		}

		List<RegType> argtypes = subject.argTypes();
		if ( 1 != argtypes.size()  ||  RegType.OID != argtypes.get(0) )
			throw new SQLException(String.format(
				"%s must have one parameter of type pg_catalog.oid; has %s",
				subject, argtypes));

		if ( RegType.VOID != subject.returnType() )
			throw new SQLException(String.format(
				"%s must have return type pg_catalog.void; has %s",
				subject, subject.returnType()));

		/*
		 * This check probably cannot fail, given that PostgreSQL dispatched the
		 * validation here. But in the interest of thoroughness and sanity ....
		 */
		if ( ! doInPG(() -> ((ProceduralLanguageImpl)subject.language())
			.isPLJavaHandler(null)) )
			throw new SQLException(String.format(
				"the language %s of %s does not appear " +
				"to be PL/Java's dispatcher",
				subject.language(), subject));

		String src = subject.src();

		if ( null == src  ||  ! javaTypeName.matcher(src).matches() )
			throw new SQLException(String.format(
				"%s AS must be a Java class name; found: %s",
				subject, src));

		if ( ! checkBody )
			return;

		@SuppressWarnings("unchecked")
		RegProcedure<Validator> rpv = (RegProcedure<Validator>)subject;

		/*
		 * This will verify that the class can be found, implements the right
		 * interface, and has the expected public constructor, throwing
		 * appropriate exceptions if not.
		 */
		validatorToCtor(rpv, PLJavaBasedLanguage.class);
	}

	@Override
	public RegProcedure<?> target()
	{
		return m_target;
	}

	@Override
	public TupleDescriptor inputsDescriptor() throws SQLException
	{
		return doInPG(() ->
		{
			if ( null != m_inputsDescriptor )
				return m_inputsDescriptor;

			TupleDescriptor tpl = m_target.inputsTemplate();
			BitSet unres = m_target.unresolvedInputs();
			if ( unres.isEmpty() ) // nothing to resolve, use template as-is
				return m_inputsDescriptor = tpl;

			int tplSize = tpl.size();
			int argSize = m_savedCall.nargs();
			ByteBuffer bb = // assert Integer.BYTES == SIZEOF_Oid
				ByteBuffer.allocateDirect(
					argSize * Integer.BYTES + ALIGNOF_INT - 1)
					.alignedSlice(ALIGNOF_INT).order(nativeOrder());
			tpl.stream()
				.map(Attribute::type)
				.mapToInt(RegType::oid)
				.forEachOrdered(bb::putInt);
			/*
			 * The buffer bb is argSize Oids long, with maybe fewer
			 * (just tplSize) written. See inputsAreSpread().
			 *
			 * Even though the C code won't be modifying this Bitmapset,
			 * OR in a guard bit anyway because, if empty, we would be
			 * violating PG's assumption that the only empty Bitmapset
			 * is a null one.
			 */
			int guardPos = unres.length();
			unres.set(guardPos);
			ByteBuffer unres_b = toBitmapset(unres);
			if ( ! _resolveArgTypes(
				m_savedCall.m_fcinfo, bb, unres_b, tplSize, argSize) )
				throw new SQLException(
					"failure resolving polymorphic argument types");
			unres.clear(guardPos);

			Identifier.Simple[] names = new Identifier.Simple [argSize];
			RegType[] types = new RegType[argSize];

			IntBuffer ib = bb.rewind().asIntBuffer();

			for ( int i = 0 ; i < argSize ; ++ i )
			{
				if ( i >= tplSize )
					names[i] = Identifier.None.INSTANCE;
				else
				{
					names[i] = tpl.get(i).name();
					if ( ! unres.get(i) )
					{
						types[i] = tpl.get(i).type();
						continue;
					}
				}
				types[i] = of(RegType.CLASSID, ib.get(i));
			}

			return m_inputsDescriptor =
				synthesizeDescriptor(
					List.of(types), List.of(names), null);
		});
	}

	@Override
	public TupleDescriptor outputsDescriptor() throws SQLException
	{
		return doInPG(() ->
		{
			if ( null != m_outputsDescriptor )
				return m_outputsDescriptor;

			BitSet unres = m_target.unresolvedOutputs();
			if ( null != unres  &&  unres.isEmpty() )
			{
				m_returnType = m_target.returnType();
				return m_outputsDescriptor = m_target.outputsTemplate();
			}

			/*
			 * Having a template TupleDescriptor already cached with the
			 * RegProcedure itself, and a BitSet identifying exactly
			 * which of its types call for resolution, evokes pleasing
			 * visions of somehow using the PostgreSQL API routines to
			 * resolve only the types needing it, and reusing the rest.
			 * But funcapi.h just really doesn't offer anything easy to
			 * use that way. It is ultimately simplest to just let
			 * get_call_result_type re-do the whole job from scratch
			 * and use that descriptor in place of the cached one.
			 * Still, we're able to skip doing any of that when the
			 * cached one requires no resolution, so there's that.
			 */
			int[] retOid = new int[1];
			TupleDescriptor td =
				_notionalCallResultType(m_savedCall.m_fcinfo, retOid);
			m_returnType = of(RegType.CLASSID, retOid[0]);

			/*
			 * The native method will have returned null for these cases:
			 * - Type resolved to VOID. No problem. Return null here.
			 * - Resolved type is not composite. Make an OfType descriptor here.
			 * - Type resolved to RECORD, no columns at call site. Bad.
			 */
			if ( null == td  &&  RegType.VOID != m_returnType )
			{
				if ( RegType.RECORD != m_returnType )
					td = new OfType(m_returnType);
				else
				{
					throw new SQLSyntaxErrorException(String.format(
						"RECORD-returning function %s called without the " +
						"required column-definition list following " +
						"the call", m_target), "42P18");
					/*
					 * ^^^ that can also happen if a polymorphic return type
					 * gets resolved to match an input for which a row type
					 * was passed whose tuple descriptor is not cataloged.
					 * (Interned is no good, because the polymorphic
					 * resolution does not keep track of the typmods.)
					 * For now, I'm just going to leave that gobbledygook
					 * out of the message. And maybe forever.
					 */
				}
			}
			return m_outputsDescriptor = td;
		});
	}

	@Override
	public boolean inputsAreSpread()
	{
		if ( ANYOID != m_target.variadicType().oid() )
			return false;
		return ! doInPG(() -> _get_fn_expr_variadic(m_savedCall.m_fcinfo));
	}

	@Override
	public BitSet stableInputs(BitSet ofInterest)
	{
		BitSet s = (BitSet)ofInterest.clone();

		/*
		 * Add one bit above the highest one of interest, which the C code
		 * will not touch. That ensures the C code will not be trying to grow
		 * or shrink the memory region, which isn't palloc'd, so that wouldn't
		 * go well.
		 */
		int guardPos = s.length();
		s.set(guardPos);
		ByteBuffer b = toBitmapset(s);
		doInPG(() -> _stableInputs(m_savedCall.m_fcinfo, b));
		s = fromBitmapset(b);
		s.clear(guardPos);
		return s;
	}

	/**
	 * The implementation of {@link Call}, encapsulating the information
	 * supplied by PostgreSQL in the per-call C struct.
	 */
	class CallImpl implements RegProcedure.Call
	{
		private final ByteBuffer m_fcinfo;
		private final ByteBuffer m_context;
		private final ByteBuffer m_resultinfo;

		/* mutable, accessed on the PG thread */
		private TupleTableSlot m_arguments;
		private Context m_contextImpl;
		private ResultInfo m_resultinfoImpl;

		private CallImpl(
			ByteBuffer fcinfo, ByteBuffer context, ByteBuffer resultinfo)
		{
			m_fcinfo = fcinfo.order(nativeOrder());
			m_context = asReadOnlyNativeOrder(context);
			m_resultinfo = asReadOnlyNativeOrder(resultinfo);
		}

		private void dispatch(Routine r) throws SQLException
		{
			r.call(this);

			/*
			 * For now, set isNull if the result would be interpreted as a
			 * reference. For a function, that means outputsDescriptor is other
			 * than one column wide (one column is treated as scalar by the
			 * function caller) or the one attribute isn't byValue. Otherwise,
			 * the temporary PG_RETURN_VOID in the C wrapper will be returning
			 * a non-null, zero result. That could be bogus for some arbitrary
			 * by-value type, but at least isn't an immediate bad dereference.
			 * The condition for procedures is simpler; they can only return
			 * VOID or RECORD, so if it isn't void, null it must be.
			 */
			TupleDescriptor td = outputsDescriptor();

			if ( null == td ) // declared as returning void; nothing to do
				return;

			if ( 1 != td.size()                      // it can't be a wrapper
				||  td.get(0).type() != m_returnType // or it isn't a wrapper
				||  ! m_returnType.byValue() )       // or wraps a by-ref type
				isNull(true);                        // avoid a dereference
		}

		private short nargs()
		{
			assert Short.BYTES == SIZEOF_fcinfo_nargs : "sizeof fcinfo nargs";
			return m_fcinfo.getShort(OFFSET_fcinfo_nargs);
		}

		@Override
		public RegProcedure.Lookup lookup()
		{
			return LookupImpl.this;
		}

		@Override
		public TupleTableSlot arguments() throws SQLException
		{
			return doInPG(() ->
			{
				if ( null == m_arguments )
				{
					TupleDescriptor td = inputsDescriptor();
					m_arguments = newNullableDatum(td, m_fcinfo
		/*
		 * Java 13: .slice(OFFSET_fcinfo_args,
		 *  m_fcinfo.capacity() - OFFSET_fcinfo_args).order(m_fcinfo.order())
		 */
						.duplicate().position(OFFSET_fcinfo_args)
						.slice().order(m_fcinfo.order())
					);
				}
				return m_arguments;
			});
		}

		@Override
		public TupleTableSlot result()
		{
			throw notyet();
		}

		@Override
		public void isNull(boolean nullness)
		{
			assert 1 == SIZEOF_fcinfo_isnull : "sizeof fcinfo isnull";
			doInPG(() ->
				m_fcinfo.put(
					OFFSET_fcinfo_isnull, nullness ? (byte)1 : (byte)0));
		}

		@Override
		public RegCollation collation()
		{
			assert Integer.BYTES == SIZEOF_fcinfo_fncollation
				: "sizeof fcinfo fncollation";
			int oid = m_fcinfo.getInt(OFFSET_fcinfo_fncollation);
			return of(RegCollation.CLASSID, oid);
		}

		@Override
		public Context context()
		{
			assert Integer.BYTES == SIZEOF_NodeTag : "sizeof NodeTag";
			return doInPG(() ->
			{
				if ( null != m_contextImpl )
					return m_contextImpl;

				if ( null == m_context )
					return null;

				int tag = m_context.getInt(0);

				if ( T_Invalid == tag )
					return null;
				if ( T_TriggerData == tag )
					return m_contextImpl = new TriggerDataImpl();
				if ( T_EventTriggerData == tag )
					return m_contextImpl = new EventTriggerDataImpl();
				if ( T_AggState == tag )
					return m_contextImpl = new AggStateImpl();
				if ( T_WindowAggState == tag )
					return m_contextImpl = new WindowAggStateImpl();
				if ( T_WindowObjectData == tag )
					return m_contextImpl = new WindowObjectImpl();
				if ( T_CallContext == tag )
					return m_contextImpl = new CallContextImpl();
				if ( T_ErrorSaveContext == tag )
					return m_contextImpl = new ErrorSaveContextImpl();
				return null;
			});
		}

		@Override
		public ResultInfo resultInfo()
		{
			return doInPG(() ->
			{
				if ( null != m_resultinfoImpl )
					return m_resultinfoImpl;

				if ( null == m_resultinfo )
					return null;

				int tag = m_resultinfo.getInt(0);

				if ( T_ReturnSetInfo == tag )
					return m_resultinfoImpl = new ReturnSetInfoImpl();
				return null;
			});
		}

		class TriggerDataImpl implements Context.TriggerData
		{
			/**
			 * When non-null, holds a {@code ByteBuffer} that windows the
			 * structurs at {@code *td_trigger}.
			 *<p>
			 * In a bit of an incestuous relationship, this will be set
			 * by the {@link #trigger trigger()} method below, which returns
			 * the corresponding instance of {@link TriggerDataImpl}. That
			 * class has a package-visible {@code withTriggerData} method that
			 * the caller can use to run client code in a scope throughout which
			 * that {@code Trigger} instance will be associated with this
			 * instance and can read from this {@code ByteBuffer}.
			 *<p>
			 * On exit of that scope, this field will be reset to null and the
			 * association between the {@code TriggerImpl} instance and this
			 * broken.
			 *<p>
			 * The dispatcher will execute client {@code specialize} and
			 * {@code apply} methods within such a scope, so those methods will
			 * see a {@code Trigger} instance that works, while avoiding thorny
			 * questions here about cache validity, since every call will use
			 * the structure freshly passed by PostgreSQL.
			 */
			ByteBuffer m_trigger;

			@Override
			public Called called()
			{
				assert Integer.BYTES == SIZEOF_TRGD_tg_event;
				int event = m_context.getInt(OFFSET_TRGD_tg_event);
				switch ( event & TRIGGER_EVENT_TIMINGMASK )
				{
					case TRIGGER_EVENT_BEFORE:
						return Called.BEFORE;
					case TRIGGER_EVENT_AFTER:
						return Called.AFTER;
					case TRIGGER_EVENT_INSTEAD:
						return Called.INSTEAD_OF;
					default:
						throw new
							AssertionError("unexpected TriggerData.called");
				}
			}

			@Override
			public Event event()
			{
				int event = m_context.getInt(OFFSET_TRGD_tg_event);
				switch ( event & TRIGGER_EVENT_OPMASK )
				{
					case TRIGGER_EVENT_INSERT:
						return Event.INSERT;
					case TRIGGER_EVENT_DELETE:
						return Event.DELETE;
					case TRIGGER_EVENT_UPDATE:
						return Event.UPDATE;
					case TRIGGER_EVENT_TRUNCATE:
						return Event.TRUNCATE;
					default:
						throw new
							AssertionError("unexpected TriggerData.event");
				}
			}

			@Override
			public Scope scope()
			{
				int event = m_context.getInt(OFFSET_TRGD_tg_event);
				if ( 0 != (event & TRIGGER_EVENT_ROW) )
					return Scope.ROW;
				return Scope.STATEMENT;
			}

			@Override
			public RegClass relation()
			{
				long r = fetchPointer(m_context, OFFSET_TRGD_tg_relation);
				ByteBuffer bb =
					mapFixedLength(r + OFFSET_Relation_rd_id, SIZEOF_Oid);
				int oid = bb.getInt(0);
				return of(RegClass.CLASSID, oid);
			}

			@Override
			public TupleTableSlot triggerTuple()
			{
				return doInPG(() ->
				{
					long t = fetchPointer(m_context, OFFSET_TRGD_tg_trigtuple);

					if ( 0 == t )
						return null;

					TupleDescriptor td = relation().tupleDescriptor();
					return
						heapTupleGetLightSlotNoFree(
							td, t, Invocation.current());
				});
			}

			@Override
			public TupleTableSlot newTuple()
			{
				return doInPG(() ->
				{
					long t = fetchPointer(m_context, OFFSET_TRGD_tg_newtuple);

					if ( 0 == t )
						return null;

					TupleDescriptor td = relation().tupleDescriptor();
					return
						heapTupleGetLightSlotNoFree(
							td, t, Invocation.current());
				});
			}

			@Override
			public Trigger trigger()
			{
				long t = fetchPointer(m_context, OFFSET_TRGD_tg_trigger);
				ByteBuffer bb = mapFixedLength(t, SIZEOF_Trigger);
				int oid = bb.getInt(OFFSET_TRG_tgoid);
				m_trigger = bb;
				return of(Trigger.CLASSID, oid);
			}

			@Override
			public Projection updatedColumns()
			{
				long t = fetchPointer(m_context, OFFSET_TRGD_tg_updatedcols);
				BitSet s = fromBitmapset(t);
				/*
				 * The PostgreSQL bitmapset representation for a zero-length set
				 * is null, so we think we got a zero-length set even for
				 * non-update trigger events. But an empty set of update columns
				 * is most likely a non-update event, so return null for that
				 * case rather than constructing an empty Projection.
				 */
				if ( 0 == s.length() )
					return null;
				/*
				 * Shifting out -FirstLowInvalidHeapAttributeNumber would
				 * yield a BitSet with SQLish one-based numbering. Shift out
				 * 1 - FirstLowInvalidHeapAttributeNumber to get a 0-based set.
				 */
				s = s.get(1 - FirstLowInvalidHeapAttributeNumber, s.length());
				return relation().tupleDescriptor().project(s);
			}
		}

		class EventTriggerDataImpl implements Context.EventTriggerData
		{
		}

		class AggStateImpl implements Context.AggState
		{
		}

		class WindowAggStateImpl implements Context.WindowAggState
		{
		}

		class WindowObjectImpl implements Context.WindowObject
		{
		}

		class CallContextImpl implements Context.CallContext
		{
			@Override
			public boolean atomic()
			{
				assert 1 == SIZEOF_CallContext_atomic;
				return 0 != m_context.get(OFFSET_CallContext_atomic);
			}
		}

		class ErrorSaveContextImpl implements Context.ErrorSaveContext
		{
		}

		class ReturnSetInfoImpl implements ResultInfo.ReturnSetInfo
		{
		}
	}

	private static class State
	extends DualState.SingleDeleteGlobalRefP<LookupImpl>
	{
		/**
		 * Constructs a {@code State} given the memory context that will bound
		 * its lifespan, and a pointer to the JNI global ref that must be freed
		 * at end of life. The caller passes the address of the {@code extra}
		 * struct, which therefore needs to have the JNI global ref as its first
		 * member.
		 */
		private State(LookupImpl referent, MemoryContext cxt, long globalRefP)
		{
			super(referent, cxt, globalRefP);
		}
	}

	private static void checkForTransforms(
		RegProcedure<PLJavaBased> p, Routines impl, boolean addlChecks)
	throws SQLException
	{
		List<RegType> types = p.transformTypes();
		if ( null == types )
			return;

		if ( ! ( impl instanceof UsingTransforms ) )
			throw new SQLSyntaxErrorException(String.format(
			"%s of %s does not implement TRANSFORM FOR TYPE",
			p.language(), p), "42P13");

		/*
		 * Check (as PostgreSQL doesn't) for multiple mentions of a type
		 * to transform, but only as an additionalCheck, at validation time.
		 * If a routine so declared sneaks past validation, we'll just do
		 * duplicative work at call time rather than checking for it then.
		 */
		if ( addlChecks )
		{
			List<RegType> dups = types.stream()
				.collect(groupingBy(t -> t, counting()))
				.entrySet().stream()
				.filter(e -> 1 < e.getValue())
				.map(e -> e.getKey())
				.collect(toList());
			if ( ! dups.isEmpty() )
				throw new SQLSyntaxErrorException(String.format(
					"%s mentions redundantly in TRANSFORM FOR TYPE: %s",
					p, dups), "42P13");
		}

		PLJavaMemo m = (PLJavaMemo)p.memo();

		/*
		 * m.transforms() will construct a list (and cache it, so it doesn't
		 * hurt much to do it here in advance) of transforms corresponding to
		 * the transformTypes. It can throw an SQLException if any needed
		 * transform can't be found, or if impl's essentialTransformChecks
		 * method throws one. Like most CatalogObject methods, transforms()
		 * will have wrapped that in an UncheckedException, but we unwrap it
		 * here if it happens. When the language handler calls transforms(), it
		 * should be getting a cached value so an exception would be unexpected.
		 */
		try
		{
			m.transforms();
		}
		catch ( UncheckedException e )
		{
			Throwable t = e.unwrap();
			if ( t instanceof SQLException )
				throw (SQLException)t;
			throw e;
		}
	}

	private static native void _cacheReference(LookupImpl instance, long extra);

	private static native boolean _get_fn_expr_variadic(ByteBuffer fcinfo);

	private static native void _stableInputs(
		ByteBuffer fcinfo, ByteBuffer bits);

	private static native TupleDescriptor _notionalCallResultType(
		ByteBuffer fcinfo, int[] returnTypeOid);

	private static native boolean _resolveArgTypes(
		ByteBuffer fcinfo, ByteBuffer types, ByteBuffer unresolvedBitmap,
		int tplSz, int argSz);

	@Native private static final int OFFSET_CallContext_atomic       = 4;
	@Native private static final int SIZEOF_CallContext_atomic       = 1;

	@Native private static final int SIZEOF_TRGD_tg_event            = 4;

	@Native private static final int TRIGGER_EVENT_INSERT            = 0;
	@Native private static final int TRIGGER_EVENT_DELETE            = 1;
	@Native private static final int TRIGGER_EVENT_UPDATE            = 2;
	@Native private static final int TRIGGER_EVENT_TRUNCATE          = 3;
	@Native private static final int TRIGGER_EVENT_OPMASK            = 3;
	@Native private static final int TRIGGER_EVENT_ROW               = 4;
	@Native private static final int TRIGGER_EVENT_BEFORE            = 8;
	@Native private static final int TRIGGER_EVENT_AFTER             = 0;
	@Native private static final int TRIGGER_EVENT_INSTEAD           = 0x10;
	@Native private static final int TRIGGER_EVENT_TIMINGMASK        = 0x18;

	@Native private static final int FirstLowInvalidHeapAttributeNumber = -7;
}
