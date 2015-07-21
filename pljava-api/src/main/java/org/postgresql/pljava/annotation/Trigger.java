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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation, only used in {@link Function#triggers @Function(triggers=...)},
 * to specify what trigger(s) the function will be called for.
 * @author Thomas Hallgren
 */
@Target({}) @Retention(RetentionPolicy.CLASS)
public @interface Trigger
{
	/**
	 * Whether the trigger is invoked before or after the specified event.
	 */
	enum When { BEFORE, AFTER };

	/**
	 * Types of event that can occasion a trigger.
	 */
	enum Event { DELETE, INSERT, UPDATE, TRUNCATE };

	/**
	 * Whether the trigger will occur only once for a statement of interest,
	 * or once for each row affected by the statement.
	 */
	enum Scope { STATEMENT, ROW };

	/**
	 * Arguments to be passed to the trigger function.
	 */
	String[] arguments() default {};

	/**
	 * The event(s) that will trigger the call.
	 */
	Event[] events();

	/**
	 * Name of the trigger. If not set, the name will
	 * be generated.
	 */
	String name() default "";

	/**
	 * The name of the schema containing the table for
	 * this trigger.
	 */
	String schema() default "";

	/**
	 * The table that this trigger is tied to.
	 */
	String table();

	/**
	 * Scope: statement or row.
	 */
	Scope scope() default Scope.STATEMENT;
	
	/**
	 * Denotes if the trigger is fired before or after its
	 * scope (row or statement)
	 */
	When when();
}
