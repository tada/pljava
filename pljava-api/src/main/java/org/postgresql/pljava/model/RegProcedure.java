/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
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

import java.sql.SQLXML;

import java.util.List;

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import org.postgresql.pljava.annotation.Function.Effects;
import org.postgresql.pljava.annotation.Function.OnNullInput;
import org.postgresql.pljava.annotation.Function.Parallel;
import org.postgresql.pljava.annotation.Function.Security;

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
	 */
	M memo();

	interface Memo<M extends Memo<M>>
	{
		RegProcedure<M> apply(RegProcedure<?> bare);
	}

	interface PlannerSupport extends Memo<PlannerSupport> { }

	interface PLJava extends Memo<PLJava>
	{
		// MethodHandleInfo methodInfo() ? \
		// MethodHandle     method() ?     }  need a RegNamespace parameter?
		// AccessControlContext acc() ?    /
	}
}
