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
package org.postgresql.pljava.pg;

import static java.lang.Integer.toUnsignedLong;

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;

import java.nio.charset.CharacterCodingException;

import static org.postgresql.pljava.internal.Backend.doInPG;
import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;

import org.postgresql.pljava.internal.CacheMap;
import org.postgresql.pljava.internal.Checked;
import static org.postgresql.pljava.internal.DualState.m;
import org.postgresql.pljava.internal.LifespanImpl;

import static org.postgresql.pljava.model.CharsetEncoding.SERVER_ENCODING;
import org.postgresql.pljava.model.MemoryContext;

import static org.postgresql.pljava.pg.DatumUtils.addressOf;
import static org.postgresql.pljava.pg.DatumUtils.asReadOnlyNativeOrder;
import static org.postgresql.pljava.pg.DatumUtils.fetchPointer;
import static org.postgresql.pljava.pg.DatumUtils.mapCString;
import static org.postgresql.pljava.pg.DatumUtils.mapFixedLength;
import static org.postgresql.pljava.pg.DatumUtils.storePointer;

import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_DATUM;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_MCTX;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_MCTX_name;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_MCTX_ident;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_MCTX_firstchild;

/*
 * CurrentMemoryContext is declared in utils/palloc.h and defined in
 * utils/mmgr/mcxt.c along with the rest of these, which are puzzlingly
 * declared in utils/memutils.h instead.
 *
 * TopMemoryContext // can be made a static
 * ErrorContext
 * PostmasterContext
 * CacheMemoryContext
 * MessageContext
 * TopTransactionContext
 * CurTransactionContext
 * PortalContext // transient; for active portal
 *
 * The structure of a context is in nodes/memnodes.h
 */

/**
 * A lazily-created mirror of a PostgreSQL MemoryContext.
 *<p>
 * PostgreSQL is creating, resetting, and deleting memory contexts all the time,
 * and most of them will never be visible in PL/Java; one of these objects only
 * gets created when Java code specifically requests a reference to a particular
 * context, generally to make it the {@code Lifespan} of some PL/Java object
 * that should be invalidated when the context goes away.
 *<p>
 * Once an instance of this class has been instantiated and before it escapes to
 * calling Java code, it must be registered for a reset/delete callback on the
 * underlying PostgreSQL context so it can track its life cycle and invalidate,
 * when the time comes, any objects it has been used as the "owner" of.
 * (Instances that might be transiently created here, say in traversing the
 * context tree, and won't escape, don't need the full registration treatment.)
 * All creation, traversal, and mutation has to happen on the PG thread. Once
 * published and while valid, an instance can be observed by other threads.
 *<p>
 * Events that can occur in the life of a memory context:
 *<dl>
 * <dt>SetParent<dd>It can be made a child of a context other than its original
 * parent. (It can also be given the null parent, immediately before being
 * deleted; this happens <em>after</em> invocation of the callback, though, so
 * gives the callback routine no help in determining what is happening.)
 * <dt>Reset<dd>It can have all of its descendant contexts deleted and its own
 * allocations freed, but remain in existence itself.
 * <dt>ResetOnly<dd>It can have its own allocations freed, with no effect on
 * descendant contexts.
 * <dt>ResetChildren<dd>All of its children can recursively get
 * the ResetChildren treatment and in addition be ResetOnly themselves, but
 * with no effect on this context itself.
 * <dt>Delete<dd>All of its descendants, and last this context itself, go away.
 * <dt>DeleteChildren<dd>All of its descendants go away, with no other effect
 * on this context.
 *</dl>
 *<p>
 * Complicating the lifecycle tracking, PostgreSQL will invoke exactly the same
 * callback, with exactly the same parameter, whether the context in question
 * is being deleted or reset. In the reset case, the context is still valid
 * after the callback; in the delete case, it is not. The difference is not
 * important for the objects "owned" by this context; they're to be invalidated
 * in either case. But it leaves the callback with a puzzle to solve regarding
 * what to do with this object itself.
 *<p>
 * A few related observations:
 *<ul>
 * <li>Within the callback itself, the context is still valid; its native struct
 *  may still be accessed safely, and its parent, child, and sibling links
 *  are sane.
 * <li>If the {@code firstchild} link is non-null, this is definitely a reset
 *  and not a delete. In any delete case, all children will already be gone.
 * <li>Conversely, though, absence of children does not prove this is deletion.
 * <li>Hence, the callback will leave this mirror in either a definitely-valid
 *  or a maybe-deleted state.
 * <li>In either state, its callback will have been deregistered. It must
 *  re-register the callback in the definitely-valid state. In the maybe-deleted
 *  state, it will receive no further callbacks, unless it can later be found
 *  revivifiable and the callback is re-registered.
 * <li>Because the callback can only proceed when none of this ResourceOwner's
 *  owned objects are pinned, and they all will be invalidated and delinked from
 *  it, it will always be the owner of no objects when the callback completes.
 *  A possible approach then is to treat maybe-deleted as definitely-deleted
 *  always, invalidate and unpublish this object, and require a Java caller to
 *  obtain a new mirror of the same context if indeed it still exists and is
 *  wanted. Efforts to retain and possibly revivify the mirror could be viewed
 *  as optimizations. (They could have API consequences, though; without
 *  revivification, the object would have to be made invalid and throw an
 *  exception if used by Java code that had held on to a reference, even if only
 *  a reset was intended. Revivification could allow the retained reference
 *  to remain usable.)
 * <li>Once the callback completes, the maybe-deleted state must be treated as
 *  completely forbidding any access to the mapped memory. If there is any
 *  information that could be useful in a later revivification decision, it must
 *  be collected by the callback and saved in the Java object state.
 * <li>If the callback for a maybe-deleted mirror saves a reference to (a
 *  published Java mirror of) its parent at callback time and, at a later
 *  attempt to use the object, the parent is found to be valid and have this
 *  object as a child, revivification is supported.
 * <li>That child-of-valid parent test can be applied recursively if the parent
 *  is also found to be maybe-deleted. But the test can spuriously fail if a
 *  (reset-but-still-valid) context was reparented after the callback saved its
 *  parent reference.
 * <li>Obtaining the reference again from one of the PostgreSQL globals or from
 *  a valid PostgreSQL data structure clearly re-establishes that it is valid.
 *  (Whether it is "the same" context is more a philosophical point; whether
 *  reset or deleted, it was left with no allocations and no owned objects at
 *  that point, so questions of its "identity" may not be critical. Its name and
 *  ident may have changed. Its operations (the 'type' of context) may also have
 *  changed, but may be a lower-level detail than needs attention here.
 *</ul>
 */
public class MemoryContextImpl extends LifespanImpl
implements MemoryContext, LifespanImpl.Addressed
{
	static final ByteBuffer[] s_knownContexts;

	/**
	 * Map from native address of a PostgreSQL MemoryContext to an instance
	 * of this class.
	 *<p>
	 * A non-concurrent map suffices, as the uses are only on the PG thread
	 * (in known() within a doInPG(), and in callback() invoked from PG).
	 */
	static final CacheMap<MemoryContextImpl> s_map =
		CacheMap.newThreadConfined(
			() -> ByteBuffer.allocate(SIZEOF_DATUM).order(nativeOrder()));

	static
	{
		ByteBuffer[] bs = EarlyNatives._window(ByteBuffer.class);
		/*
		 * The first one windows CurrentMemoryContext. Set the correct byte
		 * order but do not make it read-only; operations may be provided
		 * for setting it.
		 */
		bs[0] = bs[0].order(nativeOrder());
		/*
		 * The rest are made native-ordered and read-only.
		 */
		for ( int i = 1; i < bs.length; ++ i )
			bs[i] = asReadOnlyNativeOrder(bs[i]);
		s_knownContexts = bs;
	}

	static MemoryContext known(int which)
	{
		ByteBuffer global = s_knownContexts[which];
		return doInPG(() ->
		{
			long ctx = fetchPointer(global, 0);
			if ( 0 == ctx )
				return null;
			return fromAddress(ctx);
		});
	}

	public static MemoryContext fromAddress(long address)
	{
		assert threadMayEnterPG() : m("MemoryContext thread");

		/*
		 * Cache strongly; see LifespanImpl javadoc.
		 */
		return s_map.stronglyCache(
			b ->
			{
				if ( 4 == SIZEOF_DATUM )
					b.putInt((int)address);
				else
					b.putLong(address);
			},
			b ->
			{
				MemoryContextImpl c = new MemoryContextImpl(address);
				EarlyNatives._registerCallback(address);
				return c;
			}
		);
	}

	/**
	 * Specialized method intended, so far, only for {@code PgSavepoint}'s use.
	 *<p>
	 * Only to be called on the PG thread.
	 */
	public static long getCurrentRaw()
	{
		assert threadMayEnterPG() : m("MemoryContext thread");
		return fetchPointer(s_knownContexts[0], 0);
	}

	/**
	 * Even more specialized method intended, so far, only for
	 * {@code PgSavepoint}'s use.
	 *<p>
	 * Only to be called on the PG thread.
	 */
	public static void setCurrentRaw(long context)
	{
		assert threadMayEnterPG() : m("MemoryContext thread");
		storePointer(s_knownContexts[0], 0, context);
	}

	/**
	 * Change the current memory context to <var>c</var>, for use in
	 * a {@code try}-with-resources to restore the prior context on exit
	 * of the block.
	 */
	public static Checked.AutoCloseable<RuntimeException>
		allocatingIn(MemoryContext c)
	{
		assert threadMayEnterPG() : m("MemoryContext thread");
		MemoryContextImpl ci = (MemoryContextImpl)c;
		long prior = getCurrentRaw();
		Checked.AutoCloseable<RuntimeException> ac = () -> setCurrentRaw(prior);
		setCurrentRaw(ci.m_address);
		return ac;
	}

	/*
	 * Called only from JNI.
	 *
	 * See EarlyNatives._registerCallback below for discussion of why the native
	 * context address is used as the callback argument.
	 *
	 * Deregistering the callback is a non-issue: that has already happened
	 * when this call is made.
	 */
	private static void callback(long ctx)
	{
		CacheMap.Entry<MemoryContextImpl> e = s_map.find(
			b ->
			{
				if ( 4 == SIZEOF_DATUM )
					b.putInt((int)ctx);
				else
					b.putLong(ctx);
			}
		);

		if ( null == e )
			return;

		MemoryContextImpl c = e.get();
		if ( null == c )
			return;

		/*
		 * invalidate() has to make a (conservative) judgment whether this
		 * callback reflects a 'reset' or 'delete' operation, and return true
		 * if the mapping should be removed from the cache. It should return
		 * false only if the case is provably a reset only, or (possible future
		 * work) if it can be placed in a maybe-deleted state and possibly
		 * revivified later. Otherwise, the instance must be conservatively
		 * marked invalid, and dropped from the cache.
		 */
		if ( c.invalidate() )
			e.remove();
	}

	private final ByteBuffer m_context;
	/**
	 * The address of the context, even though technically redundant.
	 *<p>
	 * A JNI function can easily retrieve it from the {@code ByteBuffer}, but
	 * by keeping the value here, sometimes a JNI call can be avoided.
	 */
	private final long m_address;
	private String m_ident;
	private final String m_name;

	private MemoryContextImpl(long context)
	{
		m_address = context;
		m_context = mapFixedLength(context, SIZEOF_MCTX);
		String s;

		long p = fetchPointer(m_context, OFFSET_MCTX_ident);

		try
		{
			if ( 0 == p )
				s = null;
			else
				s = SERVER_ENCODING.decode(mapCString(p)).toString();
		}
		catch ( CharacterCodingException e )
		{
			s = "[unexpected encoding]";
		}
		m_ident = s;

		p = fetchPointer(m_context, OFFSET_MCTX_name);

		try
		{
			if ( 0 == p )
				s = null;
			else
				s = SERVER_ENCODING.decode(mapCString(p)).toString();
		}
		catch ( CharacterCodingException e )
		{
			s = "[unexpected encoding]";
		}
		m_name = s;
	}

	@Override
	public long address()
	{
		if ( 0 == m_context.limit() )
			throw new IllegalStateException(
				"address may not be taken of invalidated MemoryContext");
		return m_address;
	}

	@Override
	public String toString()
	{
		return String.format("MemoryContext[%s,%s]", m_name, m_ident);
	}

	/*
	 * Determine (or conservatively guess) whether this context is being deleted
	 * or merely reset, and perform (in either case) the nativeRelease() actions
	 * for dependent objects.
	 *
	 * Return false only if this is provably a reset only, or (possible future
	 * work) if it can be placed in a maybe-deleted state and possibly
	 * revivified later. Otherwise, the instance must be conservatively
	 * marked invalid, and true returned to drop it from the cache.
	 */
	private boolean invalidate()
	{
		lifespanRelease();

		/*
		 * The one easy rule is that if there is any child, the case can only be
		 * 'reset'.
		 */
		if ( 0 != fetchPointer(m_context, OFFSET_MCTX_firstchild) )
			return false;

		/*
		 * Rather than a separate field to record invalidation status, set the
		 * windowing ByteBuffer's limit to zero. This will ensure an
		 * IndexOutOfBoundsException on future attempts to read through it,
		 * without cluttering the code with additional tests.
		 */
		m_context.limit(0);
		return true;
	}

	private static class EarlyNatives
	{
		/**
		 * Returns an array of ByteBuffer, one covering each PostgreSQL known
		 * memory context global, in the same order as the arbitrary indices
		 * defined in the API class CatalogObject.Factory, which are what will
		 * be passed to the known() method.
		 *<p>
		 * Takes a {@code Class<ByteBuffer>} argument, to save the native code
		 * a lookup.
		 */
		private static native ByteBuffer[] _window(Class<ByteBuffer> component);

		/**
		 * Register a memory context callback for the context with the given
		 * native address in PostgreSQL.
		 *<p>
		 * A callback is allowed one {@code void *}-sized argument to receive
		 * when called back. If that were a JNI global reference, for example,
		 * we could arrange for {@link #callback callback} to be invoked with
		 * the affected Java instance directly. But {@code callback} will be
		 * wanting the native address anyway in order to look it up and remove
		 * it from the CacheMap, and JNI's global references surely involve
		 * their own layer of mapping under the JVM's hood. So we may as well
		 * keep it simple and use our one allowed arg to hold the context
		 * address itself, which is necessary anyway, and sufficient.
		 */
		private static native void _registerCallback(long nativeAddress);
	}

	//possibly useful operations:
	//MemoryContext parent();
	//<B> B palloc(Class<? super B> api, long size); // ByteBuffer.class for now
	// flags HUGE 1 NO_OOM 2 ZERO 4
	//<B> B repalloc(B chunk, long size);
	// others from palloc.h ?
	// AutoCloseable switchedTo();
	// reset/delete/resetonly/resetchildren/deletechildren/setparent
	//  from utils/memutils.h? only if PL/Java created the context?
	//  require intent to delete/reset to be declared on creation, and prevent
	//  such a context being used in switchedTo()?
	// AllocSetContextCreate/SlabContextCreate/GenerationContextCreate
	// protect some operations as protected methods of Adapter?
}
