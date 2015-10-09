/*
 * Copyright (c) 2004-2013 Tada AB and other contributors, as listed below.
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
	 * of the function.
	 */
	enum Type { IMMUTABLE, STABLE, VOLATILE };

	/**
	 * Whether the function only needs restricted capabilities and can
	 * run in the "trusted" language instance, or requires an unrestricted
	 * environment and has to run in an "untrusted" language instance.
	 */
	enum Trust { RESTRICTED, UNRESTRICTED };

	/**
	 * The element type in case the annotated function returns a
	 * {@link org.postgresql.pljava.ResultSetProvider ResultSetProvider}.
	 */
	String complexType() default "";

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
	 * Estimated cost in units of cpu_operator_cost.
	 *
	 * If left unspecified (-1) the backend's default will apply.
	 */
	int cost() default -1;

	/**
	 * Estimated number of rows returned (only for functions returning set).
	 *
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
	 * What the query optimizer is allowed to assume about this function
	 * (this element has nothing to do with the data type returned by the
	 * function; see complexType for that).
	 *
	 * IMMUTABLE describes a pure function whose return will always be the same
	 * for the same parameter values, with no side effects and no dependency on
	 * anything in the environment. STABLE describes a function that has no
	 * side effects and can be assumed to produce the same result for the same
	 * parameters during any one table scan. VOLATILE (the default) describes
	 * a function with side effects or about whose result the optimizer cannot
	 * make any safe assumptions.
	 */
	Type type() default Type.VOLATILE;
	
	/**
	 * Whether the function will run in the RESTRICTED ("trusted") version
	 * of the language, or requires UNRESTRICTED access and must be defined
	 * in the "untrusted" language instance.
	 */
	Trust trust() default Trust.RESTRICTED;
	
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
	 * FUNCTION command after a SET token, and so should have the forms <br />
	 * configuration_parameter {=|TO} somevalue or<br />
	 * configuration_parameter FROM CURRENT. The latter will ensure that the
	 * function executes with the same setting for configuration_parameter that
	 * was in effect when the function was created.
	 * 
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
}
