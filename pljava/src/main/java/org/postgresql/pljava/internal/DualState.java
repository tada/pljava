/*
 * Copyright (c) 2018-2019 Tada AB and other contributors, as listed below.
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

import java.util.Queue;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

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
 * However, this class does not require the {@code resourceOwner} parameter to
 * be, in all cases, a pointer to a PostgreSQL {@code ResourceOwner}. It is
 * treated simply as an opaque {@code long} value, to be compared to a value
 * passed at release time (as if in a {@code ResourceOwner} callback). Other
 * values (such as pointers to other allocated structures, which of course
 * cannot match any PG {@code ResourceOwner} existing at the same time) can also
 * be used. In PostgreSQL 9.5 and later, a {@code MemoryContext} could be used,
 * with its address passed to a {@code MemoryContextCallback} for release. For
 * state that is scoped to a single invocation of a PL/Java function, the
 * address of the {@code Invocation} can be used. Such references can be
 * considered "generalized" resource owners.
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
	private static Queue<DualState> s_liveInstances =
		new ConcurrentLinkedQueue<DualState>();

	/**
	 * Pointer value of the {@code ResourceOwner} this instance belongs to,
	 * if any.
	 */
	protected final long m_resourceOwner;

	/**
	 * Check that a cookie is valid, throwing an unchecked exception otherwise.
	 */
	protected static void checkCookie(Key cookie)
	{
		if ( ! Key.class.isInstance(cookie) )
			throw new UnsupportedOperationException(
				"Operation on DualState instance without cookie");
	}

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

		checkCookie(cookie);

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
	 * {@link #cleanEnqueuedInstances cleanEnqueuedInstances}. For that case,
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
	 * Produce a string describing this state object in a way useful for
	 * debugging, with such information as the associated {@code ResourceOwner}
	 * and whether the state is fresh or stale.
	 *<p>
	 * This method calls {@link #toString(Object)} passing {@code this}.
	 * Subclasses are encouraged to override that method with versions that add
	 * subclass-specific details.
	 * @return Description of this state object.
	 */
	@Override
	public String toString()
	{
		return toString(this);
	}

	/**
	 * Produce a string with such details of this object as might be useful for
	 * debugging, starting with an abbreviated form of the class name of the
	 * supplied object.
	 *<p>
	 * Subclasses are encouraged to override this and then call it, via super,
	 * passing the object unchanged, and then append additional
	 * subclass-specific details to the result.
	 *<p>
	 * Because the recursion ends here, this one actually does construct the
	 * abbreviated form of the class name of the object, and use it at the start
	 * of the returned string.
	 * @param o An object whose class name (abbreviated by stripping the package
	 * prefix) will be used at the start of the string. Passing {@code null} is
	 * the same as passing {@code this}.
	 * @return Description of this state object, prefixed with the abbreviated
	 * class name of the passed object.
	 */
	public String toString(Object o)
	{
		Class<?> c = (null == o ? this : o).getClass();
		String cn = c.getCanonicalName();
		int pnl = c.getPackage().getName().length();
		return String.format("%s owner:%x %s",
			cn.substring(1 + pnl), m_resourceOwner,
			nativeStateIsValid() ? "fresh" : "stale");
	}

	/**
	 * Called only from native code by the {@code ResourceOwner} callback when a
	 * resource owner is being released. Must identify the live instances that
	 * have been registered to that owner, if any, and call their
	 * {@link #nativeStateReleased nativeStateReleased} methods.
	 * @param resourceOwner Pointer value identifying the resource owner being
	 * released. Calls can be received for resource owners to which no instances
	 * here have been registered.
	 *<p>
	 * Some state subclasses may have their nativeStateReleased methods called
	 * from Java code, when it is clear the native state is no longer needed in
	 * Java. That doesn't remove the state instance from s_liveInstances though,
	 * so it will still eventually be seen by this loop and efficiently removed
	 * by the iterator. Hence the nativeStateIsValid test, to avoid invoking
	 * nativeStateReleased more than once.
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
					if ( s.nativeStateIsValid() )
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

	/**
	 * A {@code DualState} subclass serving only to guard access to a single
	 * nonzero {@code long} value (typically a native pointer).
	 *<p>
	 * Nothing in particular is done to the native resource at the time of
	 * {@code javaStateReleased} or {@code javaStateUnreachable}; if it is
	 * subject to reclamation, this class assumes it will be shortly, in the
	 * normal operation of the native code. This can be appropriate for native
	 * state that was set up by a native caller for a short lifetime, such as a
	 * single function invocation.
	 */
	public static abstract class SingleGuardedLong<T> extends DualState<T>
	{
		private volatile long m_value;

		protected SingleGuardedLong(
			Key cookie, T referent, long resourceOwner, long guardedLong)
		{
			super(cookie, referent, resourceOwner);
			m_value = guardedLong;
		}

		@Override
		public String toString(Object o)
		{
			return String.format(
				"%s GuardedLong(%x)", super.toString(o), m_value);
		}

		/**
		 * For this class, the native state is valid whenever the wrapped
		 * value is not null.
		 */
		@Override
		protected boolean nativeStateIsValid()
		{
			return 0 != m_value;
		}

		/**
		 * When the native state is released, the wrapped value is zeroed
		 * to indicate the state is no longer valid; no other action is taken,
		 * on the assumption that the resource owner's release will be
		 * followed by wholesale reclamation of the guarded state anyway.
		 */
		@Override
		protected void nativeStateReleased()
		{
			m_value = 0;
		}

		/**
		 * When the Java state is released, the wrapped pointer is zeroed to
		 * indicate the state is no longer valid; no other action is taken.
		 *<p>
		 * This overrides the inherited default, which would have removed this
		 * instance from the live instances collection. Users of this class
		 * should not call this method directly, but simply call
		 * {@link #enqueue enqueue}, and let the reclamation happen when the
		 * queue is processed.
		 */
		@Override
		protected void javaStateReleased()
		{
			m_value = 0;
		}

		/**
		 * Allows a subclass to obtain the wrapped value.
		 */
		protected long getValue() throws SQLException
		{
			assertNativeStateIsValid();
			return m_value;
		}
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code pfree} of a single pointer.
	 */
	public static abstract class SinglePfree<T> extends DualState<T>
	{
		private volatile long m_pointer;

		protected SinglePfree(
			Key cookie, T referent, long resourceOwner, long pfreeTarget)
		{
			super(cookie, referent, resourceOwner);
			m_pointer = pfreeTarget;
		}

		@Override
		public String toString(Object o)
		{
			return String.format("%s pfree(%x)", super.toString(o), m_pointer);
		}

		/**
		 * For this class, the native state is valid whenever the wrapped
		 * pointer is not null.
		 */
		@Override
		protected boolean nativeStateIsValid()
		{
			return 0 != m_pointer;
		}

		/**
		 * When the native state is released, the wrapped pointer is nulled
		 * to indicate the state is no longer valid; no {@code pfree} call is
		 * made, on the assumption that the resource owner's release will be
		 * followed by wholesale release of the containing memory context
		 * anyway.
		 */
		@Override
		protected void nativeStateReleased()
		{
			m_pointer = 0;
		}

		/**
		 * When the Java state is released, the wrapped pointer is nulled to
		 * indicate the state is no longer valid, <em>and</em> a {@code pfree}
		 * call is made so the native memory is released without having to wait
		 * for release of its containing context.
		 *<p>
		 * This overrides the inherited default, which would have removed this
		 * instance from the live instances collection. Users of this class
		 * should not call this method directly, but simply call
		 * {@link #enqueue enqueue}, and let the reclamation happen when the
		 * queue is processed.
		 */
		@Override
		protected void javaStateReleased()
		{
			synchronized(Backend.THREADLOCK)
			{
				long p = m_pointer;
				m_pointer = 0;
				if ( 0 != p )
					_pfree(p);
			}
		}

		/**
		 * This override simply calls
		 * {@link #javaStateReleased javaStateReleased}, so there is no
		 * difference in the effect of the Java object being explicitly
		 * released, or found unreachable by the garbage collector.
		 */
		@Override
		protected void javaStateUnreachable()
		{
			javaStateReleased();
		}

		/**
		 * Allows a subclass to obtain the wrapped pointer value.
		 */
		protected long getPointer() throws SQLException
		{
			assertNativeStateIsValid();
			return m_pointer;
		}

		private native void _pfree(long pointer);
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code MemoryContextDelete} of a single context.
	 *<p>
	 * This class may get called at the {@code nativeStateReleased} entry, not
	 * only if the native state is actually being released, but if it is being
	 * 'claimed' by native code for its own purposes. The effect is the same
	 * as far as Java is concerned; the object is no longer accessible, and the
	 * native code is responsible for whatever happens to it next.
	 */
	public static abstract class SingleMemContextDelete<T> extends DualState<T>
	{
		private volatile long m_context;

		protected SingleMemContextDelete(
			Key cookie, T referent, long resourceOwner, long memoryContext)
		{
			super(cookie, referent, resourceOwner);
			m_context = memoryContext;
		}

		@Override
		public String toString(Object o)
		{
			return String.format("%s MemoryContextDelete(%x)",
				super.toString(o), m_context);
		}

		/**
		 * For this class, the native state is valid whenever the wrapped
		 * context pointer is not null.
		 */
		@Override
		protected boolean nativeStateIsValid()
		{
			return 0 != m_context;
		}

		/**
		 * When the native state is released, the wrapped pointer is nulled
		 * to indicate the state is no longer valid. No
		 * {@code MemoryContextDelete} call is
		 * made; this is important, as the native code may have other plans for
		 * the memory context, such as to relink it under a different parent
		 * context, etc.
		 */
		@Override
		protected void nativeStateReleased()
		{
			m_context = 0;
		}

		/**
		 * When the Java state is released, the wrapped pointer is nulled to
		 * indicate the state is no longer valid, <em>and</em> a
		 * {@code MemoryContextDelete}
		 * call is made so the native memory is released without having to wait
		 * for release of its parent context.
		 *<p>
		 * This overrides the inherited default, which would have removed this
		 * instance from the live instances collection. Users of this class
		 * should not call this method directly, but simply call
		 * {@link #enqueue enqueue}, and let the reclamation happen when the
		 * queue is processed.
		 */
		@Override
		protected void javaStateReleased()
		{
			synchronized(Backend.THREADLOCK)
			{
				long p = m_context;
				m_context = 0;
				if ( 0 != p )
					_memContextDelete(p);
			}
		}

		/**
		 * This override simply calls
		 * {@link #javaStateReleased javaStateReleased}, so there is no
		 * difference in the effect of the Java object being explicitly
		 * released, or found unreachable by the garbage collector.
		 */
		@Override
		protected void javaStateUnreachable()
		{
			javaStateReleased();
		}

		/**
		 * Allows a subclass to obtain the wrapped pointer value.
		 */
		protected long getMemoryContext() throws SQLException
		{
			assertNativeStateIsValid();
			return m_context;
		}

		private native void _memContextDelete(long pointer);
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code FreeTupleDesc} of a single pointer.
	 */
	public static abstract class SingleFreeTupleDesc<T> extends DualState<T>
	{
		private volatile long m_pointer;

		protected SingleFreeTupleDesc(
			Key cookie, T referent, long resourceOwner, long ftdTarget)
		{
			super(cookie, referent, resourceOwner);
			m_pointer = ftdTarget;
		}

		@Override
		public String toString(Object o)
		{
			return String.format("%s FreeTupleDesc(%x)", super.toString(o),
								 m_pointer);
		}

		/**
		 * For this class, the native state is valid whenever the wrapped
		 * pointer is not null.
		 */
		@Override
		protected boolean nativeStateIsValid()
		{
			return 0 != m_pointer;
		}

		/**
		 * When the native state is released, the wrapped pointer is nulled
		 * to indicate the state is no longer valid; no
		 * {@code FreeTupleDesc} call is
		 * made, on the assumption that the resource owner's release will be
		 * followed by wholesale release of the containing memory context
		 * anyway.
		 */
		@Override
		protected void nativeStateReleased()
		{
			m_pointer = 0;
		}

		/**
		 * When the Java state is released, the wrapped pointer is nulled to
		 * indicate the state is no longer valid, <em>and</em> a
		 * {@code FreeTupleDesc}
		 * call is made so the native memory is released without having to wait
		 * for release of its containing context.
		 *<p>
		 * This overrides the inherited default, which would have removed this
		 * instance from the live instances collection. Users of this class
		 * should not call this method directly, but simply call
		 * {@link #enqueue enqueue}, and let the reclamation happen when the
		 * queue is processed.
		 */
		@Override
		protected void javaStateReleased()
		{
			synchronized(Backend.THREADLOCK)
			{
				long p = m_pointer;
				m_pointer = 0;
				if ( 0 != p )
					_freeTupleDesc(p);
			}
		}

		/**
		 * This override simply calls
		 * {@link #javaStateReleased javaStateReleased}, so there is no
		 * difference in the effect of the Java object being explicitly
		 * released, or found unreachable by the garbage collector.
		 */
		@Override
		protected void javaStateUnreachable()
		{
			javaStateReleased();
		}

		/**
		 * Allows a subclass to obtain the wrapped pointer value.
		 */
		protected long getPointer() throws SQLException
		{
			assertNativeStateIsValid();
			return m_pointer;
		}

		private native void _freeTupleDesc(long pointer);
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code heap_freetuple} of a single pointer.
	 */
	public static abstract class SingleHeapFreeTuple<T> extends DualState<T>
	{
		private volatile long m_pointer;

		protected SingleHeapFreeTuple(
			Key cookie, T referent, long resourceOwner, long hftTarget)
		{
			super(cookie, referent, resourceOwner);
			m_pointer = hftTarget;
		}

		@Override
		public String toString(Object o)
		{
			return String.format("%s heap_freetuple(%x)", super.toString(o),
								 m_pointer);
		}

		/**
		 * For this class, the native state is valid whenever the wrapped
		 * pointer is not null.
		 */
		@Override
		protected boolean nativeStateIsValid()
		{
			return 0 != m_pointer;
		}

		/**
		 * When the native state is released, the wrapped pointer is nulled
		 * to indicate the state is no longer valid; no
		 * {@code heap_freetuple} call is
		 * made, on the assumption that the resource owner's release will be
		 * followed by wholesale release of the containing memory context
		 * anyway.
		 */
		@Override
		protected void nativeStateReleased()
		{
			m_pointer = 0;
		}

		/**
		 * When the Java state is released, the wrapped pointer is nulled to
		 * indicate the state is no longer valid, <em>and</em> a
		 * {@code heap_freetuple}
		 * call is made so the native memory is released without having to wait
		 * for release of its containing context.
		 *<p>
		 * This overrides the inherited default, which would have removed this
		 * instance from the live instances collection. Users of this class
		 * should not call this method directly, but simply call
		 * {@link #enqueue enqueue}, and let the reclamation happen when the
		 * queue is processed.
		 */
		@Override
		protected void javaStateReleased()
		{
			synchronized(Backend.THREADLOCK)
			{
				long p = m_pointer;
				m_pointer = 0;
				if ( 0 != p )
					_heapFreeTuple(p);
			}
		}

		/**
		 * This override simply calls
		 * {@link #javaStateReleased javaStateReleased}, so there is no
		 * difference in the effect of the Java object being explicitly
		 * released, or found unreachable by the garbage collector.
		 */
		@Override
		protected void javaStateUnreachable()
		{
			javaStateReleased();
		}

		/**
		 * Allows a subclass to obtain the wrapped pointer value.
		 */
		protected long getPointer() throws SQLException
		{
			assertNativeStateIsValid();
			return m_pointer;
		}

		private native void _heapFreeTuple(long pointer);
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code FreeErrorData} of a single pointer.
	 */
	public static abstract class SingleFreeErrorData<T> extends DualState<T>
	{
		private volatile long m_pointer;

		protected SingleFreeErrorData(
			Key cookie, T referent, long resourceOwner, long fedTarget)
		{
			super(cookie, referent, resourceOwner);
			m_pointer = fedTarget;
		}

		@Override
		public String toString(Object o)
		{
			return String.format("%s FreeErrorData(%x)", super.toString(o),
								 m_pointer);
		}

		/**
		 * For this class, the native state is valid whenever the wrapped
		 * pointer is not null.
		 */
		@Override
		protected boolean nativeStateIsValid()
		{
			return 0 != m_pointer;
		}

		/**
		 * When the native state is released, the wrapped pointer is nulled
		 * to indicate the state is no longer valid; no
		 * {@code FreeErrorData} call is
		 * made, on the assumption that the resource owner's release will be
		 * followed by wholesale release of the containing memory context
		 * anyway.
		 */
		@Override
		protected void nativeStateReleased()
		{
			m_pointer = 0;
		}

		/**
		 * When the Java state is released, the wrapped pointer is nulled to
		 * indicate the state is no longer valid, <em>and</em> a
		 * {@code FreeErrorData}
		 * call is made so the native memory is released without having to wait
		 * for release of its containing context.
		 *<p>
		 * This overrides the inherited default, which would have removed this
		 * instance from the live instances collection. Users of this class
		 * should not call this method directly, but simply call
		 * {@link #enqueue enqueue}, and let the reclamation happen when the
		 * queue is processed.
		 */
		@Override
		protected void javaStateReleased()
		{
			synchronized(Backend.THREADLOCK)
			{
				long p = m_pointer;
				m_pointer = 0;
				if ( 0 != p )
					_freeErrorData(p);
			}
		}

		/**
		 * This override simply calls
		 * {@link #javaStateReleased javaStateReleased}, so there is no
		 * difference in the effect of the Java object being explicitly
		 * released, or found unreachable by the garbage collector.
		 */
		@Override
		protected void javaStateUnreachable()
		{
			javaStateReleased();
		}

		/**
		 * Allows a subclass to obtain the wrapped pointer value.
		 */
		protected long getPointer() throws SQLException
		{
			assertNativeStateIsValid();
			return m_pointer;
		}

		private native void _freeErrorData(long pointer);
	}
}
