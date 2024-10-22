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
package org.postgresql.pljava.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodHandles;
import static java.lang.invoke.MethodHandles.collectArguments;
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.empty;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.permuteArguments;
import java.lang.invoke.MethodType;
import static java.lang.invoke.MethodType.methodType;
import java.lang.invoke.SwitchPoint;
import java.lang.invoke.VarHandle;
import static java.lang.invoke.VarHandle.AccessMode.GET;
import static java.lang.invoke.VarHandle.AccessMode.SET;

import java.lang.reflect.Method;
import static java.lang.reflect.Modifier.isStatic;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import static java.util.function.UnaryOperator.identity;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import java.util.stream.Stream;

import static org.postgresql.pljava.internal.Backend.doInPG;
import org.postgresql.pljava.internal.DualState; // for JavaDoc
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

/**
 * Tool for implementing objects or families of objects with methods that lazily
 * compute various values and then return the same values until invalidated,
 * after which new values will be lazily computed when next requested.
 *<h2>Synchronization</h2>
 *<p>
 * Items that have been cached are returned directly, until invalidated by
 * the action of {@link SwitchPoint SwitchPoint}.
 *<p>
 * When an item has not been cached or the cached value has been invalidated,
 * its lazy recomputation at next use takes place within {@code doInPG},
 * that is, "on the PG thread". (An extended discussion of what that really
 * means can be found at {@link DualState DualState}.) The PG thread must be the
 * only thread where the {@code SwitchPoint}s will be invalidated, and an old
 * {@code SwitchPoint} must be replaced in its field by a newly-constructed one
 * before the old one is invalidated.
 */
public class SwitchPointCache
{
	private SwitchPointCache() // not to be instantiated
	{
	}

	/**
	 * Whether to cache the value returned by a computation method; true unless
	 * the method has called {@code doNotCache}.
	 *<p>
	 * Because all computation methods are constrained to run on the PG thread,
	 * a simple static suffices.
	 */
	private static boolean cache = true;

	/**
	 * Called from a computation method to prevent caching of the value being
	 * returned.
	 *<p>
	 * This can be useful in cases where a not-yet-determined value should not
	 * 'stick'. Whatever the computation method returns will be returned to the
	 * caller, but the computation method will be reinvoked the next time
	 * a caller wants the value.
	 *<p>
	 * This state is reset on entry and after return of any computation method.
	 * Therefore, if there are actions within a computation method that could
	 * involve calling other {@code SwitchPointCache}-based methods, this method
	 * must be called after all of those to have reliable effect. By convention,
	 * it should be called immediately before returning.
	 */
	public static void doNotCache()
	{
		cache = false;
	}

	/**
	 * Transform a {@code MethodHandle} into one with a reference to itself.
	 * @param m MethodHandle with methodType(r,MethodHandle,p0,...,pk) where the
	 * expected first parameter is a MethodHandle h of methodType(r,p0,...,pk)
	 * that invokes m with inserted first argument h.
	 * @return h
	 */
	public static MethodHandle fix(MethodHandle m)
	{
		MethodHandle[] a = new MethodHandle[1];
		a[0] = m.asSpreader(0, MethodHandle[].class, 1).bindTo(a);
		return a[0];
	}

	/**
	 * Replace {@code slots[index]} with a constant returning {@code v} forever,
	 * immune to invalidation.
	 *<p>
	 * The slot must already be populated, as by the initializer created by
	 * a {@link Builder Builder}; this method adapts the supplied constant to
	 * the slot's existing {@code methodType}.
	 */
	public static void setConstant(MethodHandle[] slots, int index, Object v)
	{
		MethodHandle h = slots[index];
		MethodType t = h.type();
		MethodHandle c = constant(t.returnType(), v);
		c = dropArguments(c, 0, t.parameterArray());
		slots[index] = c;
	}

	/**
	 * Builder for use during the static initialization of a class that uses
	 * {@code SwitchPointCache}.
	 *<p>
	 * The builder's constructor is passed information about the class, and
	 * about a {@link SwitchPoint SwitchPoint} that will be used when the
	 * dependent values need to be invalidated. To accommodate invalidation
	 * schemes with different granularity, the {@code SwitchPoint} used may be
	 * kept in an instance field of the class, or in a static field and
	 * governing all instances of the class, or even somewhere else entirely
	 * and used for widespread or global invalidation.
	 *<p>
	 * The builder's {@link #withDependent withDependent} method is then used to
	 * declare each value that can be computed and cached in an instance of the
	 * class, by giving the name of a <em>static</em> method that computes the
	 * value (given one argument, an instance of the class) and functions to get
	 * and set a {@code MethodHandle}-typed per-instance slot where the
	 * computation result will be cached.
	 *<p>
	 * Finally, the builder's {@link #build build} method returns
	 * a {@code Consumer<T>} that can be saved in a static final field and
	 * invoked in the object's constructor; it will initialize all of the new
	 * instance's fields that were declared in {@code withDependent} calls to
	 * their initial, uncomputed states.
	 */
	public static class Builder<T>
	{
		private final Class<T> m_class;
		private Function<? super T,String> m_describer;
		private UnaryOperator<MethodHandle[]> m_initializer;
		private Lookup m_lookup;
		private Map<String,Method> m_candidates;
		private Function<T,SwitchPoint> m_spGetter;
		private Function<T,MethodHandle[]> m_slotGetter;
		private Class<?> m_receiver;
		private Class<?> m_return;

		/**
		 * Create a builder that will be used to declare dependent values
		 * controlled by a single {@code SwitchPoint} and to create an
		 * initializer for the per-instance slots that will hold their states.
		 * @param c the class being configured by this Builder
		 */
		public Builder(Class<T> c)
		{
			m_receiver = m_class = requireNonNull(c);
			m_describer = Object::toString;
			m_initializer = identity();
		}

		/**
		 * @param describer function, with a signature like that of
		 * {@code Object.toString}, that will produce a useful description of
		 * the object if needed in an exception message. The default if this
		 * method is not called is {@code Object::toString}; a different
		 * describer can be supplied if the output of {@code toString} isn't
		 * well suited for an exception message. If null, any exception will
		 * have its bare constant message with nothing added about the specific
		 * receiver object.
		 */
		public Builder<T> withDescriber(Function<? super T,String> describer)
		{
			if ( null == describer )
				m_describer = o -> "";
			else
				m_describer = o -> ": " + describer.apply(o);
			return this;
		}

		/**
		 * Supply the {@code Lookup} object to be used in resolving dependent
		 * methods.
		 * @param l a {@link Lookup Lookup} object obtained by the caller and
		 * able to introspect in the class
		 */
		public Builder<T> withLookup(Lookup l)
		{
			m_lookup = requireNonNull(l);
			return this;
		}

		/**
		 * Supply the candidate methods to be available to subsequent
		 * {@link #withDependent withDependent} calls.
		 * @param ms array of methods such as the caller may obtain with
		 * {@link Class#getDeclaredMethods getDeclaredMethods}, avoiding the
		 * access complications of having this class do it. The methods will be
		 * filtered to only those that are static with a non-void return type
		 * and exactly one parameter, assignable from the target class, and
		 * uniquely named within that set. Only such methods can be named in
		 * later {@link #withDependent withDependent} calls. No reference to
		 * the array will be retained.
		 */
		public Builder<T> withCandidates(Method[] ms)
		{
			m_candidates = candidatesAmong(Arrays.stream(ms));
			return this;
		}

		/**
		 * Supply a function mapping a receiver object instance to the
		 * {@code SwitchPoint} to be associated with subsequently declared
		 * slots.
		 * @param spGetter a function usable to fetch the SwitchPoint
		 * that controls this cache. It is passed an instance of T but need not
		 * use it (in the case, for example, of a single controlling SwitchPoint
		 * held in a static).
		 */
		public Builder<T> withSwitchPoint(Function<T,SwitchPoint> spGetter)
		{
			m_spGetter = requireNonNull(spGetter);
			return this;
		}

		/**
		 * Supply a function mapping a receiver object instance to the
		 * per-instance {@code MethodHandle} array whose elements will be
		 * the slots.
		 * @param slotGetter a function usable to fetch the slot array
		 * for an instance.
		 */
		public Builder<T> withSlots(Function<T,MethodHandle[]> slotGetter)
		{
			m_slotGetter = requireNonNull(slotGetter);
			return this;
		}

		/**
		 * Adjust the static return type of subsequently declared dependents
		 * that return references.
		 *<p>
		 * This can be a more compact notation if compute methods or API methods
		 * from a superclass or subclass will be reused and the return type
		 * needs to be adjusted to match the static type in the API method
		 * (possibly erased from a generic type).
		 * @param t Class to serve as the following dependents' static return
		 * type. Pass null to discontinue adjusting return types for following
		 * dependents.
		 * @throws IllegalArgumentException if t represents a primitive type.
		 */
		public Builder<T> withReturnType(Class<?> t)
		{
			if ( null != t  &&  t.isPrimitive() )
				throw new IllegalArgumentException(
					"return type adjustment cannot accept primitive type " + t);
			m_return = t;
			return this;
		}

		/**
		 * Adjust the static receiver type of subsequently declared dependents.
		 *<p>
		 * This can be a more compact notation if compute methods or API methods
		 * from a superclass or subclass will be reused and the receiver type
		 * needs to be adjusted to match the static type in the API method
		 * (possibly erased from a generic type).
		 * @param t Class to serve as the following dependents' static receiver
		 * type. Pass null to discontinue adjusting receiver types for following
		 * dependents.
		 * @throws IllegalArgumentException if t is neither a widening nor a
		 * narrowing of the receiver type specified for this builder.
		 */
		public Builder<T> withReceiverType(Class<?> t)
		{
			if ( null != t
				&& ! t.isAssignableFrom(m_class)
				&& ! m_class.isAssignableFrom(t) )
				throw new IllegalArgumentException(
					"receiver type " + m_class + " cannot be adjusted to " + t);
			m_receiver = null == t ? m_class : t;
			return this;
		}

		/**
		 * Return a {@code UnaryOperator<MethodHandle[]>} to be invoked
		 * in the constructor of a client object, applied to a newly-allocated
		 * array of the right number of slots, which will initialize all of the
		 * array's elements with the corresponding fallback method handles
		 * and return the initialized array.
		 *<p>
		 * The initializer can be used conveniently in a constructor that
		 * assigns the array to a final field, or calls a superclass constructor
		 * that does so, to arrange that the array's elements are written
		 * in advance of Java's freeze of the final array reference field.
		 */
		public UnaryOperator<MethodHandle[]> build()
		{
			return m_initializer;
		}

		/**
		 * Declare one dependent item that will be controlled by this builder's
		 * {@code SwitchPoint}.
		 *<p>
		 * An item is declared by naming the <em>static</em> method that can
		 * compute its value when needed, and the index into the per-instance
		 * {@code MethodHandle[]} "slots" array that will be used to cache the
		 * value. Typically, these will be private, and there will be an API
		 * method for retrieving the value, by fetching the method handle from
		 * the array index given here, and invoking it.
		 *<p>
		 * The method handle that will be found in the named slot has a return
		 * type matching the compute method named here, and two parameters; it
		 * expects the receiver object as the first parameter, and itself as
		 * the second. So the typical API method is simply:
		 *<pre>
		 * MethodHandle h = slots[MY_SLOT];
		 * return (cast)h.invokeExact(this, h);
		 *</pre>
		 *<p>
		 * When there is a cached value and the {@code SwitchPoint} has not been
		 * invalidated, the two arguments are ignored and the cached value
		 * is returned.
		 * @param methodName name of the static method that will be used to
		 * compute values for this item. It must be found among the methods
		 * that were passed to the Builder constructor, only considering those
		 * that are static, with a non-void return and one argument of
		 * the class type.
		 * @param index index into the per-instance slot arrray where the cached
		 * state will be maintained.
		 */
		public Builder<T> withDependent(String methodName, int index)
		{
			MethodHandle m;
			MethodHandle recompute;

			try
			{
				m = m_lookup.unreflect(m_candidates.get(methodName));
			}
			catch ( ReflectiveOperationException e )
			{
				throw unchecked(e);
			}

			final MethodHandle only_p_erased = eraseP0(m);
			MethodType mt = only_p_erased.type();
			Class<?> rt = mt.returnType();
			Class<?> pt = m_receiver;
			Function<T,SwitchPoint> spGetter = m_spGetter;
			Function<T,MethodHandle[]> slotGetter = m_slotGetter;
			Function<? super T,String> describer = m_describer;

			if ( ! rt.isPrimitive() )
			{
				Class<?> rtfinal = null == m_return ? rt : m_return;

				final MethodHandle p_and_r_erased =
					m.asType(mt.changeReturnType(Object.class));
				recompute = AS_MH.bindTo((As<T,?>)(h,o,g) -> doInPG(() ->
				{
					MethodHandle[] slots = slotGetter.apply(o);
					MethodHandle gwt = slots[index];
					if ( gwt != g ) // somebody else refreshed it already
						return gwt.invoke(o, gwt);
					/*
					 * Still the same invalidated g, so the task of computing
					 * a fresh value and replacing it has fallen to us.
					 */
					SwitchPoint sp = spGetter.apply(o);
					if ( null == sp  ||  sp.hasBeenInvalidated() )
						throw new IllegalStateException(
							"function call after invalidation of object" +
							describer.apply(o));
					Object v;
					try
					{
						cache = true;
						v = p_and_r_erased.invokeExact(o);
						if ( cache )
						{
							MethodHandle c = constant(rtfinal, v);
							c = dropArguments(c, 0, pt, MethodHandle.class);
							slots[index] = sp.guardWithTest(c, h);
						}
					}
					finally
					{
						cache = true;
					}
					return v;
				}));
				recompute = recompute.asType(
					recompute.type().changeReturnType(rtfinal));
			}
			else if ( int.class == rt )
			{
				recompute = ASINT_MH.bindTo((AsInt<T>)(h,o,g) -> doInPG(() ->
				{
					MethodHandle[] slots = slotGetter.apply(o);
					MethodHandle gwt = slots[index];
					if ( gwt != g ) // somebody else refreshed it already
						return (int)gwt.invoke(o, gwt);
					/*
					 * Still the same invalidated g, so the task of computing
					 * a fresh value and replacing it has fallen to us.
					 */
					SwitchPoint sp = spGetter.apply(o);
					if ( null == sp  ||  sp.hasBeenInvalidated() )
						throw new IllegalStateException(
							"function call after invalidation of object" +
							describer.apply(o));
					int v;
					try
					{
						cache = true;
						v = (int)only_p_erased.invokeExact(o);
						if ( cache )
						{
							MethodHandle c = constant(rt, v);
							c = dropArguments(c, 0, pt, MethodHandle.class);
							slots[index] = sp.guardWithTest(c, h);
						}
					}
					finally
					{
						cache = true;
					}
					return v;
				}));
			}
			else if ( long.class == rt )
			{
				recompute = ASLONG_MH.bindTo((AsLong<T>)(h,o,g) -> doInPG(() ->
				{
					MethodHandle[] slots = slotGetter.apply(o);
					MethodHandle gwt = slots[index];
					if ( gwt != g ) // somebody else refreshed it already
						return (long)gwt.invoke(o, gwt);
					/*
					 * Still the same invalidated g, so the task of computing
					 * a fresh value and replacing it has fallen to us.
					 */
					SwitchPoint sp = spGetter.apply(o);
					if ( null == sp  ||  sp.hasBeenInvalidated() )
						throw new IllegalStateException(
							"function call after invalidation of object" +
							describer.apply(o));
					long v;
					try
					{
						cache = true;
						v = (long)only_p_erased.invokeExact(o);
						if ( cache )
						{
							MethodHandle c = constant(rt, v);
							c = dropArguments(c, 0, pt, MethodHandle.class);
							slots[index] = sp.guardWithTest(c, h);
						}
					}
					finally
					{
						cache = true;
					}
					return v;
				}));
			}
			else if ( boolean.class == rt )
			{
				recompute =
					ASBOOLEAN_MH.bindTo((AsBoolean<T>)(h,o,g) -> doInPG(() ->
				{
					MethodHandle[] slots = slotGetter.apply(o);
					MethodHandle gwt = slots[index];
					if ( gwt != g ) // somebody else refreshed it already
						return (boolean)gwt.invoke(o, gwt);
					/*
					 * Still the same invalidated g, so the task of computing
					 * a fresh value and replacing it has fallen to us.
					 */
					SwitchPoint sp = spGetter.apply(o);
					if ( null == sp  ||  sp.hasBeenInvalidated() )
						throw new IllegalStateException(
							"function call after invalidation of object" +
							describer.apply(o));
					boolean v;
					try
					{
						cache = true;
						v = (boolean)only_p_erased.invokeExact(o);
						if ( cache )
						{
							MethodHandle c = constant(rt, v);
							c = dropArguments(c, 0, pt, MethodHandle.class);
							slots[index] = sp.guardWithTest(c, h);
						}
					}
					finally
					{
						cache = true;
					}
					return v;
				}));
			}
			else if ( double.class == rt )
			{
				recompute =
					ASDOUBLE_MH.bindTo((AsDouble<T>)(h,o,g) -> doInPG(() ->
				{
					MethodHandle[] slots = slotGetter.apply(o);
					MethodHandle gwt = slots[index];
					if ( gwt != g ) // somebody else refreshed it already
						return (double)gwt.invoke(o, gwt);
					/*
					 * Still the same invalidated g, so the task of computing
					 * a fresh value and replacing it has fallen to us.
					 */
					SwitchPoint sp = spGetter.apply(o);
					if ( null == sp  ||  sp.hasBeenInvalidated() )
						throw new IllegalStateException(
							"function call after invalidation of object" +
							describer.apply(o));
					double v;
					try
					{
						cache = true;
						v = (double)only_p_erased.invokeExact(o);
						if ( cache )
						{
							MethodHandle c = constant(rt, v);
							c = dropArguments(c, 0, pt, MethodHandle.class);
							slots[index] = sp.guardWithTest(c, h);
						}
					}
					finally
					{
						cache = true;
					}
					return v;
				}));
			}
			else if ( float.class == rt )
			{
				recompute =
					ASFLOAT_MH.bindTo((AsFloat<T>)(h,o,g) -> doInPG(() ->
				{
					MethodHandle[] slots = slotGetter.apply(o);
					MethodHandle gwt = slots[index];
					if ( gwt != g ) // somebody else refreshed it already
						return (float)gwt.invoke(o, gwt);
					/*
					 * Still the same invalidated g, so the task of computing
					 * a fresh value and replacing it has fallen to us.
					 */
					SwitchPoint sp = spGetter.apply(o);
					if ( null == sp  ||  sp.hasBeenInvalidated() )
						throw new IllegalStateException(
							"function call after invalidation of object" +
							describer.apply(o));
					float v;
					try
					{
						cache = true;
						v = (float)only_p_erased.invokeExact(o);
						if ( cache )
						{
							MethodHandle c = constant(rt, v);
							c = dropArguments(c, 0, pt, MethodHandle.class);
							slots[index] = sp.guardWithTest(c, h);
						}
					}
					finally
					{
						cache = true;
					}
					return v;
				}));
			}
			else if ( short.class == rt )
			{
				recompute =
					ASSHORT_MH.bindTo((AsShort<T>)(h,o,g) -> doInPG(() ->
				{
					MethodHandle[] slots = slotGetter.apply(o);
					MethodHandle gwt = slots[index];
					if ( gwt != g ) // somebody else refreshed it already
						return (short)gwt.invoke(o, gwt);
					/*
					 * Still the same invalidated g, so the task of computing
					 * a fresh value and replacing it has fallen to us.
					 */
					SwitchPoint sp = spGetter.apply(o);
					if ( null == sp  ||  sp.hasBeenInvalidated() )
						throw new IllegalStateException(
							"function call after invalidation of object" +
							describer.apply(o));
					short v;
					try
					{
						cache = true;
						v = (short)only_p_erased.invokeExact(o);
						if ( cache )
						{
							MethodHandle c = constant(rt, v);
							c = dropArguments(c, 0, pt, MethodHandle.class);
							slots[index] = sp.guardWithTest(c, h);
						}
					}
					finally
					{
						cache = true;
					}
					return v;
				}));
			}
			else if ( char.class == rt )
			{
				recompute = ASCHAR_MH.bindTo((AsChar<T>)(h,o,g) -> doInPG(() ->
				{
					MethodHandle[] slots = slotGetter.apply(o);
					MethodHandle gwt = slots[index];
					if ( gwt != g ) // somebody else refreshed it already
						return (char)gwt.invoke(o, gwt);
					/*
					 * Still the same invalidated g, so the task of computing
					 * a fresh value and replacing it has fallen to us.
					 */
					SwitchPoint sp = spGetter.apply(o);
					if ( null == sp  ||  sp.hasBeenInvalidated() )
						throw new IllegalStateException(
							"function call after invalidation of object" +
							describer.apply(o));
					char v;
					try
					{
						cache = true;
						v = (char)only_p_erased.invokeExact(o);
						if ( cache )
						{
							MethodHandle c = constant(rt, v);
							c = dropArguments(c, 0, pt, MethodHandle.class);
							slots[index] = sp.guardWithTest(c, h);
						}
					}
					finally
					{
						cache = true;
					}
					return v;
				}));
			}
			else if ( byte.class == rt )
			{
				recompute = ASBYTE_MH.bindTo((AsByte<T>)(h,o,g) -> doInPG(() ->
				{
					MethodHandle[] slots = slotGetter.apply(o);
					MethodHandle gwt = slots[index];
					if ( gwt != g ) // somebody else refreshed it already
						return (byte)gwt.invoke(o, gwt);
					/*
					 * Still the same invalidated g, so the task of computing
					 * a fresh value and replacing it has fallen to us.
					 */
					SwitchPoint sp = spGetter.apply(o);
					if ( null == sp  ||  sp.hasBeenInvalidated() )
						throw new IllegalStateException(
							"function call after invalidation of object" +
							describer.apply(o));
					byte v;
					try
					{
						cache = true;
						v = (byte)only_p_erased.invokeExact(o);
						if ( cache )
						{
							MethodHandle c = constant(rt, v);
							c = dropArguments(c, 0, pt, MethodHandle.class);
							slots[index] = sp.guardWithTest(c, h);
						}
					}
					finally
					{
						cache = true;
					}
					return v;
				}));
			}
			else
				throw new AssertionError("unhandled primitive"); // pacify javac

			recompute = recompute.asType(
				recompute.type().changeParameterType(1, pt));

			MethodHandle init = fix(recompute);

			m_initializer = m_initializer.andThen(s ->
			{
				s[index] = init;
				return s;
			})::apply;

			return this;
		}

		/**
		 * Return a map from name to {@code Method} for all methods in ms that
		 * are static with a non-void return type and exactly one parameter,
		 * assignable from the target class, and uniquely named within that set.
		 */
		private Map<String,Method> candidatesAmong(Stream<Method> ms)
		{
			Map<String,List<Method>> m1 = ms
				.filter(m ->
					isStatic(m.getModifiers()) &&
					void.class != m.getReturnType() &&
					1 == m.getParameterCount() &&
					m.getParameterTypes()[0].isAssignableFrom(m_class))
				.collect(groupingBy(Method::getName));

			return m1.values().stream()
				.filter(list -> 1 == list.size())
				.map(list -> list.get(0))
				.collect(toMap(Method::getName, identity()));
		}

		private static MethodHandle eraseP0(MethodHandle m)
		{
			MethodType mt = m.type().changeParameterType(0, Object.class);
			return m.asType(mt);
		}
	}
	
	private static final MethodHandle AS_MH;
	private static final MethodHandle ASLONG_MH;
	private static final MethodHandle ASDOUBLE_MH;
	private static final MethodHandle ASINT_MH;
	private static final MethodHandle ASFLOAT_MH;
	private static final MethodHandle ASSHORT_MH;
	private static final MethodHandle ASCHAR_MH;
	private static final MethodHandle ASBYTE_MH;
	private static final MethodHandle ASBOOLEAN_MH;

	static
	{
		Lookup lu = lookup();
		MethodType mt =
			methodType(Object.class,
				MethodHandle.class, Object.class, MethodHandle.class);

		try
		{
			AS_MH = lu.findVirtual(As.class, "compute", mt);

			ASLONG_MH = lu.findVirtual(AsLong.class, "compute",
				mt.changeReturnType(long.class));

			ASDOUBLE_MH = lu.findVirtual(AsDouble.class, "compute",
				mt.changeReturnType(double.class));

			ASINT_MH = lu.findVirtual(AsInt.class, "compute",
				mt.changeReturnType(int.class));

			ASFLOAT_MH = lu.findVirtual(AsFloat.class, "compute",
				mt.changeReturnType(float.class));

			ASSHORT_MH = lu.findVirtual(AsShort.class, "compute",
				mt.changeReturnType(short.class));

			ASCHAR_MH= lu.findVirtual(AsChar.class, "compute",
				mt.changeReturnType(char.class));

			ASBYTE_MH = lu.findVirtual(AsByte.class, "compute",
				mt.changeReturnType(byte.class));

			ASBOOLEAN_MH = lu.findVirtual(AsBoolean.class, "compute",
				mt.changeReturnType(boolean.class));
		}
		catch ( ReflectiveOperationException e )
		{
			throw unchecked(e);
		}
	}

	@FunctionalInterface
	private interface As<T,R>
	{
		R compute(MethodHandle h, T instance, MethodHandle gwt)
		throws Throwable;
	}

	@FunctionalInterface
	private interface AsLong<T>
	{
		long compute(MethodHandle h, T instance, MethodHandle gwt)
		throws Throwable;
	}

	@FunctionalInterface
	private interface AsDouble<T>
	{
		double compute(MethodHandle h, T instance, MethodHandle gwt)
		throws Throwable;
	}

	@FunctionalInterface
	private interface AsInt<T>
	{
		int compute(MethodHandle h, T instance, MethodHandle gwt)
		throws Throwable;
	}

	@FunctionalInterface
	private interface AsFloat<T>
	{
		float compute(MethodHandle h, T instance, MethodHandle gwt)
		throws Throwable;
	}

	@FunctionalInterface
	private interface AsShort<T>
	{
		short compute(MethodHandle h, T instance, MethodHandle gwt)
		throws Throwable;
	}

	@FunctionalInterface
	private interface AsChar<T>
	{
		char compute(MethodHandle h, T instance, MethodHandle gwt)
		throws Throwable;
	}

	@FunctionalInterface
	private interface AsByte<T>
	{
		byte compute(MethodHandle h, T instance, MethodHandle gwt)
		throws Throwable;
	}

	@FunctionalInterface
	private interface AsBoolean<T>
	{
		boolean compute(MethodHandle h, T instance, MethodHandle gwt)
		throws Throwable;
	}
}
