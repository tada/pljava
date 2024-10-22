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

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;

import static org.postgresql.pljava.internal.Backend.doInPG;
import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;

import org.postgresql.pljava.internal.CacheMap;
import org.postgresql.pljava.internal.DualState;
import static org.postgresql.pljava.internal.DualState.m;
import org.postgresql.pljava.internal.LifespanImpl;

import org.postgresql.pljava.model.ResourceOwner;

import static org.postgresql.pljava.pg.DatumUtils.asReadOnlyNativeOrder;
import static org.postgresql.pljava.pg.DatumUtils.fetchPointer;
import static org.postgresql.pljava.pg.DatumUtils.storePointer;

import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_DATUM;

/**
 * A PostgreSQL {@code ResourceOwner}, one of the things that can serve as
 * a PL/Java {@code Lifespan}.
 *<p>
 * The designer of this PostgreSQL object believed strongly in encapsulation,
 * so very strongly that there is not any C header exposing its structure,
 * and any operations to be exposed here will have to be calls through JNI.
 * While a {@code ResourceOwner} does have a name (which will appear in log
 * messages involving it), there's not even an exposed API to retrieve that.
 * So this object will be not much more than a stub, known by its address
 * and capable of serving as a PL/Java lifespan.
 */
public class ResourceOwnerImpl extends LifespanImpl
implements ResourceOwner, LifespanImpl.Addressed
{
	static final ByteBuffer[] s_knownOwners;

	static final CacheMap<ResourceOwnerImpl> s_map =
		CacheMap.newThreadConfined(() -> ByteBuffer.allocate(SIZEOF_DATUM));

	static
	{
		ByteBuffer[] bs = EarlyNatives._window(ByteBuffer.class);
		/*
		 * The first one windows CurrentResourceOwner. Set the correct byte
		 * order but do not make it read-only; operations may be provided
		 * for setting it.
		 */
		bs[0] = bs[0].order(nativeOrder());
		/*
		 * The rest are made native-ordered and read-only.
		 */
		for ( int i = 1; i < bs.length; ++ i )
			bs[i] = asReadOnlyNativeOrder(bs[i]);
		s_knownOwners = bs;
	}

	static ResourceOwner known(int which)
	{
		ByteBuffer global = s_knownOwners[which];
		return doInPG(() ->
		{
			long rso = fetchPointer(global, 0);
			if ( 0 == rso )
				return null;

			return fromAddress(rso);
		});
	}

	public static ResourceOwner fromAddress(long address)
	{
		assert threadMayEnterPG() : m("ResourceOwner thread");

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
			b -> new ResourceOwnerImpl(b)
		);
	}

	/**
	 * Specialized method intended, so far, only for
	 * {@code PgSavepoint}'s use.
	 *<p>
	 * Only to be called on the PG thread.
	 */
	public static long getCurrentRaw()
	{
		assert threadMayEnterPG() : m("ResourceOwner thread");
		return fetchPointer(s_knownOwners[0], 0);
	}

	/**
	 * Even more specialized method intended, so far, only for
	 * {@code PgSavepoint}'s use.
	 *<p>
	 * Only to be called on the PG thread.
	 */
	public static void setCurrentRaw(long owner)
	{
		assert threadMayEnterPG() : m("ResourceOwner thread");
		storePointer(s_knownOwners[0], 0, owner);
	}

	/*
	 * Called only from JNI.
	 */
	private static void callback(long nativePointer)
	{
		CacheMap.Entry<ResourceOwnerImpl> e = s_map.find(
			b ->
			{
				if ( 4 == SIZEOF_DATUM )
					b.putInt((int)nativePointer);
				else
					b.putLong(nativePointer);
			}
		);

		if ( null == e )
			return;

		ResourceOwnerImpl r = e.get();
		if ( null == r )
			return;

		r.invalidate();
		e.remove();
	}

	/**
	 * The {@code ByteBuffer} keying this object.
	 *<p>
	 * As described for {@code CatalogObjectImpl}, as we'd like to be able
	 * to retrieve the address, and that's what's in the ByteBuffer that is
	 * held as the key in the CacheMap anyway, just keep a reference to that
	 * here. We must treat it as read-only, even if it hasn't officially
	 * been made that way.
	 *<p>
	 * The contents are needed only for non-routine operations like
	 * {@code toString}, where an extra {@code fetchPointer} doesn't
	 * break the bank.
	 */
	private final ByteBuffer m_key;
	private boolean m_valid = true;

	private ResourceOwnerImpl(ByteBuffer key)
	{
		m_key = key;
	}

	@Override // Addressed
	public long address()
	{
		if ( m_valid )
			return fetchPointer(m_key, 0);
		throw new IllegalStateException(
			"address may not be taken of invalidated ResourceOwner");
	}

	@Override
	public String toString()
	{
		return String.format("%s[%#x]",
			super.toString(), fetchPointer(m_key, 0));
	}

	private void invalidate()
	{
		lifespanRelease();
		m_valid = false;
		// nothing else to do here.
	}

	private static class EarlyNatives
	{
		/**
		 * Returns an array of ByteBuffer, one covering each PostgreSQL
		 * known resource owner global, in the same order as the arbitrary
		 * indices defined in the API class CatalogObject.Factory, which are
		 * what will be passed to the known() method.
		 *<p>
		 * Takes a {@code Class<ByteBuffer>} argument, to save the native
		 * code a lookup.
		 */
		private static native ByteBuffer[] _window(
			Class<ByteBuffer> component);
	}
}
