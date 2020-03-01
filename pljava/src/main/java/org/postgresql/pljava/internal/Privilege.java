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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * Clean interface to the {@code doPrivileged...} methods on
 * {@link AccessController AccessController}.
 *<p>
 * <strong>This interface must remain non-exported
 * from {@code org.postgresql.pljava.internal}.</strong>
 *<p>
 * The reason, of course, is that the real methods on {@code AccessController}
 * end up getting called from these wrappers, and will therefore apply the
 * permissions granted to this module. As long as these methods are only
 * accessible within this module, that isn't a problem.
 *<p>
 * It would be great to develop this into an exportable API so user code
 * could benefit, but that would be a much trickier undertaking, with editing of
 * {@code AccessControlContext}s to snag the correct caller's context, and
 * not for the faint of heart.
 *<p>
 * Each method here comes in a flavor accepting a
 * {@link Checked.Supplier Checked.Supplier}, matching any lambda that returns a
 * reference type, and a flavor accepting a
 * {@link Checked.Runnable Checked.Runnable} for {@code void} lambdas, because
 * the compiler will not match those up with {@code Supplier<Void>}.
 *<p>
 * Fuss no more with {@code PrivilegedExceptionAction} and catching
 * {@code PrivilegedActionException}: just pass any of these methods a lambda.
 * If the lambda throws a checked exception, so does the method. If the lambda
 * throws some checked exceptions, the method throws their least common
 * supertype, which is not as nice as throwing their union, and climbs all the
 * way up to {@code Exception} if they are unrelated. But even so, you can now
 * simply catch it, rather than catching a {@code PrivilegedActionException} and
 * having to unwrap it first.
 */
public interface Privilege
{
	public static <T, E extends Exception> T doPrivileged(
		Checked.Supplier<T,E> op)
	throws E
	{
		try
		{
			return (T)AccessController.doPrivileged(
				(PrivilegedExceptionAction<T>)op::get);
		}
		catch ( PrivilegedActionException pae )
		{
			throw Checked.<E>ederThrow(pae.getException());
		}
	}

	public static <T, E extends Exception> T doPrivileged(
		Checked.Supplier<T,E> op, AccessControlContext acc)
	throws E
	{
		try
		{
			return (T)AccessController.doPrivileged(
				(PrivilegedExceptionAction<T>)op::get, acc);
		}
		catch ( PrivilegedActionException pae )
		{
			throw Checked.<E>ederThrow(pae.getException());
		}
	}

	public static <T, E extends Exception> T doPrivileged(
		Checked.Supplier<T,E> op, AccessControlContext acc, Permission... perms)
	throws E
	{
		try
		{
			return (T)AccessController.doPrivileged(
				(PrivilegedExceptionAction<T>)op::get, acc, perms);
		}
		catch ( PrivilegedActionException pae )
		{
			throw Checked.<E>ederThrow(pae.getException());
		}
	}

	public static <T, E extends Exception>T doPrivilegedWithCombiner(
		Checked.Supplier<T,E> op)
	throws E
	{
		try
		{
			return (T)AccessController.doPrivilegedWithCombiner(
				(PrivilegedExceptionAction<T>)op::get);
		}
		catch ( PrivilegedActionException pae )
		{
			throw Checked.<E>ederThrow(pae.getException());
		}
	}

	public static <T, E extends Exception> T doPrivilegedWithCombiner(
		Checked.Supplier<T,E> op, AccessControlContext acc, Permission... perms)
	throws E
	{
		try
		{
			return (T)AccessController.doPrivilegedWithCombiner(
				(PrivilegedExceptionAction<T>)op::get, acc, perms);
		}
		catch ( PrivilegedActionException pae )
		{
			throw Checked.<E>ederThrow(pae.getException());
		}
	}

	public static <E extends Exception> void doPrivileged(
		Checked.Runnable<E> op)
	throws E
	{
		try
		{
			AccessController.doPrivileged((PrivilegedExceptionAction<Void>)() ->
			{
				op.run();
				return null;
			});
		}
		catch ( PrivilegedActionException pae )
		{
			throw Checked.<E>ederThrow(pae.getException());
		}
	}

	public static <E extends Exception> void doPrivileged(
		Checked.Runnable<E> op, AccessControlContext acc)
	throws E
	{
		try
		{
			AccessController.doPrivileged((PrivilegedExceptionAction<Void>)() ->
			{
				op.run();
				return null;
			}, acc);
		}
		catch ( PrivilegedActionException pae )
		{
			throw Checked.<E>ederThrow(pae.getException());
		}
	}

	public static <E extends Exception> void doPrivileged(
		Checked.Runnable<E> op, AccessControlContext acc, Permission... perms)
	throws E
	{
		try
		{
			AccessController.doPrivileged((PrivilegedExceptionAction<Void>)() ->
			{
				op.run();
				return null;
			}, acc, perms);
		}
		catch ( PrivilegedActionException pae )
		{
			throw Checked.<E>ederThrow(pae.getException());
		}
	}

	public static <E extends Exception> void doPrivilegedWithCombiner(
		Checked.Runnable<E> op)
	throws E
	{
		try
		{
			AccessController
			.doPrivilegedWithCombiner((PrivilegedExceptionAction<Void>)() ->
			{
				op.run();
				return null;
			});
		}
		catch ( PrivilegedActionException pae )
		{
			throw Checked.<E>ederThrow(pae.getException());
		}
	}

	public static <E extends Exception> void doPrivilegedWithCombiner(
		Checked.Runnable<E> op, AccessControlContext acc, Permission... perms)
	throws E
	{
		try
		{
			AccessController
			.doPrivilegedWithCombiner((PrivilegedExceptionAction<Void>)() ->
			{
				op.run();
				return null;
			}, acc, perms);
		}
		catch ( PrivilegedActionException pae )
		{
			throw Checked.<E>ederThrow(pae.getException());
		}
	}
}
