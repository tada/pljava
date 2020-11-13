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
 * Declares a PostgreSQL aggregate.
 *<p>
 * An aggregate function in PostgreSQL is defined by using
 * {@code CREATE AGGREGATE} to specify its name and argument types, along with
 * at least one "plan" for evaluating it, where the plan specifies at least:
 * a data type to use for the accumulating state, and a function (here called
 * "accumulate") called for each row to update the state. If the plan includes
 * a function "finish", its return type is the return type of the aggregate;
 * with no "finish" function, the state type is also the aggregate's return
 * type.
 *<p>
 * Optionally, a plan can include a "combine" function, which is passed two
 * instances of the state type and combines them, to allow aggregate evaluation
 * to be parallelized. The names "accumulate", "combine", and "finish" are not
 * exactly as used in the PostgreSQL command (those are unpronounceable
 * abbreviations), but follow the usage in {@code java.util.stream.Collector},
 * which should make them natural to Java programmers. PL/Java will generate the
 * SQL with the unpronounceable names.
 *<p>
 * If an aggregate function might be used in a window with a moving frame start,
 * it can be declared with a second plan ({@code movingPlan}) that includes a
 * "remove" function that may be called, passing values that were earlier
 * accumulated into the state, to remove them again as the frame start advances
 * past them. (Java's {@code Collector} has no equivalent of a "remove"
 * function.) A "remove" function may only be specified (and must be specified)
 * in a plan given as {@code movingPlan}.
 *<p>
 * Any function referred to in a plan is specified by its name, optionally
 * schema-qualified. Its argument types are not specified; they are implied by
 * those declared for the aggregate itself. An "accumulate" function gets one
 * argument of the state type, followed by all those given as {@code arguments}.
 * The same is true of a "remove" function. A "combine" function is passed
 * two arguments of the state type.
 *<p>
 * A "finish" function has a first argument of the state type. If the aggregate
 * is declared with any {@code directArguments}, those follow the state type.
 * (Declaring {@code directArguments} makes the aggregate an "ordered-set
 * aggregate", which could additionally have {@code hypothetical=true} to make
 * it a "hypothetical-set aggregate", for which the PostgreSQL documentation
 * covers the details.) If {@code polymorphic=true}, the "finish" function's
 * argument list will end with {@code arguments.length} additional arguments;
 * they will all be passed as {@code NULL} when the finisher is called, but will
 * have the right run-time types, which may be necessary to resolve the
 * finisher's return type, if polymorphic types are involved.
 *<p>
 * If any of the functions or types mentioned in this declaration are also being
 * generated into the same deployment descriptor, the {@code CREATE AGGREGATE}
 * generated from this annotation will follow them. Other ordering dependencies,
 * if necessary, can be explicitly arranged with {@code provides} and
 * {@code requires}.
 *<p>
 * While this annotation can generate {@code CREATE AGGREGATE} deployment
 * commands with the features available in PostgreSQL,
 * at present there are limits to which aggregate features can be implemented
 * purely in PL/Java. In particular, PL/Java functions currently have no access
 * to the PostgreSQL data structures needed for an ordered-set or
 * hypothetical-set aggregate. Such an aggregate could be implemented by writing
 * some of its support functions in another procedural language; this annotation
 * could still be used to automatically generate the declaration.
 * @author Chapman Flack
 */
@Documented
@Target({ElementType.TYPE,ElementType.METHOD})
@Repeatable(Aggregate.Container.class)
@Retention(RetentionPolicy.CLASS)
public @interface Aggregate
{
	/**
	 * Declares the effect of the {@code finish} function in a {@code Plan}.
	 *<p>
	 * If {@code READ_ONLY}, PostgreSQL can continue updating the same state
	 * with additional rows, and call the finisher again for updated results.
	 *<p>
	 * If {@code SHAREABLE}, the state cannot be further updated after
	 * a finisher call, but finishers for other aggregates that use the same
	 * state representation (and are also {@code SHAREABLE}) can be called to
	 * produce the results for those aggregates. An example could be the several
	 * linear-regression-related aggregates, all of which can work from a state
	 * that contains the count of values, sum of values, and sum of squares.
	 *<p>
	 * If {@code READ_WRITE}, no further use can be made of the state after
	 * the finisher has run.
	 */
	enum FinishEffect { READ_ONLY, SHAREABLE, READ_WRITE };

	/**
	 * Specifies one "plan" for evaluating the aggregate; one must always be
	 * specified (as {@code plan}), and a second may be specified (as
	 * {@code movingPlan}).
	 *<p>
	 * A plan must specify a data type {@code stateType} to hold the
	 * accumulating state, optionally an estimate of its expected size in bytes,
	 * and optionally its initial contents. The plan also specifies up to four
	 * functions {@code accumulate}, {@code combine}, {@code finish}, and
	 * {@code remove}. Only {@code accumulate} is always required;
	 * {@code remove} is required in a {@code movingPlan}, and otherwise not
	 * allowed.
	 *<p>
	 * Each of the four functions may be specified as a single string "name",
	 * which will be leniently parsed as an optionally schema-qualified name,
	 * or as two strings {@code {"schema","local"}} with the schema made
	 * explicit. The two-string form with {@code ""} as the schema represents
	 * an explicitly non-schema-qualified name.
	 */
	@Documented
	@Target({})
	@Retention(RetentionPolicy.CLASS)
	@interface Plan
	{
		/**
		 * The data type to be used to hold the accumulating state.
		 *<p>
		 * This will be the first argument type for all of the support functions
		 * except {@code deserialize} (both argument types for {@code combine})
		 * and also, if there is no {@code finish} function, the result type
		 * of the aggregate.
		 */
		String stateType() default "";

		/**
		 * An optional estimate of the size in bytes that the state may grow
		 * to occupy.
		 */
		int stateSize() default 0;

		/**
		 * An optional initial value for the state (which will otherwise be
		 * initially null).
		 *<p>
		 * Must be a string the state type's text-input conversion would accept.
		 *<p>
		 * Omitting the initial value only works if the {@code accumulate}
		 * function is {@code onNullInput=CALLED}, or if the aggregate's first
		 * argument type is the same as the state type.
		 */
		String initialState() default "";

		/**
		 * Name of the function that will be called for each row being
		 * aggregated.
		 *<p>
		 * The function named here must have an argument list that starts with
		 * one argument of the state type, followed by all of this aggregate's
		 * {@code arguments}. It does not receive the {@code directArguments},
		 * if any.
		 */
		String[] accumulate() default {};

		/**
		 * Name of an optional function to combine two instances of the state
		 * type.
		 *<p>
		 * The function named here should be one that has two arguments, both
		 * of the state type, and returns the state type.
		 */
		String[] combine() default {};

		/**
		 * Name of an optional function to produce the aggregate's result from
		 * the final value of the state; without this function, the aggregate's
		 * result type is the state type, and the result is simply the final
		 * value of the state.
		 *<p>
		 * When this function is specified, its result type determines the
		 * result type of the aggregate. Its argument list signature is a single
		 * argument of the state type, followed by all the
		 * {@code directArguments} if any, followed (only if {@code polymorphic}
		 * is true) by {@code arguments.length} additional arguments for which
		 * nulls will be passed at runtime but with their resolved runtime
		 * types.
		 */
		String[] finish() default {};

		/**
		 * Name of an optional function that can reverse the effect on the state
		 * of a row previously passed to {@code accumulate}.
		 *<p>
		 * The function named here should have the same argument list signature
		 * as the {@code accumulate} function.
		 *<p>
		 * Required in a {@code movingPlan}; not allowed otherwise.
		 */
		String[] remove() default {};

		/**
		 * Whether the argument list for {@code finish} should be extended with
		 * slots corresponding to the aggregated {@code arguments}, all nulls at
		 * runtime but with their resolved runtime types.
		 */
		boolean polymorphic() default false;

		/**
		 * The effect of the {@code finish} function in this {@code Plan}.
		 *<p>
		 * If {@code READ_ONLY}, PostgreSQL can continue updating the same
		 * state with additional rows, and call the finisher again for updated
		 * results.
		 *<p>
		 * If {@code SHAREABLE}, the state cannot be further updated after a
		 * finisher call, but finishers for other aggregates that use the same
		 * state representation (and are also {@code SHAREABLE}) can be called
		 * to produce the results for those aggregates. An example could be the
		 * several linear-regression-related aggregates, all of which can work
		 * from a state that contains the count of values, sum of values, and
		 * sum of squares.
		 *<p>
		 * If {@code READ_WRITE}, no further use can be made of the state after
		 * the finisher has run.
		 *<p>
		 * Leaving this to default is not exactly equivalent to specifying the
		 * default value shown here. If left to default, it will be left
		 * unspecified in the generated {@code CREATE AGGREGATE}, and PostgreSQL
		 * will apply its default, which is {@code READ_ONLY} in the case of an
		 * ordinary aggregate, but {@code READ_WRITE} for an ordered-set or
		 * hypothetical-set aggregate.
		 */
		FinishEffect finishEffect() default FinishEffect.READ_ONLY;

		/**
		 * Name of a serializing function ({@code internal} to {@code bytea}),
		 * usable only if a {@link #combine() combine} function is specified and
		 * the aggregate's state type is {@code internal}.
		 *<p>
		 * Not allowed in a {@code movingPlan}. Not allowed without
		 * {@code deserialize}.
		 */
		String[] serialize() default {};

		/**
		 * Name of a deserializing function (({@code bytea}, {@code internal})
		 * to {@code internal}), usable only if a {@code serialize} function is
		 * also specified.
		 *<p>
		 * Not allowed in a {@code movingPlan}.
		 */
		String[] deserialize() default {};
	}

	/**
	 * Name for this aggregate.
	 *<p>
	 * May be specified in explicit {@code {"schema","localname"}} form, or as
	 * a single string that will be leniently parsed as an optionally
	 * schema-qualified name. In the explicit form, {@code ""} as the schema
	 * will make the name explicitly unqualified (in case the local name might
	 * contain a dot and be misread as a qualified name).
	 *<p>
	 * When this annotation is not placed on a method, there is no default, and
	 * a name must be supplied. When the annotation is on a method (which can be
	 * either the {@code accumulate} or the {@code finish} function for the
	 * aggregate), the default name will be the same as the SQL name given for
	 * the function. That is typically possible because the parameter signature
	 * for the aggregate function will not be the same as either the
	 * {@code accumulate} or the {@code finish} function. The exception is if
	 * the annotation is on the {@code finish} function and the aggregate has
	 * exactly one parameter of the same type as the state; in that case another
	 * name must be given here.
	 */
	String[] name() default {};

	/**
	 * Names and types of the arguments to be aggregated: the ones passed to the
	 * {@code accumulate} function for each aggregated row.
	 *<p>
	 * Each element is a name and a type specification, separated by whitespace.
	 * An element that begins with whitespace declares a parameter with no
	 * name, only a type. The name is an ordinary SQL identifier; if it would
	 * be quoted in SQL, naturally each double-quote must be represented as
	 * {@code \"} in Java.
	 *<p>
	 * When this annotation does not appear on a method, there is no default,
	 * and arguments must be declared here. If the annotation appears on a
	 * method supplying the {@code accumulate} function, this element can be
	 * omitted, and the arguments will be those of the function (excepting the
	 * first one, which corresponds to the state).
	 */
	String[] arguments() default {};

	/**
	 * Names and types of the "direct arguments" to an ordered-set or
	 * hypothetical-set aggregate (specifying this element is what makes an
	 * ordered-set aggregate, which will be a hypothetical-set aggregate if
	 * {@code hypothetical=true} is also supplied).
	 *<p>
	 * Specified as for {@code arguments}. The direct arguments are not passed
	 * to the {@code accumulate} function for each aggregated row; they are only
	 * passed to the {@code finish} function when producing the result.
	 */
	String[] directArguments() default {};

	/**
	 * Specify {@code true} in an ordered-set aggregate (one with
	 * {@code directArguments} specified) to make it a hypothetical-set
	 * aggregate.
	 *<p>
	 * When {@code true}, the {@code directArguments} list must be at least as
	 * long as {@code arguments}, and its last {@code arguments.length} types
	 * must match {@code arguments} one-to-one. When the {@code finish} function
	 * is called, those last direct arguments will carry the caller-supplied
	 * values for the "hypothetical" row.
	 */
	boolean hypothetical() default false;

	/**
	 * Whether the aggregate has a variadic last argument.
	 *<p>
	 * Specify as a single boolean, {@code variadic=true}, to declare an
	 * ordinary aggregate variadic. The last type of its declared
	 * {@code arguments} must then be either an array type, or
	 * {@code pg_catalog."any"}
	 *<p>
	 * The form {@code variadic={boolean,boolean}} is for an ordered-set
	 * aggregate, which has both a list of {@code directArguments} (the first
	 * boolean) and its aggregated {@code arguments} (the second). For an
	 * ordered-set aggregate, {@code "any"} is the only allowed type for a
	 * variadic argument.
	 *<p>
	 * When also {@code hypothetical} is true, the requirement that the
	 * {@code directArguments} have a tail matching the {@code arguments}
	 * implies that the two lists must both or neither be variadic.
	 */
	boolean[] variadic() default {};

	/**
	 * The {@link Plan Plan} normally to be used for evaluating this aggregate,
	 * except possibly in a moving-window context if {@code movingPlan} is also
	 * supplied.
	 *<p>
	 * Though declared as an array, only one plan is allowed here. It may not
	 * name a {@code remove} function; only a {@code movingPlan} can do that.
	 * This plan can be omitted only if the {@code @Aggregate} annotation
	 * appears on a Java method intended as the {@code accumulate} function and
	 * the rest of the plan is all to be inferred or defaulted.
	 */
	Plan[] plan() default {};


	/**
	 * An optional {@link Plan Plan} that may be more efficient for evaluating
	 * this aggregate in a moving-window context.
	 *<p>
	 * Though declared as an array, only one plan is allowed here. It must
	 * name a {@code remove} function.
	 *<p>
	 * A {@code movingPlan} may not have {@code serialize}/{@code deserialize}
	 * functions; only {@code plan} can have those.
	 */
	Plan[] movingPlan() default {};

	/**
	 * Parallel-safety declaration for this aggregate; PostgreSQL's planner
	 * will consult this only, not the declarations on the individual supporting
	 * functions.
	 *<p>
	 * See {@link Function#parallel() Function.parallel} for the implications.
	 * In PL/Java, any setting other than {@code UNSAFE} should be considered
	 * experimental.
	 */
	Function.Parallel parallel() default Function.Parallel.UNSAFE;

	/**
	 * Name of an operator (declared as either the less-than or greater-than
	 * strategy member of a {@code btree} operator class) such that the result
	 * of this aggregate is the same as the first result from {@code ORDER BY}
	 * over the aggregated values, using this operator.
	 *<p>
	 * May be specified in explicit {@code {"schema","localname"}} form, or as
	 * a single string that will be leniently parsed as an optionally
	 * schema-qualified name. In the explicit form, {@code ""} as the schema
	 * will make the name explicitly unqualified. The operator will be assumed
	 * to have two operands of the same type as the argument to the aggregate
	 * (which must have exactly one aggregated argument, and no direct
	 * arguments). The operator's membership in a {@code btree} operator class
	 * is not (currently) checked at compile time, but if it does not hold at
	 * run time, the optimization will not be used.
	 */
	String[] sortOperator() default {};

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
	 * for this aggregate. Defaults to {@code PostgreSQL}. Set explicitly to
	 * {@code ""} to emit code not wrapped in an {@code <implementor block>}.
	 */
	String implementor() default "";

	/**
	 * A comment to be associated with the aggregate. The default is no comment
	 * if the annotation does not appear on a method, or the first sentence of
	 * the method's Javadoc comment, if any, if it does.
	 */
	String comment() default "";

	/**
	 * @hidden container type allowing Cast to be repeatable.
	 */
	@Documented
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.CLASS)
	@interface Container
	{
		Aggregate[] value();
	}
}
