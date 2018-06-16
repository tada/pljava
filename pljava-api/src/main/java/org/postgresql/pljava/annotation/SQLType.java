/*
 * Copyright (c) 2004-2016 Tada AB and other contributors, as listed below.
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
 * Optionally annotates a Java method parameter, to supply an explicit
 * SQL type for use in the SQL function declaration in place of the
 * automatically mapped type, and/or to supply an SQL default value.
 *
 * This annotation cannot be used to supply the SQL declaration's return type,
 * but {@link Function#type @Function(type=...)} can.
 *
 * @author Thomas Hallgren - pre-Java6 version
 * @author Chapman Flack (Purdue Mathematics) - updated to Java6, added SQLType
 */
@Documented
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface SQLType
{
	/**
	 * The SQL type name to use for the annotated parameter in preference to
	 * any found in the default mappings.
	 */
	String value() default "";
	
	/**
	 * Default value for the parameter. Parameters of row or array type can have
	 * defaults too, so this element accepts an array. For a scalar type,
	 * just supply one value. Values given here go into the descriptor file
	 * as properly-escaped string literals explicitly cast to the parameter
	 * type, which covers the typical case of defaults that are simple
	 * literals or can be computed as Java String-typed constant expressions
	 * (e.g. ""+Math.PI) and ensures the parsability of the descriptor file.
	 *<p>
	 * For a row type of unknown structure (PostgreSQL type {@code RECORD}), the
	 * only default that can be specified is {@code {}}, which can be useful for
	 * functions that use a {@code RECORD} parameter to accept an arbitrary
	 * sequence of named, typed parameters from the caller. For a named row type
	 * (not {@code RECORD}), an array of nonzero length will be accepted. It
	 * needs to match the number and order of components of the row type (which
	 * cannot be checked at compile time, but will cause the deployment
	 * descriptor code to fail at jar install time if it does not).
	 */
	String[] defaultValue() default {};
	
	// Is it worth having a defaultRaw() for rare cases wanting some
	// arbitrary SQL expression for the default?
	// String[] defaultRaw() default {};
}
