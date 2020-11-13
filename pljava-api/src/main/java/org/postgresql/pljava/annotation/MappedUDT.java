/*
 * Copyright (c) 2015-2020 Tada AB and other contributors, as listed below.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import java.sql.SQLData; // referred to in javadoc

/**
 * Annotation on a PL/Java class that will either map an existing PostgreSQL
 * type (provided its internal storage form is well understood), or create and
 * map a new PostgreSQL <a href=
'http://www.postgresql.org/docs/current/static/extend-type-system.html'
>composite type</a>
 * (a/k/a "structured type" in the standards).
 *<p>
 * A class marked with this annotation must implement {@link SQLData}, and will
 * use the methods of that interface to map between its own state and the
 * PostgreSQL internal form. If this annotation includes a
 * {@link #structure structure}, SQL will be emitted to create a new composite
 * type with that structure in the {@link #schema schema} named here (or at
 * the head of the {@code search_path} if unspecified), and with the
 * {@link #name name} given here (or else the simple name of the class being
 * annotated), and then to use the {@code sqlj.add_type_mapping} function to
 * associate that type with the annotated class.
 *<p>
 * If no {@link #structure structure} is given, no new type will be created,
 * and {@code sqlj.add_type_mapping} will simply be called to map some existing
 * PostgreSQL type (known by {@link #name name} if specified or the simple name
 * of the class being annotated, and found in {@link #schema schema} if
 * specified, or by following the search path) to the annotated class.
 */
@Target(ElementType.TYPE) @Retention(RetentionPolicy.CLASS) @Documented
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
	 * for this type. Defaults to {@code PostgreSQL}. Set explicitly to
	 * {@code ""} to emit code not wrapped in an {@code <implementor block>}.
	 */
	String implementor() default "";

	/**
	 * Structure of the new composite ("structured") type to create, as strings,
	 * one per attribute, for example {@code {"x float8", "y float8"}}.
	 * Collation syntax could be included if needed as well; the strings are
	 * simply dumped, with commas between, into the {@code CREATE TYPE}
	 * command.
	 *<p>
	 * An empty list {@code {}} is allowed, and will create a composite type
	 * with no attributes at all; PostgreSQL expressly allows this for some
	 * reason, and you can still tell whether you have one of those or
	 * {@code null}, so it could be used as a strange sort of boolean.
	 *<p>
	 * If no {@code structure} is given at all, no type is created, and this
	 * annotation simply maps the class being annotated to some existing
	 * PostgreSQL type.
	 */
	String[] structure() default {};

	/**
	 * A comment to be associated with the type. If left to default,
	 * and the Java class has a doc comment, its first sentence will be used.
	 * If an empty string is explicitly given, no comment will be set.
	 */
	String comment() default "";
}
