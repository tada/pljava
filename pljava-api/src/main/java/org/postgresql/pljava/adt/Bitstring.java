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

import java.nio.ByteBuffer;

import java.util.OptionalInt;

import org.postgresql.pljava.Adapter.Contract;

/**
 * Container for abstract-type functional interfaces in PostgreSQL's
 * {@code BITSTRING} type category.
 */
public interface Bitstring
{
	/**
	 * The {@code BIT} and {@code VARBIT} types' PostgreSQL semantics: the
	 * number of bits, and the sequence of bytes they're packed into.
	 */
	@FunctionalInterface
	public interface Bit<T> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 * @param nBits the actual number of bits in the value, not necessarily
		 * a multiple of 8. For type BIT, must equal the modifier nBits if
		 * specified; for VARBIT, must be equal or smaller.
		 * @param bytes a buffer of ceiling(nBits/8) bytes, not aliasing any
		 * internal storage, so safely readable (and writable, if useful for
		 * format conversion). Before accessing it in wider units, its byte
		 * order should be explicitly set. Within each byte, the logical order
		 * of the bits is from MSB to LSB; beware that this within-byte bit
		 * order is the reverse of what java.util.BitSet.valueOf(...) expects.
		 * When nBits is not a multiple of 8, the unused low-order bits of
		 * the final byte must be zero.
		 */
		T construct(int nBits, ByteBuffer bytes);

		/**
		 * Functional interface to obtain information from the PostgreSQL type
		 * modifier applied to the type.
		 */
		@FunctionalInterface
		interface Modifier<T>
		{
			/**
			 * Returns a {@code Bit} function possibly tailored ("curried")
			 * with the values from a PostgreSQL type modifier on the type.
			 * @param nBits for the BIT type, the exact number of bits the
			 * value must have; for VARBIT, the maximum. When not specified,
			 * the meaning is 1 for BIT, and unlimited for VARBIT.
			 */
			Bit<T> modify(OptionalInt nBits);
		}
	}
}
