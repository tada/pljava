/*
 * Copyright (c) 2018 Tada AB and other contributors, as listed below.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import java.sql.SQLException;

import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Base class for object state with corresponding Java and native components.
 *<p>
 * A {@code DualState} object connects some state that exists in the JVM
 * as well as some native/PostgreSQL resources. It will 'belong' to some Java
 * object that holds a strong reference to it, and this state object is,
 * in turn, a {@link WeakReference} to that object. Java state may be held in
 * that object (if it needs only to be freed by the garbage collector when
 * unreachable), or in this object if it needs some more specific cleanup.
 * Native state will be referred to by this object.
 *<p>
 * These interesting events are possible in the life cycle of a
 * {@code DualState} object:
 * <ul>
 * <li>It is explicitly closed by the Java code using it. It, and any associated
 * native state, should be released.</li>
 * <li>It is found unreachable by the Java garbage collector. Again, any
 * associated state should be released.</li>
 * <li>Its associated native state is released or invalidated (such as by exit
 * of a corresponding context). If the object is still reachable from Java, it
 * must be marked to throw an exception for any future attempted access to its
 * native state.</li>
 * </ul>
 *<p>
 * The introduction of this class represents <em>yet another</em> pattern
 * within PL/Java for objects that combine Java and native state. It is meant
 * to be general (and documented) enough to support future gradual migration of
 * other existing patterns to it.
 *<p>
 * A parameter to the {@code DualState} constructor is a {@code ResourceOwner},
 * a PostgreSQL implementation concept introduced in PG 8.0. Instances will be
 * called at their {@link #nativeStateReleased nativeStateReleased} methods
 * when the corresponding {@code ResourceOwner} is released in PostgreSQL.
 *<p>
 * Instances will be enqueued on a {@link ReferenceQueue} when found by the Java
 * garbage collector to be unreachable. The
 * {@link #cleanEnqueuedInstances cleanEnqueuedInstances} static method will
 * call those instances at their
 * {@link #javaStateUnreachable javaStateUnreachable} methods if the weak
 * reference has already been cleared by the garbage collector. The method
 * should clean up any lingering native state.
 *<p>
 * As the native cleanup is likely to involve calls into PostgreSQL, to reduce
 * thread contention, {@code cleanEnqueuedInstances} should be called in one or
 * more likely places from a thread already known to be entering/exiting Java
 * from/to PostgreSQL.
 *<p>
 * If convenient, explicit close actions from Java can be handled similarly,
 * by having the close method call {@link #enqueue enqueue}, and providing a
 * {@link #javaStateReleased javaStateReleased} method, which will be called by
 * {@code cleanEnqueuedInstances} if the weak reference is nonnull, indicating
 * the instance was enqueued explicitly rather than by the garbage collector.
 */
public abstract class DualState<T> extends WeakReference<T>
{
	/**
	 * {@code DualState} objects Java no longer needs.
	 *<p>
	 * They will turn up on this queue (with referent already set null) if
	 * the garbage collector has determined them to be unreachable. They can
	 * also arrive here (with referent <em>not</em> yet set null) if some Java
	 * method (such as a {@code close} or {@code free} has called
	 * {@code enqueue}; whether the referent is null allows the cases to be
	 * distinguished.
	 *<p>
	 * The queue is only processed by a private method called from native code
	 * in selected places where it makes sense to do so.
	 */
	private static ReferenceQueue<Object> s_releasedInstances =
		new ReferenceQueue<Object>();

	/**
	 * All instances are added to this collection upon creation.
	 */
	private static Deque<DualState> s_liveInstances =
		new LinkedBlockingDeque<DualState>();

	/**
	 * Pointer value of the {@code ResourceOwner} this instance belongs to,
	 * if any.
	 */
	private final long m_resourceOwner;

	/**
	 * Construct a {@code DualState} instance with a reference to the Java
	 * object whose state it represents.
	 *<p>
	 * Subclass constructors must accept a <em>cookie</em> parameter from the
	 * native caller, and pass it along to superclass constructors. That allows
	 * some confidence that constructor parameters representing native values
	 * are for real, and also that the construction is taking place on a thread
	 * holding the native lock, keeping the concurrency story simple.
	 * @param cookie Capability held by native code to invoke {@code DualState}
	 * constructors.
	 * @param referent The Java object whose state this instance represents.
	 * @param resourceOwner Pointer value of the native {@code ResourceOwner}
	 * whose release callback will indicate that this object's native state is
	 * no longer valid.
	 */
	protected DualState(Key cookie, T referent, long resourceOwner)
	{
		super(referent, s_releasedInstances);

		if ( ! (cookie instanceof Key) )
			throw new UnsupportedOperationException(
				"Constructing DualState instance without cookie");

		m_resourceOwner = resourceOwner;
		s_liveInstances.add(this);
	}

	/**
	 * Return {@code true} if the native state is still valid. An abstract
	 * method so it can be tailored to whatever native state is maintained
	 * by an implementing class.
	 */
	protected abstract boolean nativeStateIsValid();

	/**
	 * Method that will be called when the associated {@code ResourceOwner}
	 * is released, indicating that the native portion of the state
	 * is no longer valid. The implementing class should clean up
	 * whatever is appropriate to that event, and must ensure that
	 * {@code nativeStateIsValid} will thereafter return {@code false}.
	 *<p>
	 * This object's monitor will always be held when this method is called
	 * during resource owner release. The class whose state this is must
	 * synchronize and hold this object's monitor for the duration of any
	 * operation that could refer to the native state.
	 */
	protected abstract void nativeStateReleased();

	/**
	 * Method that will be called when the Java garbage collector has determined
	 * the referent object is no longer strongly reachable. This default
	 * implementation does nothing; a subclass should override it to do any
	 * cleanup, or release of native resources, that may be required.
	 *<p>
	 * It is not necessary for this method to remove the instance from the
	 * {@code liveInstances} collection; that will have been done just before
	 * this method is called.
	 */
	protected void javaStateUnreachable()
	{
	}

	/**
	 * Method that can be called to indicate that Java code has explicitly
	 * released the instance (for example, through calling a {@code close}
	 * method on the referent object). This can be handled two ways:
	 *<ul>
	 * <li>A {@code close} or similar method calls this directly. This instance
	 * must be removed from the {@code liveInstances} collection. This default
	 * implementation does so.
	 * <li>A {@code close} or similar method simply calls
	 * {@link #enqueue enqueue} instead of this method. This method will be
	 * called when the queue is processed, the next time native code calls
	 * {@link #clearEnqueuedInstances clearEnqueuedInstances}. For that case,
	 * this method should be overridden to do whatever other cleanup is in
	 * order, but <em>not</em> remove the instance from {@code liveInstances},
	 * which will have happened just before this method is called.
	 *</ul>
	 */
	protected void javaStateReleased()
	{
		s_liveInstances.remove(this);
	}

	/**
	 * Throw an {@code SQLException} with a specified message and SQLSTATE code
	 * if {@code nativeStateIsValid} returns {@code false}.
	 */
	public void assertNativeStateIsValid(String message, String sqlState)
	throws SQLException
	{
		if ( ! nativeStateIsValid() )
			throw new SQLException(message, sqlState);
	}

	/**
	 * Throw an {@code SQLException} with a specified message and SQLSTATE code
	 * of 55000 "object not in prerequisite state" if {@code nativeStateIsValid}
	 * returns {@code false}.
	 */
	public void assertNativeStateIsValid(String message)
	throws SQLException
	{
		assertNativeStateIsValid(message, "55000");
	}

	/**
	 * Throw an {@code SQLException} with a default message and SQLSTATE code
	 * of 55000 "object not in prerequisite state" if {@code nativeStateIsValid}
	 * returns {@code false}.
	 */
	public void assertNativeStateIsValid()
	throws SQLException
	{
		if ( ! nativeStateIsValid() )
		{
			Object referent = get();
			String message;
			if ( null != referent )
				message = referent.getClass().getName();
			else
				message = getClass().getName();
			message += " used beyond its PostgreSQL lifetime";
			throw new SQLException(message, "55000");
		}
	}

	/**
	 * Called only from native code by the {@code ResourceOwner} callback when a
	 * resource owner is being released. Must identify the live instances that
	 * have been registered to that owner, if any, and call their
	 * {@link #nativeStateReleased nativeStateReleased} methods.
	 * @param resourceOwner Pointer value identifying the resource owner being
	 * released. Calls can be received for resource owners to which no instances
	 * here have been registered.
	 */
	private static void resourceOwnerRelease(long resourceOwner)
	{
		for ( Iterator<DualState> i = s_liveInstances.iterator();
			  i.hasNext(); )
		{
			DualState s = i.next();
			if ( s.m_resourceOwner == resourceOwner )
			{
				i.remove();
				synchronized ( s )
				{
					s.nativeStateReleased();
				}
			}
		}
	}

	/**
	 * Called only from native code, at points where checking the
	 * freed/unreachable objects queue would be useful. Calls the
	 * {@link #javaStateUnreachable javaStateUnreachable} method for instances
	 * that were cleared and enqueued by the garbage collector; calls the
	 * {@link #javaStateReleased javaStateReleased} method for instances that
	 * have not yet been garbage collected, but were enqueued by Java code
	 * explicitly calling {@link #enqueue enqueue}.
	 */
	private static void cleanEnqueuedInstances()
	{
		DualState s;
		while ( null != (s = (DualState)s_releasedInstances.poll()) )
		{
			s_liveInstances.remove(s);
			try
			{
				if ( null == s.get() )
					s.javaStateUnreachable();
				else
					s.javaStateReleased();
			}
			catch ( Throwable t ) { } /* JDK 9 Cleaner ignores exceptions, so */
		}
	}

	/**
	 * Magic cookie needed as a constructor parameter to confirm that
	 * {@code DualState} subclass instances are being constructed from
	 * native code.
	 */
	public static final class Key
	{
		private static boolean constructed = false;
		private Key()
		{
			synchronized ( Key.class )
			{
				if ( constructed )
					throw new IllegalStateException("Duplicate DualState.Key");
				constructed = true;
			}
		}
	}
}
