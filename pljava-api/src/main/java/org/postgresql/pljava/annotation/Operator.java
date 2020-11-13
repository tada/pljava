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
 * Declares a PostgreSQL {@code OPERATOR}.
 *<p>
 * May annotate a Java method (which should also carry a
 * {@link Function @Function} annotation, making it a PostgreSQL function),
 * or a class or interface (just to have a place to put it when not directly
 * annotating a method).
 *
 * @author Chapman Flack
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(Operator.Container.class)
@Retention(RetentionPolicy.CLASS)
public @interface Operator
{
	/**
	 * Distinguished value usable for {@link #commutator commutator=} to
	 * indicate that an operator is its own commutator without having to
	 * repeat its schema and name.
	 *<p>
	 * This value strictly declares that the operator is its own commutator, and
	 * therefore is only allowed for an operator with two operands of the same
	 * type. If the types are different, a commutator with the same
	 * <em>name</em> would in fact be a different operator with the operand
	 * types exchanged; use {@link #TWIN TWIN} for that.
	 */
	String SELF = ".self.";

	/**
	 * Distinguished value usable for {@link #commutator commutator=} to
	 * indicate that an operator's commutator is another operator with the same
	 * schema and name, without having to repeat them.
	 *<p>
	 * This value strictly declares that the commutator is a different
	 * operator, and therefore is only allowed for an operator with two
	 * operands of different types. As commutators, this operator and its twin
	 * will have those operand types in opposite orders, so PostgreSQL
	 * overloading will allow them to have the same name.
	 *<p>
	 * This value may also be used with {@link #synthetic synthetic=} to give
	 * the synthesized function the same schema and name as the one it is based
	 * on; this also is possible only for a function synthesized by commutation
	 * where the commuted parameter types differ.
	 */
	String TWIN = ".twin.";

	/**
	 * Name for this operator.
	 *<p>
	 * May be specified in explicit {@code {"schema","operatorname"}} form, or
	 * as a single string that will be leniently parsed as an optionally
	 * schema-qualified name. In the explicit form, {@code ""} as the schema
	 * will make the name explicitly unqualified.
	 */
	String[] name();

	/**
	 * The type of the operator's left operand, if any.
	 * Will default to the first parameter type of an associated two-parameter
	 * function, or none for an associated one-parameter function.
	 */
	String left() default "";

	/**
	 * The type of the operator's right operand, if any.
	 * Will default to the second parameter type of an associated two-parameter
	 * function, or the parameter type for an associated one-parameter function.
	 */
	String right() default "";

	/**
	 * Name of the function backing the operator; may be omitted if this
	 * annotation appears on a method.
	 *<p>
	 * The function named here must take one parameter of the matching type if
	 * only one of {@code left} or {@code right} is specified, or the
	 * {@code left} and {@code right} types in that order if both are present.
	 */
	String[] function() default {};

	/**
	 * Name of a function to be synthesized by PL/Java based on the method this
	 * annotation appears on and this operator's {@code commutator} or
	 * {@code negator} relationship to another operator declared on the same
	 * method.
	 *<p>
	 * Only allowed in an annotation on a Java method, and where
	 * {@code function} is not specified.
	 *<p>
	 * The special value {@link #TWIN TWIN} is allowed, to avoid repeating the
	 * schema and name when the desired name for the synthesized function is the
	 * same as the one it is derived from (which is only possible if the
	 * derivation involves commuting the arguments and their types are
	 * different, so the two functions can be distinguished by overloading). A
	 * typical case would be the twin of a cross-type function like {@code add}
	 * that is commutative, so using the same name makes sense.
	 */
	String[] synthetic() default {};

	/**
	 * Name of an operator that is the commutator of this one.
	 *<p>
	 * Specified in the same ways as {@code name}. The value
	 * {@link #SELF SELF} can be used to avoid repeating the schema and name
	 * for the common case of an operator that is its own commutator. The value
	 * {@link #TWIN TWIN} can likewise declare that the commutator is the
	 * different operator with the same name and schema but the operand types
	 * (which must be different) reversed. A typical case would be the twin of a
	 * cross-type operator like {@code +} that is commutative, so using the same
	 * name makes sense.
	 */
	String[] commutator() default {};

	/**
	 * Name of an operator that is the negator of this one.
	 *<p>
	 * Specified in the same ways as {@code name}.
	 */
	String[] negator() default {};

	/**
	 * Whether this operator can be used in computing a hash join.
	 *<p>
	 * Only sensible for a boolean-valued binary operator, which must have a
	 * commutator in the same hash index operator family, with the underlying
	 * functions marked {@link Function.Effects#IMMUTABLE} or
	 * {@link Function.Effects#STABLE}.
	 */
	boolean hashes() default false;

	/**
	 * Whether this operator can be used in computing a merge join.
	 *<p>
	 * Only sensible for a boolean-valued binary operator, which must have a
	 * commutator also appearing as an equality member in the same btree index
	 * operator family, with the underlying functions marked
	 * {@link Function.Effects#IMMUTABLE} or {@link Function.Effects#STABLE}.
	 */
	boolean merges() default false;

	/**
	 * Name of a function that can estimate the selectivity of this operator
	 * when used in a {@code WHERE} clause.
	 *<p>
	 * Specified in the same ways as {@code function}.
	 *<p>
	 * A custom estimator is a complex undertaking (and, at present, requires
	 * a language other than Java), but several predefined ones can be found in
	 * {@link SelectivityEstimators}.
	 */
	String[] restrict() default {};

	/**
	 * Name of a function that can estimate the selectivity of this operator
	 * when used in a join.
	 *<p>
	 * Specified in the same ways as {@code function}.
	 *<p>
	 * A custom estimator is a complex undertaking (and, at present, requires
	 * a language other than Java), but several predefined ones can be found in
	 * {@link SelectivityEstimators}.
	 */
	String[] join() default {};

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
	 * for this operator. Defaults to {@code PostgreSQL}. Set explicitly to
	 * {@code ""} to emit code not wrapped in an {@code <implementor block>}.
	 */
	String implementor() default "";

	/**
	 * A comment to be associated with the operator. If left to default, and the
	 * annotated Java construct has a doc comment, its first sentence will be
	 * used. If an empty string is explicitly given, no comment will be set.
	 */
	String comment() default "";

	/**
	 * Names of several functions predefined in PostgreSQL for estimating the
	 * selectivity of operators in restriction clauses or joins.
	 */
	interface SelectivityEstimators
	{
		/**
		 * A restriction-selectivity estimator suitable for an operator
		 * with rather high selectivity typical of an operator like {@code =}.
		 */
		String EQSEL = "pg_catalog.eqsel";

		/**
		 * A restriction-selectivity estimator suitable for an operator
		 * somewhat less strict than a typical {@code =} operator.
		 */
		String MATCHINGSEL = "pg_catalog.matchingsel";

		/**
		 * A restriction-selectivity estimator suitable for an operator
		 * with rather low selectivity typical of an operator like {@code <>}.
		 */
		String NEQSEL = "pg_catalog.neqsel";

		/**
		 * A restriction-selectivity estimator suitable for an operator
		 * with selectivity typical of an operator like {@code <}.
		 */
		String SCALARLTSEL = "pg_catalog.scalarltsel";

		/**
		 * A restriction-selectivity estimator suitable for an operator
		 * with selectivity typical of an operator like {@code <=}.
		 */
		String SCALARLESEL = "pg_catalog.scalarlesel";

		/**
		 * A restriction-selectivity estimator suitable for an operator
		 * with selectivity typical of an operator like {@code >}.
		 */
		String SCALARGTSEL = "pg_catalog.scalargtsel";

		/**
		 * A restriction-selectivity estimator suitable for an operator
		 * with selectivity typical of an operator like {@code >=}.
		 */
		String SCALARGESEL = "pg_catalog.scalargesel";

		/**
		 * A join-selectivity estimator suitable for an operator
		 * with rather high selectivity typical of an operator like {@code =}.
		 */
		String EQJOINSEL = "pg_catalog.eqjoinsel";

		/**
		 * A join-selectivity estimator suitable for an operator
		 * somewhat less strict than a typical {@code =} operator.
		 */
		String MATCHINGJOINSEL = "pg_catalog.matchingjoinsel";

		/**
		 * A join-selectivity estimator suitable for an operator
		 * with rather low selectivity typical of an operator like {@code <>}.
		 */
		String NEQJOINSEL = "pg_catalog.neqjoinsel";

		/**
		 * A join-selectivity estimator suitable for an operator
		 * with selectivity typical of an operator like {@code <}.
		 */
		String SCALARLTJOINSEL = "pg_catalog.scalarltjoinsel";

		/**
		 * A join-selectivity estimator suitable for an operator
		 * with selectivity typical of an operator like {@code <=}.
		 */
		String SCALARLEJOINSEL = "pg_catalog.scalarlejoinsel";

		/**
		 * A join-selectivity estimator suitable for an operator
		 * with selectivity typical of an operator like {@code >}.
		 */
		String SCALARGTJOINSEL = "pg_catalog.scalargtjoinsel";

		/**
		 * A join-selectivity estimator suitable for an operator
		 * with selectivity typical of an operator like {@code >=}.
		 */
		String SCALARGEJOINSEL = "pg_catalog.scalargejoinsel";

		/**
		 * A join-selectivity estimator suitable for an operator
		 * doing 2-D area-based comparisons.
		 */
		String AREAJOINSEL = "pg_catalog.areajoinsel";

		/**
		 * A join-selectivity estimator suitable for an operator
		 * doing 2-D position-based comparisons.
		 */
		String POSITIONJOINSEL = "pg_catalog.positionjoinsel";

		/**
		 * A join-selectivity estimator suitable for an operator
		 * doing 2-D containment-based comparisons.
		 */
		String CONTJOINSEL = "pg_catalog.contjoinsel";
	}

	/**
	 * @hidden container type allowing Operator to be repeatable.
	 */
	@Documented
	@Target({ElementType.METHOD, ElementType.TYPE})
	@Retention(RetentionPolicy.CLASS)
	@interface Container
	{
		Operator[] value();
	}
}
