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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import static java.nio.charset.StandardCharsets.US_ASCII;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

import java.util.Base64;
import java.util.BitSet;
import java.util.Objects;
import static java.util.Optional.ofNullable;

import static java.util.stream.Collectors.toList;

import org.postgresql.pljava.PLJavaBasedLanguage.InlineBlocks;
import org.postgresql.pljava.PLJavaBasedLanguage.Routines;
import org.postgresql.pljava.PLJavaBasedLanguage.Routine;
import org.postgresql.pljava.PLJavaBasedLanguage.Template;

import org.postgresql.pljava.annotation.SQLAction;

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.ProceduralLanguage;
import org.postgresql.pljava.model.ProceduralLanguage.PLJavaBased;
import org.postgresql.pljava.model.RegProcedure;
import org.postgresql.pljava.model.RegProcedure.Call;
import org.postgresql.pljava.model.RegProcedure.Lookup;
import org.postgresql.pljava.model.TupleDescriptor;
import org.postgresql.pljava.model.TupleTableSlot;

/**
 * Example of a procedural language implemented atop PL/Java.
 *<p>
 * Glot64 has a couple of features rarely found in a PostgreSQL PL. First, you
 * can't use it to do anything, other than print some text to standard output.
 * That's the server's standard output, which you probably won't even see unless
 * running the server with standard output to your terminal, as you might run a
 * test instance under PL/Java's test harness. On a production server, what is
 * written to standard output may well go nowhere at all, and then it may truly
 * be said your Glot64 routines do nothing at all.
 *<p>
 * Second, Glot64 has the rare property that the compiled form of its code is
 * easier to read than the source. That's because when you
 * {@code CREATE FUNCTION} or {@code CREATE PROCEDURE} in Glot64, you write
 * the {@code AS} part in Base64. It gets 'compiled' by decoding it to ASCII.
 * (Unless it is malformed Base64 or doesn't decode to ASCII. Then your routine
 * gets rejected by the validator. Better luck next time.) Then, when you call
 * the function or procedure, the 'compiled' code is written to standard output.
 * Therefore,
 *<pre><code>
 * CREATE FUNCTION hello() RETURNS void
 *   LANGUAGE glot64
 *   AS 'SGVsbG8sIHdvcmxkIQo=';
 *</code></pre>
 * defines a function that writes "Hello, world!" when you call it.
 *<p>
 * However, Glot64 writes several other things to standard output ahead of the
 * output of the routine itself. That is the real purpose: to illustrate how
 * PL/Java's language handler API is arranged, and how the information about the
 * parameters and result type (or types) will be presented to your code.
 *<p>
 * You can declare a Glot64 function or procedure with any number and types of
 * parameters and return type (or {@code OUT} parameters). Because your routine
 * will not use any of the arguments or produce any result, it doesn't care how
 * they are declared. By declaring Glot64 functions in several different ways,
 * you can see, in the output messages, how the API presents that information.
 */
@SQLAction(requires = "pljavahandler language", install = {
"CREATE OR REPLACE FUNCTION javatest.glot_validator(oid)" +
" RETURNS void" +
" LANGUAGE pljavahandler AS 'org.postgresql.pljava.example.polyglot.Glot64'",

"COMMENT ON FUNCTION javatest.glot_validator(oid) IS " +
"'Validator function for the glot64 procedural language'",

"CREATE LANGUAGE glot64" +
" HANDLER sqlj.pljavaDispatchRoutine" +
" INLINE  sqlj.pljavaDispatchInline" +
" VALIDATOR javatest.glot_validator",

"COMMENT ON LANGUAGE glot64 IS " +
"'The glot64 procedural language, which is implemented atop PL/Java, " +
"and supports functions, procedures, and inline code blocks'",

"CREATE FUNCTION javatest.hello() RETURNS void" +
" LANGUAGE glot64 AS 'SGVsbG8sIHdvcmxkIQo='",

"CREATE FUNCTION javatest.hello(anyelement) RETURNS anyelement" +
" LANGUAGE glot64 AS 'SGVsbG8sIHdvcmxkIQo='",

"CREATE FUNCTION javatest.hello(a int4, b int4, OUT c int2, OUT d int2)" +
" LANGUAGE glot64 AS 'SGVsbG8sIHdvcmxkIQo='",

"CREATE FUNCTION javatest.hello(text, VARIADIC \"any\") RETURNS text[]" +
" LANGUAGE glot64 AS 'SGVsbG8sIHdvcmxkIQo='",

"DO LANGUAGE glot64 'SGVsbG8sIHdvcmxkIQo='"
}, remove = {
"DROP FUNCTION javatest.hello(text,VARIADIC \"any\")",
"DROP FUNCTION javatest.hello(int4,int4)",
"DROP FUNCTION javatest.hello(anyelement)",
"DROP FUNCTION javatest.hello()",
"DO LANGUAGE glot64 'QnllIGJ5ZSEK'",
"DROP LANGUAGE glot64",
"DROP FUNCTION javatest.glot_validator(oid)"
})
public class Glot64 implements InlineBlocks, Routines
{
	private final ProceduralLanguage pl;

	/**
	 * There must be a public constructor with a {@code ProceduralLanguage}
	 * parameter.
	 *<p>
	 * The parameter can be ignored, or used to determine the name, oid,
	 * accessibility, or other details of the declared PostgreSQL language
	 * your handler class has been instantiated for.
	 */
	public Glot64(ProceduralLanguage pl)
	{
		this.pl = pl;
	}

	/**
	 * The sole method needed to implement inline code blocks.
	 *<p>
	 * This one merely writes the 'compiled' source text to standard output.
	 * @param inlineSource the source text to be executed as the inline code
	 * block.
	 * @param atomic true if top-level transaction control must be disallowed
	 * within the block. PL/Java will already handle propagating this value to
	 * underlying PostgreSQL SPI calls your code might make, but it is also
	 * available here in case your language has compilation choices it can make
	 * based on that information.
	 */
	@Override
	public void execute(String inlineSource, boolean atomic) throws SQLException
	{
		System.out.printf("%s inline code block:\n", pl)
			.print(compile(inlineSource));
	}

	/**
	 * This and {@link #additionalChecks additionalChecks} are the two methods
	 * involved in implementing a validator for the language.
	 *<p>
	 * Each method should simply return normally if its checks pass, or throw
	 * an (ideally informative) exception if not. The work is split into two
	 * methods (which need not both be supplied) because PostgreSQL does not
	 * guarantee that the validator fully ran at the time of creating any
	 * routine. Therefore, while PL/Java will normally call both methods during
	 * validation when a function or procedure is being created, it also will
	 * call {@link #essentialChecks essentialChecks} at runtime, in advance of
	 * calling {@link #prepare prepare}, so this is the place to put checks that
	 * are necessary to support assumptions {@code prepare} relies on.
	 *<p>
	 * For Glot64, this method does nothing. The only check needed at validation
	 * time is whether the source text successfully 'compiles', and the
	 * {@code prepare} method will have to compile the code anyway, so including
	 * that check here would be redundant. It can be included in
	 * {@code additionalChecks}, so a user has useful feedback at create time.
	 * @param subject the proposed Glot64 routine to be validated
	 * @param checkBody whether to perform all checks. When false, depending on
	 * details of the language being implemented, some checks may need to be
	 * skipped. PostgreSQL can call validators with {@code checkBody} false at
	 * odd times, such as during {@code pg_restore} or {@code pg_upgrade}, when
	 * not everything in the database may be as the full suite of checks would
	 * expect.
	 */
	@Override
	public void essentialChecks(
		RegProcedure<PLJavaBased> subject, boolean checkBody)
	throws SQLException
	{
		System.out.printf("%s essentialChecks: checkBody %s\n",
			subject, checkBody);
	}

	/**
	 * This and {@link #essentialChecks essentialChecks} are the two methods
	 * involved in implementing a validator for the language.
	 *<p>
	 * Each method should simply return normally if its checks pass, or throw
	 * an (ideally informative) exception if not. See
	 * {@link #essentialChecks essentialChecks} for more on why there are two
	 * methods.
	 *<p>
	 * For Glot64, this is the only method that really checks anything, namely,
	 * that the source text can be 'compiled'. That is work the {@code prepare}
	 * method must do when it is called anyway, so there is nothing to gain by
	 * having it redundantly done in {@code essentialChecks}. Doing it here at
	 * {@code CREATE} time, though, gives helpful feedback to a user.
	 * @param subject the proposed Glot64 routine to be validated
	 * @param checkBody whether to perform all checks. When false, depending on
	 * details of the language being implemented, some checks may need to be
	 * skipped. PostgreSQL can call validators with {@code checkBody} false at
	 * odd times, such as during {@code pg_restore} or {@code pg_upgrade}, when
	 * not everything in the database may be as the full suite of checks would
	 * expect.
	 */
	@Override
	public void additionalChecks(
		RegProcedure<PLJavaBased> subject, boolean checkBody)
	throws SQLException
	{
		System.out.printf("%s additionalChecks: checkBody %s: ",
			subject, checkBody);

		/*
		 * For Glot64, 'compiling' is purely a matter of string transformation
		 * and has no interaction with database state, so the judgment call can
		 * be made (as here) to include this check even when checkBody is false.
		 */
		compile(subject.src());

		System.out.printf("ok\n");
	}

	/**
	 * Prepares an executable template for a routine (a function or procedure).
	 *<p>
	 * The parameter to this method is a {@link RegProcedure}, carrying a
	 * {@link PLJavaBased PLJavaBased} memo. The {@code RegProcedure} exposes
	 * the PostgreSQL catalog information for the routine, and the memo provides
	 * some more information computed and cached by PL/Java. However, at this
	 * stage, no information from any specific call site is presented.
	 *<p>
	 * For example, the memo describes the number, names,  and types of the
	 * routine's inputs (all of its declared parameters that have mode
	 * {@code IN}, {@code INOUT}, or {@code VARIADIC}) and outputs (the ones
	 * with mode {@code INOUT}, {@code OUT}, or {@code TABLE}), each set
	 * presented in the simple form of a {@link TupleDescriptor}.
	 *<p>
	 * Because this information is based only on the routine's declaration,
	 * these tuple descriptors are called {@code inputsTemplate} and
	 * {@code outputsTemplate}. They may contain entries with polymorphic types
	 * that will be resolved to concrete types at a given call site (and
	 * possibly to different concrete types at a different call site). The
	 * {@link BitSet BitSet}s {@code unresolvedInputs} and
	 * {@code unresolvedOutputs} indicate which positions must be resolved
	 * later. If the bit sets are empty, the template {@code TupleDescriptor}s
	 * are already complete descriptors of the inputs and outputs that will be
	 * seen at call sites.
	 *<p>
	 * This method should precompute whatever it can based on the routine's
	 * catalog declaration and the template tuple descriptors only, and return
	 * a {@link Template} instance, which must depend only on this information,
	 * as it will be cached with the {@code RegProcedure} itself, independently
	 * of any call site.
	 *<p>
	 * At a call site, the {@code Template} instance's {@code specialize} method
	 * will be called on a {@link Lookup Lookup} object (conventionally called
	 * {@code flinfo}) representing the call site. At that stage, more specific
	 * information is available, such as fully-resolved {@code TupleDescriptor}s
	 * for the inputs and outputs, and which argument expressions at that call
	 * site have stable values that will not vary in succesive calls made at
	 * that site. The {@code specialize} method should use that information to
	 * generate and return a {@link Routine Routine}, a fully-resolved object
	 * with a {@code call} method ready to be cached at that call site and used
	 * for (possibly many) calls made there.
	 *<p>
	 * When the routine has no polymorphic inputs or outputs, as reported by
	 * empty {@code unresolvedInputs} and {@code unresolvedOutputs} bit sets
	 * at the {@code prepare} stage, a final {@code Routine} can be generated
	 * at that stage, and the {@code Template} returned by {@code prepare} can
	 * simply return it unconditionally (unless it wants to look at which
	 * input expressions can be treated as stable).
	 *<p>
	 * For each call at a given call site, a {@link Call Call} instance will be
	 * passed (conventionally as {@code fcinfo}) to the generated
	 * {@code Routine}'s {@code call} method. The {@code Call} object bears the
	 * actual {@link TupleTableSlot}s from which the routine will fetch its
	 * arguments and to which it will (XXX when implemented) store its
	 * result(s).
	 */
	@Override
	public Template prepare(RegProcedure<PLJavaBased> target)
	throws SQLException
	{
		PLJavaBased memo = target.memo();

		BitSet unresolvedIn  = memo.unresolvedInputs();
		BitSet unresolvedOut = memo.unresolvedOutputs();

		System.out.printf(
			"%s prepare():\n" +
			"inputsTemplate   : %s\n" +
			"unresolvedInputs : %s\n" +
			"outputsTemplate  : %s\n" +
			"unresolvedOutputs: %s\n",

			target,

			memo.inputsTemplate()
				.stream().map(Attribute::type).collect(toList()),

			unresolvedIn,

			/*
			 * Unlike inputsTemplate, outputsTemplate can return null. That can
			 * happen for two reasons: (1) the routine is declared VOID and no
			 * outputs are needed, or (2) it is declared RECORD and will rely on
			 * an output column definition list at every call site, so there is
			 * no outputsTemplate to examine in advance.
			 */
			ofNullable(memo.outputsTemplate())
				.map(t ->
					t.stream()
					.map(Attribute::type)
					.map(Object::toString)
					.collect(toList())
					.toString())
				.orElse("null"),

			/*
			 * It's also possible for unresolvedOutputs to be null, in the
			 * declared-RECORD-so-nothing-is-known-yet case. (In the VOID case,
			 * it will just be an empty BitSet, meaning no outputs need to be
			 * resolved, just as an empty BitSet would mean any other time. That
			 * makes it simple to test for canSkipResolution, as shown below.)
			 */
			Objects.toString(unresolvedOut)
		);

		boolean canSkipResolution =
			unresolvedIn.isEmpty()
				&&  null != unresolvedOut  &&  unresolvedOut.isEmpty();

		/*
		 * For this 'language', all compilation can be done early; it does not
		 * need to see resolved type descriptors from flinfo at a call site.
		 */
		String compiled = compile(target.src());

		/*
		 * This will be the Template object, cached with the RegProcedure.
		 */
		return flinfo ->
		{
			/*
			 * It might be interesting to know which arguments are 'stable' at
			 * this 'flinfo' call site, meaning they will have the same values
			 * in any number of upcoming calls at this site. In a realistic
			 * case, there might be certain arguments we'd be interested in
			 * precomputing values from, and we can use a BitSet to indicate
			 * which arguments we'd like to know the stability of, and the set
			 * returned from inputsStable will show the subset of those
			 * positions where stable expressions have been passed. For this
			 * example, we'll start by setting all bits [0,nargs) and thus ask
			 * about all the arguments.
			 */
			TupleDescriptor inDescriptor = flinfo.inputsDescriptor();
			int nargs = inDescriptor.size();
			BitSet maybeStable = new BitSet(nargs);
			maybeStable.set(0, nargs);

			System.out.printf(
				"%s Template.specialize():\n" +
				"inputsDescriptor : %s\n" +
				"inputsAreSpread  : %s\n" +
				"stableInputs     : %s\n" +
				"outputsDescriptor: %s\n",

				target,

				inDescriptor.stream().map(Attribute::type).collect(toList()),

				flinfo.inputsAreSpread(),

				flinfo.stableInputs(maybeStable),

				/*
				 * Above, outputsTemplate could return null for two reasons.
				 * The second reason no longer applies; if the routine is
				 * declared RECORD and this call site has no column definition
				 * list, outputsDescriptor throws an exception. But a null
				 * return is still possible in the VOID case.
				 *
				 * Why not an empty descriptor for VOID? An empty descriptor
				 * really occurs if a function returns t where t is a
				 * zero-column composite type. Odd thing to do, but allowed.
				 */
				ofNullable(flinfo.outputsDescriptor())
					.map(d ->
						d.stream()
						.map(Attribute::type)
						.map(Object::toString)
						.collect(toList())
						.toString())
					.orElse("null")
			);

			/*
			 * This will be the Routine object, cached with the call site.
			 */
			return fcinfo ->
			{
				System.out.printf(
					"%s Routine.call():\n" +
					"collation: %s\n" +
					"result:\n%s",
					target,
					fcinfo.collation(),
					compiled // here we 'execute' the 'compiled' routine :)
				);
			};
		};
	}

	/**
	 * This method handles 'compiling' Glot64 source code (which is Base64)
	 * into its 'compiled' form, which is ASCII and easier to read than the
	 * source.
	 *<p>
	 * It is factored out here so it can also be conveniently used at validation
	 * time.
	 *<p>
	 * The longwinded style with explicit {@code newEncoder}/{@code newDecoder}
	 * calls is used to get strict checking (instead of lax character
	 * substitution) from the encoder/decoder, to give the most, shall we say,
	 * thorough feedback to the user.
	 */
	public static String compile(String sourceText) throws SQLException
	{
		try
		{
			CharBuffer cb = CharBuffer.wrap(sourceText);
			ByteBuffer bb = US_ASCII.newEncoder().encode(cb);
			bb = Base64.getDecoder().decode(bb);
			cb = US_ASCII.newDecoder().decode(bb);
			return cb.toString();
		}
		catch ( CharacterCodingException | IllegalArgumentException e )
		{
			throw new SQLSyntaxErrorException(
				"compiling glot64 code: " + e, "42601", e);
		}
	}
}
