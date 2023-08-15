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
package org.postgresql.pljava;

import java.sql.SQLXML; // for javadoc

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import java.util.stream.Stream;

import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.Adapter.AsBoolean;
import org.postgresql.pljava.Adapter.AsByte;
import org.postgresql.pljava.Adapter.AsChar;
import org.postgresql.pljava.Adapter.AsDouble;
import org.postgresql.pljava.Adapter.AsFloat;
import org.postgresql.pljava.Adapter.AsInt;
import org.postgresql.pljava.Adapter.AsLong;
import org.postgresql.pljava.Adapter.AsShort;

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.Portal;          // for javadoc
import org.postgresql.pljava.model.TupleDescriptor; // for javadoc
import org.postgresql.pljava.model.TupleTableSlot;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Identifies attributes to be retrieved from a set of tuples.
 *<p>
 * {@code TargetList} is more general than {@link Projection Projection}: in a
 * {@code Projection}, no attribute can appear more than once, but repetition
 * is possible in a {@code TargetList}.
 *<p>
 * In general, it will be more efficient, if processing logic requires more than
 * one copy of some attribute's value, to simply mention the attribute once in a
 * {@code Projection}, and have the Java logic then copy the value, rather than
 * fetching and converting it twice from the database native form. But there
 * may be cases where that isn't workable, such as when the value is needed in
 * different Java representations from different {@link Adapter}s, or when the
 * Java representation is a type like {@link SQLXML} that can only be used once.
 * Such cases call for a {@code TargetList} in which the attribute is mentioned
 * more than once, to be separately fetched.
 *<p>
 * Given a {@code TargetList}, query results can be processed by supplying a
 * lambda body to {@link #applyOver(Iterable,Cursor.Function) applyOver}. The
 * lambda will be supplied a {@link Cursor Cursor} whose {@code apply} methods
 * can be used to break out the wanted values on each row, in the
 * {@code TargetList} order.
 */
public interface TargetList extends List<Attribute>
{
	/**
	 * A {@code TargetList} in which no one attribute may appear more than once.
	 *<p>
	 * The prime example of a {@code Projection} is a {@link TupleDescriptor} as
	 * obtained, for example, from the {@link Portal} for a query result.
	 *<p>
	 * To preserve the "no attribute appears more than once" property, the only
	 * new {@code Projection}s derivable from an existing one involve selecting
	 * a subset of its attributes, and possibly changing their order. The
	 * {@code project} methods taking attribute names, attribute indices, or the
	 * attributes themselves can be used to do so, as can the {@code subList}
	 * method.
	 */
	interface Projection extends TargetList
	{
		/**
		 * From this {@code Projection}, returns a {@code Projection} containing
		 * only the attributes matching the supplied <var>names</var> and in the
		 * order of the argument list.
		 * @throws IllegalArgumentException if more names are supplied than this
		 * Projection has attributes, or if any remain unmatched after matching
		 * each attribute in this Projection at most once.
		 */
		Projection  project(Simple... names);

		/**
		 * From this {@code Projection}, returns a {@code Projection} containing
		 * only the attributes matching the supplied <var>names</var> and in the
		 * order of the argument list.
		 *<p>
		 * The names will be converted to {@link Simple Identifier.Simple} by
		 * its {@link Simple#fromJava fromJava} method before comparison.
		 * @throws IllegalArgumentException if more names are supplied than this
		 * Projection has attributes, or if any remain unmatched after matching
		 * each attribute in this Projection at most once.
		 */
		default Projection project(CharSequence... names)
		{
			return project(
				Arrays.stream(names)
				.map(CharSequence::toString)
				.map(Simple::fromJava)
				.toArray(Simple[]::new)
			);
		}

		/**
		 * Returns a {@code Projection} containing only the attributes found
		 * at the supplied <var>indices</var> in this {@code Projection}, and in
		 * the order of the argument list.
		 *<p>
		 * The index of the first attribute is zero.
		 * @throws IllegalArgumentException if more indices are supplied than
		 * this Projection has attributes, if any index is negative or beyond
		 * the last index in this Projection, or if any index appears more than
		 * once.
		 */
		Projection project(int... indices);

		/**
		 * Like {@link #project(int...) project(int...)} but using SQL's 1-based
		 * indexing convention.
		 *<p>
		 * The index of the first attribute is 1.
		 * @throws IllegalArgumentException if more indices are supplied than
		 * this Projection has attributes, if any index is nonpositive or beyond
		 * the last 1-based index in this Projection, or if any index appears
		 * more than once.
		 */
		Projection sqlProject(int... indices);

		/**
		 * Returns a {@code Projection} containing only <var>attributes</var>
		 * and in the order of the argument list.
		 *<p>
		 * The attributes must be found in this {@code Projection} by exact
		 * reference identity.
		 * @throws IllegalArgumentException if more attributes are supplied than
		 * this Projection has, or if any remain unmatched after matching
		 * each attribute in this Projection at most once.
		 */
		Projection project(Attribute... attributes);

		@Override
		Projection subList(int fromIndex, int toIndex);
	}

	@Override
	TargetList subList(int fromIndex, int toIndex);

	/**
	 * Executes the function <var>f</var>, once, supplying a
	 * {@link Cursor Cursor} that can be iterated over the supplied
	 * <var>tuples</var> and used to process each tuple.
	 * @return whatever f returns.
	 */
	<R,X extends Throwable> R applyOver(
		Iterable<TupleTableSlot> tuples, Cursor.Function<R,X> f)
		throws X;

	/**
	 * Executes the function <var>f</var>, once, supplying a
	 * {@link Cursor Cursor} that can be used to process the tuple.
	 *<p>
	 * The {@code Cursor} can be iterated, just as if a one-row
	 * {@code Iterable<TupleTableSlot>} had been passed to
	 * {@link #applyOver(Iterable,Cursor.Function) applyOver(tuples, f)}, but it
	 * need not be; it will already have the single supplied <var>tuple</var> as
	 * its current row, ready for its {@code apply} methods to be used.
	 * @return whatever f returns.
	 */
	<R,X extends Throwable> R applyOver(
		TupleTableSlot tuple, Cursor.Function<R,X> f)
		throws X;

	/**
	 * A {@code TargetList} that has been bound to a source of tuples and can
	 * execute code with the wanted attribute values available.
	 *<p>
	 * Being derived from a {@link TargetList}, a {@code Cursor} serves directly
	 * as an {@code Iterator<Attribute>}, supplying the attributes in the
	 * {@code TargetList} order.
	 *<p>
	 * Being bound to a source of tuples, a {@code Cursor} also implements
	 * {@code Iterable}, and can supply an iterator over the bound tuples in
	 * order. The {@code Cursor} is mutated during the iteration, having a
	 * current row that becomes each tuple in turn. The object returned by that
	 * iterator is the {@code Cursor} itself, so the caller has no need for the
	 * iteration variable, and can use the "unnamed variable" {@code _} for it,
	 * in Java versions including that feature (which appears in Java 21 but
	 * only with {@code --enable-preview}). In older Java versions it can be
	 * given some other obviously throwaway name.
	 *<p>
	 * When a {@code Cursor} has a current row, its {@code apply} methods can be
	 * used to execute a lambda body with its parameters mapped to the row's
	 * values, in {@code TargetList} order, or to a prefix of those, should
	 * a lambda with fewer parameters be supplied.
	 *<p>
	 * Each overload of {@code apply} takes some number of
	 * {@link Adapter Adapter} instances, each of which must be suited to the
	 * PostgreSQL type at its corresponding position, followed by a lambda body
	 * with the same number of parameters, each of which will receive the value
	 * from the corresponding {@code Adapter}, and have an inferred type
	 * matching what that {@code Adapter} produces.
	 *<p>
	 * Within a lambda body with fewer parameters than the length of the
	 * {@code TargetList}, the {@code Cursor}'s attribute iterator has been
	 * advanced by the number of columns consumed. It can be used again to apply
	 * an inner lambda body to remaining columns. This "curried" style can be
	 * useful when the number or types of values to be processed will not
	 * directly fit any available {@code apply} signature.
	 *<pre>
	 *  overall_result = targetlist.applyOver(tuples, c -&gt;
	 *  {
	 *      var resultCollector = ...;
	 *      for ( Cursor _ : c )
	 *      {
	 *          var oneResult = c.apply(
	 *              adap0, adap1,
	 *             ( val0,  val1 ) -&gt; c.apply(
	 *                  adap2, adap3,
	 *                 ( val2,  val3 ) -&gt; process(val0, val1, val2, val3)));
     *          resultCollector.collect(oneResult);
	 *      }
	 *      return resultCollector;
	 *  });
	 *</pre>
	 *<p>
	 * As the {@code apply} overloads for reference-typed values and those for
	 * primitive values are separate, currying must be used when processing a
	 * mix of reference and primitive types.
	 *<p>
	 * The {@code Cursor}'s attribute iterator is reset each time the tuple
	 * iterator moves to a new tuple. It is also reset on return (normal or
	 * exceptional) from an outermost {@code apply}, in case another function
	 * should then be applied to the row.
	 *<p>
	 * The attribute iterator is not reset on return from an inner (curried)
	 * {@code apply}. Therefore, it is possible to process a tuple having
	 * repeating groups of attributes with matching types, reusing an inner
	 * lambda and its matching adapters for each occurrence of the group.
	 *<p>
	 * If the tuple is nothing but repeating groups, the effect can still be
	 * achieved by using the zero-parameter {@code apply} overload as the
	 * outermost.
	 */
	interface Cursor extends Iterator<Attribute>, Iterable<Cursor>
	{
		/**
		 * Returns an {@link Iterator} that will return this {@code Cursor}
		 * instance itself, repeatedly, mutated each time to represent the next
		 * of the bound list of tuples.
		 *<p>
		 * Because the {@code Iterator} will produce the same {@code Cursor}
		 * instance on each iteration, and the instance is mutated, saving
		 * values the iterator returns will not have effects one might expect,
		 * and no more than one iteration should be in progress at a time.
		 *<p>
		 * The {@code Iterator<Attribute>} that this {@code Cursor} represents
		 * will be reset to the first attribute each time a new tuple is
		 * presented by the {@code Iterator<Cursor>}.
		 * @throws IllegalStateException within the code body passed to any
		 * {@code apply} method. Within any such code body, the cursor simply
		 * represents its current tuple. Only outside of any {@code apply()} may
		 * {@code iterator()} be called.
		 */
		@Override // Iterable<Cursor>
		Iterator<Cursor> iterator();

		/**
		 * Returns a {@link Stream} that will present this {@code Cursor}
		 * instance itself, repeatedly, mutated each time to represent the next
		 * of the bound list of tuples.
		 *<p>
		 * The stream should be used within the scope of the
		 * {@link #applyOver(Iterable,Function) applyOver} that has made
		 * this {@code Cursor} available.
		 *<p>
		 * Because the {@code Stream} will produce the same {@code Cursor}
		 * instance repeatedly, and the instance is mutated, saving instances
		 * will not have effects one might expect, and no more than one
		 * stream should be in progress at a time. Naturally, this method does
		 * not return a parallel {@code Stream}.
		 *<p>
		 * The {@code Iterator<Attribute>} that this {@code Cursor} represents
		 * will be reset to the first attribute each time a new tuple is
		 * presented by the {@code Stream}.
		 * @throws IllegalStateException within the code body passed to any
		 * {@code apply} method. Within any such code body, the cursor simply
		 * represents its current tuple. Only outside of any {@code apply()} may
		 * {@code stream()} be called.
		 */
		Stream<Cursor> stream();

		<R,X extends Throwable> R apply(
			L0<R,X> f)
			throws X;

		<R,X extends Throwable,A> R apply(
			As<A,?> a0,
			L1<R,X,A> f)
			throws X;

		<R,X extends Throwable,A,B> R apply(
			As<A,?> a0, As<B,?> a1,
			L2<R,X,A,B> f)
			throws X;

		<R,X extends Throwable,A,B,C> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2,
			L3<R,X,A,B,C> f)
			throws X;

		<R,X extends Throwable,A,B,C,D> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			L4<R,X,A,B,C,D> f)
			throws X;

		<R,X extends Throwable,A,B,C,D,E> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			As<E,?> a4,
			L5<R,X,A,B,C,D,E> f)
			throws X;

		<R,X extends Throwable,A,B,C,D,E,F> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			As<E,?> a4, As<F,?> a5,
			L6<R,X,A,B,C,D,E,F> f)
			throws X;

		<R,X extends Throwable,A,B,C,D,E,F,G> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			As<E,?> a4, As<F,?> a5, As<G,?> a6,
			L7<R,X,A,B,C,D,E,F,G> f)
			throws X;

		<R,X extends Throwable,A,B,C,D,E,F,G,H> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			As<E,?> a4, As<F,?> a5, As<G,?> a6, As<H,?> a7,
			L8<R,X,A,B,C,D,E,F,G,H> f)
			throws X;

		<R,X extends Throwable,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P> R apply(
			As<A,?> a0, As<B,?> a1, As<C,?> a2, As<D,?> a3,
			As<E,?> a4, As<F,?> a5, As<G,?> a6, As<H,?> a7,
			As<I,?> a8, As<J,?> a9, As<K,?> aa, As<L,?> ab,
			As<M,?> ac, As<N,?> ad, As<O,?> ae, As<P,?> af,
			L16<R,X,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsLong<?> a0,
			J1<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsLong<?> a0, AsLong<?> a1,
			J2<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsLong<?> a0, AsLong<?> a1, AsLong<?> a2,
			J3<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsLong<?> a0, AsLong<?> a1, AsLong<?> a2, AsLong<?> a3,
			J4<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsDouble<?> a0,
			D1<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsDouble<?> a0, AsDouble<?> a1,
			D2<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsDouble<?> a0, AsDouble<?> a1, AsDouble<?> a2,
			D3<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsDouble<?> a0, AsDouble<?> a1, AsDouble<?> a2, AsDouble<?> a3,
			D4<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsInt<?> a0,
			I1<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsInt<?> a0, AsInt<?> a1,
			I2<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsInt<?> a0, AsInt<?> a1, AsInt<?> a2,
			I3<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsInt<?> a0, AsInt<?> a1, AsInt<?> a2, AsInt<?> a3,
			I4<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsFloat<?> a0,
			F1<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsFloat<?> a0, AsFloat<?> a1,
			F2<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsFloat<?> a0, AsFloat<?> a1, AsFloat<?> a2,
			F3<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsFloat<?> a0, AsFloat<?> a1, AsFloat<?> a2, AsFloat<?> a3,
			F4<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsShort<?> a0,
			S1<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsShort<?> a0, AsShort<?> a1,
			S2<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsShort<?> a0, AsShort<?> a1, AsShort<?> a2,
			S3<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsShort<?> a0, AsShort<?> a1, AsShort<?> a2, AsShort<?> a3,
			S4<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsChar<?> a0,
			C1<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsChar<?> a0, AsChar<?> a1,
			C2<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsChar<?> a0, AsChar<?> a1, AsChar<?> a2,
			C3<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsChar<?> a0, AsChar<?> a1, AsChar<?> a2, AsChar<?> a3,
			C4<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsByte<?> a0,
			B1<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsByte<?> a0, AsByte<?> a1,
			B2<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsByte<?> a0, AsByte<?> a1, AsByte<?> a2,
			B3<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsByte<?> a0, AsByte<?> a1, AsByte<?> a2, AsByte<?> a3,
			B4<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsBoolean<?> a0,
			Z1<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsBoolean<?> a0, AsBoolean<?> a1,
			Z2<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsBoolean<?> a0, AsBoolean<?> a1, AsBoolean<?> a2,
			Z3<R,X> f)
			throws X;

		<R,X extends Throwable> R apply(
			AsBoolean<?> a0, AsBoolean<?> a1, AsBoolean<?> a2, AsBoolean<?> a3,
			Z4<R,X> f)
			throws X;

		@FunctionalInterface
		interface Function<R,X extends Throwable>
		{
			R apply(Cursor c);
		}

		@FunctionalInterface
		interface L0<R,X extends Throwable>
		{
			R apply() throws X;
		}

		@FunctionalInterface
		interface L1<R,X extends Throwable,A>
		{
			R apply(A v0) throws X;
		}

		@FunctionalInterface
		interface L2<R,X extends Throwable,A,B>
		{
			R apply(A v0, B v1) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface L3<R,X extends Throwable,A,B,C>
		{
			R apply(A v0, B v1, C v2) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface L4<R,X extends Throwable,A,B,C,D>
		{
			R apply(A v0, B v1, C v2, D v3) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface L5<R,X extends Throwable,A,B,C,D,E>
		{
			R apply(A v0, B v1, C v2, D v3, E v4) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface L6<R,X extends Throwable,A,B,C,D,E,F>
		{
			R apply(A v0, B v1, C v2, D v3, E v4, F v5) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface L7<R,X extends Throwable,A,B,C,D,E,F,G>
		{
			R apply(A v0, B v1, C v2, D v3, E v4, F v5, G v6) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface L8<R,X extends Throwable,A,B,C,D,E,F,G,H>
		{
			R apply(A v0, B v1, C v2, D v3, E v4, F v5, G v6, H v7)
			throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface L16<R,X extends Throwable,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P>
		{
			R apply(
				A v0, B v1, C v2, D v3, E v4, F v5, G v6, H v7,
				I v8, J v9, K va, L vb, M vc, N vd, O ve, P vf)
			throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface J1<R,X extends Throwable>
		{
			R apply(long v0) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface J2<R,X extends Throwable>
		{
			R apply(long v0, long v1) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface J3<R,X extends Throwable>
		{
			R apply(long v0, long v1, long v2) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface J4<R,X extends Throwable>
		{
			R apply(long v0, long v1, long v2, long v3) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface D1<R,X extends Throwable>
		{
			R apply(double v0) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface D2<R,X extends Throwable>
		{
			R apply(double v0, double v1) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface D3<R,X extends Throwable>
		{
			R apply(double v0, double v1, double v2) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface D4<R,X extends Throwable>
		{
			R apply(double v0, double v1, double v2, double v3) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface I1<R,X extends Throwable>
		{
			R apply(int v0) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface I2<R,X extends Throwable>
		{
			R apply(int v0, int v1) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface I3<R,X extends Throwable>
		{
			R apply(int v0, int v1, int v2) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface I4<R,X extends Throwable>
		{
			R apply(int v0, int v1, int v2, int v3) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface F1<R,X extends Throwable>
		{
			R apply(float v0) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface F2<R,X extends Throwable>
		{
			R apply(float v0, float v1) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface F3<R,X extends Throwable>
		{
			R apply(float v0, float v1, float v2) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface F4<R,X extends Throwable>
		{
			R apply(float v0, float v1, float v2, float v3) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface S1<R,X extends Throwable>
		{
			R apply(short v0) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface S2<R,X extends Throwable>
		{
			R apply(short v0, short v1) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface S3<R,X extends Throwable>
		{
			R apply(short v0, short v1, short v2) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface S4<R,X extends Throwable>
		{
			R apply(short v0, short v1, short v2, short v3) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface C1<R,X extends Throwable>
		{
			R apply(char v0) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface C2<R,X extends Throwable>
		{
			R apply(char v0, char v1) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface C3<R,X extends Throwable>
		{
			R apply(char v0, char v1, char v2) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface C4<R,X extends Throwable>
		{
			R apply(char v0, char v1, char v2, char v3) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface B1<R,X extends Throwable>
		{
			R apply(byte v0) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface B2<R,X extends Throwable>
		{
			R apply(byte v0, byte v1) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface B3<R,X extends Throwable>
		{
			R apply(byte v0, byte v1, byte v2) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface B4<R,X extends Throwable>
		{
			R apply(byte v0, byte v1, byte v2, byte v3) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface Z1<R,X extends Throwable>
		{
			R apply(boolean v0) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface Z2<R,X extends Throwable>
		{
			R apply(boolean v0, boolean v1) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface Z3<R,X extends Throwable>
		{
			R apply(boolean v0, boolean v1, boolean v2) throws X;
		}

		/**
		 * @hidden
		 */
		@FunctionalInterface
		interface Z4<R,X extends Throwable>
		{
			R apply(boolean v0, boolean v1, boolean v2, boolean v3) throws X;
		}
	}
}
