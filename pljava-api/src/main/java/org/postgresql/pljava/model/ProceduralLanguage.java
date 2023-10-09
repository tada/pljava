/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.model.RegProcedure.Memo;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import org.postgresql.pljava.PLPrincipal;

import org.postgresql.pljava.annotation.Function.Trust;

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
	 * a language-handler method that has been passed am argument of
	 * {@code RegProcedure<PLJavaBased>}.
	 */
	interface PLJavaBased extends Memo<PLJavaBased>
	{
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
