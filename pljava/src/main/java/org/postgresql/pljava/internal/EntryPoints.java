/*
 * Copyright (c) 2020-2025 Tada AB and other contributors, as listed below.
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
import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.sql.SQLSyntaxErrorException;
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
 * The primary entry point is {@code invoke}. The supplied {@code Invocable},
 * created by {@code invocable} below for its caller {@code Function.create},
 * may contain a {@code MethodHandle}, or a {@code PrivilegedAction} that
 * wraps one. The {@code MethodHandle} may have bound references to static
 * parameter areas, and will fetch the actual parameters from there to the
 * stack before invoking the (potentially reentrant) target method. Primitive
 * return values are then stored (after the potentially reentrant method has
 * returned) in the first slot of the static parameter area, to allow a single
 * {@code Object}-returning {@code invoke} method to cover those cases, rather
 * than versions for references, every primitive return type, and {@code void}.
 * The {@code PrivilegedAction} is expected to return null for a {@code void}
 * or primitive-typed target.
 *<p>
 * The remaining {@code fooInvoke} methods here are for user-defined type (UDT)
 * support. For now, those are not consolidated into the general {@code invoke}
 * pattern, as UDT support methods may need to be called from the C code
 * while it is populating the static parameter area for an ultimate target
 * method, so they must be invocable without using the same area.
 *<p>
 * An {@code Invocable} carries the {@code AccessControlContext} under which the
 * invocation target will execute.
 */
class EntryPoints
{
	/**
	 * Prevent instantiation.
	 */
	private EntryPoints()
	{
	}

	private static final MethodType s_generalType =
		methodType(Object.class, AccessControlContext.class);
	private static final MethodType s_udtCtor = methodType(SQLData.class);
	private static final MethodType s_udtParse =
		methodType(SQLData.class, String.class, String.class);

	/**
	 * Wrap a {@code MethodHandle} in an {@code Invocable} suitable for
	 * passing directly to {@link #invoke invoke()}.
	 *<p>
	 * The supplied method handle must have type
	 * {@code (AccessControlContext)Object}, and fetch any
	 * parameter values needed by its target from bound-in references to the
	 * static reference and primitive parameter areas. If its ultimate target
	 * has {@code void} or a primitive return type, the handle must be
	 * constructed to return null, storing any primitive value returned into
	 * the first static primitive-parameter slot.
	 *<p>
	 * The {@code AccessControlContext} passed to the handle will be the same
	 * one under which it will be invoked, and so can be ignored in most cases.
	 * A handle for a value-per-call set-returning function can copy it into the
	 * {@code Invocable} that it creates from this function's result, to be used
	 * for iteratively retrieving the results.
	 */
	static Invocable<?> invocable(MethodHandle mh, AccessControlContext acc)
	{
		if ( null == mh
			|| s_udtCtor.equals(mh.type()) ||  s_udtParse.equals(mh.type()) )
			return new Invocable<MethodHandle>(mh, acc);

		if ( ! s_generalType.equals(mh.type()) )
			throw new IllegalArgumentException(
				"invocable() passed a MethodHandle with unexpected type");

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
				return mh.invokeExact(acc);
			}
			catch ( Error | RuntimeException e )
			{
				throw e;
			}
			catch ( Throwable t )
			{
				throw unchecked(t);
			}
		};

		return new Invocable<PrivilegedAction<Object>>(a, acc);
	}

	/**
	 * Entry point for a general PL/Java function.
	 * @param target Invocable obtained from Function.create that will
	 * push the actual parameters and call the target method.
	 * @return The value returned by the target method, or null if the method
	 * has void type or returns a primitive (which will have been returned in
	 * the first static primitive parameter slot).
	 */
	private static Object invoke(Invocable<PrivilegedAction<Object>> target)
	throws Throwable
	{
		assert PrivilegedAction.class.isInstance(target.payload);

		return doPrivilegedAndUnwrap(target.payload, target.acc);
	}

	/**
	 * Entry point for calling the {@code writeSQL} method of a UDT.
	 *<p>
	 * Like {@code udtReadInvoke}, this is a distinct entry point in order to
	 * avoid use of the static parameter area. While this operation is not
	 * expected during preparation of a function's parameter list, it can occur
	 * during a function's execution, if it stores values of user-defined type
	 * into result sets, prepared-statement bindings, etc. Such uses are not
	 * individually surrounded by {@code pushInvocation}/{@code popInvocation}
	 * as ordinary function calls are, and the {@code ParameterFrame} save and
	 * restore mechanism relies on those, so it is better for this entry point
	 * also to be handled specially.
	 * @param target Invocable carrying the appropriate AccessControlContext
	 * (<var>target</var>'s action is unused and expected to be null)
	 * @param o the UDT instance
	 * @param stream the SQLOutput stream on which the type's internal
	 * representation will be written
	 */
	private static void udtWriteInvoke(
		Invocable<Void> target, SQLData o, SQLOutput stream)
	throws Throwable
	{
		PrivilegedAction<Void> action = () ->
		{
			try
			{
				o.writeSQL(stream);
				return null;
			}
			catch ( SQLException e )
			{
				throw unchecked(e);
			}
		};

		doPrivilegedAndUnwrap(action, target.acc);
	}

	/**
	 * Entry point for calling the {@code toString} method of a UDT.
	 *<p>
	 * This can be called during transformation of a UDT that has a
	 * NUL-terminated storage form, and without being separately wrapped in
	 * {@code pushInvocation}/{@code popInvocation}, so it gets its own entry
	 * point here to avoid use of the static parameter area.
	 * @param target Invocable carrying the appropriate AccessControlContext
	 * (target's action is unused and expected to be null)
	 * @param o the UDT instance
	 * @return the UDT's text representation
	 */
	private static String udtToStringInvoke(Invocable<Void> target, SQLData o)
	{
		PrivilegedAction<String> action = () ->
		{
			return o.toString();
		};

		return doPrivileged(action, target.acc);
	}

	/**
	 * Entry point for calling the {@code readSQL} method of a UDT, after
	 * constructing an instance first.
	 *<p>
	 * This gets its own entry point so parameters can be passed to it
	 * independently of the static parameter area used for ordinary function
	 * invocations. Should an ordinary function have a parameter that is of a
	 * user-defined type, this entry point is used to instantiate the Java
	 * form of that parameter <em>during</em> the assembly of the function's
	 * parameter list, so the static area is not touched here.
	 * @param target an Invocable that returns a new instance, on which readSQL
	 * will then be called. The Invocable's access control context will be in
	 * effect for both operations.
	 * @param stream the SQLInput stream from which to read the UDT's internal
	 * representation
	 * @param typeName the SQL type name to be associated with the instance
	 * @return the allocated and initialized instance
	 */
	private static SQLData udtReadInvoke(
		Invocable<MethodHandle> target, SQLInput stream, String typeName)
	throws Throwable
	{
		PrivilegedAction<SQLData> action = () ->
		{
			try
			{
				SQLData o = (SQLData)target.payload.invokeExact();
				o.readSQL(stream, typeName);
				return o;
			}
			catch ( Error | RuntimeException e )
			{
				throw e;
			}
			catch ( Throwable t )
			{
				throw unchecked(t);
			}
		};

		return doPrivilegedAndUnwrap(action, target.acc);
	}

	/**
	 * Entry point for calling the {@code parse} method of a UDT, which will
	 * construct an instance given its text representation.
	 *<p>
	 * This can be called during transformation of a UDT that has a
	 * NUL-terminated storage form, and without being separately wrapped in
	 * {@code pushInvocation}/{@code popInvocation}, so it gets its own entry
	 * point here to avoid use of the static parameter area.
	 * @param target a MethodHandle to the class's static parse method, which
	 * will allocate and return an instance.
	 * @param textRep the text representation
	 * @param typeName the SQL type name to be associated with the instance
	 * @return the allocated and initialized instance
	 */
	private static SQLData udtParseInvoke(
		Invocable<MethodHandle> target, String textRep, String typeName)
	throws Throwable
	{
		PrivilegedAction<SQLData> action = () ->
		{
			try
			{
				return (SQLData)target.payload.invokeExact(textRep, typeName);
			}
			catch ( Error | RuntimeException e )
			{
				throw e;
			}
			catch ( Throwable t )
			{
				throw unchecked(t);
			}
		};

		return doPrivilegedAndUnwrap(action, target.acc);
	}

	/**
	 * Factors out the common {@code doPrivileged} and unwrapping of possible
	 * wrapped checked exceptions for the above entry points.
	 */
	private static <T> T doPrivilegedAndUnwrap(
		PrivilegedAction<T> action, AccessControlContext context)
	throws SQLException
	{
		Throwable t;
		try
		{
			return doPrivileged(action, context);
		}
		catch ( ExceptionInInitializerError e )
		{
			t = e.getCause();
		}
		catch ( Error e )
		{
			throw e;
		}
		catch ( UncheckedException e )
		{
			t = e.unwrap();
		}
		catch ( Throwable e )
		{
			t = e;
		}

		if ( t instanceof SQLException )
			throw (SQLException)t;

		if ( t instanceof SecurityException )
			/*
			 * Yes, SQL and JDBC lump syntax errors and access violations
			 * together, and this is the right exception class for 42xxx.
			 */
			throw new SQLSyntaxErrorException(t.getMessage(), "42501", t);

		throw new SQLException(t.getMessage(), t);
	}

	/**
	 * Called from {@code Function} to perform the initialization of a class,
	 * under a selected access control context.
	 */
	static Class<?> loadAndInitWithACC(
		String className, ClassLoader schemaLoader, AccessControlContext acc)
	throws SQLException
	{
		PrivilegedAction<Class<?>> action = () ->
		{
			try
			{
				return Class.forName(className, true, schemaLoader);
			}
			catch ( ExceptionInInitializerError e )
			{
				throw e;
			}
			catch ( LinkageError | ClassNotFoundException e )
			{
				/*
				 * It would be odd to get a ClassNotFoundException here, as
				 * the caller had to look it up once already to decide what acc
				 * to use. But try telling that to javac.
				 */
				throw unchecked(new SQLNonTransientException(
					"Initializing class " + className + ": " + e, "46103", e));
			}
		};

		return doPrivilegedAndUnwrap(action, acc);
	}

	/**
	 * A class carrying a payload of some kind and an access control context
	 * to impose when it is invoked.
	 *<p>
	 * The type of the payload will be specific to which entry point above
	 * will be used to invoke it.
	 */
	static final class Invocable<T>
	{
		final T payload;
		final AccessControlContext acc;

		Invocable(T payload, AccessControlContext acc)
		{
			this.payload = payload;
			this.acc = acc;
		}
	}
}
