/*
 * Copyright (c) 2020-2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.adt.spi;

import static java.lang.System.identityHashCode;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

import static java.util.Arrays.stream;
import static java.util.Collections.addAll;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import static java.util.Objects.requireNonNull;

import static java.util.stream.Stream.concat;
import static java.util.stream.Collectors.joining;

/**
 * Custom implementations of Java's {@link Type Type} interfaces, with methods
 * for a couple useful manipulations.
 *<p>
 * The implementations returned from Java reflection methods are internal, with
 * no way to instantiate arbitrary new ones to represent the results of
 * computations with them.
 *<p>
 * Note: the implementations here do not override {@code equals} and
 * {@code hashCode} inherited from {@code Object}. The JDK internal ones do,
 * but not with documented behaviors, so it didn't seem worthwhile to try
 * to match them. (The API specifies an {@code equals} behavior only for
 * {@code ParameterizedType}, and no corresponding {@code hashCode} even for
 * that, so good luck matching it.) Results from methods in this class can
 * include new objects (instances of these classes) and original ones
 * constructed by Java; don't assume anything sane will happen using
 * {@code equals} or {@code hashCode} between them. There is a
 * {@code typesEqual} static method defined here to do that job.
 */
public abstract class AbstractType implements Type
{
	enum TypeKind
	{
		ARRAY(GenericArrayType.class),
		PT(ParameterizedType.class),
		TV(TypeVariable.class),
		WILDCARD(WildcardType.class),
		CLASS(Class.class);

		private Class<? extends Type> m_class;

		TypeKind(Class<? extends Type> cls)
		{
			m_class = cls;
		}

		static TypeKind of(Class<? extends Type> cls)
		{
			for ( TypeKind k : values() )
				if ( k.m_class.isAssignableFrom(cls) )
					return k;
			throw new AssertionError("TypeKind nonexhaustive: " + cls);
		}
	}

	/**
	 * Compare two Types for equality without relying on their own
	 * {@code equals} methods.
	 */
	static boolean typesEqual(Type a, Type b)
	{
		if ( a == b )
			return true;

		if ( null == a  ||  null == b )
			return false;

		TypeKind ak = TypeKind.of(a.getClass());
		TypeKind bk = TypeKind.of(b.getClass());

		if ( ak != bk )
			return false;

		switch ( ak )
		{
		case ARRAY:
			GenericArrayType gaa = (GenericArrayType)a;
			GenericArrayType gab = (GenericArrayType)b;
			return typesEqual(gaa, gab);
		case PT:
			ParameterizedType pta = (ParameterizedType)a;
			ParameterizedType ptb = (ParameterizedType)b;
			if ( ! typesEqual(pta.getRawType(), ptb.getRawType()) )
				return false;
			Type[] taa = pta.getActualTypeArguments();
			Type[] tab = ptb.getActualTypeArguments();
			if ( taa.length != tab.length )
				return false;
			for ( int i = 0; i < taa.length; ++ i )
				if ( ! typesEqual(taa[i], tab[i]) )
					return false;
			return true;
		case TV:
			TypeVariable<?> tva = (TypeVariable<?>)a;
			TypeVariable<?> tvb = (TypeVariable<?>)b;
			return tva.getGenericDeclaration() == tvb.getGenericDeclaration()
				&& tva.getName().equals(tvb.getName());
		case WILDCARD:
			WildcardType wa = (WildcardType)a;
			WildcardType wb = (WildcardType)b;
			Type[] ua = wa.getUpperBounds();
			Type[] ub = wb.getUpperBounds();
			Type[] la = wa.getLowerBounds();
			Type[] lb = wb.getLowerBounds();
			if ( ua.length != ub.length  ||  la.length != lb.length )
				return false;
			for ( int i = 0; i < ua.length; ++ i )
				if ( ! typesEqual(ua[i], ub[i]) )
					return false;
			for ( int i = 0; i < la.length; ++ i )
				if ( ! typesEqual(la[i], lb[i]) )
					return false;
			return true;
		case CLASS:
			return false; // they failed the == test at the very top
		}

		return false; // unreachable, but tell that to javac
	}

	/**
	 * Refines some {@code Type}s <var>in</var> by unifying the first of them
	 * with <var>using</var>.
	 *<p>
	 * The variadic array of <var>in</var> arguments is returned, modified
	 * in place.
	 *<p>
	 * The type <var>using</var> is unified with {@code in[0]} and then used to
	 * replace {@code in[0]}, while any variable substitutions made in
	 * the unification are repeated in the remaining <var>in</var> elements.
	 */
	public static Type[] refine(Type using, Type... in)
	{
		Map<VKey,Type> bindings = new HashMap<>();
		unify(bindings, using, in[0]);

		TypeVariable<?>[] vars = new TypeVariable<?>[bindings.size()];
		Type           [] args = new Type           [bindings.size()];

		int i = 0;
		for ( Map.Entry<VKey,Type> e : bindings.entrySet() )
		{
			vars[i] = e.getKey().get();
			args[i] = e.getValue();
			++ i;
		}
		Bindings b = new Bindings(vars, args);

		in[0] = using;
		for ( i = 1; i < in.length; ++ i )
			in[i] = substitute(b, in[i]);

		return in;
	}

	/**
	 * A simpleminded unify that assumes one argument is always
	 * the more-specific one, should resolve type variables found in the other,
	 * and that this can be done for cases of interest without generating and
	 * then solving constraints.
	 */
	static void unify(Map<VKey,Type> bindings, Type specific, Type general)
	{
		Type element1;
		Type element2;

		while ( null != (element1 = toElementIfArray(specific))
			&&  null != (element2 = toElementIfArray(general)) )
		{
			specific = element1;
			general  = element2;
		}

		if ( general instanceof TypeVariable<?> )
		{
			// XXX verify here that specific satisfies the variable's bounds
			Type wasBound =
				bindings.put(new VKey((TypeVariable<?>)general), specific);
			if ( null != wasBound && ! typesEqual(specific, wasBound) )
				throw new UnsupportedOperationException(
					"unimplemented case in AbstractType.unify: binding again");
			return;
		}

		if ( general instanceof ParameterizedType )
		{
			ParameterizedType t = (ParameterizedType)general;
			Type[] oldActuals = t.getActualTypeArguments();
			Class<?> raw = (Class<?>)t.getRawType();
			Type[] newActuals = specialization(specific, raw);
			if ( null != newActuals )
			{
				for ( int i = 0; i < oldActuals.length; ++ i )
					unify(bindings, newActuals[i], oldActuals[i]);
				return;
			}
		}
		else if ( general instanceof Class<?> )
		{
			Class<?> c = (Class<?>)general;
			TypeVariable<?>[] formals = c.getTypeParameters();
			Type[] actuals = specialization(specific, c);
			if ( null != actuals )
			{
				for ( int i = 0; i < formals.length; ++ i )
					unify(bindings, actuals[i], formals[i]);
				return;
			}
		}

		throw new IllegalArgumentException(
			"failed to unify " + specific + " with " + general);
	}

	/**
	 * Returns the component type of either a {@code GenericArrayType} or
	 * an array {@code Class}, otherwise null.
	 */
	private static Type toElementIfArray(Type possibleArray)
	{
		if ( possibleArray instanceof GenericArrayType )
			return ((GenericArrayType)possibleArray).getGenericComponentType();
		if ( ! (possibleArray instanceof Class<?>) )
			return null;
		return ((Class<?>)possibleArray).getComponentType(); // null if !array
	}

	/**
	 * Needed: test whether <var>sub</var> is a subtype of <var>sup</var>.
	 *<p>
	 * <b>XXX</b> For the time being, this is nothing but a test of
	 * <em>erased</em> subtyping, hastily implemented by requiring that
	 * {@code specialization(sub, erase(sup))} does not return null.
	 *<p>
	 * This must sooner or later be replaced with an implementation of
	 * the subtyping rules from Java Language Specification 4.10, taking
	 * also type parameterization into account.
	 */
	public static boolean isSubtype(Type sub, Type sup)
	{
		return null != specialization(sub, erase(sup));
	}

	/**
	 * Equivalent to {@code specialization(candidate, expected, null)}.
	 */
	public static Type[] specialization(Type candidate, Class<?> expected)
	{
		return specialization(candidate, expected, null);
	}

	/**
	 * Test whether the type {@code candidate} is, directly or indirectly,
	 * a specialization of generic type {@code expected}.
	 *<p>
	 * For example, the Java type T of a particular adapter A that extends
	 * {@code Adapter.As<T,?>} can be retrieved with
	 * {@code specialization(A.class, As.class)[0]}.
	 *<p>
	 * More generally, this method can retrieve the generic type information
	 * from any "super type token", as first proposed by Neal Gafter in 2006,
	 * where a super type token is generally an instance of an anonymous
	 * subclass that specializes a certain generic type. Although the idea has
	 * been often used, the usages have not settled on one agreed name for the
	 * generic type. This method will work with any of them, by supplying the
	 * expected generic type itself as the second parameter. For example, a
	 * super type token {@code foo} derived from Gafter's suggested class
	 * {@code TypeReference} can be unpacked with
	 * {@code specialization(foo.getClass(), TypeReference.class)}.
	 * @param candidate a type to be checked
	 * @param expected known (normally generic) type to check for
	 * @param rtype array to receive (if non-null) the corresponding
	 * (parameterized or raw) type if the result is non-null.
	 * @return null if candidate does not extend expected,
	 * otherwise the array of type arguments with which it specializes
	 * expected
	 * @throws IllegalArgumentException if passed a Type that is not a
	 * Class or a ParameterizedType
	 * @throws NullPointerException if either argument is null
	 * @throws UnsupportedOperationException if candidate does extend
	 * expected but does not carry the needed parameter bindings (such as
	 * when the raw expected Class itself is passed)
	 */
	public static Type[] specialization(
		Type candidate, Class<?> expected, Type[] rtype)
	{
		Type t = requireNonNull(candidate, "candidate is null");
		requireNonNull(expected, "expected is null");
		boolean superinterfaces = expected.isInterface();
		Class<?> c;
		ParameterizedType pt = null;
		Bindings latestBindings = null;
		boolean ptFound = false;
		boolean rawTypeFound = false;

		if ( t instanceof Class<?> )
		{
			c = (Class<?>)t;
			if ( ! expected.isAssignableFrom(c) )
				return null;
			if ( expected == c )
				rawTypeFound = true;
			else
				latestBindings = // trivial, non-null initial value
					new Bindings(new TypeVariable<?>[0], new Type[0]);
		}
		else if ( t instanceof ParameterizedType )
		{
			pt = (ParameterizedType)t;
			c = (Class<?>)pt.getRawType();
			if ( ! expected.isAssignableFrom(c) )
				return null;
			if ( expected == c )
				ptFound = true;
			else
				latestBindings = new Bindings(latestBindings, pt);
		}
		else
			throw new IllegalArgumentException(
				"expected Class or ParameterizedType, got: " + t);

		if ( ! ptFound  &&  ! rawTypeFound )
		{
			List<Type> pending = new LinkedList<>();
			pending.add(c.getGenericSuperclass());
			if ( superinterfaces )
				addAll(pending, c.getGenericInterfaces());

			while ( ! pending.isEmpty() )
			{
				t = pending.remove(0);
				if ( null == t )
					continue;
				if ( t instanceof Class<?> )
				{
					c = (Class<?>)t;
					if ( expected == c )
					{
						rawTypeFound = true;
						break;
					}
					if ( ! expected.isAssignableFrom(c) )
						continue;
					pending.add(latestBindings);
				}
				else if ( t instanceof ParameterizedType )
				{
					pt = (ParameterizedType)t;
					c = (Class<?>)pt.getRawType();
					if ( expected == c )
					{
						ptFound = true;
						break;
					}
					if ( ! expected.isAssignableFrom(c) )
						continue;
					pending.add(new Bindings(latestBindings, pt));
				}
				else if ( t instanceof Bindings )
				{
					latestBindings = (Bindings)t;
					continue;
				}
				else
					throw new AssertionError(
						"expected Class or ParameterizedType, got: " + t);

				pending.add(c.getGenericSuperclass());
				if ( superinterfaces )
					addAll(pending, c.getGenericInterfaces());
			}
		}

		Type[] actualArgs = null;

		if ( ptFound )
		{
			if ( null != latestBindings )
				pt = (ParameterizedType)
					AbstractType.substitute(latestBindings, pt);
			actualArgs = pt.getActualTypeArguments();
			if ( null != rtype )
				rtype[0] = pt;
		}
		else if ( rawTypeFound )
		{
			actualArgs = new Type[0];
			if ( null != rtype )
				rtype[0] = expected;
		}

		if ( null == actualArgs
			|| actualArgs.length != expected.getTypeParameters().length )
			throw new UnsupportedOperationException(
				"failed checking whether " + candidate +
				" specializes " + expected);

		return actualArgs;
	}

	/**
	 * Returns the erasure of a type.
	 *<p>
	 * If <var>t</var> is a {@code Class}, it is returned unchanged.
	 */
	public static Class<?> erase(Type t)
	{
		if ( t instanceof Class<?> )
		{
			return (Class<?>)t;
		}
		else if ( t instanceof GenericArrayType )
		{
			int dims = 0;
			do
			{
				++ dims;
				GenericArrayType a = (GenericArrayType)t;
				t = a.getGenericComponentType();
			} while ( t instanceof GenericArrayType );
			Class<?> c = (Class<?>)erase(t);
			// in Java 12+ see TypeDescriptor.ofField.arrayType(int)
			return Array.newInstance(c, new int [ dims ]).getClass();
		}
		else if ( t instanceof ParameterizedType )
		{
			return (Class<?>)((ParameterizedType)t).getRawType();
		}
		else if ( t instanceof WildcardType )
		{
			throw new UnsupportedOperationException("erase on wildcard type");
			/*
			 * Probably just resolve all the lower and/or upper bounds, as long
			 * as b is known to be the right set of bindings for the type that
			 * contains the member declaration, but I'm not convinced at present
			 * that wouldn't require more work keeping track of bindings.
			 */
		}
		else if ( t instanceof TypeVariable<?> )
		{
			return erase(((TypeVariable<?>)t).getBounds()[0]);
		}
		else
			throw new UnsupportedOperationException(
				"erase on unknown Type " + t.getClass());
	}

	/**
	 * Recursively descend t substituting any occurrence of a type variable
	 * found in b, returning a new object, or t unchanged if no substitutions
	 * were made.
	 *<p>
	 * Currently throws {@code UnsupportedOperationException} if t is
	 * a wildcard, as that case shouldn't be needed for the analysis of
	 * class/interface inheritance hierarchies that {@code specialization}
	 * is concerned with.
	 *<p>
	 */
	public static Type substitute(Bindings b, Type t)
	{
		if ( t instanceof GenericArrayType )
		{
			GenericArrayType a = (GenericArrayType)t;
			Type oc = a.getGenericComponentType();
			Type nc = substitute(b, oc);
			if ( nc == oc )
				return t;
			return new GenericArray(nc);
		}
		else if ( t instanceof ParameterizedType )
		{
			ParameterizedType p = (ParameterizedType)t;
			Type[] as = p.getActualTypeArguments();
			Type oown = p.getOwnerType();
			Type oraw = p.getRawType();
			assert oraw instanceof Class<?>;

			boolean changed = substituted(b, as);

			if ( null != oown )
			{
				Type nown = substitute(b, oown);
				if ( nown != oown )
				{
					oown = nown;
					changed = true;
				}
			}

			if ( changed )
				return new Parameterized(as, oown, oraw);
			return t;
		}
		else if ( t instanceof WildcardType )
		{
			WildcardType w = (WildcardType)t;
			Type[] lbs = w.getLowerBounds();
			Type[] ubs = w.getUpperBounds();

			boolean changed = substituted(b, lbs) | substituted(b, ubs);

			if ( changed )
				return new Wildcard(lbs, ubs);
			return t;
		}
		else if ( t instanceof TypeVariable<?> )
		{
			/*
			 * First the bad news: there isn't a reimplementation of
			 * TypeVariable here, to handle returning a changed version with
			 * substitutions in its bounds. Doesn't seem worth the effort, as
			 * the classes that hold/supply TypeVariables are Class/Method/
			 * Constructor, and we're not going to be reimplementing *them*.
			 *
			 * Next the good news: TypeVariable bounds are the places where
			 * a good story for terminating recursion would be needed, so
			 * if we can't substitute in them anyway, that's a non-concern.
			 */
			return b.substitute((TypeVariable<?>)t);
		}
		else if ( t instanceof Class<?> )
		{
			return t;
		}
		else
			throw new UnsupportedOperationException(
				"substitute on unknown Type " + t.getClass());
	}

	/**
	 * Applies substitutions in <var>b</var> to each type in <var>types</var>,
	 * updating them in place, returning true if any change resulted.
	 */
	private static boolean substituted(Bindings b, Type[] types)
	{
		boolean changed = false;
		for ( int i = 0; i < types.length; ++ i )
		{
			Type ot = types[i];
			Type nt = substitute(b, ot);
			if ( nt == ot )
				continue;
			types[i] = nt;
			changed = true;
		}
		return changed;
	}

	static String toString(Type t)
	{
		if ( t instanceof Class )
			return ((Class)t).getCanonicalName();
		return t.toString();
	}

	/**
	 * A key class for entering {@code TypeVariable}s in hash structures,
	 * without relying on the undocumented behavior of the Java implementation.
	 *<p>
	 * Assumes that object identity is significant for
	 * {@code GenericDeclaration} instances ({@code Class} instances are chiefly
	 * what will be of interest here), just as {@code typesEqual} does.
	 */
	static final class VKey
	{
		private final TypeVariable<?> m_tv;

		VKey(TypeVariable<?> tv)
		{
			m_tv = tv;
		}

		@Override
		public int hashCode()
		{
			return
				m_tv.getName().hashCode()
					^ identityHashCode(m_tv.getGenericDeclaration());
		}

		@Override
		public boolean equals(Object other)
		{
			if ( this == other )
				return true;
			if ( ! (other instanceof VKey) )
				return false;
			return typesEqual(m_tv, ((VKey)other).m_tv);
		}

		TypeVariable<?> get()
		{
			return m_tv;
		}
	}

	public static TypeVariable<?>[] freeVariables(Type t)
	{
		Set<VKey> result = new HashSet<>();
		freeVariables(result, t);
		return result.stream().map(VKey::get).toArray(TypeVariable<?>[]::new);
	}

	private static void freeVariables(Set<VKey> s, Type t)
	{
		if ( t instanceof Class<?> )
			return;
		if ( t instanceof GenericArrayType )
		{
			GenericArrayType a = (GenericArrayType)t;
			freeVariables(s, a.getGenericComponentType());
			return;
		}
		if ( t instanceof ParameterizedType )
		{
			ParameterizedType p = (ParameterizedType)t;
			freeVariables(s, p.getOwnerType());
			stream(p.getActualTypeArguments())
				.forEach(tt -> freeVariables(s, tt));
			return;
		}
		if ( t instanceof TypeVariable<?> )
		{
			TypeVariable<?> v = (TypeVariable<?>)t;
			if ( s.add(new VKey(v)) )
				stream(v.getBounds()).forEach(tt -> freeVariables(s, tt));
			return;
		}
		if ( t instanceof WildcardType )
		{
			WildcardType w = (WildcardType)t;
			concat(stream(w.getUpperBounds()), stream(w.getLowerBounds()))
				.forEach(tt -> freeVariables(s, tt));
			return;
		}
	}

	@Override
	public String getTypeName()
	{
		return toString();
	}

	static class GenericArray extends AbstractType implements GenericArrayType
	{
		private final Type component;

		GenericArray(Type component)
		{
			this.component = component;
		}

		@Override
		public Type getGenericComponentType()
		{
			return component;
		}

		@Override
		public String toString()
		{
			return toString(component) + "[]";
		}
	}

	static class Parameterized extends AbstractType implements ParameterizedType
	{
		private final Type[] arguments;
		private final Type   owner;
		private final Type   raw;

		Parameterized(Type[] arguments, Type owner, Type raw)
		{
			this.arguments = arguments;
			this.owner     = owner;
			this.raw       = raw;
		}

		@Override
		public Type[] getActualTypeArguments()
		{
			return arguments;
		}

		@Override
		public Type getOwnerType()
		{
			return owner;
		}

		@Override
		public Type getRawType()
		{
			return raw;
		}

		@Override
		public String toString()
		{
			if ( 0 == arguments.length )
				return toString(raw);
			return toString(raw) + stream(arguments)
				.map(AbstractType::toString).collect(joining(",", "<", ">"));
		}
	}

	static class Wildcard extends AbstractType implements WildcardType
	{
		private final Type[] lbounds;
		private final Type[] ubounds;

		Wildcard(Type[] lbounds, Type[] ubounds)
		{
			this.lbounds = lbounds;
			this.ubounds = ubounds;
		}

		@Override
		public Type[] getLowerBounds()
		{
			return lbounds;
		}

		@Override
		public Type[] getUpperBounds()
		{
			return ubounds;
		}

		@Override
		public String toString()
		{
			if ( 0 < lbounds.length )
				return "? super " + stream(lbounds)
					.map(AbstractType::toString).collect(joining(" & "));
			else if ( 0 < ubounds.length && Object.class != ubounds[0] )
				return "? extends " + stream(ubounds)
					.map(AbstractType::toString).collect(joining(" & "));
			else
				return "?";
		}
	}

	/**
	 * A class recording the bindings made in a ParameterizedType to the type
	 * parameters in a GenericDeclaration&lt;Class&gt;. Implements {@code Type}
	 * so it can be added to the {@code pending} queue in
	 * {@code specialization}.
	 *<p>
	 * In {@code specialization}, the tree of superclasses/superinterfaces will
	 * be searched breadth-first, with all of a node's immediate supers enqueued
	 * before any from the next level. By recording a node's type variable to
	 * type argument bindings in an object of this class, and enqueueing it
	 * before any of the node's supers, any type variables encountered as actual
	 * type arguments to any of those supers should be resolvable in the object
	 * of this class most recently dequeued.
	 */
	public static class Bindings implements Type
	{
		private final TypeVariable<?>[] formalTypeParams;
		private final Type[] actualTypeArgs;

		public Bindings(TypeVariable<?>[] formalParams, Type[] actualArgs)
		{
			actualTypeArgs = actualArgs;
			formalTypeParams = formalParams;
			if ( actualTypeArgs.length != formalTypeParams.length )
				throw new IllegalArgumentException(
					"formalParams and actualArgs differ in length");
			// XXX check actualTypeArgs against bounds of the formalParams
		}

		Bindings(Bindings prior, ParameterizedType pt)
		{
			actualTypeArgs = pt.getActualTypeArguments();
			formalTypeParams =
				((GenericDeclaration)pt.getRawType()).getTypeParameters();
			assert actualTypeArgs.length == formalTypeParams.length;

			if ( 0 == prior.actualTypeArgs.length )
				return;

			for ( int i = 0; i < actualTypeArgs.length; ++ i )
				actualTypeArgs[i] =
					AbstractType.substitute(prior, actualTypeArgs[i]);
		}

		Type substitute(TypeVariable<?> v)
		{
			for ( int i = 0; i < formalTypeParams.length; ++ i )
				if ( typesEqual(formalTypeParams[i], v) )
					return actualTypeArgs[i];
			return v;
		}
	}

	/**
	 * A class dedicated to manipulating the types of multidimensional Java
	 * arrays, and their instances, that conform to PostgreSQL array constraints
	 * (non-'jagged', each dimension's arrays all equal size, no intermediate
	 * nulls).
	 *<p>
	 * Construct a {@code MultiArray} by supplying a component {@link Type} and
	 * a number of dimensions. The resulting {@code MultiArray} represents the
	 * Java array type, and has a number of bracket pairs equal to the supplied
	 * dimensions argument plus those of the component type if it is itself a
	 * Java array. (There could be an {@code Adapter} for some PostgreSQL scalar
	 * type that presents it as a Java array, and then there could be a
	 * PostgreSQL array of that type.) So the type reported by
	 * {@link #arrayType arrayType} may have more bracket pairs than the
	 * {@code MultiArray}'s dimensions. Parentheses are used by
	 * {@link #toString toString} to help see what's going on.
	 *<p>
	 * When converting a {@code MultiArray} to a {@link Sized Sized}, only as
	 * many sizes are supplied as the multiarray's dimensions, and when
	 * converting that to an {@link Sized.Allocated Allocated}, only that much
	 * allocation is done. Populating the arrays at that last allocated level
	 * with the converted elements of the PostgreSQL array is the work left
	 * for the caller.
	 */
	public static class MultiArray
	{
		public final Type component;
		public final int dimensions;

		/**
		 * Constructs a description of a multiarray with a given component type
		 * and dimensions.
		 * @param component the type of the component (which may itself be an
		 * array)
		 * @param dimensions dimensions of the multiarray (if the component type
		 * is an array, the final resulting type will have the sum of its
		 * dimensions and these)
		 */
		public MultiArray(Type component, int dimensions)
		{
			if ( 1 > dimensions )
				throw new IllegalArgumentException(
					"dimensions must be positive: " + dimensions);
			this.component = component;
			this.dimensions = dimensions;
		}

		/**
		 * Returns a representation of the resulting Java array type, with
		 * parentheses around the component type (which may itself be an array
		 * type) and around the array brackets corresponding to this
		 * multiarray's dimensions.
		 */
		@Override
		public String toString()
		{
			return "MultiArray: (" + component + ")([])*" + dimensions;
		}

		/**
		 * Returns the resulting Java array type (which, if the component type
		 * is also an array, does not distinguish between its dimensions and
		 * those of this multiarray).
		 */
		public Type arrayType()
		{
			Type t = component;

			if ( t instanceof Class<?> )
				t = Array.newInstance((Class<?>)t, new int[dimensions])
					.getClass();
			else
				for ( int i = 0 ; i < dimensions ; ++ i )
					t = new GenericArray(t);

			return t;
		}

		/**
		 * Returns a {@code MultiArray} representing an array type <var>t</var>
		 * in a canonical form, with its ultimate non-array type as the
		 * component type, and all of its array dimensions belonging to the
		 * multiarray.
		 */
		public static MultiArray canonicalize(Type t)
		{
			Type t1 = requireNonNull(t);
			int dims = 0;

			for ( ;; )
			{
				t1 = toElementIfArray(t1);
				if ( null == t1 )
					break;
				t = t1;
				++ dims;
			}

			if ( 0 == dims )
				throw new IllegalArgumentException("not an array type: " + t);

			return new MultiArray(t, dims);
		}

		/**
		 * Returns a new {@code MultiArray} with the same Java array type but
		 * where {@link #component} is a non-array type and {@link #dimensions}
		 * holds the total number of dimensions.
		 */
		public MultiArray canonicalize()
		{
			if ( null == toElementIfArray(component) )
				return this;

			MultiArray a = canonicalize(component);
			return new MultiArray(a.component, dimensions + a.dimensions);
		}

		/**
		 * Returns this {@code MultiArray} as a 'prefix' of <var>suffix</var>
		 * (which must have the same ultimate non-array type but a smaller
		 * number of dimensions).
		 *<p>
		 * The result will have the array type of <var>suffix</var> as its
		 * component type, and the dimensions required to have the same overall
		 * Java {@link #arrayType arrayType} as the receiver.
		 */
		public MultiArray asPrefixOf(MultiArray suffix)
		{
			MultiArray pfx = canonicalize();
			MultiArray sfx = suffix.canonicalize();

			if ( 1 + sfx.dimensions > pfx.dimensions )
				throw new IllegalArgumentException(
					"suffix too long: ("+ this +").asPrefixOf("+ suffix +")");

			if ( ! typesEqual(pfx.component, sfx.component) )
				throw new IllegalArgumentException(
					"asPrefixOf with different component types: "
					+ pfx.component + ", " + sfx.component);

			Type c = sfx.arrayType();

			return new MultiArray(c, pfx.dimensions - sfx.dimensions);
		}

		/**
		 * Returns a new {@code MultiArray} with this one's type (possibly a
		 * raw, or parameterized type) refined according to the known type of
		 * <var>model</var>.
		 */
		public MultiArray refine(Type model)
		{
			int modelDims = 0;

			if ( null != toElementIfArray(model) )
			{
				MultiArray cmodel = canonicalize(model);
				modelDims = cmodel.dimensions;
				model = cmodel.component;
			}

			MultiArray canon = canonicalize();

			Type[] rtype = new Type[1];
			if ( null == specialization(model, erase(canon.component), rtype) )
				throw new IllegalArgumentException(
					"refine: " + model + " does not specialize "
					+ canon.component);

			MultiArray result = new MultiArray(rtype[0], canon.dimensions);

			if ( 0 < modelDims )
			{
				MultiArray suffix =	new MultiArray(rtype[0], modelDims);
				result = result.asPrefixOf(suffix);
			}

			return result;
		}

		/**
		 * Returns a {@link Sized Sized} representing this {@code MultiArray}
		 * with a size for each of its dimensions.
		 */
		public Sized size(int... dims)
		{
			return new Sized(dims);
		}

		/**
		 * Represents a {@code MultiArray} for which sizes for its dimensions
		 * have been specified, so that an instance can be allocated.
		 */
		public class Sized
		{
			private final int[] lengths;

			private Sized(int[] dims)
			{
				if ( dims.length != dimensions )
					throw new IllegalArgumentException(
						"("+ this +").size(passed "
						+ dims.length +" dimensions)");
				lengths = dims.clone();
			}

			@Override
			public String toString()
			{
				return MultiArray.this.toString();
			}

			/**
			 * Returns an {@link Allocated Allocated} that wraps a
			 * freshly-allocated array having the sizes recorded here.
			 *<p>
			 * The result is returned with wildcard types. If the caller code
			 * has been written so as to have type variables with the proper
			 * types at compile time, it may do an unchecked cast on the result,
			 * which may make later operations more concise.
			 */
			public Allocated<?,?> allocate()
			{
				Class<?> c = erase(component);
				Object a = Array.newInstance(c, lengths);

				return new Allocated(a);
			}

			/**
			 * Wraps an existing instance of the multiarray type in question.
			 *
			 * @param <TA> the overall Java type of the whole array, which
			 * can be retrieved with array()
			 * @param <TI> the type of the arrays at the final level
			 * (one-dimensional arrays of the component type) that can be
			 * iterated, in order, to be populated or read out. &lt;TI&gt; is
			 * always an array type, but can be a reference array or any
			 * primitive array type, and therefore not as convenient as it might
			 * be, because the least upper bound of those types is
			 * {@code Object}.
			 */
			public class Allocated<TA,TI> implements Iterable<TI>
			{
				final Object array;

				private Allocated(Object a)
				{
					array = requireNonNull(a);
				}

				/**
				 * Returns the resulting array.
				 */
				public TA array()
				{
					@SuppressWarnings("unchecked")
					TA result = (TA)array;
					return result;
				}

				@Override
				public String toString()
				{
					return MultiArray.this.toString();
				}

				/**
				 * Returns an {@code Iterator} over the array(s) at the bottom
				 * level of this multiarray, the ones that are one-dimensional
				 * arrays of the component type.
				 *<p>
				 * They are returned in order, so that a simple loop to copy the
				 * component values into or out of each array in turn will
				 * amount to a row-major traversal (same as PostgreSQL's storage
				 * order) of the whole array.
				 */
				@Override
				public Iterator<TI> iterator()
				{
					final Object[][] arrays = new Object [ dimensions ] [];
					final int[] indices = new int [ dimensions ];
					final int rightmost = dimensions - 1;

					arrays[0] = new Object[] { array };

					for ( int i = 1; i < arrays.length; ++ i )
					{
						Object[] a = arrays[i-1];
						if ( 0 == a.length )
						{
							++ indices[0];
							break;
						}
						arrays[i] = (Object[])requireNonNull(a[0]);
					}

					return new Iterator<TI>()
					{
						@Override
						public boolean hasNext()
						{
							return 0 == indices[0];
						}

						@Override
						public TI next()
						{
							if ( 0 < indices[0] )
								throw new NoSuchElementException();

							@SuppressWarnings("unchecked")
							TI o = (TI)arrays[rightmost][indices[rightmost]++];

							if (indices[rightmost] >= arrays[rightmost].length)
							{
								int i = rightmost - 1;
								while ( 0 <= i )
								{
									if ( ++ indices[i] < arrays[i].length )
										break;
									-- i;
								}
								if ( 0 <= i )
								{
									while ( i < rightmost )
									{
										Object a = arrays[i][indices[i]];
										++ i;
										arrays[i] = (Object[])requireNonNull(a);
										indices[i] = 0;
									}
								}
							}

							return o;
						}
					};
				}
			}
		}
	}
}
