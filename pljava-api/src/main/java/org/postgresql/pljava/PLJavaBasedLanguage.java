/*
 * Copyright (c) 2023 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.model.ProceduralLanguage.PLJavaBased;
import org.postgresql.pljava.model.RegProcedure;
import org.postgresql.pljava.model.RegProcedure.Call;
import org.postgresql.pljava.model.RegProcedure.Lookup;
import org.postgresql.pljava.model.TupleTableSlot; // javadoc

/**
 * Interface for a procedural language on PL/Java infrastructure.
 *<p>
 * An implementation must also implement at least one of
 * {@link InlineBlocks InlineBlocks}
 * and {@link Routines Routines}.
 */
public interface PLJavaBasedLanguage
{
	/**
	 * To be implemented by a language that supports routines (that is,
	 * functions and/or procedures).
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
		 * fails, otherwise returning normally.
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
		 * receiver has already been loaded and instantiated. If it has not,
		 * and <var>checkBody</var> is false, PL/Java does not attempt to do so,
		 * and treats the validation as successful.
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
		 * PL/Java calls this method only at routine-creation time, if
		 * {@link #essentialChecks essentialChecks} completed normally.
		 *<p>
		 * This method should throw an informative exception for any check that
		 * fails, otherwise returning normally.
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
	 * To be implemented by a language that supports inline code blocks.
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
		void call(Call fcinfo) throws SQLException;
	}

	/**
	 * The result of a {@link Routines#prepare prepare} call on a PL/Java-based
	 * routine.
	 *<p>
	 * An instance should depend only on the static catalog information for the
	 * routine as passed to {@code prepare}, and may encapsulate any values that
	 * can be precomputed from that information alone. Its
	 * {@link #specialize specialize} method will be called, passing information
	 * specific to a call site, to obtain a {@link Routine}.
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
}
