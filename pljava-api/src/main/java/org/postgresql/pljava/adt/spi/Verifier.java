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
package org.postgresql.pljava.adt.spi;

import java.io.InputStream;

import java.nio.ByteBuffer;

/**
 * A {@code Verifier} verifies the proper form of content written to a
 * {@code Datum}.
 *<p>
 * This is necessary only when the correctness of the written stream may be
 * doubtful, as when an API spec requires exposing a method for client code
 * to write arbitrary bytes. If a type implementation exposes only
 * type-appropriate operations to client code, and always controls the byte
 * stream written to the varlena, the {@code NOOP} verifier can be used.
 *<p>
 * There are no methods accepting an unextended {@code Verifier}, only those
 * accepting one of its contained functional interfaces
 * {@link OfBuffer OfBuffer} and {@link OfStream OfStream}.
 *<p>
 * A type-specific verifier must supply a {@code verify} method that reads all
 * of the content and completes normally if it is a complete and well-formed
 * representation of the type. Otherwise, it must throw an exception.
 *<p>
 * An {@code OfBuffer} verifier must leave the buffer's position equal to the
 * value of the buffer's limit when the verifier was entered. An
 * {@code OfStream} verifier must leave the stream at end of input. An
 * {@code OfStream} verifier may assume that the supplied {@code InputStream}
 * supports {@code mark} and {@code reset} efficiently.
 *<p>
 * An {@code OfStream} verifier may execute in another thread concurrently with
 * the writing of the content by the adapter.
 * <em>Its {@code verify} method must not interact with PostgreSQL.</em>
 */
public interface Verifier
{
	/**
	 * A verifier interface to be used when the {@code ByteBuffer} API provides
	 * the most natural interface for manipulating the content.
	 *<p>
	 * Such a verifier will be run only when the content has been completely
	 * produced.
	 */
	@FunctionalInterface
	interface OfBuffer extends Verifier
	{
		/**
		 * Completes normally if the verification succeeds, otherwise throwing
		 * an exception.
		 *<p>
		 * The buffer's {@code position} when this method returns must equal the
		 * value of the buffer's {@code limit} when the method was called.
		 */
		void verify(ByteBuffer b) throws Exception;
	}

	/**
	 * A verifier interface to be used when the {@code InputStream} API provides
	 * the most natural interface for manipulating the content.
	 *<p>
	 * Such a verifier may be run concurrently in another thread while the
	 * data type adapter is writing the content. It must therefore be able to
	 * verify the content without interacting with PostgreSQL.
	 */
	@FunctionalInterface
	interface OfStream extends Verifier
	{
		/**
		 * Completes normally if the verification succeeds, otherwise throwing
		 * an exception.
		 *<p>
		 * The method must leave the stream at end-of-input. It may assume that
		 * the stream supports {@code mark} and {@code reset} efficiently.
		 * It must avoid interacting with PostgreSQL, in case it is run in
		 * another thread concurrently with the production of the content.
		 */
		void verify(InputStream s) throws Exception;
	}
}
