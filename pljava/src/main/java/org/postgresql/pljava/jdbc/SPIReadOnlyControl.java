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
package org.postgresql.pljava.jdbc;

/**
 * An extended interface on {@code Statement} (accessible with {@code unwrap()})
 * allowing control of the {@code read_only} flag that PostgreSQL SPI will see
 * when the statement is next executed.
 *<p>
 * Currently an internal interface, not in {@code pljava-api}, as the known need
 * so far is just for the internal class loader.
 */
public interface SPIReadOnlyControl
{
	/**
	 * Specify that the statement, when next executed, will have the
	 * behavior recommended in the PostgreSQL SPI documentation:
	 * {@code read_only} will be set to {@code true} if the currently-executing
	 * PL/Java function is declared {@code IMMUTABLE}, {@code false} otherwise.
	 */
	void defaultReadOnly();

	/**
	 * Specify that the statement, when next executed, will have have
	 * {@code read_only} set to {@code true} unconditionally.
	 */
	void forceReadOnly();

	/**
	 * Specify that the statement, when next executed, will have have
	 * {@code read_only} set to {@code false} unconditionally.
	 */
	void clearReadOnly();
}

