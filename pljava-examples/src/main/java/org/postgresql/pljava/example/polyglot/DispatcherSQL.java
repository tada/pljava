/*
 * Copyright (c) 2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.polyglot;

import org.postgresql.pljava.annotation.SQLAction;

/**
 * An empty class that only carries {@link SQLAction SQLAction} annotations to
 * set up the main PL/Java dispatcher functions and (meta)"language".
 *<p>
 * Naturally, these will eventually be automated as part of
 * {@code CREATE EXTENSION pljava}, but for now, they are just here.
 */
@SQLAction(provides="pljavahandler language", install={
"DO LANGUAGE plpgsql '" +
" DECLARE" +
"  qbin text;" +
" BEGIN" +
"  SELECT quote_literal(probin) INTO STRICT qbin FROM pg_proc" +
"   WHERE oid = ''sqlj.java_call_handler()''::regprocedure;" +
"  EXECUTE ''" +
"   CREATE OR REPLACE FUNCTION sqlj.pljavaDispatchValidator(pg_catalog.oid)" +
"    RETURNS pg_catalog.void" +
"    LANGUAGE C AS '' || qbin || '', ''''pljavaDispatchValidator''''" +
"  '';" +
"  EXECUTE ''" +
"   CREATE OR REPLACE FUNCTION sqlj.pljavaDispatchValidator()" +
"    RETURNS pg_catalog.language_handler" +
"    LANGUAGE C AS '' || qbin || '', ''''pljavaDispatchValidator''''" +
"  '';" +
"  EXECUTE ''" +
"   CREATE OR REPLACE FUNCTION sqlj.pljavaDispatchRoutine()" +
"    RETURNS pg_catalog.language_handler" +
"    LANGUAGE C AS '' || qbin || '', ''''pljavaDispatchRoutine''''" +
"  '';" +
"  EXECUTE ''" +
"   CREATE OR REPLACE FUNCTION sqlj.pljavaDispatchInline(pg_catalog.internal)" +
"    RETURNS pg_catalog.void" +
"    LANGUAGE C AS '' || qbin || '', ''''pljavaDispatchInline''''" +
"  '';" +
"END'",

"COMMENT ON FUNCTION sqlj.pljavaDispatchValidator(oid) IS " +
"'The validator function for the \"PL/Java handler\" language (in which one " +
"can only write functions that are validators of actual procedural languages " +
"implemented atop PL/Java).'",

"COMMENT ON FUNCTION sqlj.pljavaDispatchValidator() IS " +
"'The call handler for the \"PL/Java handler\" language (in which one " +
"can only write functions that are validators of actual procedural languages " +
"implemented atop PL/Java). The C entry point is the same as for the " +
"validator handler, which works because the only functions that can be " +
"written in this \"language\" are validators.'",

"COMMENT ON FUNCTION sqlj.pljavaDispatchRoutine() IS " +
"'The call handler that must be named (as HANDLER) in CREATE LANGUAGE for " +
"a procedural language implemented atop PL/Java. (PostgreSQL requires every " +
"CREATE LANGUAGE to include HANDLER, but PL/Java allows a language to " +
"simply not implement the Routines interface, if it is only intended for " +
"InlineBlocks.)'",

"COMMENT ON FUNCTION sqlj.pljavaDispatchInline(pg_catalog.internal) IS " +
"'The handler that must be named (as INLINE) in CREATE LANGUAGE for " +
"a procedural language implemented atop PL/Java, if that language is " +
"intended to support inline code blocks.'",

"CREATE LANGUAGE pljavahandler" +
" HANDLER sqlj.pljavaDispatchValidator" +
" VALIDATOR sqlj.pljavaDispatchValidator",

"COMMENT ON LANGUAGE pljavahandler IS " +
"'The PL/Java \"handler language\", used in implementing other procedural " +
"languages atop PL/Java. Only one kind of function can be written in this " +
"\"language\", namely, a validator function, and the AS string of such a " +
"validator function is simply the name of a Java class that must implement " +
"one or both of PLJavaBasedLanguage.Routines or " +
"PLJavaBasedLanguage.InlineBlocks, and that class will be used as the " +
"implementation of the new language.'"
}, remove={
"DROP LANGUAGE pljavahandler",
"DROP FUNCTION sqlj.pljavaDispatchInline(internal)",
"DROP FUNCTION sqlj.pljavaDispatchRoutine()",
"DROP FUNCTION sqlj.pljavaDispatchValidator()",
"DROP FUNCTION sqlj.pljavaDispatchValidator(oid)"
})
public abstract class DispatcherSQL
{
}
