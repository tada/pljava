/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Purdue University
 */
package org.postgresql.pljava.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a Java method for which an SQL function declaration should be
 * generated into the deployment descriptor file.
 *
 * @author Thomas Hallgren - pre-Java6 version
 * @author Chapman Flack (Purdue Mathematics) - update to Java6, add trust,
 * cost, rows, settings, leakproof, provide/require
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Function
{
	/**
	 * Whether the function is called even for null input,
	 * or known to return null in that case and therefore not called.
	 */
	enum OnNullInput { CALLED, RETURNS_NULL };

	/**
	 * Whether the function executes with the same identity and
	 * permissions as the role that has invoked it (the usual case), or
	 * with the rights of the role that <em>defined</em> it (such as to
	 * offer controlled access to data the invoker would otherwise have
	 * no access to). A function should be annotated <code>SECURITY
	 * DEFINER</code> only after carefully <a href=
'http://www.postgresql.org/docs/current/static/sql-createfunction.html#SQL-CREATEFUNCTION-SECURITY'
>considering the implications</a>.
	 */
	enum Security { INVOKER, DEFINER };

	/**
	 * The <a href=
'http://www.postgresql.org/docs/current/static/xfunc-volatility.html'>volatility
category</a>
	 * describing the presence or absence of side-effects constraining what
	 * the optimizer can safely do with the function.
	 */
	enum Effects { IMMUTABLE, STABLE, VOLATILE };

	/**
	 * Whether the function only needs limited capabilities and can
	 * run in the "trusted" language sandbox, or has to be unsandboxed
	 * and run in an "untrusted" language instance.
	 */
	enum Trust { SANDBOXED, UNSANDBOXED };

	/** 
	 * Whether the function is unsafe to use in any parallel query plan at all,
	 * or avoids certain operations and can appear in such a plan but must be
	 * executed only in the parallel group leader, or avoids an even larger
	 * set of operations and is safe to execute anywhere in a parallel plan.
	 */
	enum Parallel { UNSAFE, RESTRICTED, SAFE };

	/**
	 * The element type in case the annotated function returns a
	 * {@link org.postgresql.pljava.ResultSetProvider ResultSetProvider},
	 * or can be used to specify the return type of any function if the
	 * compiler hasn't inferred it correctly.
	 *<p>
	 * Only one of {@code type} or {@code out} may appear.
	 */
	String type() default "";

	/**
	 * The result column names and types of a composite-returning function.
	 *<p>
	 * This is for a function defining its own one-off composite type
	 * (declared with {@code OUT} parameters). If the function returns some
	 * composite type known to the catalog, simply use {@code type} and the name
	 * of that type.
	 *<p>
	 * Each element is a name and a type specification, separated by whitespace.
	 * An element that begins with whitespace declares an output column with no
	 * name, only a type. The name is an ordinary SQL identifier; if it would
	 * be quoted in SQL, naturally each double-quote must be represented as
	 * {@code \"} in Java.
	 */
	String[] out() default {};

	/**
	 * The name of the function. This is optional. The default is
	 * to use the name of the annotated method. 
	 */
	String name() default "";

	/**
	 * The name of the schema if any.
	 */
	String schema() default "";

	/**
	 * Whether PostgreSQL should gather trailing arguments into an array that
	 * will be bound to the last (non-output) Java parameter (which must have an
	 * array type).
	 * Appeared in PostgreSQL 8.4.
	 */
	boolean variadic() default false;

	/**
	 * Estimated cost in units of cpu_operator_cost.
	 *<p>
	 * If left unspecified (-1) the backend's default will apply.
	 */
	int cost() default -1;

	/**
	 * Estimated number of rows returned (only for functions returning set).
	 *<p>
	 * If left unspecified (-1) the backend's default will apply.
	 */
	int rows() default -1;

	/**
	 * Defines what should happen when input to the function
	 * is null. RETURNS_NULL means that if any parameter value is null, Postgres
	 * will use null as the return value without even calling the function.
	 * CALLED means the function is called in all cases, and must do its own
	 * checks for null parameters and determine what to return.
	 */
	OnNullInput onNullInput() default OnNullInput.CALLED;
	
	/**
	 * Whether the function will run with the security credentials of the
	 * invoker (the usual case) or with those of its definer (the special
	 * case for a function that needs to access objects with the authority
	 * of the user who declared it). Security.DEFINER functions must be coded
	 * and declared carefully; see at least <a href=
'http://www.postgresql.org/docs/current/static/sql-createfunction.html#SQL-CREATEFUNCTION-SECURITY'
>Writing SECURITY DEFINER Functions Safely</a> in the PostgreSQL docs, and the
	 * {@link #settings} element of this annotation.
	 */
	Security security() default Security.INVOKER;
	
	/**
	 * What the query optimizer is allowed to assume about this function.
	 *<p>
	 * IMMUTABLE describes a pure function whose return will always be the same
	 * for the same parameter values, with no side effects and no dependency on
	 * anything in the environment. STABLE describes a function that has no
	 * side effects and can be assumed to produce the same result for the same
	 * parameters during any one table scan. VOLATILE (the default) describes
	 * a function with side effects or about whose result the optimizer cannot
	 * make any safe assumptions.
	 */
	Effects effects() default Effects.VOLATILE;
	
	/**
	 * Whether the function will run in the SANDBOXED ("trusted") version
	 * of the language, or requires UNSANDBOXED access and must be defined
	 * in the "untrusted" language instance.
	 */
	Trust trust() default Trust.SANDBOXED;

	/**
	 * The name of the PostgreSQL procedural language to which this function
	 * should be declared, as an alternative to specifying {@link #trust trust}.
	 *<p>
	 * Ordinarily, PL/Java installs two procedural languages, {@code java} and
	 * {@code javau}, and a function is declared in one or the other by giving
	 * {@code trust} the value {@code SANDBOXED} or {@code UNSANDBOXED},
	 * respectively. It is possible to declare other named procedural languages
	 * sharing PL/Java's handler functions, and assign customized permissions
	 * to them in {@code pljava.policy}. A function can be declared in the
	 * specific language named with this element.
	 *<p>
	 * It is an error to specify both {@code language} and {@code trust} in
	 * the same annotation.
	 */
	String language() default "";

	/** 
	 * Whether the function is <a id='parallel'>UNSAFE</a> to use in any
	 * parallel query plan at all
	 * (the default), or avoids all disqualifying operations and so is SAFE to
	 * execute anywhere in a parallel plan, or, by avoiding <em>some</em> such
	 * operations, may appear in parallel plans but RESTRICTED to execute only
	 * on the parallel group leader. The operations that must be considered are
	 * set out in <a href=
'https://www.postgresql.org/docs/current/static/parallel-safety.html#PARALLEL-LABELING'
>Parallel Labeling for Functions and Aggregates</a> in the PostgreSQL docs.
	 *<p>
	 * For much more on the practicalities of parallel query and PL/Java,
	 * please see <a href=
'../../../../../../../use/parallel.html'>the users' guide</a>.
	 *<p>
	 * Appeared in PostgreSQL 9.6.
	 */
	Parallel parallel() default Parallel.UNSAFE;
	
	/**
	 * Whether the function can be safely pushed inside the evaluation of views
	 * created with the <a href=
'http://www.postgresql.org/docs/current/static/rules-privileges.html'
>security_barrier option.</a>
	 * This should only be set true on
	 * a function known not to leak data under any circumstances (even, for
	 * example, throwing errors for certain parameter values and not others).
	 * Appeared in PostgreSQL 9.2.
	 */
	boolean leakproof() default false;
	
	/**
	 * Configuration parameter overrides to apply during execution of the
	 * function, reverting afterward to the former setting. Suggested for, e.g.,
	 * applying a trusted search_path during execution of a SECURITY DEFINER
	 * function. Each string will simply be injected into the generated CREATE
	 * FUNCTION command after a SET token, and so should have the forms <br>
	 * configuration_parameter {=|TO} somevalue or<br>
	 * configuration_parameter FROM CURRENT. The latter will ensure that the
	 * function executes with the same setting for configuration_parameter that
	 * was in effect when the function was created.
	 *<p>
	 * Appeared in PostgreSQL 8.3.
	 */
	String[] settings() default {};

	/**
	 * The Triggers that will call this function (if any).
	 */
	Trigger[] triggers() default {};

	/**
	 * One or more arbitrary labels that will be considered 'provided' by the
	 * object carrying this annotation. The deployment descriptor will be
	 * generated in such an order that other objects that 'require' labels
	 * 'provided' by this come later in the output for install actions, and
	 * earlier for remove actions.
	 */
	String[] provides() default {};

	/**
	 * One or more arbitrary labels that will be considered 'required' by the
	 * object carrying this annotation. The deployment descriptor will be
	 * generated in such an order that other objects that 'provide' labels
	 * 'required' by this come earlier in the output for install actions, and
	 * later for remove actions.
	 */
	String[] requires() default {};

	/**
	 * The {@code <implementor name>} to be used around SQL code generated
	 * for this function (and for its triggers, if any, and not overridden for
	 * them). Defaults to {@code PostgreSQL}. Set explicitly to {@code ""} to
	 * emit code not wrapped in an {@code <implementor block>}.
	 */
	String implementor() default "";

	/**
	 * A comment to be associated with the SQL function. If left to default,
	 * and the Java function has a doc comment, its first sentence will be used.
	 * If an empty string is explicitly given, no comment will be set.
	 */
	String comment() default "";
}
