/*
 * Copyright (c) 2022-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.pg;

import static java.util.Arrays.copyOf;
import java.util.BitSet;

import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;

import org.postgresql.pljava.internal.DualState;
import static org.postgresql.pljava.internal.DualState.m;
import org.postgresql.pljava.internal.LifespanImpl;

import org.postgresql.pljava.PLJavaBasedLanguage; // javadoc

import org.postgresql.pljava.model.MemoryContext;

/**
 * A lazily-created mirror of a PostgreSQL ExprContext, exposing only as much
 * as might be useful in the dispatching of set-returning functions.
 *<p>
 * There is no PL/Java API interface that corresponds to this model; it is only
 * used in the implementation module. It may be exposed in the API as an object
 * that implements {@code Lifespan} but is otherwise unspecified.
 *<p>
 * PostgreSQL is creating, resetting, and deleting ExprContexts all the time,
 * and most of them will never be visible in PL/Java; one of these objects only
 * gets created when Java code specifically requests a reference to a particular
 * context, generally to make it the {@code Lifespan} of some PL/Java object
 * that should be invalidated when the context is shut down.
 *<p>
 * Once an instance of this class has been instantiated and before it escapes to
 * calling Java code, it is registered for a shutdown callback on the
 * underlying PostgreSQL context so it can track its life cycle and invalidate,
 * when the time comes, any objects it has been used as the "owner" of.
 * All creation, traversal, and mutation has to happen on the PG thread. Once
 * published and while valid, an instance can be observed by other threads.
 *<p>
 * Events that can occur in the life of an ExprContext:
 *<dl>
 * <dt>Rescan<dd>PostgreSQL calls {@code ReScanExprContext} before re-scanning
 * its plan node from the start. Its callbacks are invoked and left
 * unregistered.
 * <dt>Free<dd>PostgreSQL calls {@code FreeExprContext} when done with the node,
 * whether on a successful path or in error cleanup. All callbacks are left
 * unregistered, but will have been invoked only in the successful case. In
 * either case, the backing native structure is then freed.
 *</dl>
 *<p>
 * Because {@code FreeExprContext} in error cleanup does not invoke callbacks,
 * it is quite possible for the native memory backing an ExprContext to vanish
 * without notice, so any values of interest from the structure (chiefly its
 * per-tuple and per-query memory contexts) must be eagerly fetched while it is
 * known valid. Its callback can become unregistered while thought to be
 * registered. And the chief need for the callback (for PL/Java's purposes),
 * to ensure that the {@link PLJavaBasedLanguage.SRFNext#close close()} method
 * of a set-returning function gets called, goes unmet in error cleanup if
 * relying on the ExprContext callback alone.
 *<p>
 * It is also unsafe to call {@code UnregisterExprContextCallback} at an
 * arbitrary time. Unregistering an already-unregistered callback is not
 * a problem, but unregistering a callback on an ExprContext that has been
 * freed without notice is crashworthy. Happily, the only time PL/Java might
 * explicitly unregister the callback is after a successful final call of
 * {@link PLJavaBasedLanguage.SRFNext#nextResult nextResult}, a moment when
 * error cleanup is not in progress and the ExprContext can be safely expected
 * to be live.
 *<p>
 * To ensure that {@link PLJavaBasedLanguage.SRFNext#close close()} is
 * called even in error cleanup, this object is itself given a state with
 * a {@code Lifespan}, namely, that of the per-query memory context, in which
 * the ExprContext is normally allocated, and expiration of that
 * {@code Lifespan} will also be treated as expiration of this one.
 *<p>
 * The approach is nonconservative: because an ExprContext can be (and normally
 * is) freed explicitly, there is no guarantee it is live whenever its
 * {@code Lifespan} is unexpired, but it is definitely gone when its
 * {@code Lifespan} is.
 *<p>
 * With that addition, it is not strictly necessary to have an action on a
 * successful final call of {@code nextResult}; one of the two events already
 * handled (the callback firing or the memory context being reset) will
 * eventually occur and mop up the state. The final-call action could be added
 * as an optimization to release the instance more promptly.
 */
public class ExprContextImpl extends LifespanImpl
{
	/**
	 * Map from integer key to an instance of this class.
	 *<p>
	 * A non-concurrent map suffices, as the uses are only on the PG thread
	 * (in known() within a doInPG(), and in callback() invoked from PG).
	 */
	private static ExprContextImpl[] s_map = new ExprContextImpl [ 2 ];
	private static final BitSet s_used = new BitSet(s_map.length);

	/**
	 * Returns a new instance, given the address of the underlying native
	 * structure and the per-query memory context that bounds its lifespan.
	 */
	static ExprContextImpl newInstance(long address, MemoryContext querycxt)
	{
		assert threadMayEnterPG() : "ExprContextImpl.newInstance thread";

		int key = s_used.nextClearBit(0);
		if ( s_map.length <= key )
			s_map = copyOf(s_map, key << 1);
		ExprContextImpl inst = new ExprContextImpl(querycxt, key);
		s_used.set(key);
		s_map [ key ] = inst;
		_registerCallback(address, key);
		return inst;
	}

	/*
	 * Called either by a native ExprContext callback or by nativeStateReleased
	 * on demise of the ExprContext's containing memory context.
	 *
	 * Those are two times it is surely safe to decache an ExprContext, as no
	 * callback with that key can be forthcoming.
	 *
	 * It would be conceivable as an optimization to arrange to unregister the
	 * callback and decache when a set-returning function has been successfully
	 * read to completion, rather than waiting for one of the two events above.
	 */
	private static void releaseAndDecache(int key)
	{
		assert threadMayEnterPG() : "ExprContextImpl.releaseAndDecache thread";

		ExprContextImpl inst = s_map [ key ];
		assert null != inst  &&  s_used.get(key) :
			"ExprContextImpl.releaseAndDecache bad key";
		s_map [ key ] = null;
		s_used.clear(key);
		inst.lifespanRelease();
	}

	private final State m_state;

	private ExprContextImpl(MemoryContext querycxt, int key)
	{
		m_state = new State(this, querycxt, key);
	}

	private static final class State extends DualState<ExprContextImpl>
	{
		private final int m_key;

		private State(ExprContextImpl referent, MemoryContext lifespan, int key)
		{
			super(referent, lifespan);
			m_key = key;
		}

		@Override
		protected void nativeStateReleased(boolean javaStateLive)
		{
			if ( ! javaStateLive )
				return;
			releaseAndDecache(m_key);
		}
	}

	private static native void _registerCallback(long address, int key);
}
