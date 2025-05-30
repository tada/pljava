/*
 * Copyright (c) 2018-2025 Tada AB and other contributors, as listed below.
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

import static java.lang.invoke.MethodHandles.lookup;
import java.lang.invoke.VarHandle;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import java.sql.SQLException;

import java.util.ArrayDeque;
import static java.util.Arrays.copyOf;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.unpark;

import java.util.function.Supplier;

import static java.lang.management.ManagementFactory.getPlatformMBeanServer;
import javax.management.ObjectName;
import javax.management.JMException;

import org.postgresql.pljava.mbeans.DualStateStatistics;

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
 * must throw an exception for any future attempted access to its
 * native state.</li>
 * </ul>
 *<p>
 * A subclass overrides the {@link #javaStateReleased javaStateReleased},
 * {@link #javaStateUnreachable javaStateUnreachable}, or
 * {@link #nativeStateReleased nativeStateReleased} methods, respectively,
 * to add behavior for those life cycle events.
 *<p>
 * A subclass calls {@link #releaseFromJava releaseFromJava} to signal an event
 * of the first kind. Events of the second kind are, naturally, detected by the
 * Java garbage collector. To detect events of the third kind, a resource owner
 * must be associated with the instance.
 *<p>
 * A parameter to the {@code DualState} constructor is a {@code ResourceOwner},
 * a PostgreSQL implementation concept introduced in PG 8.0. A
 * {@code nativeStateReleased} event occurs when the corresponding
 * {@code ResourceOwner} is released in PostgreSQL.
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
 * Java code may execute in multiple threads, but PostgreSQL is not
 * multi-threaded; at any given time, there is no more than one thread that may
 * safely make JNI calls into PostgreSQL routines (for that thread,
 * {@code Backend.threadMayEnterPG()} returns true). Depending on the setting of
 * the {@code pljava.java_thread_pg_entry} PostgreSQL configuration variable,
 * that may be the same one thread for the duration of a session, or it may be
 * possible for one thread to relinquish that status and another thread to take
 * it: for the {@code pljava.java_thread_pg_entry} setting {@code allow}, the
 * status is represented by holding the object monitor on
 * {@code Backend.THREADLOCK}, and {@code Backend.threadMayEnterPG()} returns
 * true for whatever thread holds it. Under that setting, there can be moments
 * when {@code Backend.threadMayEnterPG()} is not true for any thread, if one
 * has released the monitor and no other thread has yet acquired it. For brevity
 * in what follows, "the PG thread" will be used to mean whatever thread, at a
 * given moment, would observe {@code Backend.threadMayEnterPG()} to return
 * true.
 *<p>
 * Some methods of {@code DualState} and subclasses may be called from any Java
 * thread, while some must be called from the PG thread. The life-cycle
 * callbacks, {@code javaStateReleased}, {@code javaStateUnreachable}, and
 * {@code nativeStateReleased}, are called by the implementation, and always
 * on the PG thread.
 *<p>
 * The Java Memory Model imposes strict conditions for updates to memory state
 * made in one thread to be visible to other threads. Methods that are known to
 * be called only on the PG thread can sidestep those complexities, at least
 * to the extent that they manipulate only data structures not accessed in other
 * threads. This is true even under the {@code pljava.java_thread_pg_entry}
 * setting {@code allow}, where "the PG thread" may not always be the same
 * thread. Because a Java synchronization event is involved whenever
 * "the PG thread" changes, unbroken visibility is assured, just as it would be
 * in one unchanging thread, so one can say "the PG thread" for convenience and
 * without loss of generality.
 *<p>
 * For the {@code nativeStateReleased} lifecycle event, rules for memory
 * visibility are not enough; a mechanism for mutual exclusion is needed. The
 * callback is made on the PG thread from PostgreSQL code that is in the process
 * of invalidating the native state, and will do so once the callback returns.
 * If any other Java thread is actively referring to that native state, there is
 * no choice but to block the PG thread making the callback until such other
 * threads are no longer relying on the native state.
 *<p>
 * To that end, the {@link #pin pin} and {@link #unpin unpin} methods are
 * provided, and must be used to surround any block of code that accesses the
 * native state:
 *<pre>
 *pin();
 *try
 *{
 *    ... code that dereferences or relies on
 *    a valid native state ...
 *}
 *finally
 *{
 *    unpin();
 *}
 *</pre>
 *<p>
 * Pins are lightweight, nonexclusive (any number of threads may simultaneously
 * pin the same {@code DualState} instance), and reentrant (a single thread may
 * obtain and release nested pins on the same instance). The code protected by a
 * pin is ideally a short sequence representing a simple operation (reading a
 * value, or refilling a small buffer with data) on the native state. The chief
 * purpose of holding a pin is to hold off the possible invalidation of the
 * native state until the pin is released.
 *<p>
 * If either the native state or the Java state has been released already (by
 * the resource owner callback or an explicit call to {@code releaseFromJava},
 * respectively), {@code pin()} will detect that and throw the appropriate
 * exception. Otherwise, the state is safe to make use of until {@code unpin}.
 * A subclass can customize the messages or {@code SQLSTATE} codes for the
 * exceptions {@code pin()} may throw, by overriding one or more of
 * {@link #identifierForMessage identifierForMessage},
 * {@link #invalidMessage invalidMessage},
 * {@link #releasedMessage releasedMessage},
 * {@link #invalidSqlState invalidSqlState}, or
 * {@link #releasedSqlState releasedSqlState}.
 *<p>
 * Code that holds a pin may safely act on components of the native state from
 * any thread, so long as the actions do not include native calls to PostgreSQL
 * routines (directly or transitively). Access to the native memory through a
 * direct byte buffer would be a permitted example, or even calls to JNI methods
 * to retrieve fields from C {@code struct}s or chase pointers through a data
 * structure, as long as only thread-safe routines from the C runtime are called
 * and no routines of PostgreSQL itself, and as long as the memory or structure
 * being accessed is known to be safe from modification by PostgreSQL while the
 * pin is held. In the future, PL/Java may one day have an annotation that can
 * be used to mark native methods that satisfy these limits; at present, there
 * has been no effort to segregate them into those that do and those that don't.
 * Native methods that may (under any circumstances!) invoke PG routines must
 * be invoked on the PG thread.
 *<p>
 * The exclusive counterparts to {@code pin} and {@code unpin} are
 * {@link #lock lock} and {@link #unlock(int,boolean) unlock}, which are not
 * expected to be used as widely. The chief use of {@code lock}/{@code unlock}
 * is around the call to {@code nativeStateReleased} when handling a resource
 * owner callback from PostgreSQL. They can be used in subclasses to surround
 * modifications to the state, as needed. A {@code lock} will block until all
 * earlier-acquired pins are released; subsequent pins block until the lock is
 * released. Only the PG thread may use {@code lock}/{@code unlock}. An
 * {@code upgrade} argument to {@code lock} allows the lock to be acquired
 * when the PG thread already holds a pin; it should be specified
 * only when inspection of the code identifies a nearby enclosing pin and
 * confirms that the planned locked actions will not break the pinning code's
 * assumptions. Pins can be freely acquired by the PG thread while it holds a
 * lock; the coding convention's strict nesting assures they will all be
 * released before the lock is.
 *<p>
 * In an explicit call to {@code releaseFromJava}, which may be made from any
 * thread, the instance is immediately, atomically, flagged as released. No
 * subsequent pin will succeed. Pins already held are unaffected, so there must
 * be no changes made to the state, at the time {@code releaseFromJava} is
 * called, that could confuse any code that already holds a pin and is relying
 * on the state. Such changes must be made in the {@code javaStateReleased}
 * callback, which will execute only after release of the last pin, if any, and
 * always on the PG thread. If the last pin is released by a thread other than
 * the PG thread, the callback does not execute immediately, but via a queue
 * that is polled from the PG thread at convenient points.
 *<p>
 * Instances whose referents are found unreachable by Java's garbage collector
 * are placed on the same queue, so their {@code javaStateUnreachable} callbacks
 * will be executed on the PG thread when the queue is polled. The callbacks
 * should clean up any lingering native state.
 *<p>
 * As the callbacks are executed on the PG thread, any native calls they may
 * need to make into PostgreSQL are allowed without extra ceremony.
 *<p>
 * There are different abstract subclasses of {@code DualState} that wrap
 * different sorts of PostgreSQL native state, and encapsulate what needs to be
 * done when such state is released from the Java or native side. More such
 * subclasses can be added as needed.
 *<p>
 * A client class of {@code DualState} will typically contain a static nested
 * class that further extends one of these abstract subclasses, and the client
 * instance will hold a strong reference to an instance of that
 * {@code DualState} subclass constructed at the same time.
 *<p>
 * <strong>This class uses some private data structures, to track
 * created instances through their life cycles, that are not synchronized or
 * thread-safe.</strong> The design rests on the following requirements:
 * <ul>
 * <li>The structures are only traversed or modified during:
 *  <ul>
 *  <li>Instance construction
 *  <li>Reference queue processing (instances found unreachable by Java's
 *  garbage collector, or enqueued following {@code releaseFromJava})
 *  <li>Exit of a resource owner's scope
 *  </ul>
 * <li>There is only one PG thread, or only one at a time.
 * <li>Construction of any {@code DualState} instance is to take place only on
 * the PG thread. The requirement to pass any
 * constructor a {@code DualState.Key} instance, obtainable by native code, is
 * intended to reinforce that convention. It is not abuse-proof, or intended as
 * a security mechanism, but only a guard against programming mistakes.
 * <li>Reference queue processing takes place only at chosen points where a
 * thread enters or exits native code, on the PG thread.
 * <li>Resource-owner callbacks originate in native code, on the PG thread.
 * </ul>
 */
public abstract class DualState<T> extends WeakReference<T>
{
	/**
	 * {@code DualState} objects Java no longer needs.
	 *<p>
	 * They will turn up on this queue (with referent already set null) if
	 * the garbage collector has determined them to be unreachable. They can
	 * also arrive here (also with referent nulled) following
	 * {@code releaseFromJava}.
	 *<p>
	 * The queue is only processed by a private method called on the PG thread
	 * in selected places where it makes sense to do so.
	 */
	private static final ReferenceQueue<Object> s_releasedInstances =
		new ReferenceQueue<>();

	/**
	 * {@code DualState} objects that arrived on {@code s_releasedInstances}
	 * before their time.
	 *<p>
	 * A slim chance exists (see {@code releaseFromJava} code comments) for an
	 * instance occasionally to appear on {@code s_releasedInstances}
	 * before all pins on it have been released. In queue processing, they can
	 * be put back on this queue and polled again in a later pass. The rarity of
	 * the case doesn't suggest a need for anything more elaborate.
	 */
	private static final Deque<DualState<?>> s_deferredReleased =
		new ArrayDeque<>();

	/**
	 * All instances in a non-transient native scope are added here upon
	 * creation, to keep them visible to the garbage collector.
	 *<p>
	 * Because they are not in a transient native scope, only the
	 * {@code javaStateUnreachable} or {@code javaStateReleased} lifecycle
	 * events can occur, and in either case the object is in hand with no
	 * searching, and can be removed from this structure in O(1).
	 */
	private static final IdentityHashMap<DualState,DualState>
		s_unscopedInstances = new IdentityHashMap<>();

	/**
	 * All native-scoped instances are added to this structure upon creation.
	 *<p>
	 * The hash map takes a resource owner to the doubly-linked list of
	 * instances it owns. The list is implemented directly with the two list
	 * fields here (rather than by a Collections class), so that an instance can
	 * be unlinked with no searching in the case of {@code javaStateUnreachable}
	 * or {@code javaStateReleased}, where the instance to be unlinked is
	 * already at hand. The list head is of a dummy {@code DualState} subclass.
	 */
	private static final Map<Long,DualState.ListHead> s_scopedInstances =
		new HashMap<>();

	/** Backward link in per-resource-owner list. */
	private DualState m_prev;

	/** Forward link in per-resource-owner list. */
	private DualState m_next;

	/**
	 * The sole thread (at a given moment) allowed to interact with Postgres and
	 * to acquire mutate locks on {@code DualState} instances.
	 *<p>
	 * Depending on the setting of {@code pljava.java_thread_pg_entry}, this may
	 * refer to the same thread at all times, or be different threads, one at a
	 * time.
	 *<p>
	 * Not volatile; atomic operations that follow any update to it will ensure
	 * its visibility.
	 */
	private static Thread s_mutatorThread;

	/**
	 * Tracker (using thread-local storage) of possibly re-entrant pins held
	 * on objects by the current thread.
	 *<p>
	 * Organized as a stack, enforcing a strict nesting protocol for pins.
	 */
	private static final PinCount.Holder s_pinCount = new PinCount.Holder();

	/**
	 * One (state object, pin count) entry on a stack of a thread's held pins.
	 */
	static final class PinCount
	{
		/**
		 * DualState object on which the pins counted by this entry are held.
		 */
		DualState<?> m_referent;
		/**
		 * Count of pins held on {@code m_referent} at this stack level.
		 *<p>
		 * The stack may hold earlier entries tracking additional pins on the
		 * same object, if the thread took a pin on some other object in
		 * between.
		 */
		short              m_count;

		/**
		 * Construct a new {@code PinCount} for a given referent, with count
		 * zero.
		 */
		PinCount(DualState<?> referent)
		{
			if ( null == referent )
				throw new NullPointerException("null referent of a PinCount");
			m_referent = referent;
		}

		/**
		 * Thread-local stack of {@code PinCount} entries.
		 */
		static final class Holder extends ThreadLocal<Manager>
		{
			@Override
			protected Manager initialValue()
			{
				return new Manager();
			}

			/**
			 * Increment a thread-local count of pins for a DualState object.
			 * @return null if there was already at least one pin counted for
			 * the object (that is, no real pin will need to be taken; this is
			 * a reentrant pin); otherwise, a {@code Supplier<Queue<Thread>>}
			 * that can supply a preallocated queue prepopulated with
			 * the current thread, in case inflation is needed.
			 */
			Supplier<Queue<Thread>> pin(DualState<?> s)
			{
				boolean result = false; // assume a real pin must be taken
				Manager counts = get();
				PinCount pc = counts.peek();
				if ( null == pc  ||  ! pc.m_referent.equals(s) )
				{
					result = counts.hasPin(s);
					pc = counts.push(s);
				}
				if ( 0 < pc.m_count ++  ||  result )
					return null;
				/*
				 * Ensure that counts.m_protoWaiters contains a preallocated
				 * queue with this thread already added to it, ready for
				 * immediate use by a contended pin that needs to inflate.
				 */
				if ( null == counts.m_protoWaiters )
				{
					counts.m_protoWaiters = new ConcurrentLinkedQueue<>();
					counts.m_protoWaiters.add(Thread.currentThread());
				}
				return counts;
			}

			/**
			 * Decrement a thread-local count of pins for a DualState object.
			 * @return true if there remains at least one pin counted for
			 * the object (that is, no real pin will need to be released;
			 * this is a reentrant unpin).
			 */
			boolean unpin(DualState<?> s)
			{
				Manager counts = get();
				PinCount pc = counts.peek();
				if ( null == pc  ||  ! pc.m_referent.equals(s) )
					throw new IllegalThreadStateException(
						"mispairing of DualState pin/unpin");
				if ( 0 == -- pc.m_count )
				{
					counts.pop();
					return counts.hasPin(s);
				}
				return true;
			}

			/**
			 * True if the current thread holds one or more pins on {@code s}.
			 */
			boolean hasPin(DualState<?> s)
			{
				return get().hasPin(s);
			}
		}

		/**
		 * Open-coded implementation of as much of a Stack as PinCount needs.
		 *<p>
		 * A lightweight stack implementation that also pools a few of the
		 * objects once pushed on it, for reuse, intended to produce less
		 * observed garbage than the earlier straight use of ArrayDeque.
		 */
		static final class Manager implements Supplier<Queue<Thread>>
		{
			private static final int INITIAL_SIZE = 4;
			private static final int POOL_TARGET = 2;
			private PinCount[] m_array = new PinCount [ INITIAL_SIZE ];
			private int m_top = -1;
			private int m_pooled = 0;
			Queue<Thread> m_protoWaiters;

			@Override
			public Queue<Thread> get()
			{
				Queue<Thread> q = m_protoWaiters;
				m_protoWaiters = null;
				return q;
			}

			PinCount peek()
			{
				if ( m_top >= 0 )
					return m_array [ m_top ];
				return null;
			}

			/**
			 * A version of 'pop' that returns {@code void}.
			 *<p>
			 * No caller above needs the value that was popped; {@code peek} is
			 * used for that. This method simply pops an element (and may,
			 * behind the scenes, reset its fields and pool it for reuse).
			 */
			void pop()
			{
				if ( m_top < 0 )
					throw new NoSuchElementException();
				if ( m_pooled >= POOL_TARGET )
					m_array [ m_top ] = null;
				else
				{
					PinCount pc = m_array [ m_top ];
					pc.m_referent = null;
					assert 0 == pc.m_count : "won't pop a nonzero PinCount";
					++ m_pooled;
				}
				-- m_top;
			}

			/**
			 * Obtain an entry from {@code allocate}, push and return it.
			 */
			PinCount push(DualState<?> s)
			{
				PinCount pc = allocate(s);
				++ m_top;
				if ( m_top < m_array.length )
					assert m_top + m_pooled < m_array.length : "stack v. pool";
				else
				{
					assert 0 == m_pooled : "pool will be empty if extending";
					m_array = copyOf(m_array, 2 * m_array.length);
				}
				m_array [ m_top ] = pc;
				return pc;
			}

			private PinCount allocate(DualState<?> s)
			{
				if ( m_pooled > 0 )
				{
					PinCount pc = m_array [ 1 + m_top ];
					-- m_pooled;
					pc.m_referent = s;
					return pc;
				}
				return new PinCount(s);
			}

			/**
			 * True if stack of {@code PinCount}s contains any with a non-zero
			 * count for object {@code s}.
			 */
			private boolean hasPin(DualState<?> s)
			{
				for ( int i = 1 + m_top; i --> 0; )
				{
					PinCount pc = m_array [ i ];
					if ( pc.m_referent.equals(s)  &&  0 < pc.m_count )
						return true;
				}
				return false;
			}
		}
	}

	/** Thread local record of when the PG thread is invoking callbacks. */
	private static final CleanupTracker s_inCleanup = new CleanupTracker();

	/** Thread local boolean with pairing enter/exit operations. */
	static final class CleanupTracker extends ThreadLocal<Boolean>
	{
		boolean enter()
		{
			assert Backend.threadMayEnterPG() : m("inCleanup.enter thread");
			assert ! inCleanup() : m("inCleanup.enter re-entered");
			set(Boolean.TRUE);
			return true;
		}

		boolean exit()
		{
			assert inCleanup() : m("inCleanup.exit mispaired");
			set(Boolean.FALSE);
			return true;
		}

		boolean inCleanup()
		{
			return Boolean.TRUE == get();
		}
	}

	/**
	 * {@code VarHandle} for applying atomic operations on the {@code m_state}
	 * field.
	 */
	private static final VarHandle s_stateVH;

	/**
	 * {@code VarHandle} for applying atomic operations on the {@code m_waiters}
	 * field.
	 */
	private static final VarHandle s_waitersVH;

	/**
	 * Bean to expose DualState allocation/release statistics to JMX management
	 * tools.
	 */
	private static final Statistics s_stats = new Statistics();

	static {
		try
		{
			s_stateVH =
				lookup().findVarHandle(DualState.class, "m_state", int.class);
			s_waitersVH = lookup().findVarHandle(DualState.class, "m_waiters",
				Queue.class);
		}
		catch ( ReflectiveOperationException e )
		{
			throw new ExceptionInInitializerError(e);
		}

		try
		{
			ObjectName n = new ObjectName(
				"org.postgresql.pljava:type=DualState,name=Statistics");
			getPlatformMBeanServer().registerMBean(s_stats, n);
		}
		catch ( JMException e ) { /* XXX */ }
	}

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
		assert Backend.threadMayEnterPG();
		if ( ! Key.class.isInstance(cookie) )
			throw new UnsupportedOperationException(
				"Operation on DualState instance without cookie");
	}

	/** Flag held in lock state showing the native state has been released. */
	private static final int NATIVE_RELEASED = 0x80000000;
	/** Flag held in lock state showing the Java state has been released. */
	private static final int JAVA_RELEASED   = 0x40000000;
	/** Flag held in lock state showing a lock has been acquired. */
	private static final int MUTATOR_HOLDS   = 0x20000000;
	/** Flag held in lock state showing a lock is pending. */
	private static final int MUTATOR_WANTS   = 0x10000000;
	/** Reserved, clear bit above count of pinners awaiting lock release. */
	private static final int WAITERS_GUARD   = 0x08000000;
	/** Mask for count of pending pinners awaiting lock release. */
	private static final int WAITERS_MASK    = 0x07ffc000;
	/** The bit shift to get WAITERS count from PINNERS count. */
	private static final int WAITERS_SHIFT   = 14;
	/** Reserved, clear bit above count of current valid pins. */
	private static final int PINNERS_GUARD   = 0x00002000;
	/** Mask for count of current pinners holding valid pins. */
	private static final int PINNERS_MASK    = 0x00001fff;

	/** Lock state, also records whether native or Java release has occurred. */
	private volatile int m_state = 0;

	/** Threads waiting for pins pending release of lock. */
	private Queue<Thread> m_waiters;

	/** True if argument is zero. */
	static boolean z(int i) { return 0 == i; }

	/**
	 * Return the argument; convenient breakpoint target for failed assertions.
	 */
	static <T> T m(T detail)
	{
		return detail;
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
	 * no longer valid. If zero (a NULL pointer in C), it indicates that the
	 * state is held in long-lived native memory (such as JavaMemoryContext),
	 * and can only be released via {@code javaStateUnreachable} or
	 * {@code javaStateReleased}.
	 */
	protected DualState(Key cookie, T referent, long resourceOwner)
	{
		super(referent, s_releasedInstances);

		checkCookie(cookie);

		long scoped = 0L;

		m_resourceOwner = resourceOwner;

		assert Backend.threadMayEnterPG() : m("DualState construction");
		/*
		 * The following stanza publishes 'this' into one of the static data
		 * structures, for resource-owner-scoped or non-native-scoped instances,
		 * respectively. That may look like escape of 'this' from an unfinished
		 * constructor, but the structures are private, and only manipulated
		 * during construction and release, always on the thread cleared to
		 * enter PG. Depending on the pljava.java_thread_pg_entry setting, that
		 * might or might not always be the same thread: but if it isn't, a
		 * synchronizing action must occur when a different thread takes over.
		 * That will happen after this constructor returns, so the reference is
		 * safely published.
		 */
		if ( 0 != resourceOwner )
		{
			scoped = 1L;
			DualState.ListHead head = s_scopedInstances.get(resourceOwner);
			if ( null == head )
			{
				head = new DualState.ListHead(resourceOwner);
				s_scopedInstances.put(resourceOwner, head);
			}
			m_prev = head;
			m_next = ((DualState)head).m_next;
			m_prev.m_next = m_next.m_prev = this;
		}
		else
			s_unscopedInstances.put(this, this);

		s_stats.construct(scoped);
	}

	/**
	 * Private constructor only for dummy instances to use as the list heads
	 * for per-resource-owner lists.
	 */
	private DualState(T referent, long resourceOwner)
	{
		super(referent); // as a WeakReference subclass, must have a referent
		super.clear();   // but nobody ever said for how long.
		m_resourceOwner = resourceOwner;
		m_prev = m_next = this;
		m_waiters = null;
	}

	/**
	 * Method that will be called when the associated {@code ResourceOwner}
	 * is released, indicating that the native portion of the state
	 * is no longer valid. The implementing class should clean up
	 * whatever is appropriate to that event.
	 *<p>
	 * This object's exclusive {@code lock()}  will always be held when this
	 * method is called during resource owner release. The class whose state
	 * this is must use {@link #pin() pin()}, followed by
	 * {@link #unpin() unpin()} in a {@code finally} block, around every
	 * (ideally short) block of code that could refer to the native state.
	 *<p>
	 * This default implementation does nothing.
	 * @param javaStateLive true is passed if the instance's "Java state" is
	 * still considered live, that is, {@code releaseFromJava} has not been
	 * called, and the garbage collector has not determined the referent to be
	 * unreachable.
	 */
	protected void nativeStateReleased(boolean javaStateLive)
	{
	}

	/**
	 * Method that will be called when the Java garbage collector has determined
	 * the referent object is no longer strongly reachable. This default
	 * implementation does nothing; a subclass should override it to do any
	 * cleanup, or release of native resources, that may be required.
	 *<p>
	 * If the {@code nativeStateLive} parameter is false, this method must avoid
	 * any action (such as freeing) it would otherwise take on the associated
	 * native state; if it does not, double-free crashes can result.
	 *<p>
	 * It is not necessary for this method to remove the instance from the
	 * live-instances data structures; that will have been done just before
	 * this method is called.
	 * @param nativeStateLive true is passed if the instance's "native state" is
	 * still considered live, that is, no resource-owner callback has been
	 * invoked to stamp it invalid (nor has it been "adopted").
	 */
	protected void javaStateUnreachable(boolean nativeStateLive)
	{
	}

	/**
	 * Called after client code has called {@code releaseFromJava}, always on
	 * a thread for which {@code Backend.threadMayEnterPG()} is true, and after
	 * any pins held on the state have been released.
	 *<p>
	 * This should not be called directly. When Java code has called
	 * {@code releaseFromJava}, the state will be changed to 'released'
	 * immediately, though without actually disturbing any state that might be
	 * referenced by threads with existing pins. This method will be called
	 * at some later time, always on a thread able to enter PG, and with no
	 * other threads having the native state pinned, so this is the place for
	 * any actual release of native state that may be needed.
	 *<p>
	 * If the {@code nativeStateLive} parameter is false, this method must avoid
	 * any action (such as freeing) it would otherwise take on the associated
	 * native state; if it does not, double-free crashes can result.
	 *<p>
	 * This default implementation calls {@code javaStateUnreachable}, which, in
	 * typical cases, will have the same cleanup to do.
	 * @param nativeStateLive true is passed if the instance's "native state" is
	 * still considered live, that is, no resource-owner callback has been
	 * invoked to stamp it invalid (nor has it been "adopted").
	 */
	protected void javaStateReleased(boolean nativeStateLive)
	{
		javaStateUnreachable(nativeStateLive);
	}

	/**
	 * What Java code will call to explicitly release this instance
	 * (in the implementation of {@code close}, for example).
	 *<p>
	 * The state is immediately marked 'released' to prevent future use, while
	 * a call to {@code javaStateReleased} will be deferred until after any pins
	 * currently held on the state have been released.
	 */
	protected final void releaseFromJava()
	{
		/*
		 * The possibility of a race between GC and a releaseFromJava call seems
		 * remote; it stands to reason that whoever is calling releaseFromJava
		 * is holding a live reference to the referent, so it's not eligible for
		 * GC. Strictly, though, something with only a reference to the
		 * DualState could call this method. It would be a strange coding
		 * pattern, but nothing here prevents it.
		 *
		 * Of greater concern is what happens after releaseFromJava. The caller
		 * might well let go of any reference, creating a race to see who puts
		 * the object onto the ReferenceQueue first, GC or this method (or
		 * unpin, if there are pins to wait for), making it difficult for the
		 * reference queue drainer to distinguish which case it is handling.
		 *
		 * That can be avoided by doing an unconditional clear() here, so (as
		 * long as the referent was live when we started), the GC is relieved of
		 * any queuing duties, leaving us in control of the next steps.
		 *
		 * We must do the clear() and also detect whether it already was clear,
		 * by calling referent() first. Those two steps aren't an atom, but at
		 * first blush it looks safe to hold our own strong reference in r1 and
		 * so hold off any finding of unreachability from there to the clear().
		 *
		 * Optimizing JIT could muddy the picture, though, by deciding the only
		 * use of r1 here is to compare it to null, which could be moved ahead
		 * of the clear(), in the absence of the reachabilityFence below.
		 *
		 * The m_state operations are the only ones with synchronizing effects,
		 * so we had better delay our unconditional clear() until after we know
		 * the JAVA_RELEASED flag has been CAS'd in. Otherwise we'd be unable to
		 * decide whether a referent-was-cleared-at-entry condition meant we
		 * were beaten by GC or by another releaseFromJava call.
		 *
		 * Note that Reference.isEnqueued() looks promising as a way to tell if
		 * the GC has enqueued something already, but it isn't: it goes back to
		 * false again as soon as a reference is removed from the queue, so it's
		 * hopelessly racy.
		 */
		T r1 = referent();

		int s = (int)s_stateVH.getAndBitwiseOr(this, JAVA_RELEASED);
		/*
		 * The state is now marked JAVA_RELEASED; that flag may have been set
		 * before us, or here now by us (the latter indicated by its absence
		 * in s).
		 *
		 * Now to find out if the referent was live at entry, and clear it.
		 */
		boolean releaseFlagWasClear = z(s & JAVA_RELEASED);
		boolean refWasClearAtEntry = null == r1;
		super.clear();
		reachabilityFence(r1);

		if ( refWasClearAtEntry )
		{
			if ( releaseFlagWasClear )
			{
				/*
				 * The garbage collector has already enqueued it; we are too
				 * late. Count the event and return.
				 * If there are any pins, two things will happen, one more bad,
				 * one less. The GC has no clue about waiting for unpins, so
				 * the queue drainer may receive (or already has, even) this
				 * object with active pins. It can detect that, and do something
				 * reasonable.
				 * Also, when unpin() releases the last one, it will want to
				 * enqueue the object again. That's already a non-problem:
				 * Reference.enqueue() only works one time.
				 */
				s_stats.gcReleaseRace();
				return;
			}
			else
			{
				/*
				 * We were beaten by another releaseFromJava call. That thread
				 * will be handling the remaining formalities.
				 */
				s_stats.releaseReleaseRace();
				return;
			}
		}

		if ( ! releaseFlagWasClear )
			return; // Other winning thread will handle the formalities.

		if ( !z(s & (WAITERS_MASK | PINNERS_MASK)) )
			return; // unpin() will schedule when the last pin is released

		scheduleJavaReleased(s);
	}

	/**
	 * Throws {@code UnsupportedOperationException}; {@code releaseFromJava}
	 * must be used rather than calling this method directly.
	 */
	@Override
	public final boolean enqueue()
	{
		throw new UnsupportedOperationException(
			"directly calling enqueue() on a DualState object is not " +
			"supported; use releaseFromJava().");
	}

	/**
	 * Throws {@code UnsupportedOperationException}; {@code releaseFromJava}
	 * must be used rather than calling this method directly.
	 */
	@Override
	public final void clear()
	{
		/*
		 * Must relax this assertion, because for some reason the clear() method
		 * can be called from enqueue() when running on OpenJ9. See
		 * https://github.com/AdoptOpenJDK/openjdk-support/issues/42
		 *
		 * So, spare the exception as long as the instance has in fact been
		 * released.
		 */
		int s = (int)s_stateVH.get(this);
		if ( z(s & JAVA_RELEASED) )
			throw new UnsupportedOperationException(
				"directly calling clear() on a DualState object is not " +
				"supported; use releaseFromJava().");
		super.clear();
	}

	/**
	 * Throws {@code UnsupportedOperationException}; client code should already
	 * hold a reference.
	 */
	@Override
	public final T get()
	{
		throw new UnsupportedOperationException(
			"directly calling get() on a DualState object is not supported.");
	}

	/**
	 * Used internally to obtain this object's referent.
	 */
	protected final T referent()
	{
		return super.get();
	}

	/**
	 * Obtain a pin on this state, throwing an appropriate exception if it
	 * is not still valid, blocking if necessary until release of a lock.
	 *<p>
	 * Pins are re-entrant; a thread may obtain more than one on the same
	 * object, in strictly nested fashion. Only the outer acquisition (and
	 * corresponding release) will have any memory synchronization effect;
	 * likewise, only the outer acquisition will detect release of the object
	 * and throw the associated exception.
	 * @throws SQLException if the native state or the Java state has been
	 * released.
	 * @throws CancellationException if the thread is interrupted while waiting.
	 */
	public final void pin() throws SQLException
	{
		int r = _pin();
		if ( z(r) )
			return;
		if ( !z(r & NATIVE_RELEASED) )
			throw new SQLException(invalidMessage(), invalidSqlState());
		throw new SQLException(releasedMessage(), releasedSqlState());
	}

	/**
	 * Obtain a pin on this state, if it is still valid, blocking if necessary
	 * until release of a lock.
	 *<p>
	 * Pins are re-entrant; a thread may obtain more than one on the same
	 * object, in strictly nested fashion. Only the outer acquisition (and
	 * corresponding release) will have any memory synchronization effect;
	 * likewise, only the outer acquisition will detect release of the object
	 * and throw the associated exception.
	 * @return true if the state has already been released; this will often be
	 * used in a caller (such as a {@code close} or {@code free} operation) that
	 * will have nothing to do and return immediately if this method returns
	 * true.
	 * @throws CancellationException if the thread is interrupted while waiting.
	 */
	public final boolean pinUnlessReleased()
	{
		return !z(_pin());
	}

	/**
	 * Workhorse for {@code pin()} and {@code pinUnlessReleased()}.
	 * @return zero if the pin was obtained, otherwise {@code NATIVE_RELEASED},
	 * {@code JAVA_RELEASED}, or both.
	 */
	private final int _pin()
	{
		/*
		 * The test for a reentrant pin will indicate its result by returning
		 * null (if the pin is reentrant and no further action is needed here)
		 * or a queue supplier, which is ready to supply a preallocated queue
		 * in case inflation is needed.
		 */
		Supplier<Queue<Thread>> qSupplier = s_pinCount.pin(this);

		if ( null == qSupplier )
			return 0; // reentrant pin, no need for sync effort

		int s = 1 + (int)s_stateVH.getAndAdd(this, 1); // be optimistic
		if ( z(s & ~ PINNERS_MASK) )       // nothing in s but a pin count? ->
			return 0;                        // ... uncontended win!
		if ( !z(s & (NATIVE_RELEASED | JAVA_RELEASED)) )
			return backoutPinBeforeEnqueue(s);
		if ( !z(s & PINNERS_GUARD) )
		{
			s = (int)s_stateVH.getAndAdd(this, -1); //recovery iffy in this case
			s_pinCount.unpin(this);
			throw new Error("DualState pin tracking capacity exceeded");
		}
		/*
		 * The state is either MUTATOR_HOLDS or MUTATOR_WANTS. In either case,
		 * we're too late to get a pin right now, and need to join the waiters
		 * queue and move our bit from the PINNERS_MASK region to the
		 * WAITERS_MASK region (by adding the value of the least waiters bit
		 * minus one, which is equal to PINNERS_GUARD|PINNERS_MASK).
		 *
		 * Proceeding in that order allows the mutator thread (if it is in
		 * MUTATOR_HOLDS and already unparked), when it releases, to ensure it
		 * sees us in the queue, by spinning as long as it sees any bits in the
		 * 'wrong' area.
		 *
		 * If moving our bit leaves zero under PINNERS_MASK and it's the
		 * MUTATOR_WANTS case, we promote and unpark the mutator before parking.
		 */

		Thread thr = Thread.currentThread();

		/*
		 * Observation shows contention is very rare, so m_waiters can be left
		 * null for most DualState instances, and be 'inflated' by having a
		 * queue installed when first needed. That requires a null check here.
		 */
		if ( null != m_waiters )
			m_waiters.add(thr);
		else
		{
			/*
			 * We install the queue with a CAS on m_waiters, which is enough to
			 * coordinate with any other thread trying to do this concurrently.
			 * It is not enough to synchronize with unlock(), which uses only a
			 * plain read on m_waiters. But it only does that after seeing our
			 * upcoming modification to m_state, which this happens before.
			 */
			if ( s_waitersVH.compareAndSet(this, null, qSupplier.get()) )
			{
				/*
				 * We successfully inflated. The queue obtained from get() above
				 * already has this thread enqueued on it, so there is nothing
				 * else to do here.
				 */
			}
			else
			{
				/*
				 * Somebody beat us to it. Their queue is just as good; use it.
				 */
				m_waiters.add(thr);
			}
		}

		int t;
		int u;
		/*
		 * Top-of-loop invariant, s has either MUTATOR_HOLDS or MUTATOR_WANTS,
		 * and we're counted under PINNERS_MASK, but under WAITERS_MASK is where
		 * we belong.
		 *
		 * Construct t from s, but moving us under WAITERS_MASK; if that leaves
		 * zero under PINNERS_MASK and s has MUTATOR_WANTS, promote it to
		 * MUTATOR_HOLDS. Try to CAS the new state into place.
		 *
		 * The top-of-loop invariant must still hold if the CAS fails
		 * and s is refetched: a state without MUTATOR_HOLDS or MUTATOR_WANTS
		 * cannot be reached as long as we are looping, because our presence in
		 * the PINNERS count prevents a WANTS advancing to HOLDS, and also
		 * blocks the final CAS in the release of a HOLDS.
		 */
		for ( ;; s = u )
		{
			t = s + (PINNERS_GUARD|PINNERS_MASK);
			/*
			 * Not necessary to check here for NATIVE_RELEASED - it only gets
			 * set at the release of a lock, which is prevented while we spin.
			 * JAVA_RELEASED could have appeared, though.
			 */
			if ( !z(s & JAVA_RELEASED) )
				return backoutPinAfterEnqueue(s);
			if ( !z(s & PINNERS_GUARD) )
			{
				backoutPinAfterEnqueue(s);
				throw new Error("DualState wait tracking capacity exceeded");
			}
			if ( !z(t & MUTATOR_WANTS)  &&  z(t & PINNERS_MASK) )
				t += MUTATOR_WANTS; // promote to MUTATOR_HOLDS, next bit left
			u = (int)s_stateVH.compareAndExchange(this, s, t);
			if ( s == u )
				break;
		}
		if ( !z(t & MUTATOR_HOLDS)  &&  !z(s & MUTATOR_WANTS)) // promoted by us
			unpark(s_mutatorThread);

		/*
		 * Invariant: t is the state before we park, and must have either
		 * MUTATOR_WANTS or MUTATOR_HOLDS (loop will exit if a state is fetched
		 * that has neither).
		 */
		for ( ;; t = s )
		{
			if ( ! thr.isInterrupted() )
				park(this);
			s = (int)s_stateVH.getVolatile(this);
			if ( thr.isInterrupted()
				|| !z(s & (NATIVE_RELEASED | JAVA_RELEASED)) )
				return backoutPinAfterPark(t);
			if ( !z(s & MUTATOR_HOLDS) ) // can only be a spurious unpark
				continue;
			if ( z(s & MUTATOR_WANTS) )  // no HOLDS, no WANTS, so
				break;                   // we have our pin and are free to go
			/*
			 * The newly-updated state has MUTATOR_WANTS. Check t (the pre-park
			 * state) to tease apart the cases for what that could mean.
			 */
			if ( !z(t & MUTATOR_HOLDS) ) // t, the pre-park state.
			{
				/*
				 * If MUTATOR_HOLDS was set when we parked, what the current
				 * state tells us is unambiguous: if it is now MUTATOR_WANTS,
				 * the earlier lock was released, we have our pin and are free
				 * to go, and the current MUTATOR_WANTS must wait for us.
				 */
				break;
			}
			/*
			 * This case is trickier. It was WANTS when we parked. The WANTS
			 * we now see could be the same WANTS we parked on, making this a
			 * spurious unpark, or it could be a new one that raced us to this
			 * point after the earlier one advanced to HOLDS, released, and
			 * unparked us. If that happened, we have our pin and are free to go
			 * (the new WANTS waits for us), and we can distinguish the cases
			 * because we are still in the queue in the former case but not the
			 * latter. (There is no race with the mutator thread draining the
			 * queue, because it does that with WANTS and HOLDS both clear, and
			 * remember, it is the only thread that gets to request a lock.)
			 */
			if ( m_waiters.contains(thr) )
				continue;
			break;
		}
		return 0;
	}

	/**
	 * Release a pin.
	 *<p>
	 * If the current thread has pinned the same object more than once, only the
	 * last {@code unpin} will have any memory synchronization effect.
	 */
	public final void unpin()
	{
		if ( s_pinCount.unpin(this) )
			return; // it was a reentrant pin, no need for sync effort

		int s = 1; // start by assuming state is simple with only one pinner, us
		int t;
		int u;
		for ( ;; s = u )
		{
			assert 1 <= (s & PINNERS_MASK) : m("DualState unpin < 1 pinner");
			t = s - 1;
			if ( !z(t & MUTATOR_WANTS)  &&  z(t & PINNERS_MASK) )
				t += MUTATOR_WANTS; // promote to MUTATOR_HOLDS, next bit left
			u = (int)s_stateVH.compareAndExchange(this, s, t);
			if ( s == u )
				break;
		}

		/*
		 * If there is a javaReleased event to schedule and a mutator to unpark,
		 * do them in that order, so the mutator will not see the event's
		 * clearing/enqueueing in progress.
		 */
		if ( !z(t & JAVA_RELEASED)  &&  z(t & (WAITERS_MASK | PINNERS_MASK)) )
			scheduleJavaReleased(t);
		if ( !z(t & MUTATOR_HOLDS)  &&  !z(s & MUTATOR_WANTS)) // promoted by us
			unpark(s_mutatorThread);
	}

	/**
	 * Whether the current thread has pinned this object, for use in assertions.
	 * @return true if the current thread holds a(t least one) pin on
	 * the receiver, or is the PG thread and holds the lock.
	 */
	public final boolean pinnedByCurrentThread()
	{
		return s_pinCount.hasPin(this)  ||  s_inCleanup.inCleanup();
	}

	/**
	 * Back out an in-progress pin before our thread has been placed on the
	 * queue.
	 * @param s the most recently known state
	 * @return the {@code NATIVE_RELEASED} and {@code JAVA_RELEASED} bits of
	 * the state
	 */
	private int backoutPinBeforeEnqueue(int s)
	{
		s_pinCount.unpin(this);
		int t;
		int u;
		for ( ;; s = u )
		{
			assert 1 <= (s & PINNERS_MASK) : m("backoutPinBeforeEnqueue");
			t = s - 1;
			if ( !z(t & MUTATOR_WANTS)  &&  z(t & PINNERS_MASK) )
				t += MUTATOR_WANTS; // promote to MUTATOR_HOLDS, next bit left
			u = (int)s_stateVH.compareAndExchange(this, s, t);
			if ( s == u )
				break;
		}
		/*
		 * See unpin() for why these are in this order.
		 */
		if ( !z(t & JAVA_RELEASED)  &&  z(t & (WAITERS_MASK | PINNERS_MASK)) )
			scheduleJavaReleased(t);
		if ( !z(t & MUTATOR_HOLDS)  &&  !z(s & MUTATOR_WANTS)) // promoted by us
			unpark(s_mutatorThread);
		return t & (NATIVE_RELEASED | JAVA_RELEASED);
	}

	/**
	 * Back out an in-progress pin after our thread has been placed on the
	 * queue, but before success of the CAS that counts us under WAITERS_MASK
	 * rather than PINNERS_MASK.
	 * @param s the most recently known state
	 * @return the {@code NATIVE_RELEASED} and {@code JAVA_RELEASED} bits of
	 * the state
	 */
	private int backoutPinAfterEnqueue(int s)
	{
		m_waiters.remove(Thread.currentThread());
		return backoutPinBeforeEnqueue(s);
	}

	/**
	 * Back out a pin acquisition attempt from within the park loop (which
	 * includes a slim chance that the pin is, in fact, acquired by the time
	 * this method can complete, in which case the pin is immediately released).
	 *<p>
	 * This is only called when a condition is detected during park for which an
	 * exception ought to be thrown. Therefore, after backing out, this method
	 * either throws the {@code CancellationException} (if thread interruption
	 * was the reason), or returns one or both of {@code NATIVE_RELEASED} or
	 * {@code JAVA_RELEASED}; it never returns zero.
	 * @param t prior state from before parking
	 * @throws CancellationException if the reason was thread interruption
	 * @return the {@code NATIVE_RELEASED} and {@code JAVA_RELEASED} bits of
	 * the state
	 */
	private int backoutPinAfterPark(int t)
	{
		boolean wasHolds = !z(t & MUTATOR_HOLDS); // t, the pre-park state
		/*
		 * Quickly ADD a (fictitious) PINNER, which will reliably jam any more
		 * transitions by the mutator thread (WANTS to HOLDS, or HOLDS to
		 * released) while we determine what to do next.
		 */
		t = 1 + (int)s_stateVH.getAndAdd(this, 1);

		/*
		 * From the current state, determine whether our pin has in fact been
		 * acquired (so to back it out we must in fact unpin), or is still
		 * pending, using the same logic explained at the end of pin() above.
		 */
		boolean mustUnpin =
			z(t & (MUTATOR_HOLDS | MUTATOR_WANTS))
			|| z(t & MUTATOR_HOLDS)
				&& (wasHolds  ||  ! m_waiters.contains(Thread.currentThread()));

		/*
		 * If the pin has been acquired and must be unpinned, we simply subtract
		 * the fictitious extra pinner added in this method, then unpin.
		 *
		 * Otherwise, we are still enqueued, and counted in the WAITERS region
		 * (along with our fictitious added PINNER). Subtract out the WAITER,
		 * which leaves (with the added PINNER) exactly the situation that
		 * backoutPinAfterEnqueue() knows how to clean up.
		 */

		int delta;
		if ( mustUnpin )
		{
			delta = 1;
			assert 1 < (t & PINNERS_MASK) : m("backoutPinAfterPark(acquired)");
		}
		else
		{
			delta = 1 << WAITERS_SHIFT;
			assert delta <= (t & WAITERS_MASK) : m("backoutPinAfterPark");
		}
		t = (int)s_stateVH.getAndAdd(this, - delta) - delta;

		if ( mustUnpin )
			unpin();
		else
			t = backoutPinAfterEnqueue(t);
		/*
		 * One of the following conditions was the reason this method was
		 * called, so throw the appropriate exception.
		 */
		if ( Thread.interrupted() )
			throw (CancellationException)
				new CancellationException("Interrupted waiting for pin")
					.initCause(new InterruptedException());
		return t & (NATIVE_RELEASED | JAVA_RELEASED);
	}

	/**
	 * Arrange the real work of cleaning up for an instance released by Java,
	 * as soon as there are no pins held on it.
	 *<p>
	 * This is called immediately by {@code releaseFromJava} if there are no
	 * pins at the time; otherwise, it is called by {@code unpin} when the last
	 * pin is released.
	 *<p>
	 * It could call {@code javaStateReleased} directly if the current thread
	 * may enter the native PostgreSQL code, otherwise adding the instance to
	 * the reference queue, to be handled when the queue is polled by such
	 * a thread.
	 *<p>
	 * A complication arises because of a very slim chance that the instance has
	 * already been enqueued (see {@code releaseFromJava} for details), and
	 * should not be enqueued again. As if tailor-made for such a situation, the
	 * {@code Reference.enqueue} method only works one time, and is a no-op
	 * thereafter. Hence, a simple and workable scheme is to unconditionally
	 * call {@code enqueue} here (enqueuing the object, or not, if it already
	 * has been), and then call the queue drainer if this is the PG thread.
	 */
	private void scheduleJavaReleased(int s)
	{
		super.enqueue();

		if ( Backend.threadMayEnterPG() )
			cleanEnqueuedInstances();
	}

	/**
	 * Take an exclusive lock in preparation to mutate the state.
	 *<p>
	 * Only a thread for which {@code Backend.threadMayEnterPG()} returns true
	 * may acquire this lock.
	 * @param upgrade whether to acquire the lock without blocking even in the
	 * presence of a pin held by this thread; should be true only in cases where
	 * inspection shows a nearby enclosing pin whose assumptions clearly will
	 * not be violated by the actions to be taken under the lock.
	 * @return A semi-redacted version of the lock state, enough to discern
	 * whether it contains {@code NATIVE_RELEASED} or {@code JAVA_RELEASED}
	 * in case the caller cares, and for the paired {@code unlock} call to know
	 * whether this was a reentrant call, or should really be released.
	 */
	protected final int lock(boolean upgrade)
	{
		if ( ! Backend.threadMayEnterPG() )
			throw new IllegalThreadStateException(
				"This thread may not mutate a DualState object");
		s_mutatorThread = Thread.currentThread();

		assert !upgrade  ||  pinnedByCurrentThread() : m("upgrade without pin");

		int s = upgrade ? 1 : 0; // to start, assume simple state, no other pins
		int t;
		int u;
		int contended = 0;
		for ( ;; )
		{
			t = s;
			if ( upgrade )
				t += (PINNERS_GUARD|PINNERS_MASK); // hide my pin as a waiter
			t |= ( !z(t & PINNERS_MASK) ? MUTATOR_WANTS : MUTATOR_HOLDS );
			u = (int)s_stateVH.compareAndExchange(this, s, t);
			if ( s == u )
				break;
			s = u;
			if ( !z(s & MUTATOR_HOLDS) ) // surprise! this is a reentrant call.
				return s & (NATIVE_RELEASED | JAVA_RELEASED | MUTATOR_HOLDS);
			if ( z(s & PINNERS_MASK) )
				upgrade = false; // apparently we have no pin to upgrade
		}
		while ( z(t & MUTATOR_HOLDS) )
		{
			contended = 1;
			park(this);
			t = (int)s_stateVH.getVolatile(this);
		}
		s_stats.lockContended(contended);
		return t & (NATIVE_RELEASED | JAVA_RELEASED) | (upgrade? 1 : 0);
	}

	/**
	 * Calls {@link #unlock(int,boolean) unlock(s, false)}.
	 * @param s must be the value returned by the {@code lock} call.
	 */
	protected final void unlock(int s)
	{
		unlock(s, false);
	}

	/**
	 * Release a lock, optionally setting the {@code NATIVE_RELEASED} flag
	 * atomically in the process.
	 * @param s must be the value returned by the {@code lock} call.
	 * @param isNativeRelease whether to set the {@code NATIVE_RELEASED} flag.
	 */
	protected final void unlock(int s, boolean isNativeRelease)
	{
		int t;
		int u;
		if ( !z(s & MUTATOR_HOLDS) )
		{
			/*
			 * The paired lock() determined it was already held (this was a
			 * reentrant acquisition), so that the obvious thing to do here
			 * is nothing. However, if the caller wants to set NATIVE_RELEASED
			 * (and it wasn't already), that has to happen, even if nothing
			 * else does.
			 */
			if ( isNativeRelease  &&  z(s & NATIVE_RELEASED) )
				u = (int)s_stateVH.getAndBitwiseOr(this, NATIVE_RELEASED);
			return;
		}

		boolean upgrade = !z(s & 1); // saved there in the last line of lock()

		/*
		 * We are here, so this is a real unlock action. In the same motion, we
		 * will CAS in the NATIVE_RELEASED bit if the caller wants it.
		 */
		int release = isNativeRelease ? NATIVE_RELEASED : 0;

		s = MUTATOR_HOLDS; // start by assuming state is simple, just our lock
		if ( upgrade )
			s |= 1 << WAITERS_SHIFT; // ok, assume our stashed pin is there too
		for ( ;; )
		{
			t = s & ~(MUTATOR_HOLDS|WAITERS_MASK|PINNERS_MASK);
			t |= release | ( (s & WAITERS_MASK) >>> WAITERS_SHIFT );
			/*
			 * Zero the PINNERS region in s, so the CAS will fail if anything's
			 * there. During MUTATOR_HOLDS, the only bits under PINNERS_MASK
			 * represent new would-be pinners while they add themselves to the
			 * queue, so we just spin for them here until they've moved their
			 * bits under WAITERS_MASK where they belong. Then trust the queue.
			 */
			s &= ~ PINNERS_MASK;
			u = (int)s_stateVH.compareAndExchange(this, s, t);
			if ( s == u )
				break;
			s = u;
			assert !z(s & MUTATOR_HOLDS) : m("DualState mispaired unlock");
			/*
			 * Most of our CAS spins in this class require only that no other
			 * thread write s between our fetch and CAS, so 'starving' other
			 * threads can't last long, and in fact guarantees rapid success.
			 * This spin, however, could go on for as long as it takes for some
			 * other thread to enqueue itself and move its bit out of the way.
			 * That's still a very short spin, unless we are short of CPUs and
			 * actually competing with the thread we're waiting for. Hence
			 * onSpinWait seems prudent, if pinner count is the reason we spin.
			 */
			if ( !z(s & PINNERS_MASK) )
				Thread.onSpinWait();
		}
		/*
		 * It's good to be the only thread allowed to mutate. Nobody else will
		 * touch this queue until the next time we want a mutate lock, so simply
		 * drain it and unpark every waiting thread.
		 */
		t &= PINNERS_MASK; // should equal the number of threads on the queue
		if ( upgrade ) // my pin bit was stashed as a waiter, but nothing queued
			-- t;
		/*
		 * If no waiters (t is zero), we are done. Don't bother comparing zero
		 * to the queue size; inflation may not have supplied a queue yet.
		 */
		if ( 0 == t )
			return;
		s_stats.pinContended(t);
		Thread thr;
		while ( null != (thr = m_waiters.poll()) )
		{
			-- t;
			unpark(thr);
		}
		assert 0 == t : m("Miscount of DualState wait queue");
	}

	/**
	 * Specialized version of {@link #lock lock} for use by code implementing an
	 * {@code adopt} operation (in which complete control of an object is handed
	 * back to PostgreSQL and it is dissociated from Java).
	 *<p>
	 * Can only be called on the PG thread, which must already hold a pin.
	 * No other thread can hold a pin, and neither the {@code NATIVE_RELEASED}
	 * nor {@code JAVA_RELEASED} flag may be set. This method is non-blocking
	 * and will simply throw an exception if these preconditions are not
	 * satisfied.
	 * @param cookie Capability held by native code to invoke special
	 * {@code DualState} methods.
	 */
	protected final void adoptionLock(Key cookie) throws SQLException
	{
		checkCookie(cookie);
		s_mutatorThread = Thread.currentThread();
		assert pinnedByCurrentThread() : m("adoptionLock without pin");
		int s = 1; // must be: quiescent (our pin only), unreleased
		int t = NATIVE_RELEASED | JAVA_RELEASED | MUTATOR_HOLDS
				| 1 << WAITERS_SHIFT;
		if ( ! (boolean)s_stateVH.compareAndSet(this, s, t) )
			throw new SQLException(
				"Attempt by PostgreSQL to adopt a released or non-quiescent " +
				"Java object");
	}

	/**
	 * Specialized version of {@link #unlock(int, boolean) unlock} for use by
	 * code implementing an {@code adopt} operation (in which complete control
	 * of an object is handed back to PostgreSQL and it is dissociated from
	 * Java).
	 *<p>
	 * Must only be called on the PG thread, which must have acquired
	 * {@code adoptionLock}. Invokes the {@code nativeStateReleased} callback,
	 * then releases the lock, leaving both {@code NATIVE_RELEASED}
	 * and {@code JAVA_RELEASED} flags set. When the calling code releases the
	 * prior pin it was expected to hold, the {@code javaStateReleased} callback
	 * will execute. A value of false will be passed to both callbacks.
	 * @param cookie Capability held by native code to invoke special
	 * {@code DualState} methods.
	 */
	protected final void adoptionUnlock(Key cookie) throws SQLException
	{
		checkCookie(cookie);
		int s = NATIVE_RELEASED | JAVA_RELEASED | MUTATOR_HOLDS
				| 1 << WAITERS_SHIFT;
		int t = NATIVE_RELEASED | JAVA_RELEASED | 1;

		/*
		 * nativeStateReleased is, as usual, executed while holding the lock.
		 */
		nativeStateReleased(false);

		if ( ! (boolean)s_stateVH.compareAndSet(this, s, t) )
			throw new SQLException("Release failed while adopting Java object");

		/*
		 * The release of our pre-existing pin will take care of delisting and
		 * executing javaStateReleased.
		 */
	}

	/**
	 * Return a string identifying this object in a way useful within an
	 * exception message for use of this state after native release or Java
	 * release.
	 *<p>
	 * This implementation returns the class name of the referent, or of this
	 * object if the referent has already been cleared.
	 */
	protected String identifierForMessage()
	{
		String id;
		Object referent = referent();
		if ( null != referent )
			id = referent.getClass().getName();
		else
			id = getClass().getName();
		return id;
	}

	/**
	 * Return a string for an exception message reporting the use of this object
	 * after the native state has been released.
	 *<p>
	 * This implementation returns {@code identifierForMessage()} with
	 * " used beyond its PostgreSQL lifetime" appended.
	 */
	protected String invalidMessage()
	{
		return identifierForMessage() + " used beyond its PostgreSQL lifetime";
	}

	/**
	 * Return a string for an exception message reporting the use of this object
	 * after the Java state has been released.
	 *<p>
	 * This implementation returns {@code identifierForMessage()} with
	 * " used after released by Java" appended.
	 */
	protected String releasedMessage()
	{
		return identifierForMessage() + " used after released by Java";
	}

	/**
	 * Return the SQLSTATE appropriate for an attempt to use this object
	 * after its native state has been released.
	 *<p>
	 * This implementation returns 55000, object not in prerequisite state.
	 */
	protected String invalidSqlState()
	{
		return "55000";
	}

	/**
	 * Return the SQLSTATE appropriate for an attempt to use this object
	 * after its Java state has been released.
	 *<p>
	 * This implementation returns 55000, object not in prerequisite state.
	 */
	protected String releasedSqlState()
	{
		return "55000";
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
		int pnl = c.getPackageName().length();
		return String.format("%s owner:%x %s",
			cn.substring(1 + pnl), m_resourceOwner,
			z((int)s_stateVH.getVolatile(this) & NATIVE_RELEASED)
				? "fresh" : "stale");
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
	 * Java. That doesn't remove the state instance from s_scopedInstances,
	 * though, so it will still eventually be seen by this loop and efficiently
	 * removed by the iterator. Hence the {@code NATIVE_RELEASED} test, to avoid
	 * invoking nativeStateReleased more than once.
	 */
	private static void resourceOwnerRelease(long resourceOwner)
	{
		long total = 0L, release = 0L;

		assert Backend.threadMayEnterPG() : m("resourceOwnerRelease thread");

		DualState head = s_scopedInstances.remove(resourceOwner);
		if ( null == head )
			return;

		DualState t = head.m_next;
		head.m_prev = head.m_next = null;
		for ( DualState s = t ; s != head ; s = t )
		{
			t = s.m_next;
			s.m_prev = s.m_next = null;
			++ total;
			/*
			 * This lock() is part of DualState's contract with clients.
			 * They are responsible for pinning the state instance
			 * whenever they need the wrapped native state (which is verified
			 * to still be valid at that time) and for the duration of whatever
			 * operation needs access to that state. Taking this lock here
			 * ensures the native state is blocked from vanishing while it is
			 * actively in use.
			 */
			int state = s.lock(false);
			try
			{
				if ( z(NATIVE_RELEASED & state) )
				{
					++ release;
					s.nativeStateReleased(
						z(JAVA_RELEASED & state)  &&  null != s.referent());
				}
			}
			finally
			{
				s.unlock(state, true); // true -> ensure NATIVE_RELEASED is set.
			}
		}

		s_stats.resourceOwnerPoll(release, total);
	}

	/**
	 * Called only from native code, at points where checking the
	 * freed/unreachable objects queue would be useful, or from
	 * {@code scheduleJavaReleased} when on the PG thread. Calls the
	 * {@link #javaStateUnreachable javaStateUnreachable} method for instances
	 * that were cleared and enqueued by the garbage collector; calls the
	 * {@link #javaStateReleased javaStateReleased} method for instances that
	 * have not yet been garbage collected, but were enqueued by Java code
	 * explicitly calling {@link #releaseFromJava releaseFromJava}.
	 */
	private static void cleanEnqueuedInstances()
	{
		long total = 0L, release = 0L, reDefer = 0L;
		DualState s;
		int nDeferred = s_deferredReleased.size();
		boolean isDeferred;

		assert s_inCleanup.enter(); // no-op when assertions disabled
		try
		{
			for ( ;; )
			{
				isDeferred = 0 < nDeferred;
				if ( isDeferred )
				{
					-- nDeferred;
					s = s_deferredReleased.remove();
				}
				else if ( null == (s = (DualState)s_releasedInstances.poll()) )
					break;

				int state = (int)s_stateVH.getVolatile(s);
				if ( !z((PINNERS_MASK | WAITERS_MASK) & state) )
				{
					s_deferredReleased.add(s);
					if ( isDeferred )
						++ reDefer;
					continue;
				}

				++ total;

				s.delist();
				try
				{
					if ( !z(JAVA_RELEASED & state) )
					{
						++ release;
						s.javaStateReleased(z(NATIVE_RELEASED & state));
					}
					else if ( z(NATIVE_RELEASED & state) )
						s.javaStateUnreachable(z(NATIVE_RELEASED & state));
				}
				catch ( Throwable t ) { } /* JDK 9 Cleaner ignores exceptions */
			}
		}
		finally
		{
			assert s_inCleanup.exit();
		}

		s_stats.referenceQueueDrain(total - release, release, total, reDefer);
	}

	/**
	 * Remove this instance from the data structure holding it, for scoped
	 * instances if it has a non-zero resource owner, otherwise for unscoped
	 * instances.
	 */
	private void delist()
	{
		assert Backend.threadMayEnterPG() : m("DualState delist thread");

		if ( 0 == m_resourceOwner )
		{
			if ( null != s_unscopedInstances.remove(this) )
				s_stats.delistUnscoped();
			return;
		}

		if ( null == m_prev  ||  null == m_next )
			return;
		if ( this == m_prev.m_next )
			m_prev.m_next = m_next;
		if ( this == m_next.m_prev )
			m_next.m_prev = m_prev;
		m_prev = m_next = null;
		s_stats.delistScoped();
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
	 * Dummy DualState concrete class whose instances only serve as list
	 * headers in per-resource-owner lists of instances.
	 */
	private static class ListHead extends DualState<String> // because why not?
	{
		/**
		 * Construct a {@code ListHead} instance. As a subclass of
		 * {@code DualState}, it can't help having a resource owner field, so
		 * may as well use it to store the resource owner that the list is for,
		 * in case it's of interest in debugging.
		 * @param owner The resource owner
		 */
		private ListHead(long owner)
		{
			super("", owner); // An instance needs an object to be its referent
		}

		@Override
		public String toString(Object o)
		{
			return String.format(
				"DualState.ListHead for resource owner %x", m_resourceOwner);
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
		private final long m_guardedLong;

		protected SingleGuardedLong(
			Key cookie, T referent, long resourceOwner, long guardedLong)
		{
			super(cookie, referent, resourceOwner);
			m_guardedLong = guardedLong;
		}

		@Override
		public String toString(Object o)
		{
			return
				String.format(formatString(), super.toString(o), m_guardedLong);
		}

		/**
		 * Return a {@code printf} format string resembling
		 * {@code "%s something(%x)"} where the {@code %x} will be the value
		 * being guarded; the "something" should indicate what the value
		 * represents, or what will be done with it when released by Java.
		 */
		protected String formatString()
		{
			return "%s GuardedLong(%x)";
		}

		protected final long guardedLong()
		{
			assert pinnedByCurrentThread() : m("guardedLong() without pin");
			return m_guardedLong;
		}
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code pfree} of a single pointer.
	 */
	public static abstract class SinglePfree<T> extends SingleGuardedLong<T>
	{
		protected SinglePfree(
			Key cookie, T referent, long resourceOwner, long pfreeTarget)
		{
			super(cookie, referent, resourceOwner, pfreeTarget);
		}

		@Override
		protected String formatString()
		{
			return "%s pfree(%x)";
		}

		/**
		 * When the Java state is released or unreachable, a {@code pfree}
		 * call is made so the native memory is released without having to wait
		 * for release of its containing context.
		 */
		@Override
		protected void javaStateUnreachable(boolean nativeStateLive)
		{
			assert Backend.threadMayEnterPG();
			if ( nativeStateLive )
				_pfree(guardedLong());
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
	public static abstract class SingleMemContextDelete<T>
	extends SingleGuardedLong<T>
	{
		protected SingleMemContextDelete(
			Key cookie, T referent, long resourceOwner, long memoryContext)
		{
			super(cookie, referent, resourceOwner, memoryContext);
		}

		@Override
		public String formatString()
		{
			return "%s MemoryContextDelete(%x)";
		}

		/**
		 * When the Java state is released or unreachable, a
		 * {@code MemoryContextDelete}
		 * call is made so the native memory is released without having to wait
		 * for release of its parent context.
		 */
		@Override
		protected void javaStateUnreachable(boolean nativeStateLive)
		{
			assert Backend.threadMayEnterPG();
			if ( nativeStateLive )
				_memContextDelete(guardedLong());
		}

		private native void _memContextDelete(long pointer);
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code FreeTupleDesc} of a single pointer.
	 */
	public static abstract class SingleFreeTupleDesc<T>
	extends SingleGuardedLong<T>
	{
		protected SingleFreeTupleDesc(
			Key cookie, T referent, long resourceOwner, long ftdTarget)
		{
			super(cookie, referent, resourceOwner, ftdTarget);
		}

		@Override
		public String formatString()
		{
			return "%s FreeTupleDesc(%x)";
		}

		/**
		 * When the Java state is released or unreachable, a
		 * {@code FreeTupleDesc}
		 * call is made so the native memory is released without having to wait
		 * for release of its containing context.
		 */
		@Override
		protected void javaStateUnreachable(boolean nativeStateLive)
		{
			assert Backend.threadMayEnterPG();
			if ( nativeStateLive )
				_freeTupleDesc(guardedLong());
		}

		private native void _freeTupleDesc(long pointer);
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code heap_freetuple} of a single pointer.
	 */
	public static abstract class SingleHeapFreeTuple<T>
	extends SingleGuardedLong<T>
	{
		protected SingleHeapFreeTuple(
			Key cookie, T referent, long resourceOwner, long hftTarget)
		{
			super(cookie, referent, resourceOwner, hftTarget);
		}

		@Override
		public String formatString()
		{
			return"%s heap_freetuple(%x)";
		}

		/**
		 * When the Java state is released or unreachable, a
		 * {@code heap_freetuple}
		 * call is made so the native memory is released without having to wait
		 * for release of its containing context.
		 */
		@Override
		protected void javaStateUnreachable(boolean nativeStateLive)
		{
			assert Backend.threadMayEnterPG();
			if ( nativeStateLive )
				_heapFreeTuple(guardedLong());
		}

		private native void _heapFreeTuple(long pointer);
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code FreeErrorData} of a single pointer.
	 */
	public static abstract class SingleFreeErrorData<T>
	extends SingleGuardedLong<T>
	{
		protected SingleFreeErrorData(
			Key cookie, T referent, long resourceOwner, long fedTarget)
		{
			super(cookie, referent, resourceOwner, fedTarget);
		}

		@Override
		public String formatString()
		{
			return "%s FreeErrorData(%x)";
		}

		/**
		 * When the Java state is released or unreachable, a
		 * {@code FreeErrorData}
		 * call is made so the native memory is released without having to wait
		 * for release of its containing context.
		 */
		@Override
		protected void javaStateUnreachable(boolean nativeStateLive)
		{
			assert Backend.threadMayEnterPG();
			if ( nativeStateLive )
				_freeErrorData(guardedLong());
		}

		private native void _freeErrorData(long pointer);
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code SPI_freeplan} of a single pointer.
	 */
	public static abstract class SingleSPIfreeplan<T>
	extends SingleGuardedLong<T>
	{
		protected SingleSPIfreeplan(
			Key cookie, T referent, long resourceOwner, long fpTarget)
		{
			super(cookie, referent, resourceOwner, fpTarget);
		}

		@Override
		public String formatString()
		{
			return "%s SPI_freeplan(%x)";
		}

		/**
		 * When the Java state is released or unreachable, an
		 * {@code SPI_freeplan}
		 * call is made so the native memory is released without having to wait
		 * for release of its containing context.
		 */
		@Override
		protected void javaStateUnreachable(boolean nativeStateLive)
		{
			assert Backend.threadMayEnterPG();
			if ( nativeStateLive )
				_spiFreePlan(guardedLong());
		}

		private native void _spiFreePlan(long pointer);
	}

	/**
	 * A {@code DualState} subclass whose only native resource releasing action
	 * needed is {@code SPI_cursor_close} of a single pointer.
	 */
	public static abstract class SingleSPIcursorClose<T>
	extends SingleGuardedLong<T>
	{
		protected SingleSPIcursorClose(
			Key cookie, T referent, long resourceOwner, long ccTarget)
		{
			super(cookie, referent, resourceOwner, ccTarget);
		}

		@Override
		public String formatString()
		{
			return "%s SPI_cursor_close(%x)";
		}

		/**
		 * When the Java state is released or unreachable, an
		 * {@code SPI_cursor_close}
		 * call is made so the native memory is released without having to wait
		 * for release of its containing context.
		 *<p>
		 * For this class (and for reasons that weren't made
		 * obvious in the original code this reimplements), the native code will
		 * avoid calling {@code SPI_cursor_close} if the {@code Invocation}'s
		 * error-occurred flag is set, or during a callback from the executor
		 * through an {@code ExprContextCallbackFunction}.
		 */
		@Override
		protected void javaStateUnreachable(boolean nativeStateLive)
		{
			assert Backend.threadMayEnterPG();
			if ( nativeStateLive )
				_spiCursorClose(guardedLong());
		}

		/*
		 * This code copied from its former location in Portal.c, for reasons
		 * not really explained there, is different from most of the other
		 * javaStateReleased actions here, by virtue of being conditional; it
		 * does nothing if the current Invocation's errorOccurred flag is set,
		 * or during an end-of-expression-context callback from the executor.
		 */
		private native void _spiCursorClose(long pointer);
	}

	/**
	 * Bean exposing some {@code DualState} allocation and lifecycle statistics
	 * for viewing in a JMX management client.
	 */
	static class Statistics implements DualStateStatistics
	{
		public long getConstructed()
		{
			return constructed.sum();
		}

		public long getEnlistedScoped()
		{
			return enlistedScoped.sum();
		}

		public long getEnlistedUnscoped()
		{
			return enlistedUnscoped.sum();
		}

		public long getDelistedScoped()
		{
			return delistedScoped.sum();
		}

		public long getDelistedUnscoped()
		{
			return delistedUnscoped.sum();
		}

		public long getJavaUnreachable()
		{
			return javaUnreachable.sum();
		}

		public long getJavaReleased()
		{
			return javaReleased.sum();
		}

		public long getNativeReleased()
		{
			return nativeReleased.sum();
		}

		public long getResourceOwnerPasses()
		{
			return resourceOwnerPasses.sum();
		}

		public long getReferenceQueuePasses()
		{
			return referenceQueuePasses.sum();
		}

		public long getReferenceQueueItems()
		{
			return referenceQueueItems.sum();
		}

		public long getContendedLocks()
		{
			return contendedLocks.sum();
		}

		public long getContendedPins()
		{
			return contendedPins.sum();
		}

		public long getRepeatedlyDeferred()
		{
			return repeatedlyDeferred.sum();
		}

		public long getGcReleaseRaces()
		{
			return gcRelRaces.sum();
		}

		public long getReleaseReleaseRaces()
		{
			return relRelRaces.sum();
		}


		private LongAdder          constructed = new LongAdder();
		private LongAdder       enlistedScoped = new LongAdder();
		private LongAdder     enlistedUnscoped = new LongAdder();
		private LongAdder       delistedScoped = new LongAdder();
		private LongAdder     delistedUnscoped = new LongAdder();
		private LongAdder      javaUnreachable = new LongAdder();
		private LongAdder         javaReleased = new LongAdder();
		private LongAdder       nativeReleased = new LongAdder();
		private LongAdder  resourceOwnerPasses = new LongAdder();
		private LongAdder referenceQueuePasses = new LongAdder();
		private LongAdder  referenceQueueItems = new LongAdder();
		private LongAdder       contendedLocks = new LongAdder();
		private LongAdder        contendedPins = new LongAdder();
		private LongAdder   repeatedlyDeferred = new LongAdder();
		private LongAdder           gcRelRaces = new LongAdder();
		private LongAdder          relRelRaces = new LongAdder();

		final void construct(long scoped)
		{
			constructed.increment();
			enlistedScoped.add(scoped);
			enlistedUnscoped.add(1L - scoped);
		}

		final void resourceOwnerPoll(long released, long total)
		{
			resourceOwnerPasses.increment();
			nativeReleased.add(released);
			delistedScoped.add(total);
		}

		final void javaRelease(long scoped, long unscoped)
		{
			javaReleased.increment();
			delistedScoped.add(scoped);
			delistedUnscoped.add(unscoped);
		}

		final void referenceQueueDrain(
			long unreachable, long release, long total, long reDefer)
		{
			referenceQueuePasses.increment();
			referenceQueueItems.add(total);
			javaUnreachable.add(unreachable);
			javaReleased.add(release);
			repeatedlyDeferred.add(reDefer);
		}

		final void delistScoped()
		{
			delistedScoped.increment();
		}

		final void delistUnscoped()
		{
			delistedUnscoped.increment();
		}

		final void javaRelease()
		{
			javaReleased.increment();
		}

		final void lockContended(int n)
		{
			contendedLocks.add(n);
		}

		final void pinContended(int n)
		{
			contendedPins.add(n);
		}

		final void gcReleaseRace()
		{
			gcRelRaces.increment();
		}

		final void releaseReleaseRace()
		{
			gcRelRaces.increment();
		}
	}
}
