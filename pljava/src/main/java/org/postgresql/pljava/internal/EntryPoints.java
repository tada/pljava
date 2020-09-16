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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import static java.lang.invoke.MethodType.methodType;

import java.security.AccessControlContext;
import static java.security.AccessController.doPrivileged;
import java.security.PrivilegedAction;

import java.sql.SQLData;
import java.sql.SQLInput;
import java.sql.SQLOutput;

import static java.util.Objects.requireNonNull;

import org.postgresql.pljava.internal.UncheckedException;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

/*
 * PrivilegedAction is used here in preference to PrivilegedExceptionAction,
 * because a PrivilegedActionException can only wrap an Exception, but method
 * handle invocation is declared to throw any Throwable. So there needs to be
 * wrapping done even with PrivilegedExceptionAction, and whatever is wrapped
 * as a runtime exception will propagate up through PrivilegedAction just fine,
 * leaving only one flavor of wrapping to deal with rather than two.
 */

/**
 * A class to consolidate entry points from C to PL/Java functions.
 *<p>
 * The *invoke methods in this class can be private, as they are invoked only
 * from C via JNI, not from Java.
 *<p>
 * The primary entry point is {@code invoke}. The supplied
 * {@code PrivilegedAction}, as
 * obtained from {@code Function.create}, may have bound references to static
 * parameter areas, and will fetch the actual parameters from there to the
 * stack before invoking the (potentially reentrant) target method. Primitive
 * return values are then stored (after the potentially reentrant method has
 * returned) in the first slot of the static parameter area, to allow a single
 * {@code Object}-returning {@code invoke} method to cover those cases, rather
 * than versions for references, every primitive return type, and {@code void}.
 * The {@code PrivilegedAction} is expected to return null for a {@code void}
 * or primitive-typed target.
 *<p>
 * The remaining methods here are for user-defined type (UDT) support. For now,
 * those are not consolidated into the {@code invoke}/{@code refInvoke} pattern,
 * as UDTs may need to be constructed from the C code while it is populating the
 * static parameter area for the ultimate target method, so the UDT methods
 * must be invocable without using the same area.
 */
class EntryPoints
{
	/**
	 * Prevent instantiation.
	 */
	private EntryPoints()
	{
	}

	private static final MethodType s_expectedType = methodType(Object.class);

	/**
	 * Wrap a {@code MethodHandle} in an {@code Invocable} suitable for
	 * passing directly to {@link #invoke invoke()}.
	 *<p>
	 * The supplied method handle must have type {@code ()Object}, and fetch any
	 * parameter values needed by its target from bound-in references to the
	 * static reference and primitive parameter areas. If its ultimate target
	 * has {@code void} or a primitive return type, the handle must be
	 * constructed to return null, storing any primitive value returned into
	 * the first static primitive-parameter slot.
	 */
	static Invocable invocable(MethodHandle mh, AccessControlContext acc)
	{
		if ( ! s_expectedType.equals(mh.type()) )
			throw new IllegalArgumentException(
				"invocable() requires a MethodHandle with type ()Object");

		/*
		 * The EntryPoints class is specially loaded early in PL/Java's startup
		 * to have unrestricted permissions, so that PL/Java user code can be
		 * granted permissions as desired in the policy and be able to use those
		 * without fussing with doPrivileged or having to grant the same
		 * permissions redundantly to PL/Java itself.
		 *
		 * Lambdas end up looking like distinct classes under the hood, but the
		 * "class" of a lambda is given the same protection domain as its host
		 * class, so the lambda created here shares the EntryPoints class's own
		 * specialness, making the scheme Just Work.
		 */
		PrivilegedAction<Object> a = () ->
		{
			try
			{
				return mh.invokeExact();
			}
			catch ( Throwable t )
			{
				throw unchecked(t);
			}
		};

		return new Invocable(a, acc);
	}

	/**
	 * Entry point for a general PL/Java function.
	 * @param target PrivilegedAction obtained from Function.create that will
	 * push the actual parameters and call the target method.
	 * @return The value returned by the target method, or null if the method
	 * has void type or returns a primitive (which will have been returned in
	 * the first static primitive parameter slot).
	 */
	private static Object invoke(Invocable target)
	throws Throwable
	{
		try
		{
			return doPrivileged(target.action, target.acc);
		}
		catch ( UncheckedException e )
		{
			throw e.unwrap();
		}
	}

	/**
	 * Entry point for calling the {@code writeSQL} method of a UDT.
	 * @param o the UDT instance
	 * @param stream the SQLOutput stream on which the type's internal
	 * representation will be written
	 */
	private static void udtWriteInvoke(SQLData o, SQLOutput stream)
	throws Throwable
	{
		o.writeSQL(stream);
	}

	/**
	 * Entry point for calling the {@code toString} method of a UDT.
	 * @param o the UDT instance
	 * @return the UDT's text representation
	 */
	private static String udtToStringInvoke(SQLData o)
	{
		return o.toString();
	}

	/**
	 * Entry point for calling the {@code readSQL} method of a UDT, after
	 * constructing an instance first.
	 * @param mh a MethodHandle that composes the allocation of a new instance
	 * by its no-arg constructor with the call of readSQL.
	 * @param stream the SQLInput stream from which to read the UDT's internal
	 * representation
	 * @param typeName the SQL type name to be associated with the instance
	 * @return the allocated and initialized instance
	 */
	private static SQLData udtReadInvoke(
		MethodHandle mh, SQLInput stream, String typeName)
	throws Throwable
	{
		return (SQLData)mh.invokeExact(stream, typeName);
	}

	/**
	 * Entry point for calling the {@code parse} method of a UDT, which will
	 * construct an instance given its text representation.
	 * @param mh a MethodHandle to the class's static parse method, which will
	 * allocate and return an instance.
	 * @param textRep the text representation
	 * @param typeName the SQL type name to be associated with the instance
	 * @return the allocated and initialized instance
	 */
	private static SQLData udtParseInvoke(
		MethodHandle mh, String textRep, String typeName)
	throws Throwable
	{
		return (SQLData)mh.invokeExact(textRep, typeName);
	}

	static final class Invocable
	{
		final PrivilegedAction<Object> action;
		final AccessControlContext acc;

		Invocable(PrivilegedAction<Object> action, AccessControlContext acc)
		{
			this.action = requireNonNull(action);
			this.acc = acc;
		}
	}
}
