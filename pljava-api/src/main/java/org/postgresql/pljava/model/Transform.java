/*
 * Copyright (c) 2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.model;

import org.postgresql.pljava.PLJavaBasedLanguage.UsingTransforms; // for javadoc
import org.postgresql.pljava.annotation.Function.Effects; // for javadoc

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.model.RegProcedure.Memo.Why;

/**
 * Model of the PostgreSQL {@code pg_transform} system catalog.
 *<p>
 * A transform is a very open-ended PostgreSQL arrangement for controlling how
 * values of a target PostgreSQL type may be converted to values of some
 * appropriate data type available in a given procedural language, and back
 * again. PostgreSQL does none of this work itself, but simply provides a way to
 * <a href="https://www.postgresql.org/docs/17/sql-createtransform.html">declare
 * a transform</a> (associating a {@code fromSQL} and a {@code toSQL} function
 * with a procedural language and a PostgreSQL type), and syntax in
 * {@code CREATE FUNCTION} and {@code CREATE PROCEDURE} to indicate
 * {@linkplain RegProcedure#transformTypes() which types} should have
 * such transforms applied.
 *<p>
 * Beyond verifying, at {@code CREATE FUNCTION} or {@code CREATE PROCEDURE}
 * time, that any transforms mentioned in the declaration do exist, PostgreSQL
 * does nothing to apply any transforms when the function or procedure
 * is called. If a function's or procedure's declaration indicates any types
 * for which transforms should be applied, the full responsibility for doing so
 * (including all details of how it is done) falls to the function's or
 * procedure's implementing procedural language.
 *<p>
 * If a procedural language implementation does not contain logic to apply
 * transforms when requested, it <em>should</em> reject any function or
 * procedure with non-null {@link RegProcedure#transformTypes() transformTypes},
 * at validation time when possible. If it does not, PostgreSQL will allow
 * functions and procedures in that language to declare transforms for types,
 * and the declarations will have no effect.
 *<p>
 * For a PL/Java-based language, such declarations will always be rejected
 * if the language does not implement the {@link UsingTransforms} interface.
 */
public interface Transform extends Addressed<Transform>
{
	RegClass.Known<Transform> CLASSID =
		formClassId(TransformRelationId, Transform.class);

	interface FromSQL extends Why<FromSQL> { }
	interface ToSQL   extends Why<ToSQL>   { }

	/**
	 * The PostgreSQL data type to which this transform is intended to apply.
	 */
	RegType type();

	/**
	 * The procedural language with which this transform can be used.
	 */
	ProceduralLanguage language();

	/**
	 * Function that, at least conceptually, converts a value of
	 * {@linkplain #type() the intended PostgreSQL type} to a value of some
	 * appropriate type in the {@linkplain #language() target language}.
	 *<p>
	 * A result with {@link RegProcedure#isValid() isValid()}{@code =false}
	 * indicates that the target language should use its default from-SQL
	 * conversion for this transform's type. The language's
	 * {@link UsingTransforms#essentialTransformChecks essentialTransformChecks}
	 * method, in that case, should verify that the language has a usable
	 * default from-SQL conversion for the type.
	 *<p>
	 * Otherwise, PostgreSQL will already have ensured that this is a
	 * non-{@linkplain RegProcedure#returnsSet() set-returning},
	 * non-{@linkplain Effects#VOLATILE volatile}
	 * {@linkplain RegProcedure.Kind#FUNCTION function}
	 * declared with a {@linkplain RegProcedure#returnType() return type} of
	 * {@code INTERNAL} and a single argument of type {@code INTERNAL}.
	 *<p>
	 * There are no other assurances made by PostgreSQL, and there can be many
	 * functions with such a signature that are not transform functions at all.
	 * It will be up to the {@linkplain #language() target language} and, if it
	 * is a PL/Java-based language, its
	 * {@link UsingTransforms#essentialTransformChecks essentialTransformChecks}
	 * method, to verify (if there is any way to do so) that this function is
	 * one that the language implementation can use to convert the intended
	 * PostgreSQL type to a usable type in the target language.
	 *<p>
	 * Because both the argument and the return type are declared
	 * {@code INTERNAL}, there is no way to be sure from the declaration alone
	 * that this is a transform function expecting the transform's PostgreSQL
	 * type.
	 *<p>
	 * Whatever use is to be made of this function, including exactly what is
	 * passed as its {@code INTERNAL} argument and what it is expected to
	 * produce as its {@code INTERNAL} return type, is completely up to the
	 * {@linkplain #language() target language}. Therefore, each target language
	 * defines how to write transform functions that it can apply. A target
	 * language may impose requirements (such as what the function's
	 * {@linkplain RegProcedure#language() language of implementation} must be)
	 * to simplify the problem of determining whether the function is suitable,
	 * perhaps by inspection of the function's
	 * {@linkplain RegProcedure#src() source text} when its language of
	 * implementation is known. It is even up to the target language
	 * implementation whether this function is ever 'called' in the usual sense
	 * at all, as opposed to, say, having its source text interpreted in some
	 * other way.
	 */
	RegProcedure<FromSQL> fromSQL();

	/**
	 * Function that, at least conceptually, converts a value of
	 * some appropriate type in the {@linkplain #language() target language}
	 * to a value of {@linkplain #type() the intended PostgreSQL type}.
	 *<p>
	 * A result with {@link RegProcedure#isValid() isValid()}{@code =false}
	 * indicates that the target language should use its default to-SQL
	 * conversion for this transform's type. The language's
	 * {@link UsingTransforms#essentialTransformChecks essentialTransformChecks}
	 * method, in that case, should verify that the language has a usable
	 * default to-SQL conversion for the type.
	 *<p>
	 * Otherwise, PostgreSQL will already have ensured that this is a
	 * non-{@linkplain RegProcedure#returnsSet() set-returning},
	 * non-{@linkplain Effects#VOLATILE volatile}
	 * {@linkplain RegProcedure.Kind#FUNCTION function}
	 * declared with a {@linkplain RegProcedure#returnType() return type} of
	 * {@linkplain #type() the intended PostgreSQL type} and a single argument
	 * of type {@code INTERNAL}.
	 *<p>
	 * There are no other assurances made by PostgreSQL, and there could be
	 * functions with such a signature that are not transform functions at all.
	 * It will be up to the {@linkplain #language() target language} and, if it
	 * is a PL/Java-based language, its
	 * {@link UsingTransforms#essentialTransformChecks essentialTransformChecks}
	 * method, to verify (if there is any way to do so) that this function is
	 * one that the language implementation can use to convert the expected
	 * target-language type to the intended PostgreSQL type.
	 *<p>
	 * The return type of this function will match the transform's PostgreSQL
	 * type, but as the argument type is declared {@code INTERNAL}, there is
	 * no way to be sure from the declaration alone that the argument this
	 * function expects is what the target language implementation will pass
	 * to a transform function.
	 *<p>
	 * Whatever use is to be made of this function, including exactly what is
	 * passed as its {@code INTERNAL} argument, is completely up to the
	 * {@linkplain #language() target language}. Therefore, each target language
	 * defines how to write transform functions that it can apply. A target
	 * language may impose requirements (such as what the function's
	 * {@linkplain RegProcedure#language() language of implementation} must be)
	 * to simplify the problem of determining whether the function is suitable,
	 * perhaps by inspection of the function's
	 * {@linkplain RegProcedure#src() source text} when its language of
	 * implementation is known. It is even up to the target language
	 * implementation whether this function is ever 'called' in the usual sense
	 * at all, as opposed to, say, having its source text interpreted in some
	 * other way.
	 */
	RegProcedure<ToSQL> toSQL();
}
