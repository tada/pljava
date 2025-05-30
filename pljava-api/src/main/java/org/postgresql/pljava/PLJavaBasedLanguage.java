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
package org.postgresql.pljava;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

import java.util.List;

import org.postgresql.pljava.model.ProceduralLanguage; // javadoc
import org.postgresql.pljava.model.ProceduralLanguage.PLJavaBased;
import org.postgresql.pljava.model.RegProcedure;
import org.postgresql.pljava.model.RegProcedure.Call;
import org.postgresql.pljava.model.RegProcedure.Call.Context.TriggerData; // jvd
import org.postgresql.pljava.model.RegProcedure.Lookup;
import org.postgresql.pljava.model.RegType;
import org.postgresql.pljava.model.Transform;
import org.postgresql.pljava.model.Trigger;
import org.postgresql.pljava.model.Trigger.ForTrigger;
import org.postgresql.pljava.model.TupleTableSlot; // javadoc

import org.postgresql.pljava.annotation.Trigger.Called; // javadoc
import org.postgresql.pljava.annotation.Trigger.Event; // javadoc
import org.postgresql.pljava.annotation.Trigger.Scope; // javadoc

/**
 * Interface for a procedural language on PL/Java infrastructure.
 *<p>
 * An implementing class does not implement this interface directly, but rather
 * implements one or both of the subinterfaces {@link InlineBlocks InlineBlocks}
 * and {@link Routines Routines}. A language that implements {@code Routines}
 * may also implement one or more of: {@link Triggers Triggers},
 * {@link UsingTransforms UsingTransforms}. The implementing class must
 * have a public constructor with a
 * {@link ProceduralLanguage ProceduralLanguage} parameter, which it may ignore,
 * or use to determine the name, oid, accessibility, or other details of the
 * declared PostgreSQL language the handler class has been instantiated for.
 */
public interface PLJavaBasedLanguage
{
	/**
	 * To be implemented by a language that supports routines (that is,
	 * functions and/or procedures).
	 *<p>
	 * Whether a routine is a function or procedure can be determined at
	 * validation time ({@code subject.}{@link RegProcedure#kind() kind()} in
	 * {@link #essentialChecks essentialChecks} or
	 * {@link #additionalChecks additionalChecks}) or at
	 * {@link #prepare prepare} time
	 * (({@code target.}{@link RegProcedure#kind() kind()}).
	 * A procedure can also be distinguished from a function in that,
	 * at {@link Routine#call Routine.call(fcinfo)} time, if a procedure is
	 * being called, {@code fcinfo.}{@link Call#context() context()} returns
	 * an instance of {@link Call.Context.CallContext CallContext}.
	 *<h2>Transaction control</h2>
	 *<p>
	 * A function is always called within an existing transaction; while it may
	 * use subtransactions / savepoints, it can never commit, roll back, or
	 * start a new top-level transaction.
	 *<p>
	 * A procedure is allowed to start, commit, and roll back top-level
	 * transactions, provided it was not called inside an existing explicit
	 * transation. That condition can be checked by consulting
	 * {@link Call.Context.CallContext#atomic() CallContext.atomic()} when
	 * {@code fcinfo.context()} returns an instance of {@code CallContext}.
	 * When {@code atomic()} returns {@code true}, transaction control is not
	 * allowed. (If {@code fcinfo.context()} returns anything other than an
	 * instance of {@code CallContext}, this is not a procedure call, and
	 * transaction control is never allowed.)
	 *<p>
	 * A handler may use this information to impose its own (for example,
	 * compile-time) limits on a routine's access to transaction-control
	 * operations. Any use of SPI by the routine will be appropriately limited
	 * with no need for attention from the handler, as PL/Java propagates the
	 * atomic/nonatomic flag to SPI always.
	 */
	public interface Routines extends PLJavaBasedLanguage
	{
		/**
		 * Performs the essential validation checks on a proposed
		 * PL/Java-based routine.
		 *<p>
		 * This method should check (when <var>checkBody</var> is true) all the
		 * essential conditions that {@link #prepare prepare} may assume have
		 * been checked. Because there is no guarantee that validation at
		 * routine-creation time always occurred, PL/Java's dispatcher will not
		 * only call this method at validation time, but also will never call
		 * {@code prepare} without making sure this method (passing true for
		 * <var>checkBody</var>) has been called first.
		 *<p>
		 * This method should throw an informative exception for any check that
		 * fails, otherwise returning normally. Unless there is a more-specific
		 * choice, {@link SQLSyntaxErrorException} with {@code SQLState}
		 * {@code 42P13} corresponds to PostgreSQL's
		 * {@code invalid_function_definition}.
		 *<p>
		 * Checks that are helpful at routine-creation time, but not essential
		 * to correctness of {@code prepare}, can be made in
		 * {@link #additionalChecks additionalChecks}.
		 *<p>
		 * The dispatcher will never invoke this method for a <var>subject</var>
		 * with {@link RegProcedure#returnsSet returnsSet()} true, so this
		 * method may assume that property is false, unless the language also
		 * implements {@link ReturningSets ReturningSets} and the
		 * {@link ReturningSets#essentialSRFChecks essentialSRFChecks} method
		 * delegates to this one (as its default implementation does).
		 *<p>
		 * If <var>checkBody</var> is false, less-thorough checks may be
		 * needed. The details are left to the language implementation;
		 * in general, basic checks of syntax, matching parameter counts, and
		 * so on are ok, while checks that load or compile user code or depend
		 * on other database state may be better avoided. The validator may be
		 * invoked with <var>checkBody</var> false at times when not all
		 * expected state may be in place, such as during {@code pg_restore}
		 * or {@code pg_upgrade}.
		 *<p>
		 * This method is invoked with <var>checkBody</var> false only if the
		 * JVM has been started and PL/Java has already loaded and instantiated
		 * this language-handler class, or succeeds in doing so. If not, and
		 * <var>checkBody</var> is false, PL/Java simply treats the validation
		 * as successful.
		 *<p>
		 * This default implementation checks nothing.
		 */
		default void essentialChecks(
			RegProcedure<?> subject, PLJavaBased memo, boolean checkBody)
		throws SQLException
		{
		}

		/**
		 * Performs additional validation checks on a proposed PL/Java-based
		 * routine.
		 *<p>
		 * This method should be used for checks that may give helpful feedback
		 * at routine-creation time, but can be skipped at run time because the
		 * correct behavior of {@link #prepare prepare} does not depend on them.
		 * PL/Java calls this method only at routine-creation time, just after
		 * {@link #essentialChecks essentialChecks} has completed normally.
		 *<p>
		 * This method should throw an informative exception for any check that
		 * fails, otherwise returning normally. Unless there is a more-specific
		 * choice, {@link SQLSyntaxErrorException} with {@code SQLState}
		 * {@code 42P13} corresponds to PostgreSQL's
		 * {@code invalid_function_definition}.
		 *<p>
		 * Checks of conditions essential to correctness of {@code prepare}
		 * must be made in {@code essentialChecks}.
		 *<p>
		 * The dispatcher will never invoke this method for a <var>subject</var>
		 * with {@link RegProcedure#returnsSet returnsSet()} true, so this
		 * method may assume that property is false, unless the language also
		 * implements {@link ReturningSets ReturningSets} and the
		 * {@link ReturningSets#additionalSRFChecks additionalSRFChecks} method
		 * delegates to this one (as its default implementation does).
		 *<p>
		 * If <var>checkBody</var> is false, less-thorough checks may be
		 * needed. The details are left to the language implementation;
		 * in general, basic checks of syntax, matching parameter counts, and
		 * so on are ok, while checks that load or compile user code or depend
		 * on other database state may be better avoided. The validator may be
		 * invoked with <var>checkBody</var> false at times when not all
		 * expected state may be in place, such as during {@code pg_restore}
		 * or {@code pg_upgrade}.
		 *<p>
		 * This default implementation checks nothing.
		 */
		default void additionalChecks(
			RegProcedure<?> subject, PLJavaBased memo, boolean checkBody)
		throws SQLException
		{
		}

		/**
		 * Prepares a template for a call of the routine <var>target</var>.
		 *<p>
		 * This method is never called without
		 * {@link #essentialChecks essentialChecks} having been called
		 * immediately prior and completing normally.
		 *<p>
		 * The information available at this stage comes from the system
		 * catalogs, reflecting the static declaration of the target routine.
		 * The methods of <var>target</var> can be used to examine that
		 * catalog information; the {@link PLJavaBased PLJavaBased}
		 * <var>memo</var> holds additional derived information,
		 * including tuple descriptors for the inputs and outputs.
		 * (All routines, including those treated by PostgreSQL as
		 * returning a scalar result, are presented to a PL/Java handler with
		 * the inputs and outputs represented by {@link TupleTableSlot}.)
		 * The tuple descriptors seen at this stage may include attributes with
		 * polymorphic types, not resolvable to specific types until the
		 * {@code Template} instance this method returns is later applied at
		 * an actual call site.
		 *<p>
		 * This method is never called for a <var>target</var> with
		 * {@link RegProcedure#returnsSet returnsSet()} true. If the language
		 * also implements {@link ReturningSets ReturningSets}, any such
		 * <var>target</var> will be passed to the
		 * {@link ReturningSets#prepareSRF prepareSRF} method instead;
		 * otherwise, it will incur an exception stating the language does not
		 * support returning sets.
		 *<p>
		 * This method should return a {@link Template Template}, which may
		 * encapsulate any useful precomputed values based on the catalog
		 * information this method consulted.
		 *<p>
		 * The template, when its {@link Template#specialize specialize} method
		 * is invoked on an actual {@link Lookup Lookup} instance, should return
		 * a {@link Routine Routine} able to apply the target function's logic
		 * when invoked any number of times on {@link Call Call} instances
		 * associated with the same {@code Lookup}.
		 *<p>
		 * When there is no polymorphic or variadic-"any" funny business in
		 * <var>target</var>'s declaration, this method may return a
		 * {@code Template} that ignores its argument and always returns the
		 * same {@code Routine}. It could even do so in all cases, if
		 * implementing a language where those dynamic details are left to user
		 * code.
		 */
		Template prepare(RegProcedure<?> target, PLJavaBased memo)
		throws SQLException;
	}

	/**
	 * To be implemented by a language that can be used to write functions
	 * returning sets (that is, more than a single result or row).
	 */
	public interface ReturningSets extends PLJavaBasedLanguage
	{
		/**
		 * Performs the essential validation checks on a proposed
		 * PL/Java-based set-returning function.
		 *<p>
		 * See {@link Routines#essentialChecks essentialChecks} for
		 * the explanation of what to consider 'essential' checks.
		 *<p>
		 * This default implementation simply delegates to the
		 * {@link Routines#essentialChecks essentialChecks} method, which must
		 * therefore be prepared for <var>subject</var> to have either value of
		 * {@link RegProcedure#returnsSet returnsSet()}.
		 */
		default void essentialSRFChecks(
			RegProcedure<?> subject, PLJavaBased memo, boolean checkBody)
		throws SQLException
		{
			/*
			 * A cast, because the alternative of having SetReturning extend
			 * Routines would allow Routines to be omitted from the implements
			 * clause of a language handler, which I would rather not encourage
			 * as a matter of style.
			 */
			((Routines)this).essentialChecks(subject, memo, checkBody);
		}

		/**
		 * Performs additional validation checks on a proposed
		 * PL/Java-based set-returning function.
		 *<p>
		 * See {@link Routines#additionalChecks additionalChecks} for
		 * the explanation of what to consider 'additional' checks.
		 *<p>
		 * This default implementation simply delegates to the
		 * {@link Routines#additionalChecks additionalChecks} method, which must
		 * therefore be prepared for <var>subject</var> to have either value of
		 * {@link RegProcedure#returnsSet returnsSet()}.
		 */
		default void additionalSRFChecks(
			RegProcedure<?> subject, PLJavaBased memo, boolean checkBody)
		throws SQLException
		{
			((Routines)this).additionalChecks(subject, memo, checkBody);
		}

		/**
		 * Prepares a template for a call of the set-returning function
		 * <var>target</var>.
		 *<p>
		 * This method is never called without
		 * {@link #essentialSRFChecks essentialSRFChecks} having been called
		 * immediately prior and completing normally.
		 *<p>
		 * This method is analogous to the
		 * {@link Routines#prepare prepare} method, but is called only for
		 * a <var>target</var> with {@link RegProcedure#returnsSet returnsSet()}
		 * true, and must return {@link SRFTemplate SRFTemplate} rather than
		 * {@link Template Template}.
		 *<p>
		 * The documentation of the {@link Routines#prepare prepare} method
		 * further describes what is expected of an implementation.
		 */
		SRFTemplate prepareSRF(RegProcedure<?> target, PLJavaBased memo)
		throws SQLException;
	}

	/**
	 * To be implemented by a language that supports triggers.
	 *<p>
	 * The methods of this interface will be called, instead of those declared
	 * in {@link Routines Routines}, for any function declared with return type
	 * {@link RegType#TRIGGER TRIGGER}. If a language does not implement
	 * this interface, any attempt to validate or use such a function will incur
	 * an exception.
	 */
	public interface Triggers extends PLJavaBasedLanguage
	{
		/**
		 * Performs the essential validation checks on a proposed
		 * trigger function.
		 *<p>
		 * See {@link Routines#essentialChecks Routines.essentialChecks} for
		 * details on what to check here.
		 *<p>
		 * Any <var>subject</var> passed to this method is already known to be
		 * a function with no declared parameters and a non-set return type of
		 * {@link RegType#TRIGGER TRIGGER}.
		 *<p>
		 * This default implementation checks nothing further.
		 */
		default void essentialTriggerChecks(
			RegProcedure<ForTrigger> subject, PLJavaBased memo,
			boolean checkBody)
		throws SQLException
		{
		}

		/**
		 * Performs additional validation checks on a proposed trigger function.
		 *<p>
		 * See {@link Routines#additionalChecks Routines.additionalChecks} for
		 * details on what to check here.
		 *<p>
		 * Any <var>subject</var> passed to this method is already known to be
		 * a function with no declared parameters and a non-set return type of
		 * {@link RegType#TRIGGER TRIGGER}, and to have passed the
		 * {@link #essentialTriggerChecks essentialTriggerChecks}.
		 *<p>
		 * This default implementation checks nothing further.
		 */
		default void additionalTriggerChecks(
			RegProcedure<ForTrigger> subject, PLJavaBased memo,
			boolean checkBody)
		throws SQLException
		{
		}

		/**
		 * Prepares a template for a call of the trigger function
		 * <var>target</var>.
		 *<p>
		 * This method is never called without
		 * {@link #essentialTriggerChecks essentialTriggerChecks} having
		 * been called immediately prior and completing normally.
		 *<p>
		 * See {@link Routines#prepare Routines.prepare} for background on
		 * what to do here.
		 *<p>
		 * This method should return a {@link TriggerTemplate TriggerTemplate},
		 * which may encapsulate any useful precomputed values based on
		 * the catalog information this method consulted.
		 *<p>
		 * Any <var>target</var> passed to this method is already known to be
		 * a function with no declared parameters and a non-set return type of
		 * {@link RegType#TRIGGER TRIGGER}, and to have passed the
		 * {@link #essentialTriggerChecks essentialTriggerChecks}.
		 *<p>
		 * The template, when its {@link TriggerTemplate#specialize specialize}
		 * method is invoked on a {@link Trigger Trigger} instance, should
		 * return a {@link TriggerFunction TriggerFunction} that can be invoked
		 * on a {@link TriggerData TriggerData} instance.
		 *<p>
		 * The template may generate a {@code TriggerFunction} that encapsulates
		 * specifics of the {@code Trigger} such as its target table, name,
		 * arguments, enabled events, scope, and columns of interest.
		 * A {@code TriggerFunction} will not be invoked for any trigger except
		 * the one passed to the {@code specialize} call that returned it.
		 */
		TriggerTemplate prepareTrigger(
			RegProcedure<ForTrigger> target, PLJavaBased memo)
		throws SQLException;
	}

	/**
	 * To be implemented by a language that supports routines declared with
	 * {@code TRANSFORM FOR TYPE}.
	 *<p>
	 * In addition to implementing the abstract method declared here, a language
	 * that implements this interface takes up full responsibility  for doing
	 * whatever must be done to give effect to any such transforms declared on
	 * routines that use the language. PostgreSQL itself provides nothing but
	 * a way to declare transforms and associate them with routine declarations.
	 *<p>
	 * PL/Java will reject, at validation time when possible, any routine
	 * declared with {@code TRANSFORM FOR TYPE} if the language does not
	 * implement this interface.
	 *<p>
	 * A language that does implement this interface can learn
	 * what transforms are to be applied by calling
	 * {@link PLJavaBased#transforms() memo.transforms()} in its
	 * {@link Routines#prepare prepare} and/or
	 * {@link Triggers#prepareTrigger prepareTrigger} methods, and perhaps
	 * also in its validation methods to detect configuration issues as early
	 * as possible.
	 */
	public interface UsingTransforms extends PLJavaBasedLanguage
	{
		/**
		 * Performs validation checks on a {@link Transform} that purports to be
		 * usable with this language.
		 *<p>
		 * PL/Java will already have checked that <var>t</var>'s
		 * {@link Transform#language() language()} refers to this language.
		 * This method should use best effort to make sure that <var>t</var>'s
		 * {@link Transform#fromSQL() fromSQL()} and
		 * {@link Transform#toSQL() toSQL()} functions are, in fact, functions
		 * that this language implementation can use to transform values between
		 * <var>t</var>'s target PostgreSQL {@link Transform#type() type()} and
		 * a data type available to this language. See documentation of the
		 * {@link Transform#fromSQL() fromSQL()} and
		 * {@link Transform#toSQL() toSQL()} methods for more detail on what may
		 * need to be checked.
		 *<p>
		 * It is possible for {@link Transform#fromSQL() fromSQL()}
		 * or {@link Transform#toSQL() toSQL()} to return
		 * a {@code RegProcedure} instance for which
		 * {@link RegProcedure#isValid() isValid()} is false, which indicates
		 * that this language's default from-SQL or to-SQL handling,
		 * respectively, is to be used for the transform's
		 * {@linkplain Transform#type() type}. In such cases, this method should
		 * check that this language has a usable default conversion in the
		 * indicated direction for that type.
		 *<p>
		 * This method should return normally on success, otherwise throwing
		 * an informative exception. Unless there is a more-specific
		 * choice, {@link SQLSyntaxErrorException} with {@code SQLState}
		 * {@code 42P17} corresponds to PostgreSQL's
		 * {@code invalid_object_definition}.
		 */
		void essentialTransformChecks(Transform t) throws SQLException;
	}

	/**
	 * To be implemented by a language that supports inline code blocks.
	 *<h2>Transaction control</h2>
	 *<p>
	 * A {@code DO} block is allowed to start, commit, and roll back top-level
	 * transactions, as long as it was not invoked inside an existing explicit
	 * transaction. The <var>atomic</var> parameter passed to
	 * {@link #execute execute} will be {@code true} if transaction control
	 * is disallowed.
	 *<p>
	 * A handler may use this information to impose its own (for example,
	 * compile-time) limits on the availability of transaction-control
	 * operations. Any use of SPI by the code block will be appropriately
	 * limited with no need for attention from the handler, as PL/Java
	 * propagates the atomic/nonatomic flag to SPI always.
	 */
	public interface InlineBlocks extends PLJavaBasedLanguage
	{
		/**
		 * Parses and executes an inline code block.
		 * @param source_text the inline code to be parsed and executed
		 * @param atomic true if transaction control actions must be disallowed
		 * within the code block
		 */
		void execute(String source_text, boolean atomic) throws SQLException;
	}

	/**
	 * The result of a {@link Template#specialize specialize} call on
	 * a {@link Template Template}.
	 *<p>
	 * An instance can incorporate whatever can be precomputed based on the
	 * resolved parameter types and other information available to
	 * {@code specialize}. Its {@link #call call} method will then be invoked to
	 * supply the arguments and produce the results for each call made
	 * at that call site.
	 */
	@FunctionalInterface
	public interface Routine
	{
		/**
		 * Actually executes the prepared and specialized {@code Routine}, using
		 * the arguments and other call-specific information passed in
		 * {@code fcinfo}.
		 *<p>
		 * Various special cases of routine calls (triggers, procedure calls,
		 * and so on) can be distinguished by the specific subtypes of
		 * {@link Call.Context} that may be returned by
		 * {@code fcinfo.}{@link Call#context() context()}.
		 */
		void call(Call fcinfo) throws SQLException;
	}

	/**
	 * The result of a {@link TriggerTemplate#specialize specialize} call on
	 * a {@link TriggerTemplate TriggerTemplate}.
	 *<p>
	 * An instance can incorporate whatever can be precomputed based on the
	 * specific {@link Trigger Trigger} that was passed to {@code specialize}.
	 * Its {@link #apply apply} method will then be invoked to act on
	 * the {@link TriggerData TriggerData} and produce the results each time
	 * that trigger fires.
	 */
	@FunctionalInterface
	public interface TriggerFunction
	{
		/**
		 * Actually executes the prepared and specialized
		 * {@code TriggerFunction}, with the triggering data available in
		 * <var>triggerData</var>.
		 *<p>
		 * The return value, ignored for an {@link Called#AFTER AFTER} trigger,
		 * and restricted to null for any
		 * {@link Called#BEFORE BEFORE} {@link Scope#STATEMENT STATEMENT}
		 * trigger, can influence the triggering operation for other types
		 * of triggers. To permit the operation with no changes by the trigger,
		 * return exactly {@link TriggerData#triggerTuple triggerTuple} (for
		 * a trigger on {@link Event#INSERT INSERT} or
		 * {@link Event#DELETE DELETE}), or exactly
		 * {@link TriggerData#newTuple newTuple} (for a trigger on
		 * {@link Event#UPDATE UPDATE}). To suppress the triggering operation,
		 * return null.
		 * @return a TupleTableSlot, or null
		 */
		TupleTableSlot apply(TriggerData triggerData) throws SQLException;
	}

	/**
	 * The result of a {@link Routines#prepare prepare} call on a PL/Java-based
	 * routine.
	 *<p>
	 * An instance should depend only on the static catalog information for the
	 * routine as passed to {@code prepare}, and may encapsulate any values that
	 * can be precomputed from that information alone. Its
	 * {@link #specialize specialize} method will be called, passing information
	 * specific to a call site, to obtain a {@link Routine Routine}.
	 */
	@FunctionalInterface
	public interface Template
	{
		/**
		 * Given the information present at a particular call site, specialize
		 * this template into a {@link Routine Routine} that will handle calls
		 * through this call site.
		 *<p>
		 * Typical activities for {@code specialize} would be to consult
		 * <var>flinfo</var>'s {@link Lookup#inputsDescriptor inputsDescriptor}
		 * and {@link Lookup#outputsDescriptor outputsDescriptor} for the number
		 * and types of the expected input and output parameters, though it is
		 * unnecessary if the tuple descriptors obtained at
		 * {@link Routines#prepare prepare} time included no unresolved types.
		 * The {@link Lookup#inputsAreSpread inputsAreSpread} method should be
		 * consulted if the routine has a variadic parameter of the wildcard
		 * {@code "any"} type.
		 */
		Routine specialize(Lookup flinfo) throws SQLException;
	}

	/**
	 * Superinterface for the result of a
	 * {@link ReturningSets#prepareSRF prepareSRF} call on a PL/Java-based
	 * set-returning function.
	 *<p>
	 * An instance returned by {@link ReturningSets#prepareSRF prepareSRF} must
	 * implement at least one of the member subinterfaces. If it implements
	 * more than one, it will need to override the {@link #negotiate negotiate}
	 * method to select the behavior to be used at a given call site.
	 */
	public interface SRFTemplate
	{
		/**
		 * Returns the index of a preferred subinterface of {@code SRFTemplate}
		 * among a list of those the caller supports.
		 *<p>
		 * The list is ordered with a caller's more-preferred choices early.
		 *<p>
		 * An implementation could simply return the first index of an
		 * <var>allowed</var> class <var>C</var> such that
		 * {@code this instanceof C} to use the caller's preferred method
		 * always, or could make a choice informed by characteristics of
		 * the template.
		 * @return the index within <var>allowed</var> of the interface to be
		 * used at this call site, or -1 if no interface in <var>allowed</var>
		 * is supported.
		 */
		int negotiate(List<Class<? extends SRFTemplate>> allowed);

		/**
		 * An {@code SRFTemplate} subinterface that can generate
		 * a specialization returning the set result materialized in
		 * a {@code Tuplestore}.
		 */
		interface Materialize extends SRFTemplate
		{
			/**
			 * {@inheritDoc}
			 *<p>
			 * This default implementation simply returns
			 * {@code allowed.indexOf(Materialize.class)}.
			 */
			@Override
			default int negotiate(List<Class<? extends SRFTemplate>> allowed)
			{
				return allowed.indexOf(Materialize.class);
			}
		}

		/**
		 * An {@code SRFTemplate} subinterface that can generate
		 * a specialization returning the set result in a series of calls
		 * each returning one value or row.
		 */
		interface ValuePerCall extends SRFTemplate
		{
			/**
			 * {@inheritDoc}
			 *<p>
			 * This default implementation simply returns
			 * {@code allowed.indexOf(ValuePerCall.class)}.
			 */
			@Override
			default int negotiate(List<Class<? extends SRFTemplate>> allowed)
			{
				return allowed.indexOf(ValuePerCall.class);
			}

			SRFFirst specializeValuePerCall(Lookup flinfo) throws SQLException;
		}
	}

	/**
	 * The result of a {@link SRFTemplate.ValuePerCall#specializeValuePerCall
	 * specializeValuePerCall} call on an {@link SRFTemplate SRFTemplate}.
	 *<p>
	 * An instance can incorporate whatever can be precomputed based on the
	 * resolved parameter types and other information available to
	 * {@code specializeValuePerCall}. Its {@link #firstCall firstCall} method
	 * will then be invoked, for each call made at that call site, to supply the
	 * arguments and obtain an instance of {@link SRFNext SRFNext} whose
	 * {@link SRFNext#nextResult nextResult} method will be called, as many
	 * times as needed, to retrieve all rows of the result.
	 */
	@FunctionalInterface
	public interface SRFFirst
	{
		/**
		 * Executes the prepared and specialized {@code SRFFirst} code, using
		 * the arguments and other call-specific information passed in
		 * {@code fcinfo} and returns an instance of {@link SRFNext SRFNext}
		 * to produce a result set row by row.
		 *<p>
		 * This method should not access <var>fcinfo</var>'s
		 * {@link RegProcedure.Call#result result} or
		 * {@link RegProcedure.Call#isNull isNull} methods to return any value,
		 * but should return an instance of {@code SRFNext} that will do so.
		 */
		SRFNext firstCall(Call fcinfo) throws SQLException;
	}

	/**
	 * The result of a {@link SRFFirst#firstCall firstCall} call on an instance
	 * of {@link SRFFirst SRFFirst}.
	 *<p>
	 * The {@link #nextResult nextResult} method will be called repeatedly
	 * as long as its return value indicates another row may follow, unless
	 * PostgreSQL earlier determines no more rows are needed.
	 *<p>
	 * The {@link #close close} method will be called after the last call of
	 * {@code nextResult}, whether because all rows have been read or because
	 * PostgreSQL has read all it needs. It is not called, however,
	 * if {@code nextResult} has returned {@link Result#SINGLE Result.SINGLE}.
	 *
	 */
	public interface SRFNext extends AutoCloseable
	{
		/**
		 * Called when PostgreSQL will be making no further calls of
		 * {@link #nextResult nextResult} for this result set, which may be
		 * before all results have been fetched.
		 *<p>
		 * When a degenerate single-row set is returned (as indicated by
		 * {@link #nextResult nextResult} returning
		 * {@link Result#SINGLE Result.SINGLE}), this method is not called.
		 */
		void close();

		/**
		 * Called to return a single result.
		 *<p>
		 * As with non-set-returning routines, this method should store result
		 * values into {@link RegProcedure.Call#result fcinfo.result()} or set
		 * {@link RegProcedure.Call#isNull fcinfo.isNull(true)} (which, in this
		 * context, produces a row of all nulls). If there is no result
		 * to store, the method should return {@link Result#END Result.END}:
		 * no row will be produced, and the result set is considered complete.
		 *<p>
		 * If the method has exactly one row to return, it may store the values
		 * and return {@link Result#SINGLE Result.SINGLE}: the result will be
		 * considered to be just that one row. None of the rest of the
		 * set-returning protocol will be involved, and
		 * {@link SRFNext#close close()} will not be called.
		 *<p>
		 * Otherwise, the method should return
		 * {@link Result#MULTIPLE Result.MULTIPLE} after storing each row, and
		 * conclude by returning {@link Result#END Result.END} from the final
		 * call (without storing anything).
		 *<p>
		 * It is a protocol violation to return
		 * {@link Result#SINGLE Result.SINGLE} from any but the very first call.
		 *<p>
		 * The arguments in
		 * {@link RegProcedure.Call#arguments fcinfo.arguments()} will not be
		 * changing as the rows of a single result are retrieved. Any argument
		 * values that will be referred to repeatedly may be worth fetching once
		 * in the {@link SRFFirst#firstCall firstCall} method and their Java
		 * representations captured in this object, rather than fetching and
		 * converting them repeatedly.
		 */
		Result nextResult(Call fcinfo) throws SQLException;

		/**
		 * Used to indicate the state of the result sequence on return from
		 * a single call in the {@code ValuePerCall} protocol.
		 */
		enum Result
		{
			/**
			 * There is exactly one row and this call has returned it.
			 *<p>
			 * None of the rest of the set-returning protocol will be involved,
			 * and {@link SRFNext#close close()} will not be called.
			 */
			SINGLE,

			/**
			 * This call has returned one of possibly multiple rows, and
			 * another call should be made to retrieve the next row if any.
			 */
			MULTIPLE,

			/**
			 * This call has no row to return and the result sequence
			 * is complete.
			 */
			END
		}
	}

	/**
	 * The result of a {@link Triggers#prepareTrigger prepareTrigger} call on
	 * a PL/Java-based trigger function.
	 *<p>
	 * An instance should depend only on the static catalog information for the
	 * function as passed to {@code prepareTrigger}, and may encapsulate any
	 * values that can be precomputed from that information alone. Its
	 * {@link #specialize specialize} method will be called, passing information
	 * specific to one trigger, to obtain a
	 * {@link TriggerFunction TriggerFunction}.
	 */
	@FunctionalInterface
	public interface TriggerTemplate
	{
		/**
		 * Given the specifics of one {@link Trigger Trigger}, specialize
		 * this template into a {@link TriggerFunction TriggerFunction} that
		 * will handle calls through this trigger.
		 *<p>
		 * Typical activities for {@code specialize} would be to consult
		 * <var>trigger</var>'s {@link Trigger#name name},
		 * {@link Trigger#relation relation}, {@link Trigger#called called},
		 * {@link Trigger#events events}, {@link Trigger#scope scope},
		 * {@link Trigger#arguments arguments}, and
		 * {@link Trigger#columns columns} to
		 * determine the kind of trigger it is, and fold those values into
		 * the returned {@code TriggerFunction}.
		 *<p>
		 * This stage is well suited for checking that the characteristics of
		 * the trigger (events, scope, when called, arguments, column types of
		 * the target table) conform to what the trigger function can handle.
		 */
		TriggerFunction specialize(Trigger trigger) throws SQLException;
	}
}
