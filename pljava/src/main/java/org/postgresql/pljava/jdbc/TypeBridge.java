/*
 * Copyright (c) 2018-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import static java.util.Collections.addAll;
import java.util.List;
import java.util.LinkedList;

/**
 * Encapsulate some information about Java object classes and their possible
 * mappings to PostgreSQL types.
 *<p>
 * This may be a temporary class that goes away entirely in a future major
 * release of PL/Java that revamps how type mappings are determined. Or, it may
 * evolve and take on greater responsibility in a revamped scheme: type mapping
 * information is, at present, diffused and duplicated a lot of places in
 * PL/Java, and bringing it into one place would not be a bad thing.
 *<p>
 * For now, in the 1.5.x series, this is a simple stopgap so that the few places
 * in PL/Java where an object type can be passed to PostgreSQL (SingleRowWriter,
 * TriggerResultSet, SQLOutputToTuple, PreparedStatement) are able to pass an
 * object that isn't of the class expected by default, and have the right native
 * conversion get selected. All of those sites currently work by some variant of
 * putting supplied objects into an Object array or list later passed to the
 * native code, and when an object will not be of the expected class, what is
 * stored in the array should be a TypeBridge.Holder for it.
 */
public abstract class TypeBridge<S>
{
	/**
	 * Canonical name of the Java class or interface that this TypeBridge
	 * 'captures'.
	 *<p>
	 * Held as a string so that the class does not need to be loaded for a
	 * TypeBridge to be made for it. There can be TypeBridges for classes that
	 * not all supported JRE versions provide.
	 */
	protected final String m_canonName;

	/**
	 * Oid of the PostgreSQL type to be associated by default with this Java
	 * class or interface.
	 *<p>
	 * Stored as a simple int here, not a PL/Java Oid object, which I am tempted
	 * to deprecate.
	 */
	protected final int m_defaultOid;

	/**
	 * If the Java class associated with the TypeBridge <em>is</em> loaded and
	 * available, it can be cached here.
	 *<p>
	 * That will always be the case after a {@link #captures} method has
	 * returned {@code true}.
	 */
	protected Class<S> m_cachedClass;

	@SuppressWarnings("unchecked")
	protected void setCachedClass(Class<?> cls)
	{
		m_cachedClass = (Class<S>)cls;
	}

	/**
	 * List of TypeBridges to check, in order, for one that 'captures' a given
	 * class.
	 *<p>
	 * This list is populated as TypeBridges are constructed, and whatever code
	 * calls the factory methods must take responsibility for the order of the
	 * list, by not constructing one TypeBridge earlier than another one that it
	 * would capture.
	 *<p>
	 * This can't be checked automatically because the classes in question may
	 * not yet be loaded, or even available.
	 */
	private static List<TypeBridge<?>> m_candidates = new LinkedList<>();

	/**
	 * Return an object wrapped, if it is of any type captured by a known
	 * TypeBridge.
	 * @param o An object, representing a value to be presented to PostgreSQL.
	 * @return A Holder wrapping o, or null if no known TypeBridge captures the
	 * type of o, or o itself is null.
	 */
	public static <T, U extends T> TypeBridge<T>.Holder wrap(U o)
	{
		if ( null == o )
			return null;
		Class<?> c = o.getClass();
		for ( TypeBridge<?> tb : m_candidates )
			if ( tb.captures(c) )
			{
				@SuppressWarnings("unchecked")
				TypeBridge<T> tbt = (TypeBridge<T>)tb;
				return tbt.new Holder(o);
			}
		if ( o instanceof TypeBridge<?>.Holder )
			throw new IllegalArgumentException("Not valid as argument: " +
				o.toString());
		return null;
	}

	private TypeBridge(String cName, int dfltOid)
	{
		if ( null == cName )
			throw new NullPointerException("TypeBridge cName must be nonnull.");
		m_canonName = cName;
		m_defaultOid = dfltOid;
		m_candidates.add(this);
	}

	/*
	 * For now, anyway, these factory methods are private; only native code
	 * will be calling them.
	 */

	/**
	 * Construct a TypeBridge given the canonical name of a Java type that need
	 * not be loaded, but is known to be a class (not an interface).
	 */
	private static <T> TypeBridge<T> ofClass(String cName, int dOid)
	{
		return new OfClass<>(cName, dOid);
	}

	/**
	 * Construct a TypeBridge given the canonical name of a Java type that need
	 * not be loaded, but is known to be an interface (not a class).
	 */
	private static <T> TypeBridge<T> ofInterface(String cName, int dOid)
	{
		return new OfInterface<>(cName, dOid);
	}

	/**
	 * Construct a TypeBridge directly from a Class object, when available.
	 */
	private static <T> TypeBridge<T> of(Class<T> c, int dOid)
	{
		String cn = c.getCanonicalName();
		TypeBridge<T> tb =
			c.isInterface() ? ofInterface(cn, dOid) : ofClass(cn, dOid);
		tb.m_cachedClass = c;
		return tb;
	}

	/**
	 * Determine whether this TypeBridge 'captures' a given Class.
	 *<p>
	 * If the class this TypeBridge represents has already been loaded and is
	 * cached here, the test is a simple {@code isAssignableFrom}. Otherwise,
	 * the test is conducted by climbing the superclasses or superinterfaces, as
	 * appropriate, of the passed Class, comparing canonical names. If a match
	 * is found, the winning Class object is cached before returning
	 * {@code true}.
	 */
	public final boolean captures(Class<?> c)
	{
		if ( null != m_cachedClass )
			return m_cachedClass.isAssignableFrom(c);
		return virtuallyCaptures(c);
	}

	/**
	 * Method the two subclasses implement to conduct the "Class-less"
	 * superclass or superinterface check, respectively.
	 */
	protected abstract boolean virtuallyCaptures(Class<?> c);

	/**
	 * TypeBridge subclass representing a class (not an interface).
	 *<p>
	 * Its {@code virtuallyCaptures} method simply climbs the superclass chain.
	 */
	final static class OfClass<S> extends TypeBridge<S>
	{
		private OfClass(String cn, int oid) { super(cn, oid); }

		@Override
		protected boolean virtuallyCaptures(Class<?> c)
		{
			for ( ; null != c ; c = c.getSuperclass() )
			{
				if ( ! m_canonName.equals(c.getCanonicalName()) )
					continue;
				setCachedClass(c);
				return true;
			}
			return false;
		}
	}

	/**
	 * TypeBridge subclass representing an interface (not a class).
	 *<p>
	 * Its {@code virtuallyCaptures} method climbs the superinterfaces,
	 * breadth first.
	 */
	final static class OfInterface<S> extends TypeBridge<S>
	{
		private OfInterface(String cn, int oid) { super(cn, oid); }

		@Override
		protected boolean virtuallyCaptures(Class<?> c)
		{
			List<Class<?>> q = new LinkedList<>();
			q.add(c);

			while ( 0 < q.size() )
			{
				c = q.remove(0);

				if ( ! c.isInterface() )
				{
					addAll(q, c.getInterfaces());
					c = c.getSuperclass();
					if ( null != c )
						q.add(c);
					continue;
				}

				if ( m_canonName.equals(c.getCanonicalName()) )
				{
					setCachedClass(c);
					return true;
				}
				addAll(q, c.getInterfaces());
			}
			return false;
		}
	}

	/**
	 * Class that holds an object reference being passed from Java to PG, when
	 * the object is of one of the known classes that were not accepted by
	 * PL/Java's JDBC driver before PL/Java 1.5.1.
	 *<p>
	 * When a native-code Object-to-Datum coercer encounters a Holder instead of
	 * an object of the normally-expected class for the PostgreSQL type, it can
	 * retrieve the class, classname, default PG type oid, and the payload
	 * object itself, from the Holder, and obtain and apply a different coercer
	 * appropriate to the class.
	 */
	public final class Holder
	{
		private final S m_payload;

		private Holder(S o)
		{
			m_payload = o;
		}

		public Class<S> bridgedClass()
		{
			return m_cachedClass;
		}

		public String className()
		{
			return m_canonName;
		}

		public S payload()
		{
			return m_payload;
		}

		public int defaultOid()
		{
			return m_defaultOid;
		}
	}
}
