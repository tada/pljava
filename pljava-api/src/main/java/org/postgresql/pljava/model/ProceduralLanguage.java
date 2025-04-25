/*
 * Copyright (c) 2022-2025 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.model.RegProcedure.Call;
import org.postgresql.pljava.model.RegProcedure.Lookup;
import org.postgresql.pljava.model.RegProcedure.Memo;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier; // javadoc
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import org.postgresql.pljava.PLJavaBasedLanguage; // javadoc
import org.postgresql.pljava.PLPrincipal;

import org.postgresql.pljava.annotation.Function.Trust;

import java.util.BitSet;
import java.util.List;

/**
 * Model of a PostgreSQL procedural language, including (for non-built-in
 * languages, like PL/Java) the handler functions used in its implementation.
 */
public interface ProceduralLanguage
extends
	Addressed<ProceduralLanguage>, Named<Simple>, Owned, AccessControlled<USAGE>
{
	RegClass.Known<ProceduralLanguage> CLASSID =
		formClassId(LanguageRelationId, ProceduralLanguage.class);

	/**
	 * The well-known language "internal", for routines implemented within
	 * PostgreSQL itself.
	 */
	ProceduralLanguage INTERNAL = formObjectId(CLASSID, INTERNALlanguageId);

	/**
	 * The well-known language "c", for extension routines implemented using
	 * PostgreSQL's C language conventions.
	 */
	ProceduralLanguage        C = formObjectId(CLASSID,        ClanguageId);

	/**
	 * The well-known language "sql", for routines in that PostgreSQL
	 * built-in language.
	 */
	ProceduralLanguage      SQL = formObjectId(CLASSID,      SQLlanguageId);

	interface Handler       extends Memo<Handler> { }
	interface InlineHandler extends Memo<InlineHandler> { }
	interface Validator     extends Memo<Validator> { }

	/**
	 * A {@link Memo} attached to a {@link RegProcedure} that represents
	 * a PL/Java-based routine, retaining additional information useful to
	 * a PL/Java-based language implementation.
	 *<p>
	 * A valid memo of this type may be obtained within the body of
	 * a language-handler method that has been passed an argument of
	 * {@code RegProcedure<PLJavaBased>}.
	 */
	interface PLJavaBased extends Memo<PLJavaBased>
	{
		/**
		 * A {@link TupleDescriptor} describing the expected parameters, based
		 * only on the routine declaration.
		 *<p>
		 * The {@code TupleDescriptor} returned here depends only on the static
		 * catalog information for the {@link RegProcedure} carrying this memo.
		 * A language handler can use it to generate template code that can be
		 * cached with the target {@code RegProcedure}, independently of any one
		 * call site.
		 *<p>
		 * {@link Identifier.None} may be encountered among the member names;
		 * parameters do not have to be named.
		 *<p>
		 * Some reported types may have
		 * {@link RegType#needsResolution needsResolution} true, and require
		 * resolution to specific types using the expression context at
		 * a given call site.
		 *<p>
		 * For a routine declared variadic, if the declared type of the variadic
		 * parameter is the wildcard {@code "any"} type,
		 * {@link Call#arguments arguments()}{@code .size()} at a call site can
		 * differ from {@code inputsTemplate().size()}, the variadic arguments
		 * delivered in "spread" form as distinct (and individually typed)
		 * arguments. Variadic arguments of any other declared type are always
		 * delivered in "collected" form as a PostgreSQL array of that type.
		 * A variadic {@code "any"} routine can also receive its arguments
		 * collected, when it has been called that way; therefore, there is an
		 * ambiguity when such a routine is called with a single array argument
		 * in the variadic position. A language handler must call
		 * {@link Lookup#inputsAreSpread Lookup.inputsAreSpread()} to determine
		 * the caller's intent in that case.
		 * @see #unresolvedInputs()
		 */
		TupleDescriptor inputsTemplate();

		/**
		 * A {@code BitSet} indicating (by zero-based index into
		 * {@link #inputsTemplate inputsTemplate}) which of the input
		 * parameter types need resolution against actual supplied argument
		 * types at a call site.
		 *<p>
		 * If the set is empty, such per-call-site resolution can be skipped.
		 * @return a cloned {@code BitSet}
		 */
		BitSet unresolvedInputs();

		/**
		 * A {@link TupleDescriptor} describing the expected result, based
		 * only on the routine declaration.
		 *<p>
		 * The {@code TupleDescriptor} returned here depends only on the static
		 * catalog information for the {@link RegProcedure} carrying this memo.
		 * A language handler can use it to generate template code that can be
		 * cached with the target {@code RegProcedure}, independently of any one
		 * call site.
		 *<p>
		 * For a function whose return type (in SQL) is not composite (or
		 * a function with only one output parameter, which PostgreSQL treats
		 * the same way), this method returns a synthetic ephemeral descriptor
		 * with one attribute of the declared return type. This convention
		 * allows {@link TupleTableSlot} to be the uniform API for the data type
		 * conversions to and from PostgreSQL, regardless of how a routine
		 * is declared.
		 *<p>
		 * This method returns null in two cases: if the target returns
		 * {@link RegType#VOID VOID} and no descriptor is needed, or if the
		 * target is a function whose call sites must supply a column definition
		 * list, so there is no template descriptor that can be cached with
		 * the routine proper. A descriptor can only be obtained later from
		 * {@link RegProcedure.Lookup#outputsDescriptor outputsDescriptor()}
		 * when a call site is at hand.
		 *<p>
		 * Some reported types may have
		 * {@link RegType#needsResolution needsResolution} true, and require
		 * resolution to specific types using the expression context at
		 * a given call site.
		 *<p>
		 * {@link Identifier.None} will be the name of the single attribute in
		 * the synthetic descriptor wrapping a scalar. Because PL/Java's
		 * function dispatcher will undo the wrapping to return a scalar
		 * to PostgreSQL, the name matters not.
		 * @see #unresolvedOutputs()
		 * @return a {@code TupleDescriptor}, null if the target returns
		 * {@code VOID}, or is a function and can only be called with
		 * a column definition list supplied at the call site.
		 */
		TupleDescriptor outputsTemplate();

		/**
		 * A {@code BitSet} indicating (by zero-based index into
		 * {@link #outputsTemplate outputsTemplate}) which
		 * result types need resolution against actual supplied argument types
		 * at each call site.
		 *<p>
		 * If the set is empty, such per-call-site resolution can be skipped.
		 * @return a cloned {@code BitSet}. In the two circumstances where
		 *{@link #outputsTemplate outputsTemplate} returns null, this method
		 * returns either null or an empty {@code BitSet}. It is null for the
		 * unspecified-record-returning case, where a column definition list
		 * must be consulted at each call site; it is an empty set for the
		 * {@code VOID}-returning case where no further resolution is needed
		 * (just as an empty {@code BitSet} here would normally indicate).
		 */
		BitSet unresolvedOutputs();

		/**
		 * A list of {@link Transform} instances (null if none) indicating
		 * transforms to be applied on data types supplied to or supplied by
		 * this routine.
		 *<p>
		 * When this method returns a non-null result, each {@code Transform}
		 * in the list has already been checked by the language implementation's
		 * {@link PLJavaBasedLanguage.UsingTransforms#essentialTransformChecks
		 * essentialTransformChecks} method. Any exceptions those checks might
		 * throw should have been thrown when the dispatcher invoked this method
		 * before dispatching to the language handler, so a language handler
		 * using this method need not normally expect to handle them.
		 */
		List<Transform> transforms();
	}

	default Trust trust()
	{
		return principal().trust();
	}

	PLPrincipal principal();
	RegProcedure<Handler> handler();
	RegProcedure<InlineHandler> inlineHandler();
	RegProcedure<Validator> validator();
}
