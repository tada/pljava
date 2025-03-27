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
package org.postgresql.pljava.adt;

import java.net.StandardProtocolFamily;

import org.postgresql.pljava.Adapter.Contract;

/**
 * Container for abstract-type functional interfaces in PostgreSQL's
 * {@code NETWORK} type category (and MAC addresses, which, for arcane reasons,
 * are not in that category).
 */
public interface Network
{
	/**
	 * The {@code INET} and {@code CIDR} types' PostgreSQL semantics: the
	 * family ({@code INET} or {@code INET6}), the number of network prefix
	 * bits, and the address bytes in network byte order.
	 */
	@FunctionalInterface
	public interface Inet<T> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 * @param addressFamily INET or INET6
		 * @param networkPrefixBits nonnegative, not greater than 32 for INET
		 * or 128 for INET6 (either maximum value indicates the address is for
		 * a single host rather than a network)
		 * @param networkOrderAddress the address bytes in network order. When
		 * the type is CIDR, only the leftmost networkPrefixBits bits are
		 * allowed to be nonzero. The array does not alias any internal storage
		 * and may be used as desired.
		 */
		T construct(
			StandardProtocolFamily addressFamily, int networkPrefixBits,
			byte[] networkOrderAddress);
	}

	/**
	 * The {@code macaddr} and {@code macaddr8} types' PostgreSQL semantics:
	 * a byte array (6 or 8 bytes, respectively)., of which byte 0 is the one
	 * appearing first in the text representation (and stored in the member
	 * named <var>a</var> of the C struct).
	 */
	@FunctionalInterface
	public interface MAC<T> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 * @param address array of 6 (macaddr) or 8 (macaddr8) bytes, of which
		 * byte 0 is the one appearing first in the text representation (and
		 * stored in the member named <var>a</var> of the C struct). The array
		 * does not alias any internal storage and may be used as desired.
		 */
		T construct(byte[] address);
	}
}
