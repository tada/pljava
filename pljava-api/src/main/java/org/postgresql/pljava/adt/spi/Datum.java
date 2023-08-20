/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.adt.spi;

import java.io.Closeable;
import java.io.InputStream;

import java.nio.ByteBuffer;

import java.sql.SQLException;

import org.postgresql.pljava.Adapter; // for javadoc
import org.postgresql.pljava.model.Attribute;

/**
 * Raw access to the contents of a PostgreSQL datum.
 *<p>
 * For type safety, only {@link Adapter Adapter} implementations should be
 * able to obtain a {@code Datum}, and should avoid leaking it to other code.
 */
public interface Datum extends Closeable
{
	/**
	 * Use the given {@link Verifier} to confirm that the {@code Datum} content
	 * is well-formed, throwing an exception if not.
	 */
	void verify(Verifier.OfBuffer v) throws SQLException;

	/**
	 * Use the given {@link Verifier} to confirm that the {@code Datum} content
	 * is well-formed, throwing an exception if not.
	 */
	void verify(Verifier.OfStream v) throws SQLException;

	/**
	 * Interface through which PL/Java code reads the content of an existing
	 * PostgreSQL datum.
	 */
	interface Input<T extends InputStream & Datum> extends Datum
	{
		default void pin() throws SQLException
		{
		}

		default boolean pinUnlessReleased()
		{
			return false;
		}

		default void unpin()
		{
		}

		/**
		 * Returns a read-only {@link ByteBuffer} covering the content of the
		 * datum.
		 *<p>
		 * When the datum is a {@code varlena}, the "content" does not include
		 * the four-byte header. When implementing an adapter for a varlena
		 * datatype, note carefully whether offsets used in the PostgreSQL C
		 * code are relative to the start of the content or the start of the
		 * varlena overall. If the latter, they will need adjustment when
		 * indexing into the {@code ByteBuffer}.
		 *<p>
		 * If the byte order of the buffer will matter, it should be explicitly
		 * set.
		 *<p>
		 * The buffer may window native memory allocated by PostgreSQL, so
		 * {@link #pin pin()} and {@link #unpin unpin()} should surround
		 * accesses through it. Like {@code Datum} itself, the
		 * {@code ByteBuffer} should be used only within an {@code Adapter}, and
		 * not exposed to other code.
		 */
		ByteBuffer buffer() throws SQLException;

		/**
		 * Returns an {@link InputStream} that presents the same bytes contained
		 * in the buffer returned by {@link #buffer buffer()}.
		 *<p>
		 * When necessary, the {@code InputStream} will handle pinning the
		 * buffer when reading, so the {@code InputStream} can safely be exposed
		 * to other code, if it is a reasonable way to present the contents of
		 * the datatype in question.
		 *<p>
		 * The stream supports {@code mark} and {@code reset}.
		 */
		T inputStream() throws SQLException;
	}

	/**
	 * Empty superinterface of {@code Accessor.Deformed} and
	 * {@code Accessor.Heap}, which are erased at run time but help distinguish,
	 * in source code, which memory layout convention an {@code Accessor}
	 * is tailored for.
	 */
	interface Layout
	{
	}

	/**
	 * Accessor for a {@code Datum} located, at some offset, in
	 * memory represented by a {@code <B>} object.
	 *<p>
	 * {@code <B>} is a type variable to anticipate future memory abstractions
	 * like the incubating {@code MemorySegment} from JEP 412. The present
	 * implementation will work with any {@code <B>} that you want as long
	 * as it is {@code java.nio.ByteBuffer}.
	 *<p>
	 * Given an {@code Accessor} instance properly selected for the memory
	 * layout, datum width, type length, and by-value/by-reference passing
	 * convention declared for a given {@link Attribute Attribute}, methods on
	 * the {@code Accessor} are available to retrieve the individual datum
	 * in {@code Datum} form (essentially another {@code <B>} of exactly
	 * the length of the datum, wrapped with methods to avoid access outside
	 * of its lifetime), or as any Java primitive type appropriate to
	 * the datum's width. A {@code get} method of the datum's exact width or
	 * wider may be used (except for {@code float} and {@code double}, which
	 * only work for width exactly 4 or 8 bytes, respectively).
	 *<p>
	 * PostgreSQL only allows power-of-two widths up to {@code SIZEOF_DATUM} for
	 * a type that specifies the by-value convention, and so an {@code Accessor}
	 * for the by-value case only supports those widths. An {@code Accessor} for
	 * the by-reference case supports any size, with direct access as a Java
	 * primitive supported for any size up to the width of a Java long.
	 *<p>
	 * {@code getBoolean} can be used for any width the {@code Accessor}
	 * supports up to the width of Java long, and the result will be true
	 * if the value has any 1 bits.
	 *<p>
	 * Java {@code long} and {@code int} are always treated as
	 * signed by the language (though unsigned operations are available as
	 * methods), but have paired methods here to explicitly indicate which
	 * treatment is intended. The choice can affect the returned value when
	 * fetching a value as a primitive type that is wider than its type's
	 * declared length. Paired methods for {@code byte} are not provided because
	 * a byte is not wider than any type's length. When a type narrower than
	 * {@code SIZEOF_DATUM} is stored (in the {@code Deformed} layout), unused
	 * high bits are stored as zero. This should not strictly matter, as
	 * PostgreSQL strictly ignores the unused high bits, but it is consistent
	 * with the way PostgreSQL declares {@code Datum} as an unsigned integral
	 * type.
	 * 
	 * @param <B> type of the memory abstraction used. Accessors will be
	 * available supporting {@code ByteBuffer}, and may be available supporting
	 * a newer abstraction like {@code MemorySegment}.
	 * @param <L> a subinterface of {@code Layout}, either {@code Deformed} or
	 * {@code Heap}, indicating which {@code TupleTableSlot} layout the
	 * {@code Accessor} is intended for, chiefly as a tool for compile-time
	 * checking that they haven't been mixed up.
	 */
	interface Accessor<B,L extends Layout>
	{
		Datum.Input getDatum(B buffer, int offset, Attribute a);

		long getLongSignExtended(B buffer, int offset);

		long getLongZeroExtended(B buffer, int offset);

		double getDouble(B buffer, int offset);

		int getIntSignExtended(B buffer, int offset);

		int getIntZeroExtended(B buffer, int offset);

		float getFloat(B buffer, int offset);

		short getShort(B buffer, int offset);

		char getChar(B buffer, int offset);

		byte getByte(B buffer, int offset);

		boolean getBoolean(B buffer, int offset);

		/**
		 * An accessor for use with a 'deformed' (array-of-{@code Datum})
		 * memory layout.
		 *<p>
		 * When using a 'deformed' accessor, the caller is responsible for
		 * passing an {@code offset} value that is an integral multiple of
		 * {@code SIZEOF_DATUM} from where the array-of-{@code Datum} starts.
		 */
		interface Deformed extends Layout
		{
		}

		/**
		 * An accessor for use with a heap-tuple styled, flattened,
		 * memory layout.
		 *<p>
		 * When using a heap accessor, the caller is responsible for passing an
		 * {@code offset} value properly computed from the sizes of preceding
		 * members and the alignment of the member to be accessed.
		 */
		interface Heap extends Layout
		{
		}
	}
}
