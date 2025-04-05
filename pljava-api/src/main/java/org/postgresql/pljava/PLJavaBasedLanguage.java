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

import org.postgresql.pljava.model.ProceduralLanguage; // javadoc
import org.postgresql.pljava.model.ProceduralLanguage.PLJavaBased;
import org.postgresql.pljava.model.RegProcedure;
import org.postgresql.pljava.model.RegProcedure.Call;
import org.postgresql.pljava.model.RegProcedure.Call.Context.TriggerData; // jvd
import org.postgresql.pljava.model.RegProcedure.Lookup;
import org.postgresql.pljava.model.RegType;
import org.postgresql.pljava.model.Trigger;
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
 * may also implement {@link Triggers Triggers}. The implementing class must
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
		 * essential conditions that {@link #prepare prepare} will assume have
		 * been checked. Because there is no guarantee that validation at
		 * routine-creation time always occurred, PL/Java's dispatcher will call
		 * this method not only at validation time, but also just before
		 * {@code prepare} is first called at run time (always passing true for
		 * <var>checkBody</var> in that case).
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
		 * the implementing class, or succeeds in doing so. If not, and
		 * <var>checkBody</var> is false, PL/Java simply treats the validation
		 * as successful.
		 *<p>
		 * This default implementation checks nothing.
		 */
		default void essentialChecks(
			RegProcedure<PLJavaBased> subject, boolean checkBody)
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
			RegProcedure<PLJavaBased> subject, boolean checkBody)
		throws SQLException
		{
		}

		/**
		 * Prepares a template for a call of the routine <var>target</var>.
		 *<p>
		 * The information available at this stage comes from the system
		 * catalogs, reflecting the static declaration of the target routine.
		 * The methods of <var>target</var> can be used to examine that
		 * catalog information; the {@link RegProcedure#memo memo} method
		 * retrieves a PL/Java-specific {@link PLJavaBased memo} with some
		 * derived information, including tuple descriptors for the inputs and
		 * outputs. (All routines, including those treated by PostgreSQL as
		 * returning a scalar result, are presented to a PL/Java handler with
		 * the inputs and outputs represented by {@link TupleTableSlot}.)
		 * The tuple descriptors seen at this stage may include attributes with
		 * polymorphic types, not resolvable to specific types until the
		 * {@code Template} instance this method returns is later applied at
		 * an actual call site.
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
		Template prepare(RegProcedure<PLJavaBased> target) throws SQLException;
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
			RegProcedure<PLJavaBased> subject, boolean checkBody)
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
			RegProcedure<PLJavaBased> subject, boolean checkBody)
		throws SQLException
		{
		}

		/**
		 * Prepares a template for a call of the trigger function
		 * <var>target</var>.
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
		TriggerTemplate prepareTrigger(RegProcedure<PLJavaBased> target)
		throws SQLException;
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
