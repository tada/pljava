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
package org.postgresql.pljava.model;

import java.sql.SQLException;
import java.sql.SQLXML;

import java.util.BitSet;
import java.util.List;

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import org.postgresql.pljava.TargetList.Projection;

import org.postgresql.pljava.annotation.Function.Effects;
import org.postgresql.pljava.annotation.Function.OnNullInput;
import org.postgresql.pljava.annotation.Function.Parallel;
import org.postgresql.pljava.annotation.Function.Security;

import org.postgresql.pljava.annotation.Trigger.Called;
import org.postgresql.pljava.annotation.Trigger.Event;
import org.postgresql.pljava.annotation.Trigger.Scope;

/**
 * Model of a PostgreSQL "routine" (which in late versions can include
 * procedures and functions of various kinds) as defined in the system catalogs,
 * including its parameter and result types and many other properties.
 * @param <M> distinguishes {@code RegProcedure} instances used for different
 * known purposes, by specifying the type of a 'memo' that could be attached to
 * the instance, perhaps with extra information helpful for the intended use.
 * At present, such memo interfaces are all empty, but still this parameter can
 * serve a compile-time role to discourage mixing different procedures up.
 */
public interface RegProcedure<M extends RegProcedure.Memo<M>>
extends
	Addressed<RegProcedure<?>>, Namespaced<Simple>, Owned,
	AccessControlled<EXECUTE>
{
	RegClass.Known<RegProcedure<?>> CLASSID =
		formClassId(ProcedureRelationId, (Class<RegProcedure<?>>)null);

	ProceduralLanguage language();

	float cost();

	float rows();

	RegType variadicType();

	/**
	 * A planner-support function that may transform call sites of
	 * this function.
	 *<p>
	 * In PG 9.5 to 11, there was a similar, but less flexible, "transform"
	 * function that this method can return when running on those versions.
	 * @since PG 12
	 */
	RegProcedure<PlannerSupport> support();

	/**
	 * The kind of procedure or function.
	 *<p>
	 * Before PG 11, there were separate booleans to indicate an aggregate or
	 * window function, which this method can consult when running on earlier
	 * versions.
	 * @since PG 11
	 */
	Kind kind();

	Security security();

	boolean leakproof();

	OnNullInput onNullInput();

	boolean returnsSet();

	Effects effects();

	Parallel parallel();

	RegType returnType();

	List<RegType> argTypes();

	List<RegType> allArgTypes();

	/**
	 * Modes corresponding 1-for-1 to the arguments in {@code allArgTypes}.
	 */
	List<ArgMode> argModes();

	/**
	 * Names corresponding 1-for-1 to the arguments in {@code allArgTypes}.
	 */
	List<Simple> argNames();

	/**
	 * A {@code pg_node_tree} representation of a list of <var>n</var>
	 * expression trees, corresponding to the last <var>n</var> input arguments
	 * (that is, the last <var>n</var> returned by {@code argTypes}).
	 */
	SQLXML argDefaults();

	List<RegType> transformTypes();

	String src();

	String bin();

	/**
	 * A {@code pg_node_tree} representation of a pre-parsed SQL function body,
	 * used when it is given in SQL-standard notation rather than as a string
	 * literal, otherwise null.
	 * @since PG 14
	 */
	SQLXML sqlBody();

	/**
	 * This is surely a list of {@code guc=value} pairs and ought to have
	 * a more specific return type.
	 *<p>
	 * XXX
	 */
	List<String> config();

	enum ArgMode { IN, OUT, INOUT, VARIADIC, TABLE };

	enum Kind { FUNCTION, PROCEDURE, AGGREGATE, WINDOW };

	/**
	 * Obtain memo attached to this {@code RegProcedure}, if any.
	 *<p>
	 * A {@code RegProcedure} may have an implementation of {@link Memo Memo}
	 * attached, providing additional information on what sort of procedure
	 * it is and how to use it. Many catalog getters that return
	 * {@code RegProcedure} specialize the return type to indicate
	 * an expected subinterface of {@code Memo}.
	 *<p>
	 * It may not be the case that a given {@code RegProcedure} has a valid
	 * {@code Memo} attached at all times. Documentation for a specific
	 * {@code Memo} subinterface should explain the circumstances when this
	 * method can be called to rely on a memo of that type.
	 */
	M memo();

	interface Memo<M extends Memo<M>> { }

	interface PlannerSupport extends Memo<PlannerSupport> { }

	/**
	 * Counterpart to the PostgreSQL {@code FmgrInfo}.
	 */
	interface Lookup
	{
		/**
		 * The PostgreSQL function or procedure being called.
		 */
		RegProcedure<?> target();

		/*
		 * Most of the C members of FmgrInfo are just as easy here to look up
		 * on target. The API here will focus on exposing such higher-level
		 * queries as might be made in C with the functions in fmgr.h and
		 * funcapi.h.
		 */

		/**
		 * A {@link TupleDescriptor} describing the incoming arguments, with any
		 * polymorphic types from the routine's declaration resolved to the
		 * actual types at this call site.
		 *<p>
		 * If there are no polymorphic types among the routine's declared
		 * parameters, an unchanged {@code TupleDescriptor} cached with the
		 * routine may be returned.
		 *<p>
		 * See {@link #inputsAreSpread inputsAreSpread} for one case where the
		 * {@code size()} of this {@code TupleDescriptor} can exceed the
		 * {@code size()} of a {@code TupleDescriptor} constructed from the
		 * routine's declaration.
		 *<p>
		 * {@link RegType#ANYARRAY ANYARRAY}, normally seen only in templates
		 * as a polymorphic pseudotype, can appear in this result in rare cases,
		 * where an expression involves certain columns of statistics-related
		 * system catalogs. An argument with this resolved type represents an
		 * array, but one whose element type may differ from call to call. See
		 * {@link RegType#ANYARRAY ANYARRAY} for how such an array can be
		 * handled.
		 */
		TupleDescriptor inputsDescriptor() throws SQLException;

		/**
		 * A {@link TupleDescriptor} describing the expected result, with any
		 * polymorphic types from the routine's declaration resolved to the
		 * actual types at this call site.
		 *<p>
		 * Returns null if the routine has a declared return type of
		 * {@link RegType#VOID VOID} and does not need to return anything.
		 *<p>
		 * If there are no polymorphic types among the routine's declared
		 * outputs, an unchanged {@code TupleDescriptor} cached with the
		 * routine may be returned.
		 *<p>
		 * When the routine is a function declared with a non-composite return
		 * type (or with a single {@code OUT} parameter, a case PostgreSQL
		 * treats the same way), this method returns a synthetic ephemeral
		 * {@code TupleDescriptor} with one unnamed attribute of that type.
		 *<p>
		 * {@link RegType#ANYARRAY ANYARRAY}, normally seen only in templates
		 * as a polymorphic pseudotype, can appear in this result in rare cases,
		 * where an expression involves certain columns of statistics-related
		 * system catalogs. An argument with this resolved type represents an
		 * array, but one whose element type may differ from call to call. See
		 * {@link RegType#ANYARRAY ANYARRAY} for how such an array can be
		 * handled.
		 */
		TupleDescriptor outputsDescriptor() throws SQLException;

		/**
		 * Returns true if a routine with a variadic parameter declared with the
		 * wildcard {@code "any"} type is being called with its arguments in
		 * "spread" form at this call site.
		 *<p>
		 * In "spread" form, {@link Call#arguments arguments()}{@code .size()}
		 * can exceed the routine's declared number of parameters, with
		 * the values <em>and types</em> of the variadic arguments to be found
		 * at successive positions of {@link Call#arguments}. In "collected"
		 * form, the position of the variadic parameter is passed a single
		 * PostgreSQL array of the variadic arguments' type. A call with zero
		 * arguments for the variadic parameter can only be made in
		 * "collected" form, with an empty array at the variadic parameter's
		 * declared position; therefore, no case arises where the passed
		 * arguments are fewer than the declared parameters.
		 *<p>
		 * When the routine declaration has a variadic parameter of any type
		 * other than the wildcard {@code "any"}, collected form is always used.
		 * In the wildcard case, collected or spread form may be seen, at the
		 * caller's option. Therefore, there is an ambiguity when such a routine
		 * receives a single argument of array type at the variadic position,
		 * and this method must be used in that case to determine the caller's
		 * intent.
		 * @return always false, except for a routine declared
		 * {@code VARIADIC "any"} when its arguments are being passed
		 * in "spread" form.
		 */
		boolean inputsAreSpread();

		/**
		 * For the arguments at (zero-based) positions in {@code arguments()}
		 * indicated by <var>ofInterest</var>, report (in the returned bit set)
		 * which of those are 'stable', that is, will keep their values across
		 * calls associated with the current {@code Lookup}.
		 */
		BitSet stableInputs(BitSet ofInterest);
	}

	/**
	 * Counterpart to the PostgreSQL {@code FunctionCallInfoBaseData}.
	 *<p>
	 * Presents arguments in the form of a {@code TupleTableSlot}.
	 */
	interface Call
	{
		Lookup lookup();
		TupleTableSlot arguments() throws SQLException;
		TupleTableSlot result() throws SQLException;
		void isNull(boolean nullness);
		RegCollation collation();
		Context context();
		ResultInfo resultInfo();
		/*
		 * Using TupleTableSlot, this interface does not so much need to
		 * expose the get_call_result_type / get_fn_expr_argtype /
		 * get_fn_expr_variadic routines as to just go ahead and use them
		 * and present a coherent picture.
		 */

		interface Context
		{
			/**
			 * Supplied when the routine is being called as a trigger.
			 */
			interface TriggerData extends Context
			{
				/**
				 * When the trigger is being called (before, after, or instead)
				 * with respect to the triggering event.
				 */
				Called called();

				/**
				 * The event that has fired this trigger.
				 */
				Event event();

				/**
				 * The scope (per-row or per-statement) of this trigger.
				 */
				Scope scope();

				/**
				 * The relation on which this trigger is declared.
				 */
				RegClass relation();

				/**
				 * The row for which the trigger was fired.
				 *<p>
				 * In a trigger fired for {@code INSERT} or {@code DELETE}, this
				 * is the row to return if not altering or skipping the
				 * operation.
				 */
				TupleTableSlot triggerTuple();

				/**
				 * The proposed new version of the row, only in a trigger fired
				 * for {@code UPDATE}.
				 *<p>
				 * In a trigger fired for {@code UPDATE}, this is the row
				 * to return if not altering or skipping the operation.
				 */
				TupleTableSlot newTuple();

				/**
				 * Information from the trigger's declaration in the system
				 * catalogs.
				 */
				Trigger trigger();

				/**
				 * For {@code UPDATE} triggers, which columns have been updated
				 * by the triggering command; null for other triggers.
				 */
				Projection updatedColumns();
			}

			interface EventTriggerData extends Context
			{
			}

			interface AggState extends Context
			{
			}

			interface WindowAggState extends Context
			{
			}

			interface WindowObject extends Context
			{
			}

			/**
			 * Supplied when the routine being called is a procedure
			 * rather than a function.
			 */
			interface CallContext extends Context
			{
				/**
				 * Indicates whether transaction control operations within
				 * the procedure are disallowed (true) or allowed (false).
				 */
				boolean atomic();
			}

			interface ErrorSaveContext extends Context
			{
			}
		}

		interface ResultInfo
		{
			interface ReturnSetInfo extends ResultInfo
			{
			}
		}
	}
}
