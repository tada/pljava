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
package org.postgresql.pljava;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.collectArguments;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.lookup;
import java.lang.invoke.MethodType;
import static java.lang.invoke.MethodType.methodType;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import java.security.AccessController;
import java.security.Permission;
import java.security.PermissionCollection;

import java.sql.SQLException;
import java.sql.SQLDataException;

import java.util.Arrays;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyEnumeration;
import static java.util.Collections.enumeration;
import java.util.Enumeration;
import java.util.List;
import static java.util.Objects.requireNonNull;

import java.util.function.Predicate;

import org.postgresql.pljava.adt.spi.AbstractType;
import org.postgresql.pljava.adt.spi.AbstractType.Bindings;
import org.postgresql.pljava.adt.spi.Datum;
import org.postgresql.pljava.adt.spi.TwosComplement;

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.RegType;
import org.postgresql.pljava.model.TupleTableSlot.Indexed;

import static org.postgresql.pljava.adt.spi.AbstractType.erase;
import static org.postgresql.pljava.adt.spi.AbstractType.isSubtype;
import static org.postgresql.pljava.adt.spi.AbstractType.refine;
import static org.postgresql.pljava.adt.spi.AbstractType.specialization;
import static org.postgresql.pljava.adt.spi.AbstractType.substitute;

/**
 * Base for classes that implement data types over raw PostgreSQL datums.
 *<p>
 * A PL/Java data type adapter is a concrete subclass of this class that knows
 * the structure of one or more PostgreSQL data types and can convert between
 * their raw {@code Datum} form and an appropriate Java class or primitive type
 * for use in PL/Java code. It will use the {@code Via...} enum declared here
 * (to indicate how it will access the PostgreSQL {@code Datum}), and extend
 * an {@code As...} abstract class declared here (to indicate the supported
 * Java reference or primitive type).
 *<p>
 * An adapter should be stateless and thread-safe. There should be no need to
 * instantiate more than one instance of an adapter for a given type mapping.
 *<p>
 * An adapter has a "top" type T, indicating the type it will present to client
 * code, and an "under" type U, which client code can generally wildcard and
 * ignore; an implementing class that can be composed over another adapter uses
 * U to indicate what that "under" adapter's "top" type must be. The Java
 * compiler records enough information for both parameters to allow PL/Java to
 * reconstruct the type relationships in a stack of composed adapters.
 *<p>
 * An implementing leaf adapter (which will work directly on PostgreSQL Datum
 * rather than being composed over another adapter) can declare {@code Void}
 * for U by convention. An adapter meant to be composed over another, where the
 * "under" adapter has a primitive type, can declare the primitive type's boxed
 * counterpart as U.
 *<p>
 * For a primitive-typed adapter, the "top" type is implicit in the class name
 * {@code asLong}, {@code asInt}, and so on, and the "under" type follows as the
 * parameter U. For ease of reading, the type parameters of the two-parameter
 * classes like {@code As<T,U>} are also in that order, T first.
 *<p>
 * The precise meaning of the "top" type T depends on whether an adapter is
 * an instance of {@code As<T,U>} or of {@code Primitive<T,U>}. In the
 * {@code As} case, the top type is a reference type and is given by T directly.
 * In the primitive case, T is the boxed counterpart of the actual top type.
 *<p>
 * To preserve type safety, only classes that are permitted to instantiate
 * this class will be able to manipulate raw {@code Datum}s. An adapter class
 * should avoid leaking a {@code Datum} to other code.
 */
public abstract class Adapter<T,U>
{
	final Type m_topType;
	/**
	 * The "under" adapter in the composed case; null in a leaf adapter.
	 */
	final Adapter<U,?> m_underAdapter;
	final MethodHandle m_fetchHandle;

	/**
	 * In this private constructor, <var>witness</var> is declared as
	 * {@code Type} rather than {@code Class<T>}.
	 *<p>
	 * It can be invoked that way from {@code As} for array adapters; otherwise,
	 * the subclass constructors all declare the parameter as {@code Class<T>}.
	 */
	private <A extends Adapter<U,?>, C extends Contract<T>> Adapter(
		Configuration configuration, A over, C using, Type witness)
	{
		requireNonNull(configuration,
			getClass() + " instantiated without a Configuration object");
		if ( getClass() != configuration.m_class )
			throw new IllegalArgumentException(
				getClass() + " instantiated with a Configuration object " +
				"for the wrong class");

		if ( configuration instanceof Configuration.Leaf )
		{
			if ( null != over )
				throw new IllegalArgumentException(
					getClass() + " instantiated with non-null 'over' but is " +
					"a leaf adapter");

			Configuration.Leaf leaf = (Configuration.Leaf)configuration;

			Type top = leaf.m_top;
			/*
			 * If instantiated with a subclass of Contract, the type with
			 * which it specializes Contract may tell us more than our top
			 * type precomputed at configuration.
			 */
			if ( null != using )
				top = specialization(using.getClass(), Contract.class)[0];

			MethodHandle mh = leaf.m_fetch.bindTo(this);
			Class<?> erased = erase(top);
			if ( null == witness )
			{
				if ( top instanceof TypeVariable<?>
					&& 1 == ((TypeVariable<?>)top).getBounds().length )
					top = erased;
			}
			else
			{
				if ( ! isSubtype(witness, erased) )
					throw new IllegalArgumentException(
						"cannot instantiate " + getClass() + " as " +
						"adapter producing " + witness);
				top = witness;
				mh = mh.asType(mh.type().changeReturnType(erase(witness)));
			}
			m_topType = top;
			m_underAdapter = null;
			m_fetchHandle = mh;
			return;
		}

		/*
		 * Very well then, it is not a leaf adapter.
		 */

		requireNonNull(over,
			getClass() + " instantiated with null 'over' but is " +
				"a non-leaf adapter");
		if ( null != using )
			throw new IllegalArgumentException(
				getClass() + " instantiated with non-null 'using' but is " +
				"not a leaf adapter");

		Configuration.NonLeaf nonLeaf = (Configuration.NonLeaf)configuration;

		Type[] refined = refine(over.m_topType, nonLeaf.m_under, nonLeaf.m_top);
		Type under = refined[0];
		Type top   = refined[1];

		if ( null != witness )
		{
			if ( ! isSubtype(witness, top) )
				throw new IllegalArgumentException(
					"cannot instantiate " + getClass() + " as " +
					"adapter producing " + witness);
			top = witness;
		}

		m_topType = top;
		m_underAdapter = over;

		MethodHandle producer = nonLeaf.m_adapt.bindTo(this);
		MethodHandle fetcher  = over.m_fetchHandle;

		MethodType mt = producer
			.type()
			.changeReturnType(erase(top))
			.changeParameterType(1, erase(under));

		producer = producer.asType(mt);
		fetcher  = fetcher.asType(
			fetcher.type().changeReturnType(mt.parameterType(1)));

		m_fetchHandle = collectArguments(producer, 1, fetcher);
	}

	/**
	 * Specifies, for a leaf adapter (one not composed over a lower adapter),
	 * the form in which the value fetched from PostgreSQL will be presented to
	 * it (or how it will produce a value to be stored to PostgreSQL).
	 *<p>
	 * At this level, an adapter is free to use {@code Via.CHAR} and treat
	 * {@code char} internally as a 16-bit unsigned integral type with no other
	 * special meaning. If an adapter will <em>return</em> an unsigned 16-bit
	 * type, it should extend either {@code AsShort.Unsigned} or {@code AsChar},
	 * based on whether the value it returns represents UTF-16 character data.
	 */
	protected enum Via
	{
		DATUM   (   Datum.Input.class, "getDatum"),
		INT64SX (    long.class, "getLongSignExtended"),
		INT64ZX (    long.class, "getLongZeroExtended"),
		DOUBLE  (  double.class, "getDouble"),
		INT32SX (     int.class, "getIntSignExtended"),
		INT32ZX (     int.class, "getIntZeroExtended"),
		FLOAT   (   float.class, "getFloat"),
		SHORT   (   short.class, "getShort"),
		CHAR    (    char.class, "getChar"),
		BYTE    (    byte.class, "getByte"),
		BOOLEAN ( boolean.class, "getBoolean");

		Via(Class<?> type, String method)
		{
			try
			{
				MethodHandle h;
				h = lookup().findVirtual(Datum.Accessor.class, method,
					type.isPrimitive()
					? methodType(
						type, Object.class, int.class)
					: methodType(
						type, Object.class, int.class, Attribute.class));

				if ( type.isPrimitive() )
					h = dropArguments(h, 3, Attribute.class);

				m_handle = h;
			}
			catch ( ReflectiveOperationException e )
			{
				throw wrapped(e);
			}
		}

		MethodHandle m_handle;
	}

	@Override
	public String toString()
	{
		Class<?> c = getClass();
		Module m = c.getModule();
		return
			c.getModule().getName() + "/" +
			c.getCanonicalName().substring(1 + c.getPackageName().length() ) +
			" to produce " + topType();
	}

	public abstract boolean canFetch(RegType pgType);

	public boolean canFetch(Attribute attr)
	{
		return canFetch(attr.type());
	}

	public abstract boolean canFetchNull();

	public static Type topType(Class<? extends Adapter> cls)
	{
		Type[] params = specialization(cls, Adapter.class);
		if ( null == params )
			throw new IllegalArgumentException(
				cls + " does not extend Adapter");
		Type top = params[0];
		if ( Primitive.class.isAssignableFrom(cls) )
		{
			top = methodType((Class<?>)top).unwrap().returnType();
			assert ((Class<?>)top).isPrimitive();
		}
		return top;
	}

	/**
	 * The full generic {@link Type Type} this Adapter presents to Java.
	 *<p>
	 * Unlike the static method, this instance method, on an adapter formed
	 * by composition, returns the actual type obtained by unifying
	 * the "under" adapter's top type with the top adapter's "under" type, then
	 * making the indicated substitutions in the top adapter's "top" type.
	 */
	public Type topType()
	{
		return m_topType;
	}

	public static Type underType(Class<? extends Adapter> cls)
	{
		Type[] params = specialization(cls, Adapter.class);
		if ( null == params )
			throw new IllegalArgumentException(
				cls + " does not extend Adapter");
		return params[1];
	}

	protected static abstract class Configuration
	{
		final Class<? extends Adapter> m_class;
		/**
		 * In the case of a primitive-typed adapter, this will really be the
		 * primitive Class object, not the corresponding boxed class.
		 */
		final Type m_top;

		Configuration(Class<? extends Adapter> cls, Type top)
		{
			m_class = cls;
			m_top = top;
		}

		static class Leaf extends Configuration
		{
			final MethodHandle m_fetch;

			Leaf(Class<? extends Adapter> cls, Type top, MethodHandle fetcher)
			{
				super(cls, top);
				m_fetch = fetcher;
			}
		}

		static class NonLeaf extends Configuration
		{
			/**
			 * For an adapter meant to compose over a primitive-typed one, this
			 * is the actual primitive class object for the under-adapter's
			 * expected return type, not the boxed counterpart.
			 */
			final Type m_under;
			final MethodHandle m_adapt;

			NonLeaf(
				Class<? extends Adapter> cls, Type top, Type under,
				MethodHandle fetcher)
			{
				super(cls, top);
				m_under = under;
				m_adapt = fetcher;
			}
		}
	}

	/**
	 * Throws a security exception if permission to configure an adapter
	 * isn't held.
	 *<p>
	 * For the time being, there is only Permission("*", "fetch"), so this needs
	 * no parameters and can use a static instance of the permission.
	 */
	@SuppressWarnings("removal") // JEP 411
	private static void checkAllowed()
	{
		AccessController.checkPermission(Permission.INSTANCE);
	}

	protected static Configuration configure(
		Class<? extends Adapter> cls, Via via)
	{
		Adapter.class.getModule().addReads(cls.getModule());
		Type top = topType(cls);
		Type under = underType(cls);
		Class<?> topErased = erase(top);
		Class<?> underErased = erase(under);

		if ( Primitive.class.isAssignableFrom(cls) )
		{
			MethodType mt = methodType(topErased);
			assert mt.hasWrappers();
			top = topErased = mt.unwrap().returnType();
		}

		MethodHandle underFetcher = null;
		String fetchName;
		Predicate<Method> fetchPredicate;

		if ( Void.class == underErased )
		{
			checkAllowed();
			requireNonNull(via, "a leaf Adapter must have a non-null Via");
			underFetcher = via.m_handle;
			underErased = underFetcher.type().returnType();
			Class<?>[] params = { Attribute.class, underErased };
			final String fn = fetchName = "fetch";
			fetchPredicate = m -> fn.equals(m.getName())
				&& Arrays.equals(m.getParameterTypes(), params);
		}
		else
		{
			if ( null != via )
				throw new IllegalArgumentException(
					"a non-leaf (U is not Void) adapter must have null Via");
			final String fn = fetchName = "adapt";
			MethodType mt = methodType(underErased);
			if ( mt.hasWrappers() ) // Void, handled above, won't be seen here
			{
				Class<?> underOrig = underErased;
				Class<?> underPrim = mt.unwrap().parameterType(0);
				fetchPredicate = m ->
				{
					if ( ! fn.equals(m.getName()) )
						return false;
					Class<?>[] ptypes = m.getParameterTypes();
					return
						2 == ptypes.length && Attribute.class == ptypes[0] &&
						( underOrig == ptypes[1] || underPrim == ptypes[1] );
				};
			}
			else
			{
				Class<?>[] params = { Attribute.class, underErased };
				fetchPredicate = m -> fn.equals(m.getName())
					&& Arrays.equals(m.getParameterTypes(), params);
			}
		}

		Method[] fetchCandidates = stream(cls.getMethods())
			.filter(fetchPredicate).toArray(Method[]::new);
		if ( 1 < fetchCandidates.length )
			fetchCandidates = stream(fetchCandidates)
				.filter(m -> ! m.isBridge()).toArray(Method[]::new);
		if ( 1 != fetchCandidates.length )
			throw new IllegalArgumentException(
				cls + " lacks a " + fetchName + " method with the " +
				"expected signature");
		if ( ! topErased.isAssignableFrom(fetchCandidates[0].getReturnType()) )
			throw new IllegalArgumentException(
				cls + " lacks a " + fetchName + " method with the " +
				"expected return type");

		MethodHandle fetcher;

		try
		{
			fetcher = lookup().unreflect(fetchCandidates[0]);
		}
		catch ( IllegalAccessException e )
		{
			throw new IllegalArgumentException(
				cls + " has a " + fetchName + " method that is inaccessible",
				e);
		}

		/*
		 * Adjust the return type. isAssignableFrom was already checked, so
		 * this can only be a no-op or a widening, to make sure the handle
		 * will fit invokeExact with the expected return type.
		 */
		fetcher = fetcher.asType(fetcher.type().changeReturnType(topErased));

		if ( null != via )
		{
			fetcher = collectArguments(fetcher, 2, underFetcher);
			return new Configuration.Leaf(cls, top, fetcher);
		}

		Class<?> asFound = fetcher.type().parameterType(1);
		if ( asFound.isPrimitive() )
			under = underErased = asFound;

		return new Configuration.NonLeaf(cls, top, under, fetcher);
	}

	/**
	 * Provided to serve as a superclass for a 'container' class that is used
	 * to group several related adapters without being instantiable
	 * as an adapter itself.
	 *<p>
	 * By being technically a subclass of {@code Adapter}, the container class
	 * will have access to the protected {@code Configuration} class and
	 * {@code configure} method.
	 */
	public static abstract class Container extends Adapter<Void,Void>
	{
		protected Container()
		{
			super(null, null, null, null);
		}
	}

	/**
	 * Superclass for adapters that fetch something and return it as a reference
	 * type T.
	 *<p>
	 * The type variable U for the thing consumed gets no enforcement from
	 * the compiler, because any extending adapter class provides its own
	 * {@code T fetch(Attribute,something)} method, with no abstract version
	 * inherited from this class to constrain it. The method will be found
	 * reflectively by name and parameter types, so the "something" only has to
	 * match the type of the accessor method specified with {@code Via}, or the
	 * type returned by an underlying adapter that this one will be composed
	 * over.
	 *<p>
	 * In particular, that means this is the class to extend even if using a
	 * primitive accessor method, or composing over an adapter that returns a
	 * primitive type, as long as this adapter will return a reference type T.
	 * Such an adapter simply declares that it extends {@code As<T,Void>} when
	 * based on a primitive accessor method, or {@code As<T,boxed-class>} when
	 * composed over another adapter of primitive type, where boxed-class is the
	 * boxed counterpart of the other adapter's primitive type.
	 *<p>
	 * If Java's reflection methods on generic types will be used to compute
	 * the (non-erased) result type of a stack of composed adapters, the type
	 * variable U can be used in relating the input to the output type of each.
	 */
	public abstract static class As<T,U> extends Adapter<T,U>
	{
		private final MethodHandle m_fetchHandleErased;

		/**
		 * Constructor for a simple leaf {@code Adapter}, or a composing
		 * (non-leaf) {@code Adapter} when passed another adapter over which
		 * it should be composed.
		 * @param c Configuration instance generated for this class
		 * @param over null for a leaf Adapter, otherwise another Adapter
		 * to compose this one over
		 * @param witness if not null, the top type the resulting
		 * adapter will produce, if a Class object can specify that more
		 * precisely than the default typing rules.
		 */
		protected <V> As(Configuration c, Adapter<U,V> over, Class<T> witness)
		{
			super(c, over, null, witness);

			MethodHandle mh = m_fetchHandle;
			m_fetchHandleErased =
				mh.asType(mh.type().changeReturnType(Object.class));
		}

		/**
		 * Constructor for a leaf {@code Adapter} that is based on
		 * a {@code Contract}.
		 * @param using the scalar Contract that will be used to produce
		 * the value returned
		 * @param witness if not null, the top type the resulting
		 * adapter will produce, if a Class object can specify that more
		 * precisely than the default typing rules.
		 * @param c Configuration instance generated for this class
		 */
		protected As(
			Contract.Scalar<T> using, Class<T> witness, Configuration c)
		{
			super(c, null, using, witness);

			MethodHandle mh = m_fetchHandle;
			m_fetchHandleErased =
				mh.asType(mh.type().changeReturnType(Object.class));
		}

		/**
		 * Used only by the {@code Array} subclass below.
		 */
		private <E,A extends As<E,?>> As(
			Contract.Array<T,E> using, As<E,?> adapter, Class<T> witness,
			Configuration c)
		{
			super(c, null, using,
				witness != null ? witness : refinement(using, adapter));

			MethodHandle mh = m_fetchHandle;
			m_fetchHandleErased =
				mh.asType(mh.type().changeReturnType(Object.class));
		}

		private static <T,E> Type refinement(
			Contract.Array<T,E> using, As<E,?> adapter)
		{
			Type[] unrefined =
				specialization(using.getClass(), Contract.Array.class);
			Type result = unrefined[0];
			Type element = unrefined[1];
			return refine(adapter.topType(), element, result)[1];
		}

		public final <B> T fetch(
			Datum.Accessor<B,?> acc, B buffer, int offset, Attribute a)
		{
			try
			{
				return (T)
					m_fetchHandleErased.invokeExact(a, acc, buffer, offset, a);
			}
			catch ( Throwable t )
			{
				throw wrapped(t);
			}
		}

		@Override
		public boolean canFetchNull()
		{
			return true;
		}

		public T fetchNull(Attribute a) throws SQLException
		{
			return null;
		}
	}

	public abstract static class Array<T,E> extends As<T,Void>
	{
		protected final Contract.Array<T,E> m_contract;
		protected final As<E,?> m_elementAdapter;

		/**
		 * Constructor for a leaf array {@code Adapter} that is based on
		 * a {@code Contract.Array}.
		 * @param using the array Contract that will be used to produce
		 * the value returned
		 * @param adapter an Adapter producing a representation of the array's
		 * element type
		 * @param witness if not null, the top type the resulting
		 * adapter will produce, if a Class object can specify that more
		 * precisely than the default typing rules.
		 * @param c Configuration instance generated for this class
		 */
		protected Array(
			Contract.Array<T,E> using, As<E,?> adapter, Class<T> witness,
			Configuration c)
		{
			super(using, adapter, witness, c);
			m_contract = using;
			m_elementAdapter = adapter;
		}
	}

	/**
	 * Ancestor class for adapters that fetch something and return it as
	 * a Java primitive type.
	 *<p>
	 * Subclasses for integral types, namely {@code AsLong}, {@code asInt},
	 * and {@code AsShort}, cannot be extended directly, but only via their
	 * {@code Signed} or {@code Unsigned} nested subclasses, according to how
	 * the value is meant to be used. Nothing can change how Java treats the
	 * primitive types (always as signed), but the {@code Signed} and
	 * {@code Unsigned} subclasses here offer methods for the operations that
	 * differ, allowing the right behavior to be achieved if those methods
	 * are used.
	 *<p>
	 * Whether an adapter extends {@code AsShort.Unsigned} or {@code AsChar}
	 * (also an unsigned 16-bit type) should be determined based on whether
	 * the resulting value is meant to have a UTF-16 character meaning.
	 */
	public abstract static class Primitive<T,U> extends Adapter<T,U>
	{
		private <V,A extends Adapter<U,V>> Primitive(Configuration c, A over)
		{
			super(c, over, null, null);
		}

		@Override
		public boolean canFetchNull()
		{
			return false;
		}
	}

	public abstract static class AsLong<U> extends Primitive<Long,U>
	implements TwosComplement
	{
		private <V,A extends Adapter<U,V>> AsLong(Configuration c, A over)
		{
			super(c, over);
		}

		public final <B> long fetch(
			Datum.Accessor<B,?> acc, B buffer, int offset, Attribute a)
		{
			try
			{
				return (long)
					m_fetchHandle.invokeExact(a, acc, buffer, offset, a);
			}
			catch ( Throwable t )
			{
				throw wrapped(t);
			}
		}

		public long fetchNull(Attribute a) throws SQLException
		{
			throw new SQLDataException(
				"SQL NULL cannot be returned as Java long", "22002");
		}

		public abstract static class Signed<U> extends AsLong<U>
		implements TwosComplement.Signed
		{
			protected <V,A extends Adapter<U,V>> Signed(Configuration c, A over)
			{
				super(c, over);
			}
		}

		public abstract static class Unsigned<U> extends AsLong<U>
		implements TwosComplement.Unsigned
		{
			protected <V,A extends Adapter<U,V>> Unsigned(
				Configuration c, A over)
			{
				super(c, over);
			}
		}
	}

	public abstract static class AsDouble<U> extends Primitive<Double,U>
	{
		protected <V,A extends Adapter<U,V>> AsDouble(Configuration c, A over)
		{
			super(c, over);
		}

		public final <B> double fetch(
			Datum.Accessor<B,?> acc, B buffer, int offset, Attribute a)
		{
			try
			{
				return (double)
					m_fetchHandle.invokeExact(a, acc, buffer, offset, a);
			}
			catch ( Throwable t )
			{
				throw wrapped(t);
			}
		}

		public double fetchNull(Attribute a) throws SQLException
		{
			throw new SQLDataException(
				"SQL NULL cannot be returned as Java double", "22002");
		}
	}

	public abstract static class AsInt<U> extends Primitive<Integer,U>
	implements TwosComplement
	{
		private <V,A extends Adapter<U,V>> AsInt(Configuration c, A over)
		{
			super(c, over);
		}

		public final <B> int fetch(
			Datum.Accessor<B,?> acc, B buffer, int offset, Attribute a)
		{
			try
			{
				return (int)
					m_fetchHandle.invokeExact(a, acc, buffer, offset, a);
			}
			catch ( Throwable t )
			{
				throw wrapped(t);
			}
		}

		public int fetchNull(Attribute a) throws SQLException
		{
			throw new SQLDataException(
				"SQL NULL cannot be returned as Java int", "22002");
		}

		public abstract static class Signed<U> extends AsInt<U>
		implements TwosComplement.Signed
		{
			protected <V,A extends Adapter<U,V>> Signed(Configuration c, A over)
			{
				super(c, over);
			}
		}

		public abstract static class Unsigned<U> extends AsInt<U>
		implements TwosComplement.Unsigned
		{
			protected <V,A extends Adapter<U,V>> Unsigned(
				Configuration c, A over)
			{
				super(c, over);
			}
		}
	}

	public abstract static class AsFloat<U> extends Primitive<Float,U>
	{
		protected <V,A extends Adapter<U,V>> AsFloat(Configuration c, A over)
		{
			super(c, over);
		}

		public final <B> float fetch(
			Datum.Accessor<B,?> acc, B buffer, int offset, Attribute a)
		{
			try
			{
				return (float)
					m_fetchHandle.invokeExact(a, acc, buffer, offset, a);
			}
			catch ( Throwable t )
			{
				throw wrapped(t);
			}
		}

		public float fetchNull(Attribute a) throws SQLException
		{
			throw new SQLDataException(
				"SQL NULL cannot be returned as Java float", "22002");
		}
	}

	public abstract static class AsShort<U> extends Primitive<Short,U>
	implements TwosComplement
	{
		private <V,A extends Adapter<U,V>> AsShort(Configuration c, A over)
		{
			super(c, over);
		}

		public final <B> short fetch(
			Datum.Accessor<B,?> acc, B buffer, int offset, Attribute a)
		{
			try
			{
				return (short)
					m_fetchHandle.invokeExact(a, acc, buffer, offset, a);
			}
			catch ( Throwable t )
			{
				throw wrapped(t);
			}
		}

		public short fetchNull(Attribute a) throws SQLException
		{
			throw new SQLDataException(
				"SQL NULL cannot be returned as Java short", "22002");
		}

		public abstract static class Signed<U> extends AsShort<U>
		implements TwosComplement.Signed
		{
			protected <V,A extends Adapter<U,V>> Signed(Configuration c, A over)
			{
				super(c, over);
			}
		}

		public abstract static class Unsigned<U> extends AsShort<U>
		implements TwosComplement.Unsigned
		{
			protected <V,A extends Adapter<U,V>> Unsigned(
				Configuration c, A over)
			{
				super(c, over);
			}
		}
	}

	public abstract static class AsChar<U> extends Primitive<Character,U>
	{
		protected <V,A extends Adapter<U,V>> AsChar(Configuration c, A over)
		{
			super(c, over);
		}

		public final <B> char fetch(
			Datum.Accessor<B,?> acc, B buffer, int offset, Attribute a)
		{
			try
			{
				return (char)
					m_fetchHandle.invokeExact(a, acc, buffer, offset, a);
			}
			catch ( Throwable t )
			{
				throw wrapped(t);
			}
		}

		public char fetchNull(Attribute a) throws SQLException
		{
			throw new SQLDataException(
				"SQL NULL cannot be returned as Java char", "22002");
		}
	}

	public abstract static class AsByte<U> extends Primitive<Byte,U>
	implements TwosComplement
	{
		private <V,A extends Adapter<U,V>> AsByte(Configuration c, A over)
		{
			super(c, over);
		}

		public final <B> byte fetch(
			Datum.Accessor<B,?> acc, B buffer, int offset, Attribute a)
		{
			try
			{
				return (byte)
					m_fetchHandle.invokeExact(a, acc, buffer, offset, a);
			}
			catch ( Throwable t )
			{
				throw wrapped(t);
			}
		}

		public byte fetchNull(Attribute a) throws SQLException
		{
			throw new SQLDataException(
				"SQL NULL cannot be returned as Java byte", "22002");
		}

		public abstract static class Signed<U> extends AsByte<U>
		implements TwosComplement.Signed
		{
			protected <V,A extends Adapter<U,V>> Signed(Configuration c, A over)
			{
				super(c, over);
			}
		}

		public abstract static class Unsigned<U> extends AsByte<U>
		implements TwosComplement.Unsigned
		{
			protected <V,A extends Adapter<U,V>> Unsigned(
				Configuration c, A over)
			{
				super(c, over);
			}
		}
	}

	public abstract static class AsBoolean<U> extends Primitive<Boolean,U>
	{
		protected <V,A extends Adapter<U,V>> AsBoolean(Configuration c, A over)
		{
			super(c, over);
		}

		public final <B> boolean fetch(
			Datum.Accessor<B,?> acc, B buffer, int offset, Attribute a)
		{
			try
			{
				return (boolean)
					m_fetchHandle.invokeExact(a, acc, buffer, offset, a);
			}
			catch ( Throwable t )
			{
				throw wrapped(t);
			}
		}

		public boolean fetchNull(Attribute a) throws SQLException
		{
			throw new SQLDataException(
				"SQL NULL cannot be returned as Java boolean", "22002");
		}
	}

	/**
	 * A marker interface to be extended by functional interfaces that
	 * serve as ADT contracts.
	 *<p>
	 * It facilitates the declaration of "dispenser" interfaces by which
	 * one contract can rely on others.
	 * @param <T> the type to be returned by an instance of the contract
	 */
	public interface Contract<T>
	{
		/**
		 * Marker interface for contracts for simple scalar types.
		 */
		interface Scalar<T> extends Contract<T>
		{
		}

		/**
		 * Base for functional interfaces that serve as contracts
		 * for array-like types.
		 *<p>
		 * The distinguishing feature is an associated {@code Adapter} handling
		 * the element type of the array-like type. This form of contract may
		 * be useful for range and multirange types as well as for arrays.
		 * @param <T> the type to be returned by an instance of the contract
		 * @param <E> the type returned by an associated {@code Adapter} for
		 * the element type
		 */
		public interface Array<T,E> extends Contract<T>
		{
			/**
			 * Constructs a representation <var>T</var> representing
			 * a PostgreSQL array.
			 * @param nDims the number of array dimensions (always one half of
			 * {@code dimsAndBounds.length}, but passed separately for
			 * convenience)
			 * @param dimsAndBounds the first <var>nDims</var> elements
			 * represent the total number of valid indices for each dimension,
			 * and the next <var>nDims</var> elements represent the first valid index for each
			 * dimension. For example, if nDims is 3, dimsAndBounds[1] is 6, and
			 * dimsAndBounds[4] is -2, then the array's second dimension uses
			 * indices in [-2,4). The array is a copy and may be used freely.
			 * @param adapter an Adapter producing a representation of
			 * the array's element type
			 * @param slot A TupleTableSlot with multiple components accessible
			 * by a (single, flat) index, all of the same type, described by
			 * a one-element TupleDescriptor.
			 */
			T construct(
				int nDims, int[] dimsAndBounds, As<E,?> adapter, Indexed slot)
				throws SQLException;
		}
	}

	/**
	 * Functional interface able to dispense one instance of an ADT by passing
	 * its constituent values to a supplied {@code Contract} and returning
	 * whatever that returns.
	 */
	@FunctionalInterface
	public interface Dispenser<T, U extends Contract<T>>
	{
		T get(U constructor);
	}

	/**
	 * Functional interface able to dispense multiple instances of an ADT
	 * identified by a zero-based index, passing the its constituent values
	 * to a supplied {@code Contract} and returning whatever that returns.
	 */
	@FunctionalInterface
	public interface PullDispenser<T, U extends Contract<T>>
	{
		T get(int index, U constructor);
	}

	private static RuntimeException wrapped(Throwable t)
	{
		if ( t instanceof RuntimeException )
			return (RuntimeException)t;
		if ( t instanceof Error )
			throw (Error)t;
		return new AdapterException(t.getMessage(), t);
	}

	public static class AdapterException extends RuntimeException
	{
		AdapterException(String message, Throwable cause)
		{
			super(message, cause, true, false);
		}
	}

	/**
	 * A permission allowing the creation of a leaf {@code Adapter}.
	 *<p>
	 * The proper spelling in a policy file is
	 * {@code org.postgresql.pljava.Adapter$Permission}.
	 *<p>
	 * For the time being, only {@code "*"} is allowed as the <var>name</var>,
	 * and only {@code "fetch"} as the <var>actions</var>.
	 *<p>
	 * Only a "leaf" adapter (one that will interact with PostgreSQL datum
	 * values directly) requires permission. Definition of composing adapters
	 * (those that can be applied over another adapter and transform the Java
	 * values somehow) is unrestricted.
	 */
	public static final class Permission extends java.security.Permission
	{
		private static final long serialVersionUID = 1L;

		/**
		 * An instance of this permission (not a singleton, merely one among
		 * possible others).
		 */
		static final Permission INSTANCE = new Permission("*", "fetch");

		public Permission(String name, String actions)
		{
			super("*");
			if ( ! ( "*".equals(name) && "fetch".equals(actions) ) )
				throw new IllegalArgumentException(
					"the only currently-allowed name and actions are " +
					"* and fetch, not " + name + " and " + actions);
		}

		@Override
		public boolean equals(Object other)
		{
			return other instanceof Permission;
		}

		@Override
		public int hashCode()
		{
			return 131129;
		}

		@Override
		public String getActions()
		{
			return "fetch";
		}

		@Override
		public PermissionCollection newPermissionCollection()
		{
			return new Collection();
		}

		@Override
		public boolean implies(java.security.Permission p)
		{
			return p instanceof Permission;
		}

		static class Collection extends PermissionCollection
		{
			private static final long serialVersionUID = 1L;

			Permission the_permission = null;

			@Override
			public void add(java.security.Permission p)
			{
				if ( isReadOnly() )
					throw new SecurityException(
						"attempt to add a Permission to a readonly " +
						"PermissionCollection");

				if ( ! (p instanceof Permission) )
					throw new IllegalArgumentException(
						"invalid in homogeneous PermissionCollection: " + p);

				if ( null == the_permission )
					the_permission = (Permission) p;
			}

			@Override
			public boolean implies(java.security.Permission p)
			{
				if ( null == the_permission )
					return false;
				return the_permission.implies(p);
			}

			@Override
			public Enumeration<java.security.Permission> elements()
			{
				if ( null == the_permission )
					return emptyEnumeration();
				return enumeration(List.of(the_permission));
			}
		}
	}
}
