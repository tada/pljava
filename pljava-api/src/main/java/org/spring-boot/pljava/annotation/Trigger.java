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
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation, only used in {@link Function#triggers @Function(triggers=...)},
 * to specify what trigger(s) the function will be called for.
 *<p>
 * Transition tables ({@link #tableOld} and {@link #tableNew}) appear in
 * PostgreSQL 10. If a trigger is declared with
 * {@code tableOld="oo", tableNew="nn"}, then the trigger function can query
 * {@code oo} and {@code nn} as if they are actual tables with the same
 * columns as the table responsible for the trigger, and containing the affected
 * rows before and after the changes. Only an AFTER trigger can have transition
 * tables. An UPDATE will populate both tables. INSERT will not populate the
 * old table, and DELETE will not populate the new table. It is an error to
 * specify either table if {@code events} does not include at least one event
 * that could populate that table. As long as at least one such event is
 * included, the table can be specified, and will simply have no rows if the
 * trigger is invoked for an event that does not populate it.
 *<p>
 * In an after-statement trigger, the transition tables include all rows
 * affected by the statement. In an after-row trigger, the same is true:
 * after-row triggers are all queued until the statement completes, and then
 * the function will be invoked for each row that was affected, but will see
 * the complete transition tables on each invocation.
 * @author Thomas Hallgren
 */
@Target({}) @Retention(RetentionPolicy.CLASS) @Documented
public @interface Trigger
{
	/**
	 * Whether the trigger is invoked before or after the specified event.
	 */
	enum Called { BEFORE, AFTER, INSTEAD_OF };

	/**
	 * Deferrability (only applies to constraint triggers).
	 * {@code NOT_DEFERRABLE} if the constraint trigger is not deferrable
	 * at all; otherwise, the trigger is deferrable and this value indicates
	 * whether initially deferred or not.
	 */
	enum Constraint { NOT_DEFERRABLE, INITIALLY_IMMEDIATE, INITIALLY_DEFERRED };
	
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
	 * Only for a constraint trigger, whether it is deferrable and, if so,
	 * initially immediate or deferred. To create a constraint trigger that is
	 * not deferrable, this attribute must be explicitly given with the value
	 * {@code NOT_DEFERRABLE}; leaving it to default is not the same. When this
	 * attribute is not specified, a normal trigger, not a constraint trigger,
	 * is created.
	 *<p>
	 * A constraint trigger must have {@code called=AFTER} and
	 * {@code scope=ROW}.
	 */
	Constraint constraint() default Constraint.NOT_DEFERRABLE;
	
	/**
	 * The event(s) that will trigger the call.
	 */
	Event[] events();

	/**
	 * The name of another table referenced by the constraint.
	 * This option is used for foreign-key constraints and is not recommended
	 * for general use. This can only be specified for constraint triggers.
	 * If the name should be schema-qualified, use
	 * {@link #fromSchema() fromSchema} to specify the schema.
	 */
	String from() default "";

	/**
	 * The schema containing another table referenced by the constraint.
	 * This can only be specified for constraint triggers, and only to name the
	 * schema for a table named with {@link #from() from}.
	 */
	String fromSchema() default "";
    
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
	 * Denotes if the trigger is fired before, after, or instead of its
	 * scope (row or statement)
	 */
	Called called();

	/**
	 * A boolean condition limiting when the trigger can be fired.
	 * This text is injected verbatim into the generated SQL, after
	 * the keyword {@code WHEN}.
	 */
	String when() default "";

	/**
	 * A list of columns (only meaningful for an UPDATE trigger). The trigger
	 * will only fire for update if at least one of the columns is mentioned
	 * as a target of the update command.
	 */
	String[] columns() default {};

	/**
	 * Name to refer to "before" table of affected rows. Only usable in an AFTER
	 * trigger whose {@code events} include UPDATE or DELETE. The trigger
	 * function can issue queries as if a table by this name exists and contains
	 * all rows affected by the event, in their prior state. (If the trigger is
	 * called for an event other than UPDATE or DELETE, the function can still
	 * query a table by this name, which will appear to be empty.)
	 */
	String tableOld() default "";

	/**
	 * Name to refer to "after" table of affected rows. Only usable in an AFTER
	 * trigger whose {@code events} include UPDATE or INSERT. The trigger
	 * function can issue queries as if a table by this name exists and contains
	 * all rows affected by the event, in their new state. (If the trigger is
	 * called for an event other than UPDATE or INSERT, the function can still
	 * query a table by this name, which will appear to be empty.)
	 */
	String tableNew() default "";

	/**
	 * A comment to be associated with the trigger. If left to default,
	 * and the Java function has a doc comment, its first sentence will be used.
	 * If an empty string is explicitly given, no comment will be set.
	 */
	String comment() default "";
}
