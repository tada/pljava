/*
 * Copyright (c) 2015- Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import java.sql.SQLData; // referred to in javadoc

/**
 * Annotation on a PL/Java class to form a User Defined Type that will
 * become a new PostgreSQL <a href=
'http://www.postgresql.org/docs/current/static/extend-type-system.html'
>base type</a>.
 *<p>
 * A class marked with this annotation must implement {@link SQLData}, and will
 * use the methods of that interface to map between its own state and an
 */
@Target(ElementType.TYPE) @Retention(RetentionPolicy.CLASS)
public @interface MappedUDT
{
	/**
	 * Name of the type in SQL, if it is not to be the simple name of
	 * the class. By default, the class name will be used, subject to
	 * PostgreSQL's normal case-folding and case-insensitive matching.
	 */
	String name() default "";

	/**
	 * Schema in which the type is declared.
	 * If not given, the names will not be schema qualified.
	 */
	String schema() default "";

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

	String[] structure() default {};
}
