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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that supplies verbatim commands to be copied into the
 * deployment descriptor.
 *
 * Strings supplied within a single SQLAction annotation will be copied
 * in the order supplied. Strings from different SQLAction annotations, and
 * generated code for functions, will be assembled in an order that can be
 * influenced by 'provides' and 'requires' labels. No snippet X will be
 * emitted ahead of any snippets that provide what X requires. The "remove"
 * actions will be assembled in the reverse of that order.
 *
 * @author Thomas Hallgren - pre-Java6 version
 * @author Chapman Flack (Purdue Mathematics) - updated to Java6,
 * added SQLAction
 */
@Documented
@Target({ElementType.PACKAGE,ElementType.TYPE})
@Repeatable(SQLActions.class)
@Retention(RetentionPolicy.CLASS)
public @interface SQLAction
{
	/**
	 * SQL commands to be included in a BEGIN INSTALL group (each string
	 * considered a separate command to which a semicolon will be added).
	 */
	String[] install() default {};

	/**
	 * SQL commands to be included in a BEGIN REMOVE group (each string
	 * considered a separate command to which a semicolon will be added).
	 */
	String[] remove() default {};

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
	 * here. Defaults to {@code PostgreSQL}. Set explicitly to {@code ""} to
	 * emit code not wrapped in an {@code <implementor block>}.
	 */
	String implementor() default "";
}
