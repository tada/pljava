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
package org.postgresql.pljava.internal;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.NoSuchElementException;
import static java.util.Objects.requireNonNull;
import java.util.stream.BaseStream;

/**
 * Functional interfaces handling checked exceptions.
 *<p>
 * It would be ideal if the compiler could preserve its union of possible
 * thrown types as the inferred exception type of the functional interface
 * method. Instead, it collapses the union to the nearest common supertype,
 * which is less useful, as it becomes {@code Exception} rather quickly if
 * the lambda can throw a few unrelated exceptions. It is still useful for
 * short lambdas that throw only a few related exceptions.
 *<p>
 * Also, the Java API lacks primitive
 * {@code Consumer}/{@code Supplier}/{@code Optional} types for {@code byte},
 * {@code short}, {@code char}, {@code float}, and some of them for
 * {@code boolean}. To allow a more orthogonal API for access to datum values,
 * those are provided here, again supporting checked exceptions. Because these
 * "bonus" types do not have checked-exception-less counterparts in the Java
 * API, they do not strictly need the wrapper methods described next.
 *<p>
 * For interoperating with Java APIs that require the Java no-checked-exceptions
 * versions of these interfaces, each checked interface here (for which a Java
 * API no-checked version exists) has an {@code ederWrap} method that produces
 * the Java no-checked version of the same interface, using a lightweight idiom
 * advanced by Lukas Eder, developer of jOOλ. The checked exception is not
 * wrapped, but simply flown under {@code javac}'s radar. That idiom is extended
 * here with a corresponding {@code in} method to pass the wrapped interface
 * into code that requires it, re-exposing the checked exception type. That
 * makes possible constructions like:
 *
 *<pre>
 * Stream&lt;String&gt; strs = ...;
 * Writer w = ...;
 * try {
 *   Checked.Consumer.use((String s) -&gt; w.write(s)) // throws IOException!
 *     .in(c -&gt; strs.forEach(c));
 * }
 * catch ( IOException e ) { ... }
 *</pre>
 *
 * where the {@code Stream.forEach} method requires a Java {@code Consumer}
 * that declares no checked exceptions.
 *<p>
 * Such an idiom is, of course, contrary to an <a
href='https://wiki.sei.cmu.edu/confluence/display/java/ERR06-J.+Do+not+throw+undeclared+checked+exceptions'
>SEI CERT coding standard</a>,
 * and likely to produce surprises if the exception will be 'flown' through deep
 * layers of code by others that may contain {@code catch} blocks. That said, as
 * a convenience for dealing with checked exceptions and simple Java APIs that
 * cannot accept them, it can be useful as long as the intervening code through
 * which the exception may be 'flown' is simple and short.
 *<p>
 * The functional interfaces defined here that do <em>not</em> correspond to a
 * Java API no-checked version, while not strictly needing an {@code ederWrap}
 * method, have one anyway, a no-op identity function. That avoids arbitrary
 * limits on which ones can participate in the {@code use(...).in(...)} idiom.
 *<p>
 * Static {@code composed()} methods are provided here in place of the instance
 * {@code compose} or {@code andThen} methods in Java's function API, which seem
 * to challenge {@code javac}'s type inference when exception types are thrown
 * in. A static {@code composed} method can substitute for {@code compose} or
 * {@code andThen}, by ordering the parameters as desired. Likewise, static
 * {@code and} and {@code or} methods are provided in place of the instance
 * methods on Java's {@code Predicate}.
 *<p>
 * Each functional interface declared here has a static {@code use(...)} method
 * that can serve, as a concise alternative to casting, to constrain the type
 * of a lambda expression when the compiler won't infer it.
 *<p>
 * A {@link AutoCloseable variant of AutoCloseable} with an exception-type
 * parameter, and some {@link #closing(AutoCloseable) closing} methods (inspired
 * by Python, for use with resources that do not already implement
 * {@code AutoCloseable}), are also provided.
 */
public interface Checked<WT, EX extends Throwable>
{
	@SuppressWarnings("unchecked")
	static <E extends Throwable> E ederThrow(Throwable t) throws E
	{
		throw (E) t;
	}

	WT ederWrap();

	default <RX extends Throwable>
	void in(Consumer<? super WT, RX> c)
	throws EX, RX
	{
		c.accept(ederWrap());
	}

	default <RT, RX extends Throwable>
	RT inReturning(Function<? super WT, RT, RX> f)
	throws EX, RX
	{
		return f.apply(ederWrap());
	}

	default <RX extends Throwable>
	double inDoubleReturning(ToDoubleFunction<? super WT, RX> f)
		throws EX, RX
	{
		return f.apply(ederWrap());
	}

	default <RX extends Throwable>
	int inIntReturning(ToIntFunction<? super WT, RX> f)
	throws EX, RX
	{
		return f.apply(ederWrap());
	}

	default <RX extends Throwable>
	long inLongReturning(ToLongFunction<? super WT, RX> f)
	throws EX, RX
	{
		return f.apply(ederWrap());
	}

	default <RX extends Throwable>
	boolean inBooleanReturning(Predicate<? super WT, RX> f)
		throws EX, RX
	{
		return f.test(ederWrap());
	}

	default <RX extends Throwable>
	byte inByteReturning(ToByteFunction<? super WT, RX> f)
		throws EX, RX
	{
		return f.apply(ederWrap());
	}

	default <RX extends Throwable>
	short inShortReturning(ToShortFunction<? super WT, RX> f)
	throws EX, RX
	{
		return f.apply(ederWrap());
	}

	default <RX extends Throwable>
	char inCharReturning(ToCharFunction<? super WT, RX> f)
	throws EX, RX
	{
		return f.apply(ederWrap());
	}

	default <RX extends Throwable>
	float inFloatReturning(ToFloatFunction<? super WT, RX> f)
	throws EX, RX
	{
		return f.apply(ederWrap());
	}

	/*
	 * Short-circuiting predicate combinators.
	 */

	static <T, E extends Throwable>
		Predicate<T,E> and(
			Predicate<? super T, ? extends E> first,
			Predicate<? super T, ? extends E> after)
	{
		return t -> first.test(t) && after.test(t);
	}

	static <T, E extends Throwable>
		Predicate<T,E> or(
			Predicate<? super T, ? extends E> first,
			Predicate<? super T, ? extends E> after)
	{
		return t -> first.test(t) || after.test(t);
	}

	/*
	 * composed() methods.
	 */

	static <T, R, V, E extends Throwable>
		Function<T,R,E> composed(
			Function<? super T, ? extends V, ? extends E> first,
			Function<? super V, ? extends R, ? extends E> after)
	{
		return t -> after.apply(first.apply(t));
	}

	static <T, E extends Throwable>
		Consumer<T,E> composed(
			Consumer<? super T, ? extends E> first,
			Consumer<? super T, ? extends E> after)
	{
		return t ->
		{
			first.accept(t);
			after.accept(t);
		};
	}

	static <E extends Throwable>
		DoubleConsumer<E> composed(
			DoubleConsumer<? extends E> first,
			DoubleConsumer<? extends E> after)
	{
		return t ->
		{
			first.accept(t);
			after.accept(t);
		};
	}

	static <E extends Throwable>
		IntConsumer<E> composed(
			IntConsumer<? extends E> first,
			IntConsumer<? extends E> after)
	{
		return t ->
		{
			first.accept(t);
			after.accept(t);
		};
	}

	static <E extends Throwable>
		LongConsumer<E> composed(
			LongConsumer<? extends E> first,
			LongConsumer<? extends E> after)
	{
		return t ->
		{
			first.accept(t);
			after.accept(t);
		};
	}

	/**
	 * Version of {@link java.lang.AutoCloseable} with an exception-type
	 * parameter.
	 *<p>
	 * This does not need {@code use} or {@code ederWrap} methods because Java's
	 * {@code AutoCloseable} already allows checked exceptions. The only trouble
	 * with the Java one is it can't be parameterized to narrow the thrown type
	 * from {@code Exception}. In Java's API docs, implementers are "strongly
	 * encouraged" to narrow their {@code throws} clauses, but that's only
	 * helpful where the compiler sees the specific implementing class.
	 */
	@FunctionalInterface
	interface AutoCloseable<E extends Exception>
	extends java.lang.AutoCloseable
	{
		@Override
		void close() throws E;
	}

	/**
	 * Returns its argument; shorthand for casting a suitable lambda to
	 * {@code AutoCloseable<E>}.
	 *<p>
	 * Where some resource does not itself implement {@code AutoCloseable}, an
	 * idiom like the following, inspired by Python, can be used in Java 10 or
	 * later, and the compiler will infer that it can throw whatever
	 * {@code thing.release()} can throw:
	 *<pre>
	 *  try(var ac = closing(() -&gt; { thing.release(); }))
	 *  {
	 *    ...
	 *  }
	 *</pre>
	 *<p>
	 * Pre-Java 10, without {@code var}, you have to specify the exception type,
	 * but you still get the simple idiom without needing to declare some new
	 * interface:
	 *<pre>
	 *  try(Checked.AutoCloseable&lt;ThingException&gt; ac =
	 *		closing(() -&gt; { thing.release(); }))
	 *  {
	 *    ...
	 *  }
	 *</pre>
	 */
	static <E extends Exception>
		AutoCloseable<E> closing(AutoCloseable<E> o)
	{
		return o;
	}

	/**
	 * Wrap some payload and a 'closer' lambda as a {@code Closing} instance
	 * that can supply the payload and implements {@code AutoCloseable} using
	 * the lambda; useful in a {@code try}-with-resources when the payload
	 * itself does not implement {@code AutoCloseable}.
	 */
	static <T, E extends Exception>
		Closing<T,E> closing(T payload, AutoCloseable<E> closer)
	{
		return new Closing<>(payload, closer);
	}

	/**
	 * Given a stream and a lambda that should be invoked when it is closed,
	 * construct a new stream that runs that lambda when closed, and return a
	 * {@code Closing} instance with the new stream as its payload, which will
	 * be closed by the {@code close} action.
	 *<p>
	 * Intended for use in a {@code try}-with-resources. Any checked exception
	 * throwable by <em>closer</em> will be remembered as throwable by the
	 * {@code close} method of the returned {@code Closing} instance (and
	 * therefore will be considered throwable by the {@code try}-with-resources
	 * in which it is used. Any other code that calls {@code close} directly on
	 * the returned stream could be surprised by the checked exception, as a
	 * stream's {@code close} method is not declared to throw any. When used as
	 * intended in a {@code try}-with-resources, any such surprise is bounded
	 * by the scope of that statement.
	 */
	static <T, S extends BaseStream<T,S>, E extends Exception>
		Closing<S,E> closing(S stream, Runnable<E> closer)
	{
		S newStream = stream.onClose(closer.ederWrap());
		return new Closing<>(newStream, newStream::close);
	}

	/**
	 * A class that can supply a {@code T} while also implementing
	 * {@code AutoCloseable<E>}; suitable for use in a
	 * {@code try}-with-resources to wrap some value that does not itself
	 * implement {@code AutoCloseable}.
	 *<p>
	 * Obtained via one of the {@code closing} methods above.
	 */
	/*
	 * This class also encloses the private interface Trivial, simply to make it
	 * private (a private interface can only exist within a class) to ensure it
	 * is only extended by other interfaces in this compilation unit (its
	 * default method includes an unchecked cast). It did not seem worth
	 * creating another entire class only to enclose a private interface.
	 */
	class Closing<T, E extends Exception>
	implements java.util.function.Supplier<T>, AutoCloseable<E>
	{
		private final T m_payload;
		private final AutoCloseable<E> m_closer;

		private Closing(T payload, AutoCloseable<E> closer)
		{
			m_payload = payload;
			m_closer = requireNonNull(closer);
		}

		@Override
		public T get()
		{
			return m_payload;
		}

		@Override
		public void close() throws E
		{
			m_closer.close();
		}

		/**
		 * Superinterface of the functional interfaces declared here that do
		 * <em>not</em> have checked-exception-less counterparts in Java's API.
		 *<p>
		 * These can all inherit a no-op default {@code ederWrap} that returns
		 * the instance unchanged, allowing them also to participate in the
		 * {@code use(...).in(...)} idiom for stylistic consistency even if it
		 * is not strictly necessary.
		 */
		private interface Trivial
			<WT extends Trivial<WT, EX>, EX extends Throwable>
		extends Checked<WT, EX>
		{
			@Override
			@SuppressWarnings("unchecked")
			default WT ederWrap()
			{
				return (WT) this;
			}
		}
	}

	/*
	 * Runnable.
	 */

	@FunctionalInterface
	interface Runnable<E extends Throwable>
	extends Checked<java.lang.Runnable, E>
	{
		void run() throws E;

		@Override
		default java.lang.Runnable ederWrap()
		{
			return () ->
			{
				try
				{
					run();
				}
				catch ( Throwable t )
				{
					throw Checked.<RuntimeException>ederThrow(t);
				}
			};
		}

		static <E extends Throwable> Runnable<E> use(Runnable<E> o)
		{
			return o;
		}
	}

	/*
	 * Suppliers that have checked-exception-less counterparts in the Java API.
	 */

	@FunctionalInterface
	interface Supplier<T, E extends Throwable>
	extends Checked<java.util.function.Supplier<T>, E>
	{
		T get() throws E;

		@Override
		default java.util.function.Supplier<T> ederWrap()
		{
			return () ->
			{
				try
				{
					return get();
				}
				catch ( Throwable t )
				{
					throw Checked.<RuntimeException>ederThrow(t);
				}
			};
		}

		static <T, E extends Throwable> Supplier<T,E> use(Supplier<T,E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface BooleanSupplier<E extends Throwable>
	extends Checked<java.util.function.BooleanSupplier, E>
	{
		boolean getAsBoolean() throws E;

		@Override
		default java.util.function.BooleanSupplier ederWrap()
		{
			return () ->
			{
				try
				{
					return getAsBoolean();
				}
				catch ( Throwable t )
				{
					throw Checked.<RuntimeException>ederThrow(t);
				}
			};
		}

		static <E extends Throwable>
		BooleanSupplier<E> use(BooleanSupplier<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface DoubleSupplier<E extends Throwable>
	extends Checked<java.util.function.DoubleSupplier, E>
	{
		double getAsDouble() throws E;

		@Override
		default java.util.function.DoubleSupplier ederWrap()
		{
			return () ->
			{
				try
				{
					return getAsDouble();
				}
				catch ( Throwable t )
				{
					throw Checked.<RuntimeException>ederThrow(t);
				}
			};
		}

		static <E extends Throwable> DoubleSupplier<E> use(DoubleSupplier<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface IntSupplier<E extends Throwable>
	extends Checked<java.util.function.IntSupplier, E>
	{
		int getAsInt() throws E;

		@Override
		default java.util.function.IntSupplier ederWrap()
		{
			return () ->
			{
				try
				{
					return getAsInt();
				}
				catch ( Throwable t )
				{
					throw Checked.<RuntimeException>ederThrow(t);
				}
			};
		}

		static <E extends Throwable> IntSupplier<E> use(IntSupplier<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface LongSupplier<E extends Throwable>
	extends Checked<java.util.function.LongSupplier, E>
	{
		long getAsLong() throws E;

		@Override
		default java.util.function.LongSupplier ederWrap()
		{
			return () ->
			{
				try
				{
					return getAsLong();
				}
				catch ( Throwable t )
				{
					throw Checked.<RuntimeException>ederThrow(t);
				}
			};
		}

		static <E extends Throwable> LongSupplier<E> use(LongSupplier<E> o)
		{
			return o;
		}
	}

	/*
	 * Suppliers without checked-exception-less Java API counterparts.
	 */

	@FunctionalInterface
	interface ByteSupplier<E extends Throwable>
	extends Closing.Trivial<ByteSupplier<E>, E>
	{
		byte getAsByte() throws E;

		static <E extends Throwable> ByteSupplier<E> use(ByteSupplier<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface ShortSupplier<E extends Throwable>
	extends Closing.Trivial<ShortSupplier<E>, E>
	{
		short getAsShort() throws E;

		static <E extends Throwable> ShortSupplier<E> use(ShortSupplier<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface CharSupplier<E extends Throwable>
	extends Closing.Trivial<CharSupplier<E>, E>
	{
		char getAsChar() throws E;

		static <E extends Throwable> CharSupplier<E> use(CharSupplier<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface FloatSupplier<E extends Throwable>
	extends Closing.Trivial<FloatSupplier<E>, E>
	{
		float getAsFloat() throws E;

		static <E extends Throwable> FloatSupplier<E> use(FloatSupplier<E> o)
		{
			return o;
		}
	}

	/*
	 * Functions that have checked-exception-less counterparts in the Java API.
	 */

	@FunctionalInterface
	interface Function<T,R,E extends Throwable>
	extends Checked<java.util.function.Function<T,R>, E>
	{
		R apply(T t) throws E;

		@Override
		default java.util.function.Function<T,R> ederWrap()
		{
			return (t) ->
			{
				try
				{
					return apply(t);
				}
				catch ( Throwable thw )
				{
					throw Checked.<RuntimeException>ederThrow(thw);
				}
			};
		}

		static <T, R, E extends Throwable>
		Function<T,R,E> use(Function<T,R,E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface ToDoubleFunction<T,E extends Throwable>
	extends Checked<java.util.function.ToDoubleFunction<T>, E>
	{
		double apply(T t) throws E;

		@Override
		default java.util.function.ToDoubleFunction<T> ederWrap()
		{
			return (t) ->
			{
				try
				{
					return apply(t);
				}
				catch ( Throwable thw )
				{
					throw Checked.<RuntimeException>ederThrow(thw);
				}
			};
		}

		static <T, E extends Throwable>
		ToDoubleFunction<T,E> use(ToDoubleFunction<T,E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface ToIntFunction<T,E extends Throwable>
	extends Checked<java.util.function.ToIntFunction<T>, E>
	{
		int apply(T t) throws E;

		@Override
		default java.util.function.ToIntFunction<T> ederWrap()
		{
			return (t) ->
			{
				try
				{
					return apply(t);
				}
				catch ( Throwable thw )
				{
					throw Checked.<RuntimeException>ederThrow(thw);
				}
			};
		}

		static <T, E extends Throwable>
		ToIntFunction<T,E> use(ToIntFunction<T,E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface ToLongFunction<T,E extends Throwable>
	extends Checked<java.util.function.ToLongFunction<T>, E>
	{
		long apply(T t) throws E;

		@Override
		default java.util.function.ToLongFunction<T> ederWrap()
		{
			return (t) ->
			{
				try
				{
					return apply(t);
				}
				catch ( Throwable thw )
				{
					throw Checked.<RuntimeException>ederThrow(thw);
				}
			};
		}

		static <T, E extends Throwable>
		ToLongFunction<T,E> use(ToLongFunction<T,E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface Predicate<T,E extends Throwable>
	extends Checked<java.util.function.Predicate<T>, E>
	{
		boolean test(T t) throws E;

		default Predicate<T,E> negate()
		{
			return t -> ! test(t);
		}

		@Override
		default java.util.function.Predicate<T> ederWrap()
		{
			return (t) ->
			{
				try
				{
					return test(t);
				}
				catch ( Throwable thw )
				{
					throw Checked.<RuntimeException>ederThrow(thw);
				}
			};
		}

		static <T, E extends Throwable>
		Predicate<T,E> use(Predicate<T,E> o)
		{
			return o;
		}
	}

	/*
	 * Functions without checked-exception-less Java API counterparts.
	 */

	@FunctionalInterface
	interface ToByteFunction<T,E extends Throwable>
	extends Closing.Trivial<ToByteFunction<T,E>, E>
	{
		byte apply(T t) throws E;

		static <T, E extends Throwable>
		ToByteFunction<T,E> use(ToByteFunction<T,E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface ToShortFunction<T,E extends Throwable>
	extends Closing.Trivial<ToShortFunction<T,E>, E>
	{
		short apply(T t) throws E;

		static <T, E extends Throwable>
		ToShortFunction<T,E> use(ToShortFunction<T,E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface ToCharFunction<T,E extends Throwable>
	extends Closing.Trivial<ToCharFunction<T,E>, E>
	{
		char apply(T t) throws E;

		static <T, E extends Throwable>
		ToCharFunction<T,E> use(ToCharFunction<T,E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface ToFloatFunction<T,E extends Throwable>
	extends Closing.Trivial<ToFloatFunction<T,E>, E>
	{
		float apply(T t) throws E;

		static <T, E extends Throwable>
		ToFloatFunction<T,E> use(ToFloatFunction<T,E> o)
		{
			return o;
		}
	}

	/*
	 * Consumers that have checked-exception-less counterparts in the Java API.
	 */

	@FunctionalInterface
	interface Consumer<T,E extends Throwable>
	extends Checked<java.util.function.Consumer<T>, E>
	{
		void accept(T t) throws E;

		@Override
		default java.util.function.Consumer<T> ederWrap()
		{
			return (t) ->
			{
				try
				{
					accept(t);
				}
				catch ( Throwable thw )
				{
					throw Checked.<RuntimeException>ederThrow(thw);
				}
			};
		}

		static <T, E extends Throwable> Consumer<T,E> use(Consumer<T,E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface DoubleConsumer<E extends Throwable>
	extends Checked<java.util.function.DoubleConsumer, E>
	{
		void accept(double value) throws E;

		@Override
		default java.util.function.DoubleConsumer ederWrap()
		{
			return (t) ->
			{
				try
				{
					accept(t);
				}
				catch ( Throwable thw )
				{
					throw Checked.<RuntimeException>ederThrow(thw);
				}
			};
		}

		static <E extends Throwable> DoubleConsumer<E> use(DoubleConsumer<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface IntConsumer<E extends Throwable>
	extends Checked<java.util.function.IntConsumer, E>
	{
		void accept(int value) throws E;

		@Override
		default java.util.function.IntConsumer ederWrap()
		{
			return (t) ->
			{
				try
				{
					accept(t);
				}
				catch ( Throwable thw )
				{
					throw Checked.<RuntimeException>ederThrow(thw);
				}
			};
		}

		static <E extends Throwable> IntConsumer<E> use(IntConsumer<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface LongConsumer<E extends Throwable>
	extends Checked<java.util.function.LongConsumer, E>
	{
		void accept(long value) throws E;

		@Override
		default java.util.function.LongConsumer ederWrap()
		{
			return (t) ->
			{
				try
				{
					accept(t);
				}
				catch ( Throwable thw )
				{
					throw Checked.<RuntimeException>ederThrow(thw);
				}
			};
		}

		static <E extends Throwable> LongConsumer<E> use(LongConsumer<E> o)
		{
			return o;
		}
	}

	/*
	 * Consumers without checked-exception-less counterparts in the Java API.
	 */

	@FunctionalInterface
	interface BooleanConsumer<E extends Throwable>
	extends Closing.Trivial<BooleanConsumer<E>, E>
	{
		void accept(boolean value) throws E;

		static <E extends Throwable>
		BooleanConsumer<E> use(BooleanConsumer<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface ByteConsumer<E extends Throwable>
	extends Closing.Trivial<ByteConsumer<E>, E>
	{
		void accept(byte value) throws E;

		static <E extends Throwable> ByteConsumer<E> use(ByteConsumer<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface ShortConsumer<E extends Throwable>
	extends Closing.Trivial<ShortConsumer<E>, E>
	{
		void accept(short value) throws E;

		static <E extends Throwable> ShortConsumer<E> use(ShortConsumer<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface CharConsumer<E extends Throwable>
	extends Closing.Trivial<CharConsumer<E>, E>
	{
		void accept(char value) throws E;

		static <E extends Throwable> CharConsumer<E> use(CharConsumer<E> o)
		{
			return o;
		}
	}

	@FunctionalInterface
	interface FloatConsumer<E extends Throwable>
	extends Closing.Trivial<FloatConsumer<E>, E>
	{
		void accept(float value) throws E;

		static <E extends Throwable> FloatConsumer<E> use(FloatConsumer<E> o)
		{
			return o;
		}
	}

	/*
	 * Optionals without checked-exception-less counterparts in the Java API.
	 *
	 * Rather than following Java's odd "Value-Based Class" conventions (which
	 * would require each class to be final and therefore preclude a None/Some
	 * implementation), these all have private constructors and constitute an
	 * effectively sealed hierarchy. Client code can and should treat them as
	 * value-based classes, and they will behave.
	 */

	abstract class OptionalBase
	{
		public boolean isPresent()
		{
			return false;
		}

		@Override
		public boolean equals(Object obj)
		{
			/*
			 * This is the equals() inherited by every EMPTY instance, and
			 * therefore can only return true when obj is an instance of the
			 * exact same type.
			 */
			return null != obj && getClass().equals(obj.getClass());
		}

		@Override
		public int hashCode()
		{
			return 0;
		}

		@Override
		public String toString()
		{
			return getClass().getSimpleName() + ".empty";
		}

		public static OptionalDouble ofNullable(Double value)
		{
			return null == value ?
				OptionalDouble.empty() : OptionalDouble.of(value);
		}

		public static OptionalInt ofNullable(Integer value)
		{
			return null == value ?
				OptionalInt.empty() : OptionalInt.of(value);
		}

		public static OptionalLong ofNullable(Long value)
		{
			return null == value ?
				OptionalLong.empty() : OptionalLong.of(value);
		}

		public static OptionalBoolean ofNullable(Boolean value)
		{
			return null == value ?
				OptionalBoolean.EMPTY : OptionalBoolean.of(value);
		}

		public static OptionalByte ofNullable(Byte value)
		{
			return null == value ?
				OptionalByte.EMPTY : OptionalByte.of(value);
		}

		public static OptionalShort ofNullable(Short value)
		{
			return null == value ?
				OptionalShort.EMPTY : OptionalShort.of(value);
		}

		public static OptionalChar ofNullable(Character value)
		{
			return null == value ?
				OptionalChar.EMPTY : OptionalChar.of(value);
		}

		public static OptionalFloat ofNullable(Float value)
		{
			return null == value ?
				OptionalFloat.EMPTY : OptionalFloat.of(value);
		}
	}

	class OptionalBoolean extends OptionalBase
	{
		public static final OptionalBoolean EMPTY = new OptionalBoolean();
		public static final OptionalBoolean FALSE = new False();
		public static final OptionalBoolean TRUE  = new True();

		private OptionalBoolean()
		{
		}

		public static OptionalBoolean of(boolean value)
		{
			return value ? TRUE : FALSE;
		}

		public boolean getAsBoolean()
		{
			throw new NoSuchElementException("No value present");
		}

		public <E extends Throwable> void ifPresent(
			BooleanConsumer<? extends E> action)
		throws E
		{
		}

		public <E extends Throwable> void ifPresentOrElse(
			BooleanConsumer<? extends E> action,
			Runnable<? extends E> emptyAction)
		throws E
		{
			emptyAction.run();
		}

		public boolean orElse(boolean other)
		{
			return other;
		}

		public <E extends Throwable> boolean orElseGet(
			BooleanSupplier<? extends E> supplier)
		throws E
		{
			return supplier.getAsBoolean();
		}

		public <E extends Throwable> boolean orElseThrow(
			Supplier<? extends E, ? extends E> exceptionSupplier)
		throws E
		{
			throw exceptionSupplier.get();
		}

		private abstract static class Present extends OptionalBoolean
		{
			private Present()
			{
			}

			@Override
			public boolean isPresent()
			{
				return true;
			}

			/*
			 * The inherited equals() works here too; this and obj must be both
			 * of class False or both of class True.
			 */

			@Override
			public int hashCode()
			{
				return Boolean.hashCode(getAsBoolean());
			}

			@Override
			public String toString()
			{
				return "OptionalBoolean[" + getAsBoolean() + ']';
			}

			@Override
			public abstract boolean getAsBoolean();

			@Override
			public <E extends Throwable> void ifPresent(
				BooleanConsumer<? extends E> action)
			throws E
			{
				action.accept(getAsBoolean());
			}

			@Override
			public <E extends Throwable> void ifPresentOrElse(
				BooleanConsumer<? extends E> action,
				Runnable<? extends E> emptyAction)
			throws E
			{
				action.accept(getAsBoolean());
			}

			@Override
			public boolean orElse(boolean other)
			{
				return getAsBoolean();
			}

			@Override
			public <E extends Throwable> boolean orElseGet(
				BooleanSupplier<? extends E> supplier)
			throws E
			{
				return getAsBoolean();
			}

			@Override
			public <E extends Throwable> boolean orElseThrow(
				Supplier<? extends E, ? extends E> exceptionSupplier)
			throws E
			{
				return getAsBoolean();
			}
		}

		private static final class False extends Present
		{
			private False()
			{
			}

			@Override
			public boolean getAsBoolean()
			{
				return false;
			}
		}

		private static final class True extends Present
		{
			private True()
			{
			}

			@Override
			public boolean getAsBoolean()
			{
				return true;
			}
		}
	}

	class OptionalByte extends OptionalBase
	{
		public static final OptionalByte EMPTY = new OptionalByte();

		private OptionalByte()
		{
		}

		public static OptionalByte of(byte value)
		{
			return new Present(value);
		}

		public byte getAsByte()
		{
			throw new NoSuchElementException("No value present");
		}

		public <E extends Throwable> void ifPresent(
			ByteConsumer<? extends E> action)
		throws E
		{
		}

		public <E extends Throwable> void ifPresentOrElse(
			ByteConsumer<? extends E> action, Runnable<? extends E> emptyAction)
		throws E
		{
			emptyAction.run();
		}

		public byte orElse(byte other)
		{
			return other;
		}

		public <E extends Throwable> byte orElseGet(
			ByteSupplier<? extends E> supplier)
		throws E
		{
			return supplier.getAsByte();
		}

		public <E extends Throwable> byte orElseThrow(
			Supplier<? extends E, ? extends E> exceptionSupplier)
		throws E
		{
			throw exceptionSupplier.get();
		}

		private static final class Present extends OptionalByte
		{
			private final byte m_value;

			private Present(byte value)
			{
				m_value = value;
			}

			@Override
			public boolean isPresent()
			{
				return true;
			}

			@Override
			public boolean equals(Object obj)
			{
				return obj instanceof Present
					&& (m_value == ((Present)obj).m_value);
			}

			@Override
			public int hashCode()
			{
				return Byte.hashCode(m_value);
			}

			@Override
			public String toString()
			{
				return "OptionalByte[" + m_value + ']';
			}

			@Override
			public byte getAsByte()
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> void ifPresent(
				ByteConsumer<? extends E> action)
			throws E
			{
				action.accept(m_value);
			}

			@Override
			public <E extends Throwable> void ifPresentOrElse(
				ByteConsumer<? extends E> action,
				Runnable<? extends E> emptyAction)
			throws E
			{
				action.accept(m_value);
			}

			@Override
			public byte orElse(byte other)
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> byte orElseGet(
				ByteSupplier<? extends E> supplier)
			throws E
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> byte orElseThrow(
				Supplier<? extends E, ? extends E> exceptionSupplier)
			throws E
			{
				return m_value;
			}
		}
	}

	class OptionalShort extends OptionalBase
	{
		public static final OptionalShort EMPTY = new OptionalShort();

		private OptionalShort()
		{
		}

		public static OptionalShort of(short value)
		{
			return new Present(value);
		}

		public short getAsShort()
		{
			throw new NoSuchElementException("No value present");
		}

		public <E extends Throwable> void ifPresent(
			ShortConsumer<? extends E> action)
		throws E
		{
		}

		public <E extends Throwable> void ifPresentOrElse(
			ShortConsumer<? extends E> action,
			Runnable<? extends E> emptyAction)
		throws E
		{
			emptyAction.run();
		}

		public short orElse(short other)
		{
			return other;
		}

		public <E extends Throwable> short orElseGet(
			ShortSupplier<? extends E> supplier)
		throws E
		{
			return supplier.getAsShort();
		}

		public <E extends Throwable> short orElseThrow(
			Supplier<? extends E, ? extends E> exceptionSupplier)
		throws E
		{
			throw exceptionSupplier.get();
		}

		private static final class Present extends OptionalShort
		{
			private final short m_value;

			private Present(short value)
			{
				m_value = value;
			}

			@Override
			public boolean isPresent()
			{
				return true;
			}

			@Override
			public boolean equals(Object obj)
			{
				return obj instanceof Present
					&& (m_value == ((Present)obj).m_value);
			}

			@Override
			public int hashCode()
			{
				return Short.hashCode(m_value);
			}

			@Override
			public String toString()
			{
				return "OptionalShort[" + m_value + ']';
			}

			@Override
			public short getAsShort()
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> void ifPresent(
				ShortConsumer<? extends E> action)
			throws E
			{
				action.accept(m_value);
			}

			@Override
			public <E extends Throwable> void ifPresentOrElse(
				ShortConsumer<? extends E> action,
				Runnable<? extends E> emptyAction)
			throws E
			{
				action.accept(m_value);
			}

			@Override
			public short orElse(short other)
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> short orElseGet(
				ShortSupplier<? extends E> supplier)
			throws E
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> short orElseThrow(
				Supplier<? extends E, ? extends E> exceptionSupplier)
			throws E
			{
				return m_value;
			}
		}
	}

	class OptionalChar extends OptionalBase
	{
		public static final OptionalChar EMPTY = new OptionalChar();

		private OptionalChar()
		{
		}

		public static OptionalChar of(char value)
		{
			return new Present(value);
		}

		public char getAsChar()
		{
			throw new NoSuchElementException("No value present");
		}

		public <E extends Throwable> void ifPresent(
			CharConsumer<? extends E> action)
		throws E
		{
		}

		public <E extends Throwable> void ifPresentOrElse(
			CharConsumer<? extends E> action, Runnable<? extends E> emptyAction)
		throws E
		{
			emptyAction.run();
		}

		public char orElse(char other)
		{
			return other;
		}

		public <E extends Throwable> char orElseGet(
			CharSupplier<? extends E> supplier)
		throws E
		{
			return supplier.getAsChar();
		}

		public <E extends Throwable> char orElseThrow(
			Supplier<? extends E, ? extends E> exceptionSupplier)
		throws E
		{
			throw exceptionSupplier.get();
		}

		private static final class Present extends OptionalChar
		{
			private final char m_value;

			private Present(char value)
			{
				m_value = value;
			}

			@Override
			public boolean isPresent()
			{
				return true;
			}

			@Override
			public boolean equals(Object obj)
			{
				return obj instanceof Present
					&& (m_value == ((Present)obj).m_value);
			}

			@Override
			public int hashCode()
			{
				return Character.hashCode(m_value);
			}

			@Override
			public String toString()
			{
				return "OptionalChar[" + (int)m_value + ']';
			}

			@Override
			public char getAsChar()
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> void ifPresent(
				CharConsumer<? extends E> action)
			throws E
			{
				action.accept(m_value);
			}

			@Override
			public <E extends Throwable> void ifPresentOrElse(
				CharConsumer<? extends E> action,
				Runnable<? extends E> emptyAction)
			throws E
			{
				action.accept(m_value);
			}

			@Override
			public char orElse(char other)
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> char orElseGet(
				CharSupplier<? extends E> supplier)
			throws E
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> char orElseThrow(
				Supplier<? extends E, ? extends E> exceptionSupplier)
			throws E
			{
				return m_value;
			}
		}
	}

	class OptionalFloat extends OptionalBase
	{
		public static final OptionalFloat EMPTY = new OptionalFloat();

		private OptionalFloat()
		{
		}

		public static OptionalFloat of(float value)
		{
			return new Present(value);
		}

		public float getAsFloat()
		{
			throw new NoSuchElementException("No value present");
		}

		public <E extends Throwable> void ifPresent(
			FloatConsumer<? extends E> action)
		throws E
		{
		}

		public <E extends Throwable> void ifPresentOrElse(
			FloatConsumer<? extends E> action, Runnable<? extends E> emptyAction)
		throws E
		{
			emptyAction.run();
		}

		public float orElse(float other)
		{
			return other;
		}

		public <E extends Throwable> float orElseGet(
			FloatSupplier<? extends E> supplier)
		throws E
		{
			return supplier.getAsFloat();
		}

		public <E extends Throwable> float orElseThrow(
			Supplier<? extends E, ? extends E> exceptionSupplier)
		throws E
		{
			throw exceptionSupplier.get();
		}

		private static final class Present extends OptionalFloat
		{
			private final float m_value;

			private Present(float value)
			{
				m_value = value;
			}

			@Override
			public boolean isPresent()
			{
				return true;
			}

			@Override
			public boolean equals(Object obj)
			{
				return obj instanceof Present
					&& (0 == Float.compare(m_value, ((Present)obj).m_value));
			}

			@Override
			public int hashCode()
			{
				return Float.hashCode(m_value);
			}

			@Override
			public String toString()
			{
				return "OptionalFloat[" + m_value + ']';
			}

			@Override
			public float getAsFloat()
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> void ifPresent(
				FloatConsumer<? extends E> action)
			throws E
			{
				action.accept(m_value);
			}

			@Override
			public <E extends Throwable> void ifPresentOrElse(
				FloatConsumer<? extends E> action,
				Runnable<? extends E> emptyAction)
			throws E
			{
				action.accept(m_value);
			}

			@Override
			public float orElse(float other)
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> float orElseGet(
				FloatSupplier<? extends E> supplier)
			throws E
			{
				return m_value;
			}

			@Override
			public <E extends Throwable> float orElseThrow(
				Supplier<? extends E, ? extends E> exceptionSupplier)
			throws E
			{
				return m_value;
			}
		}
	}
}
