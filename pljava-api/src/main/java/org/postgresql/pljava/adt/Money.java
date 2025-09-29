/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.adt;

import java.math.BigDecimal; // javadoc

import java.text.NumberFormat; // javadoc

import java.util.Currency; // javadoc
import java.util.Locale;   // javadoc

import org.postgresql.pljava.Adapter.Contract;

/**
 * The {@code MONEY} type's PostgreSQL semantics: an integer value, whose
 * scaling, display format, and currency are all determined by a
 * user-settable configuration setting.
 *<p>
 * This type is a strange duck in PostgreSQL. It is stored
 * as a (64 bit) integer, and must have a scaling applied on input and
 * output to the appropriate number of decimal places.
 *<p>
 * The appropriate scaling, the symbols for decimal point and grouping
 * separators, how the sign is shown, <em>and even what currency it
 * represents</em> and the currency symbol to use, are all determined
 * from the locale specified by the {@code lc_monetary} configuration
 * setting, which can be changed within any session with no special
 * privilege at any time. That may make {@code MONEY} the only data type
 * in PostgreSQL where a person can use a single {@code SET} command to
 * instantly change what an entire table of data means.
 *<p>
 * For example, this little catalog of products:
 *<pre>
 * =&gt; SELECT * FROM products;
 *  product |       price
 * ---------+--------------------
 *  widget  |             $19.00
 *  tokamak | $19,000,000,000.00
 *</pre>
 *<p>
 * can be instantly marked down by about 12 percent (at the exchange
 * rates looked up at this writing):
 *<pre>
 * =&gt; SET lc_monetary TO 'ja_JP';
 * SET
 * =&gt; SELECT * FROM products;
 *  product |        price
 * ---------+---------------------
 *  widget  |             ￥1,900
 *  tokamak | ￥1,900,000,000,000
 *</pre>
 *<p>
 * or marked up by roughly the same amount:
 *<pre>
 * =&gt; SET lc_monetary TO 'de_DE@euro';
 * SET
 * =&gt; SELECT * FROM products;
 *  product |        price
 * ---------+---------------------
 *  widget  |             19,00 €
 *  tokamak | 19.000.000.000,00 €
 *</pre>
 *<p>
 * or marked up even further (as of this writing, 26%):
 *<pre>
 * =&gt; SET lc_monetary TO 'en_GB';
 * SET
 * =&gt; SELECT * FROM products;
 *  product |       price
 * ---------+--------------------
 *  widget  |             £19.00
 *  tokamak | £19,000,000,000.00
 *</pre>
 *<p>
 * <b>Obtaining the locale information in Java</b>
 *<p>
 * Before the integer value provided here can be correctly scaled or
 * interpreted, the locale-dependent information must be obtained.
 * In Java, that can be done in six steps:
 *<ol>
 *<li>Obtain the string value of PostgreSQL's {@code lc_monetary}
 * configuration setting.
 *<li>Let's not talk about step 2 just yet.
 *<li>Obtain a {@code Locale} object by passing the BCP 47 tag to
 * {@link Locale#forLanguageTag Locale.forLanguageTag}.
 *<li>Pass the {@code Locale} object to
 * {@link NumberFormat#getCurrencyInstance(Locale)
		  NumberFormat.getCurrencyInstance}.
 *<li>From that, obtain an actual instance of {@code Currency} with
 * {@link NumberFormat#getCurrency NumberFormat.getCurrency}.
 *<li>Obtain the correct power of ten for scaling from
 * {@link Currency#getDefaultFractionDigits
	      Currency.getDefaultFractionDigits}.
 *</ol>
 *<p>
 * The {@code NumberFormat} obtained in step 4 knows all the appropriate
 * formatting details, but will not automatically scale the integer
 * value here by the proper power of ten. That must be done explicitly,
 * and to avoid compromising the precision objectives of the
 * {@code MONEY} type, should be done with something like a
 * {@link BigDecimal BigDecimal}. If <var>fmt</var> was obtained
 * in step 4 above and <var>scale</var> is the value from step 6:
 *<pre>
 * BigDecimal bd =
 *     BigDecimal.valueOf(scaledToInteger).movePointLeft(scale);
 * String s = fmt.format(bd);
 *</pre>
 *<p>
 * would produce the correctly-formatted value, where
 * <var>scaledToInteger</var> is the parameter supplied to this interface
 * method.
 *<p>
 * If the format is not needed, the scale can be obtained in fewer steps
 * by passing the {@code Locale} from step 3 directly to
 * {@link Currency#getInstance(Locale) Currency.getInstance}.
 * That would be enough to build a simple reference implementation for
 * this data type that would return a {@code BigDecimal} with its point
 * moved left by the scale.
 *<p>
 * Now let's talk about step 2.
 *<p>
 * Java's locale support is based on BCP 47, a format for identifiers
 * <a href="https://www.rfc-editor.org/info/bcp47">standardized by
 * IETF</a> to ensure that they are reliable and specific.
 *<p>
 * The string obtained from the {@code lc_monetary} setting in step 1
 * above is, most often, a string that makes sense to the underlying
 * operating system's C library, using some syntax that predated BCP 47,
 * and likely demonstrates all of the problems BCP 47 was created to
 * overcome.
 *<p>
 * From a first glance at a few simple examples, it can appear that
 * replacing some underscores with hyphens could turn some simple
 * OS-library strings into BCP 47 tags, but that is far from the general
 * case, which is full of nonobvious rules, special cases, and
 * grandfather clauses.
 *<p>
 * A C library, {@code liblangtag}, is available to perform exactly that
 * mapping, and weighs in at about two and a half megabytes. The library
 * might be present on the system where PostgreSQL is running, in which
 * case it could be used in step 2, at the cost of a native call.
 *<p>
 * If PostgreSQL was built with ICU, a native method could accomplish
 * the same (as nearly as practical) thing by calling
 * {@code uloc_canonicalize} followed by {@code uloc_toLanguageTag}; or,
 * if the ICU4J Java library is available,
 * {@code ULocale.createCanonical}could be used to the same effect.
 *<p>
 * It might be simplest to just use a native call to obtain the
 * scaling and other needed details from the underlying operating system
 * library.
 *<p>
 * Because of step 2's complexity, PL/Java does not here supply the
 * simple reference implementation to {@code BigDecimal} proposed above.
 */
@FunctionalInterface
public interface Money<T> extends Contract.Scalar<T>
{
	/**
	 * Constructs a representation <var>T</var> from the components
	 * of the PostgreSQL data type.
	 *<p>
	 * It might be necessary to extend this interface with extra parameters
	 * (or to use the {@code Modifier} mechanism) to receive the needed
	 * scaling and currency details, and require the corresponding
	 * {@code Adapter} (which could no longer be pure Java) to make
	 * the needed native calls to obtain those.
	 * @param scaledToInteger integer value that must be scaled according
	 * to the setting of the <var>lc_monetary</var> configuration setting,
	 * and represents a value in the currency also determined by that
	 * setting.
	 */
	T construct(long scaledToInteger);
}
