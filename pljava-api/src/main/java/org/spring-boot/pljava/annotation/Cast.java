/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a PostgreSQL {@code CAST}.
 *<p>
 * May annotate a Java method (which should also carry a
 * {@link Function @Function} annotation, making it a PostgreSQL function),
 * or a class or interface (just to have a place to put it when not directly
 * associated with a method).
 *<p>
 * If not applied to a method, must supply {@code path=} specifying
 * {@code BINARY} or {@code INOUT}.
 *<p>
 * The source and target types must be specified with {@code from} and
 * {@code to}, unless the annotation appears on a method, in which case these
 * default to the first parameter and return types of the function,
 * respectively.
 *<p>
 * The cast will, by default, have to be applied explicitly, unless
 * {@code application=ASSIGNMENT} or {@code application=IMPLICIT} is given.
 *
 * @author Chapman Flack
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(Cast.Container.class)
@Retention(RetentionPolicy.CLASS)
public @interface Cast
{
	/**
	 * When this cast can be applied: only in explicit form, when used in
	 * assignment context, or implicitly whenever needed.
	 */
	enum Application { EXPLICIT, ASSIGNMENT, IMPLICIT };

	/**
	 * A known conversion path when a dedicated function is not supplied:
	 * {@code BINARY} for two types that are known to have the same internal
	 * representation, or {@code INOUT} to invoke the first type's text-output
	 * function followed by the second type's text-input function.
	 */
	enum Path { BINARY, INOUT };

	/**
	 * The source type to be cast. Will default to the first parameter type of
	 * the associated function, if known.
	 *<p>
	 * PostgreSQL will allow this type and the function's first parameter type
	 * to differ, if there is an existing binary cast between them. That cannot
	 * be checked at compile time, so a cast with a different type given here
	 * might successfully compile but fail to deploy in PostgreSQL.
	 */
	String from() default "";

	/**
	 * The target type to cast to. Will default to the return type of
	 * the associated function, if known.
	 *<p>
	 * PostgreSQL will allow this type and the function's return type
	 * to differ, if there is an existing binary cast between them. That cannot
	 * be checked at compile time, so a cast with a different type given here
	 * might successfully compile but fail to deploy in PostgreSQL.
	 */
	String to() default "";

	/**
	 * A stock conversion path when a dedicated function is not supplied:
	 * {@code BINARY} for two types that are known to have the same internal
	 * representation, or {@code INOUT} to invoke the first type's text-output
	 * function followed by the second type's text-input function.
	 *<p>
	 * To declare an {@code INOUT} cast, {@code with=INOUT} must appear
	 * explicitly; the default value is treated as unspecified.
	 */
	Path path() default Path.INOUT;

	/**
	 * When this cast can be applied: only in explicit form, when used in
	 * assignment context, or implicitly whenever needed.
	 */
	Application application() default Application.EXPLICIT;

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
	 * for this cast. Defaults to {@code PostgreSQL}. Set explicitly to
	 * {@code ""} to emit code not wrapped in an {@code <implementor block>}.
	 */
	String implementor() default "";

	/**
	 * A comment to be associated with the cast. If left to default, and the
	 * annotated Java construct has a doc comment, its first sentence will be
	 * used. If an empty string is explicitly given, no comment will be set.
	 */
	String comment() default "";

	/**
	 * @hidden container type allowing Cast to be repeatable.
	 */
	@Documented
	@Target({ElementType.METHOD, ElementType.TYPE})
	@Retention(RetentionPolicy.CLASS)
	@interface Container
	{
		Cast[] value();
	}
}
